/**
 * KV persistence layer: catalog reads/writes, refresh bookkeeping, remote
 * config, per-app overrides, analytics aggregates.
 *
 * Key layout (all values JSON):
 *   v1:catalog              AppEntry[] — the served last-known-good catalog
 *   v1:meta                 Record<string,string> — refresh timestamps etc.
 *   v1:config               Record<string,unknown> — global remote config
 *   v1:appcfg:<package>     per-source-app overrides
 *   v1:refreshes            capped array (newest last) of refresh records
 *   v1:ctr:<package>        {i,c,n} impression/click/install counters
 *   v1:place:<p>|<s>|<t>    {i,c} per-placement counters
 *   v1:idx:targets          string[] packages with stats (dashboard index)
 *   v1:idx:places           string[] composite placement keys (dashboard index)
 *   v1:evt:<uuid>           raw event (7-day TTL, audit/recompute)
 *
 * Recommendation serving is a couple of KV reads (catalog + config +
 * batched CTR gets). KV is eventually consistent (tens of seconds) — fine
 * for a catalog refreshed every 6h. Counter updates are read-modify-write
 * and therefore approximate under concurrent load; raw events are retained
 * so counters can always be recomputed. Analytics must never break serving.
 */
import type { AppEntry, RefreshDiagnostics } from "../types";

export const META_KEYS = {
  lastRefreshAttempt: "lastRefreshAttempt",
  lastSuccessfulRefresh: "lastSuccessfulRefresh",
  lastKnownGoodRefresh: "lastKnownGoodRefresh",
  lastKnownGoodCount: "lastKnownGoodCount",
  lastSource: "lastSource",
} as const;

const K_CATALOG = "v1:catalog";
const K_META = "v1:meta";
const K_REFRESHES = "v1:refreshes";
const K_IDX_TARGETS = "v1:idx:targets";
const K_IDX_PLACES = "v1:idx:places";

export interface Counter {
  i: number;
  c: number;
  n: number;
}

function isAppEntryArray(v: unknown): v is AppEntry[] {
  return (
    Array.isArray(v) &&
    v.every(
      (e) =>
        e !== null &&
        typeof e === "object" &&
        typeof (e as Record<string, unknown>)["packageName"] === "string" &&
        typeof (e as Record<string, unknown>)["name"] === "string" &&
        typeof (e as Record<string, unknown>)["storeUrl"] === "string",
    )
  );
}

/** All apps ever seen (including disabled ones — callers filter). */
export async function readAllApps(kv: KVNamespace): Promise<AppEntry[]> {
  try {
    const v = await kv.get(K_CATALOG, "json");
    if (isAppEntryArray(v)) return v;
    return [];
  } catch {
    return [];
  }
}

/** Previous catalog state for merge-preservation (firstSeen, enabled, multiplier). */
export async function readPreviousState(
  kv: KVNamespace,
): Promise<Map<string, { firstDiscoveredAt: string; enabledForPromotion: boolean; promotionMultiplier: number }>> {
  const map = new Map<string, { firstDiscoveredAt: string; enabledForPromotion: boolean; promotionMultiplier: number }>();
  for (const a of await readAllApps(kv)) {
    map.set(a.packageName, {
      firstDiscoveredAt: a.firstDiscoveredAt,
      enabledForPromotion: a.enabledForPromotion,
      promotionMultiplier: a.promotionMultiplier,
    });
  }
  return map;
}

/**
 * Persist an accepted catalog. Packages that vanished are dropped so
 * unpublished apps disappear after the next successful refresh.
 */
export async function writeCatalog(kv: KVNamespace, apps: AppEntry[]): Promise<void> {
  await kv.put(K_CATALOG, JSON.stringify(apps));
}

export async function readMeta(kv: KVNamespace, key: string): Promise<string | null> {
  try {
    const meta = await kv.get(K_META, "json");
    if (meta && typeof meta === "object") {
      const v = (meta as Record<string, unknown>)[key];
      return typeof v === "string" ? v : null;
    }
    return null;
  } catch {
    return null;
  }
}

export async function writeMeta(kv: KVNamespace, key: string, value: string): Promise<void> {
  let meta: Record<string, unknown> = {};
  try {
    const existing = await kv.get(K_META, "json");
    if (existing && typeof existing === "object") meta = existing as Record<string, unknown>;
  } catch {
    // start fresh
  }
  meta[key] = value;
  await kv.put(K_META, JSON.stringify(meta));
}

export interface RefreshRecord {
  startedAt: string;
  finishedAt: string;
  source: string;
  discovered: number;
  accepted: number;
  rejected: number;
  metadataFailures: number;
  status: string;
  detail: string;
}

export async function logRefresh(kv: KVNamespace, entry: RefreshRecord): Promise<void> {
  let log: RefreshRecord[] = [];
  try {
    const existing = await kv.get(K_REFRESHES, "json");
    if (Array.isArray(existing)) log = existing as RefreshRecord[];
  } catch {
    log = [];
  }
  log.push(entry);
  await kv.put(K_REFRESHES, JSON.stringify(log.slice(-20)));
}

