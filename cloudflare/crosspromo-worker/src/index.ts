/**
 * Hartmann Studios Dynamic Cross-Promotion Platform — Cloudflare Worker.
 *
 * Endpoints (all versioned under /api/v1):
 *   GET  /api/v1/catalog          normalized app catalog (cached)
 *   GET  /api/v1/recommendations  weighted recommendations (cached per URL)
 *   POST /api/v1/events           promo_impression / promo_click / promo_install
 *   GET  /api/v1/health           public liveness + catalog freshness
 *   GET  /api/v1/admin/status     protected diagnostics (Bearer ADMIN_TOKEN)
 *   POST /api/v1/admin/refresh    protected manual catalog refresh
 *
 * Serving never scrapes Google Play. A Cron Trigger refreshes the normalized catalog every 6h into KV; the safety gate keeps serving last-known-good
 * data when a refresh looks broken.
 */
import { loadConfig, loadSourceAppConfig } from "./config";
import { META_KEYS, readAllApps, readCtrMap, readMeta, readRecentRefreshes, readStatsSummary } from "./catalog/store";
import { runCatalogRefresh } from "./catalog/refresh";
import { selectRecommendations } from "./ranking/engine";
import { validateRecommendationParams } from "./api/validation";
import { extractBatch, ingestEvents } from "./analytics/events";
import type { RecommendationResponse, WorkerEnv } from "./types";

const API_VERSION = 1;
const MAX_BODY_BYTES = 64 * 1024;

// Best-effort per-isolate rate limiting (pair with Cloudflare Rate Limiting
// rules in production; D1 writes are additionally bounded per request).
const buckets = new Map<string, { count: number; resetAt: number }>();
function checkRateLimit(ip: string, max: number, windowMs: number): boolean {
  const now = Date.now();
  // Periodic sweep: drop expired entries so the map doesn't grow unboundedly.
  if (buckets.size > 1000) {
    for (const [k, b] of buckets.entries()) {
      if (now >= b.resetAt) buckets.delete(k);
    }
  }
  const b = buckets.get(ip);
  if (!b || now >= b.resetAt) {
    buckets.set(ip, { count: 1, resetAt: now + windowMs });
    return true;
  }
  b.count += 1;
  return b.count <= max;
}

function json(data: unknown, status = 200, cacheSeconds = 0): Response {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (cacheSeconds > 0) {
    headers["Cache-Control"] = `public, max-age=${cacheSeconds}, s-maxage=${cacheSeconds}`;
  } else {
    headers["Cache-Control"] = "no-store";
  }
  return new Response(JSON.stringify(data), { status, headers });
}

function err(message: string, status: number): Response {
  return json({ error: message }, status);
}

function clientIp(request: Request): string {
  return (
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown"
  );
}

