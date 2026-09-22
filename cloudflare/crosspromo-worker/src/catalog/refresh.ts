/**
 * Background catalog refresh (Cloudflare Cron Trigger).
 *
 * Flow: download listing -> discover ids -> fetch metadata (bounded
 * concurrency, per-app failures tolerated) -> normalize -> validate ->
 * compare with previous catalog -> persist ONLY if acceptable -> diagnostics.
 *
 * A broken scraper result (0 apps, or a dramatic unexplained drop) NEVER
 * replaces the served catalog; the last-known-good data stays live.
 */
import { normalizeApp, validateRefresh } from "./parser";
import { PublicPlayStoreCatalogSource, fetchAllMetadata } from "./provider";
import {
  META_KEYS,
  logRefresh,
  readAllApps,
  readPreviousState,
  writeCatalog,
  writeMeta,
} from "./store";
import type { AppEntry, WorkerEnv } from "../types";

export interface RefreshResult {
  status: "success" | "rejected" | "error";
  source: string;
  discovered: number;
  accepted: number;
  rejected: number;
  metadataFailures: number;
  detail: string;
}

export function logStructured(level: string, msg: string, fields?: Record<string, unknown>): void {
  console.log(JSON.stringify({ level, msg, ts: new Date().toISOString(), ...fields }));
}

export async function runCatalogRefresh(env: WorkerEnv): Promise<RefreshResult> {
  const startedAt = new Date().toISOString();
  const developerPageUrl =
    env.DEVELOPER_PAGE_URL ?? "https://play.google.com/store/apps/developer?id=Hartmann+Studios";
  const source = new PublicPlayStoreCatalogSource(developerPageUrl, env.DEVELOPER_ID);
  const finish = async (r: Omit<RefreshResult, "source">): Promise<RefreshResult> => {
    const full: RefreshResult = { source: source.sourceName, ...r };
    try {
      await logRefresh(env.KV, {
        startedAt,
        finishedAt: new Date().toISOString(),
        source: full.source,
        discovered: full.discovered,
        accepted: full.accepted,
        rejected: full.rejected,
        metadataFailures: full.metadataFailures,
        status: full.status,
        detail: full.detail.slice(0, 2000),
      });
      await writeMeta(env.KV, META_KEYS.lastRefreshAttempt, new Date().toISOString());
    } catch (e) {
      logStructured("error", "catalog refresh bookkeeping failed", { error: String(e) });
    }
    return full;
  };

  let discovered: string[];
  try {
    const result = await source.discoverApps();
    discovered = result.packageIds;
  } catch (e) {
    const detail = `discovery fetch failed: ${String(e).slice(0, 300)}`;
    logStructured("error", "catalog refresh discovery failed", { detail });
    return finish({
      status: "error",
      discovered: 0,
      accepted: 0,
      rejected: 0,
      metadataFailures: 0,
      detail,
    });
  }

  // Metadata refresh: per-app failures are tolerated and recorded.
  const { meta, failures } = await fetchAllMetadata(source, discovered);
  const nowIso = new Date().toISOString();
  let previousCount = 0;
  let prevState = new Map<string, { firstDiscoveredAt: string; enabledForPromotion: boolean; promotionMultiplier: number }>();
  try {
    prevState = await readPreviousState(env.KV);
    const prevApps = await readAllApps(env.KV);
    previousCount = prevApps.length;
  } catch (e) {
    logStructured("error", "catalog refresh read of previous catalog failed", { error: String(e) });
  }

  const apps: AppEntry[] = [];
  const rejected: string[] = [];
  for (const pkg of discovered) {
    const scraped = meta.get(pkg);
    if (!scraped) continue; // counted in failures below
    const app = normalizeApp(pkg, scraped, nowIso, prevState.get(pkg));
    if (app) apps.push(app);
    else rejected.push(pkg);
  }

  const gate = validateRefresh(previousCount, apps, { discovered: discovered.length });
  if (!gate.ok) {
    logStructured("warn", "catalog refresh rejected by safety gate", {
      previousCount,
      discovered: discovered.length,
      accepted: apps.length,
      reason: gate.reason,
    });
    return finish({
      status: "rejected",
      discovered: discovered.length,
      accepted: apps.length,
      rejected: rejected.length,
      metadataFailures: failures.length,
      detail: `${gate.reason}; rejected=[${rejected.slice(0, 10).join(",")}]; metaFailures=[${failures.slice(0, 10).join(",")}]`,
    });
  }

  try {
    await writeCatalog(env.KV, apps);
    await writeMeta(env.KV, META_KEYS.lastSuccessfulRefresh, nowIso);
    await writeMeta(env.KV, META_KEYS.lastKnownGoodRefresh, nowIso);
    await writeMeta(env.KV, META_KEYS.lastKnownGoodCount, String(apps.length));
    await writeMeta(env.KV, META_KEYS.lastSource, source.sourceName);
  } catch (e) {
    const detail = `catalog persist failed: ${String(e).slice(0, 300)}`;
    logStructured("error", detail);
    return finish({
      status: "error",
      discovered: discovered.length,
      accepted: apps.length,
      rejected: rejected.length,
      metadataFailures: failures.length,
      detail,
    });
  }

  // Record first-discovery timestamps for genuinely new packages.
  for (const app of apps) {
    if (!prevState.has(app.packageName)) {
      logStructured("info", "new Hartmann Studios app discovered", {
        package: app.packageName,
        name: app.name,
      });
    }
  }
  logStructured("info", "catalog refresh success", {
    discovered: discovered.length,
    accepted: apps.length,
    previousCount,
  });
  return finish({
    status: "success",
    discovered: discovered.length,
    accepted: apps.length,
    rejected: rejected.length,
    metadataFailures: failures.length,
    detail: `catalog updated: ${apps.length} apps (previous=${previousCount})`,
  });
}