export async function readRecentRefreshes(kv: KVNamespace, limit = 10): Promise<RefreshRecord[]> {
  try {
    const existing = await kv.get(K_REFRESHES, "json");
    if (!Array.isArray(existing)) return [];
    return (existing as RefreshRecord[]).slice(-limit).reverse();
  } catch {
    return [];
  }
}

/** CTR per target package for ranking + dashboards. */
export async function readCtrMap(kv: KVNamespace, packages: string[]): Promise<Map<string, number>> {
  const ctr = new Map<string, number>();
  if (packages.length === 0) return ctr;
  try {
    const results = await Promise.all(
      packages.map(async (p) => ({ p, v: await kv.get(`v1:ctr:${p}`, "json") })),
    );
    for (const { p, v } of results) {
      if (v && typeof v === "object") {
        const c = v as Partial<Counter>;
        const i = typeof c.i === "number" ? c.i : 0;
        const clicks = typeof c.c === "number" ? c.c : 0;
        ctr.set(p, i > 0 ? clicks / i : 0);
      }
    }
  } catch {
    // Analytics must never break recommendations.
  }
  return ctr;
}

export async function readCounter(kv: KVNamespace, key: string): Promise<Counter> {
  try {
    const v = await kv.get(key, "json");
    if (v && typeof v === "object") {
      const c = v as Partial<Counter>;
      return {
        i: typeof c.i === "number" ? c.i : 0,
        c: typeof c.c === "number" ? c.c : 0,
        n: typeof c.n === "number" ? c.n : 0,
      };
    }
  } catch {
    // fall through
  }
  return { i: 0, c: 0, n: 0 };
}

export async function addToCounter(kv: KVNamespace, key: string, di: number, dc: number, dn: number): Promise<void> {
  const cur = await readCounter(kv, key);
  await kv.put(key, JSON.stringify({ i: cur.i + di, c: cur.c + dc, n: cur.n + dn }));
}

async function addToIndex(kv: KVNamespace, key: string, member: string): Promise<void> {
  try {
    const existing = await kv.get(key, "json");
    const arr: string[] = Array.isArray(existing) ? (existing as unknown[]).filter((x): x is string => typeof x === "string") : [];
    if (!arr.includes(member)) {
      arr.push(member);
      await kv.put(key, JSON.stringify(arr.slice(-2000)));
    }
  } catch {
    // index best-effort only
  }
}

export async function indexTarget(kv: KVNamespace, target: string): Promise<void> {
  await addToIndex(kv, K_IDX_TARGETS, target);
}

export async function indexPlace(kv: KVNamespace, composite: string): Promise<void> {
  await addToIndex(kv, K_IDX_PLACES, composite);
}

export async function readStatsSummary(kv: KVNamespace): Promise<{
  impressions: number;
  clicks: number;
  installs: number;
  byTarget: Array<{ target: string; impressions: number; clicks: number; ctr: number }>;
  byPlacement: Array<Record<string, unknown>>;
}> {
  const summary = {
    impressions: 0,
    clicks: 0,
    installs: 0,
    byTarget: [] as Array<{ target: string; impressions: number; clicks: number; ctr: number }>,
    byPlacement: [] as Array<Record<string, unknown>>,
  };
  try {
    const targetsRaw = await kv.get(K_IDX_TARGETS, "json");
    const targets: string[] = Array.isArray(targetsRaw)
      ? (targetsRaw as unknown[]).filter((x): x is string => typeof x === "string").slice(0, 200)
      : [];
    const counters = await Promise.all(targets.map(async (t) => ({ t, c: await readCounter(kv, `v1:ctr:${t}`) })));
    counters.sort((a, b) => b.c.i - a.c.i);
    for (const { t, c } of counters.slice(0, 100)) {
      summary.impressions += c.i;
      summary.clicks += c.c;
      summary.installs += c.n;
      summary.byTarget.push({
        target: t,
        impressions: c.i,
        clicks: c.c,
        ctr: c.i > 0 ? c.c / c.i : 0,
      });
    }
    const placesRaw = await kv.get(K_IDX_PLACES, "json");
    const places: string[] = Array.isArray(placesRaw)
      ? (placesRaw as unknown[]).filter((x): x is string => typeof x === "string").slice(0, 200)
      : [];
    const pcounters = await Promise.all(
      places.slice(0, 100).map(async (k) => {
        const c = await readCounter(kv, `v1:place:${k}`);
        const [placement, source, target] = k.split("|");
        return { placement, source_package: source, target_package: target, impressions: c.i, clicks: c.c };
      }),
    );
    pcounters.sort((a, b) => b.impressions - a.impressions);
    summary.byPlacement = pcounters;
  } catch {
    // Serve zeros rather than failing admin status.
  }
  return summary;
}

export function emptyDiagnostics(source: string): RefreshDiagnostics {
  return {
    source,
    discovered: 0,
    accepted: 0,
    rejected: 0,
    metadataFailures: 0,
    rejectedPackages: [],
    metadataFailedPackages: [],
  };
}