function newRequestId(): string {
  const bytes = new Uint8Array(12);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function isAuthorized(request: Request, env: WorkerEnv): boolean {
  const token = env.ADMIN_TOKEN;
  if (!token) return false;
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return false;
  const candidate = header.slice(7);
  if (!candidate || candidate.length !== token.length) return false;
  let diff = 0;
  for (let i = 0; i < token.length; i++) {
    const a = token.charCodeAt(i);
    const b = candidate.charCodeAt(i) ?? 0;
    diff |= a ^ b;
  }
  return diff === 0;
}

async function handleCatalog(request: Request, env: WorkerEnv): Promise<Response> {
  if (!checkRateLimit(`cat:${clientIp(request)}`, 120, 60_000)) {
    return err("Too many requests.", 429);
  }
  try {
    const apps = await readAllApps(env.KV);
    const enabled = apps.filter((a) => a.enabledForPromotion);
    return json(
      {
        version: API_VERSION,
        generatedAt: new Date().toISOString(),
        count: enabled.length,
        apps: enabled.map((a) => ({
          packageName: a.packageName,
          name: a.name,
          iconUrl: a.iconUrl,
          shortDescription: a.shortDescription,
          rating: a.rating,
          ratingCount: a.reviewCount,
          installText: a.installText,
          storeUrl: a.storeUrl,
          selectionType: "popular",
        })),
      },
      200,
      300,
    );
  } catch {
    // Stale-cache fallback: valid JSON, empty catalog, never a 500 shape.
    return json({ version: API_VERSION, generatedAt: new Date().toISOString(), count: 0, apps: [] }, 200, 60);
  }
}

async function handleRecommendations(request: Request, env: WorkerEnv): Promise<Response> {
  if (!checkRateLimit(`rec:${clientIp(request)}`, 120, 60_000)) {
    return err("Too many requests.", 429);
  }
  const url = new URL(request.url);
  let config;
  try {
    config = await loadConfig(env.KV);
  } catch {
    return json(emptyRecommendation(), 200, 60);
  }
  const { params, error } = validateRecommendationParams(url.searchParams, config.defaultLimit, config.maxLimit);
  if (!params || error) return err(error ?? "Bad request.", 400);

  // Global kill-switch + per-app remote config (no client update needed).
  if (!config.enabled) return json(emptyRecommendation(), 200, 60);
  const appConfig = await loadSourceAppConfig(env.KV, params.sourcePackage);
  if (appConfig && !appConfig.enabled) return json(emptyRecommendation(), 200, 60);
  if (appConfig?.placements && !appConfig.placements.includes(params.placement)) {
    return json(emptyRecommendation(), 200, 60);
  }
  const limit = appConfig?.maxCards ? Math.min(params.limit, appConfig.maxCards) : params.limit;

  let apps;
  try {
    apps = await readAllApps(env.KV);
  } catch {
    return json(emptyRecommendation(), 200, 60);
  }
  if (apps.length === 0) return json(emptyRecommendation(), 200, 60);
  const ctr = await readCtrMap(
    env.KV,
    apps.map((a) => a.packageName),
  );

  // Time bucket (6h) + session => stable recommendations per session/period
  // without storing any server-side session state.
  const bucket = Math.floor(Date.now() / (6 * 3600_000));
  const seed = `${params.sessionId ?? "anon"}|${params.sourcePackage ?? ""}|${params.placement}|${bucket}|${params.exclude.join(",")}`;
  const requestId = newRequestId();
  const now = new Date();
  const picked = selectRecommendations({
    apps,
    sourcePackage: params.sourcePackage,
    exclude: params.exclude,
    blockedTargets: appConfig?.blockedTargets ?? [],
    limit,
    seed,
    ctx: { config, ctr, nowMs: now.getTime() },
  });
  const body: RecommendationResponse = {
    version: API_VERSION,
    requestId,
    generatedAt: now.toISOString(),
    expiresAt: new Date(now.getTime() + config.recommendationTtlHours * 3600_000).toISOString(),
    apps: picked,
  };
  return json(body, 200, 300);
}

function emptyRecommendation(): RecommendationResponse {
  const now = new Date();
  return {
    version: API_VERSION,
    requestId: newRequestId(),
    generatedAt: now.toISOString(),
    expiresAt: new Date(now.getTime() + 6 * 3600_000).toISOString(),
    apps: [],
  };
}

async function handleEvents(request: Request, env: WorkerEnv): Promise<Response> {
  if (!checkRateLimit(`evt:${clientIp(request)}`, 120, 60_000)) {
    return err("Too many requests.", 429);
  }
  const ct = request.headers.get("Content-Type") || "";
  if (!ct.toLowerCase().includes("application/json")) {
    return err("Content-Type must be application/json.", 400);
  }
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return err("Request body too large.", 413);
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    return err("Malformed JSON body.", 400);
  }
  const { raw, error } = extractBatch(body);
  if (error) return err(error, 400);
  let known = new Set<string>();
  try {
    const apps = await readAllApps(env.KV);
    known = new Set(apps.map((a) => a.packageName));
  } catch {
    return err("Analytics temporarily unavailable.", 503);
  }
  try {
    const result = await ingestEvents(env.KV, raw, known, new Date().toISOString());
    return json({ accepted: result.accepted, rejected: result.rejected, errors: result.errors });
  } catch {
    return err("Unable to record events.", 503);
  }
}

async function handleHealth(_request: Request, env: WorkerEnv): Promise<Response> {
  let catalogApps = 0;
  let lastRefresh: string | null = null;
  let lastSuccess: string | null = null;
  let lastKnownGood: string | null = null;
  try {
    const apps = await readAllApps(env.KV);
    catalogApps = apps.filter((a) => a.enabledForPromotion).length;
    lastRefresh = await readMeta(env.KV, META_KEYS.lastRefreshAttempt);
    lastSuccess = await readMeta(env.KV, META_KEYS.lastSuccessfulRefresh);
    lastKnownGood = await readMeta(env.KV, META_KEYS.lastKnownGoodRefresh);
  } catch {
    // fall through with zeros; never leak internals or secrets
  }
  const staleHours =
    lastSuccess == null ? null : (Date.now() - Date.parse(lastSuccess)) / 3600_000;
  return json({
    status: "ok",
    catalogApps,
    lastCatalogRefresh: lastRefresh,
    lastSuccessfulRefresh: lastSuccess,
    lastKnownGoodRefresh: lastKnownGood,
    cacheStatus: staleHours == null ? "empty" : staleHours <= 12 ? "fresh" : "stale",
  });
}

async function handleAdminStatus(request: Request, env: WorkerEnv): Promise<Response> {
  if (!isAuthorized(request, env)) return err("Unauthorized.", 401);
  const apps = await readAllApps(env.KV).catch(() => []);
  const [lastAttempt, lastSuccess, lastGood, lastGoodCount, lastSource] = await Promise.all([
    readMeta(env.KV, META_KEYS.lastRefreshAttempt),
    readMeta(env.KV, META_KEYS.lastSuccessfulRefresh),
    readMeta(env.KV, META_KEYS.lastKnownGoodRefresh),
    readMeta(env.KV, META_KEYS.lastKnownGoodCount),
    readMeta(env.KV, META_KEYS.lastSource),
  ]);
  const refreshes = await readRecentRefreshes(env.KV, 10);
  const stats = await readStatsSummary(env.KV);
  const config = await loadConfig(env.KV).catch(() => null);
  return json({
    catalogCount: apps.filter((a) => a.enabledForPromotion).length,
    totalApps: apps.length,
    lastRefreshAttempt: lastAttempt,
    lastSuccessfulRefresh: lastSuccess,
    lastKnownGoodRefresh: lastGood,
    lastKnownGoodCount: lastGoodCount ? Number(lastGoodCount) : null,
    discoverySource: lastSource,
    recentRefreshes: refreshes,
    recommendationsNote: "recommendations are stateless; see promo_stats for served-signal proxies",
    impressions: stats.impressions,
    clicks: stats.clicks,
    installs: stats.installs,
    overallCtr: stats.impressions > 0 ? stats.clicks / stats.impressions : 0,
    byTarget: stats.byTarget,
    byPlacement: stats.byPlacement,
    config,
    apps: apps.map((a) => ({
      packageName: a.packageName,
      name: a.name,
      enabledForPromotion: a.enabledForPromotion,
      rating: a.rating,
      reviewCount: a.reviewCount,
      installText: a.installText,
      firstDiscoveredAt: a.firstDiscoveredAt,
      promotionMultiplier: a.promotionMultiplier,
    })),
  });
}

async function handleAdminRefresh(request: Request, env: WorkerEnv): Promise<Response> {
  if (!isAuthorized(request, env)) return err("Unauthorized.", 401);
  const result = await runCatalogRefresh(env);
  return json(result, result.status === "error" ? 502 : 200);
}

export default {
  async fetch(request: Request, env: WorkerEnv): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    const method = request.method.toUpperCase();

    if (path === "/api/v1/catalog" && method === "GET") return handleCatalog(request, env);
    if (path === "/api/v1/recommendations" && method === "GET") {
      return handleRecommendations(request, env);
    }
    if (path === "/api/v1/events" && method === "POST") return handleEvents(request, env);
    if (path === "/api/v1/health" && method === "GET") return handleHealth(request, env);
    if (path === "/api/v1/admin/status" && method === "GET") return handleAdminStatus(request, env);
    if (path === "/api/v1/admin/refresh" && method === "POST") {
      return handleAdminRefresh(request, env);
    }
    if (path === "/health" && method === "GET") return handleHealth(request, env);
    return err("Not found.", 404);
  },

  async scheduled(_event: ScheduledEvent, env: WorkerEnv): Promise<void> {
    await runCatalogRefresh(env);
  },
};
