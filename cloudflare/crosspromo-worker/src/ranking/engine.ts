/**
 * Recommendation / ranking engine (pure functions — fully unit-tested).
 *
 * Popularity blends public Play signals with on-platform promo performance:
 *
 *   popularity = installs*0.50 + reviews*0.25 + rating*0.15 + promoPerf*0.10
 *
 * with weights renormalized over whichever signals are actually present
 * (metrics are NEVER invented when data is missing). Future revenue or
 * attributed-install signals can extend `promoPerformanceScore` and the
 * effective-weight formula without changing Android clients.
 *
 * Exposure strategy per slot: ~65% popularity-weighted, ~35% uniform
 * exploration. New apps (first seen within `newAppBoostDays`) get a
 * temporary weight multiplier so zero-review launches still surface.
 */
import type { AppEntry, PromoConfig, RecommendedApp, SelectionType } from "../types";

export interface RankingContext {
  config: PromoConfig;
  /** targetPackage -> CTR (0..1), from promo_stats aggregates. */
  ctr: Map<string, number>;
  nowMs: number;
}

export function hashSeed(s: string): number {
  // xFNV-1a 32-bit — deterministic per (session, time-bucket).
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function log10(x: number): number {
  return Math.log10(Math.max(1, x));
}

function ageDays(app: AppEntry, nowMs: number): number {
  const t = Date.parse(app.firstDiscoveredAt);
  if (!Number.isFinite(t)) return Number.POSITIVE_INFINITY;
  return (nowMs - t) / 86_400_000;
}

export function isNewApp(app: AppEntry, nowMs: number, boostDays: number): boolean {
  if (boostDays <= 0) return false;
  return ageDays(app, nowMs) <= boostDays;
}

/**
 * Normalized 0..1 popularity from public Play metadata + observed CTR.
 * Missing signals are skipped and remaining weights renormalized.
 */
export function computePopularity(
  app: AppEntry,
  allApps: AppEntry[],
  ctr: Map<string, number>,
): number {
  const installs = allApps.map((a) => a.estimatedMinimumInstalls ?? 0);
  const reviews = allApps.map((a) => a.reviewCount ?? 0);
  const maxInstalls = Math.max(1, ...installs);
  const maxReviews = Math.max(1, ...reviews);

  const parts: Array<{ score: number; weight: number }> = [];
  if (app.estimatedMinimumInstalls != null) {
    parts.push({
      score: log10(app.estimatedMinimumInstalls + 1) / log10(maxInstalls + 1),
      weight: 0.5,
    });
  }
  if (app.reviewCount != null) {
    parts.push({
      score: log10(app.reviewCount + 1) / log10(maxReviews + 1),
      weight: 0.25,
    });
  }
  if (app.rating != null) {
    parts.push({ score: Math.min(1, Math.max(0, (app.rating - 3) / 2)), weight: 0.15 });
  }
  const appCtr = ctr.get(app.packageName);
  if (appCtr != null && Number.isFinite(appCtr)) {
    // 5% CTR saturates this component; CTR is deliberately a small input
    // so one early winner cannot permanently dominate.
    parts.push({ score: Math.min(1, Math.max(0, appCtr / 0.05)), weight: 0.1 });
  }
  if (parts.length === 0) return 0.25; // no signal at all: neutral middle
  const wSum = parts.reduce((s, p) => s + p.weight, 0);
  return parts.reduce((s, p) => s + (p.score * p.weight) / wSum, 0);
}

/** Final per-app selection weight incl. boosts and manual multipliers. */
export function effectiveWeight(app: AppEntry, popularity: number, ctx: RankingContext): number {
  // Floor keeps zero-signal apps reachable inside the weighted pool.
  let w = 0.2 + popularity;
  if (isNewApp(app, ctx.nowMs, ctx.config.newAppBoostDays)) {
    w *= ctx.config.newAppBoostMultiplier;
  }
  const mult = Number.isFinite(app.promotionMultiplier) ? app.promotionMultiplier : 1;
  w *= Math.min(10, Math.max(0, mult));
  return Math.max(0.01, w);
}

export interface SelectionInput {
  apps: AppEntry[];
  sourcePackage: string | null;
  exclude: string[];
  blockedTargets: string[];
  limit: number;
  seed: string;
  ctx: RankingContext;
}

function weightedPick(pool: AppEntry[], weights: number[], rand: () => number): number {
  const total = weights.reduce((s, w) => s + w, 0);
  let roll = rand() * total;
  for (let i = 0; i < pool.length; i++) {
    roll -= weights[i]!;
    if (roll <= 0) return i;
  }
  return pool.length - 1;
}

function toRecommended(app: AppEntry, selectionType: SelectionType): RecommendedApp {
  return {
    packageName: app.packageName,
    name: app.name,
    iconUrl: app.iconUrl,
    shortDescription: app.shortDescription,
    rating: app.rating,
    ratingCount: app.reviewCount,
    installText: app.installText,
    storeUrl: app.storeUrl,
    selectionType,
  };
}

/**
 * Select up to `limit` unique recommendations. Never includes the source
 * app, excluded packages, blocked pairs, or disabled apps. Never returns
 * duplicates. Returns fewer than `limit` only when fewer eligible apps
 * exist.
 */
export function selectRecommendations(input: SelectionInput): RecommendedApp[] {
  const excluded = new Set([...input.exclude, ...input.blockedTargets]);
  const eligible = input.apps.filter(
    (a) =>
      a.enabledForPromotion &&
      (!input.sourcePackage || a.packageName !== input.sourcePackage) &&
      !excluded.has(a.packageName),
  );
  if (eligible.length === 0) return [];

  const rand = mulberry32(hashSeed(input.seed));
  const limit = Math.min(input.limit, eligible.length);
  const popularity = new Map<string, number>();
  for (const a of eligible) popularity.set(a.packageName, computePopularity(a, eligible, input.ctx.ctr));

  const remaining = [...eligible];
  const out: RecommendedApp[] = [];
  for (let slot = 0; slot < limit; slot++) {
    const roll = rand();
    let idx: number;
    let type: SelectionType;
    const weights = remaining.map((a) =>
      effectiveWeight(a, popularity.get(a.packageName) ?? 0.25, input.ctx),
    );
    if (roll < input.ctx.config.popularWeight) {
      idx = weightedPick(remaining, weights, rand);
      const picked = remaining[idx]!;
      type = isNewApp(picked, input.ctx.nowMs, input.ctx.config.newAppBoostDays)
        ? "new_app_boost"
        : "popular";
    } else {
      idx = Math.floor(rand() * remaining.length);
      type = "random";
    }
    const [picked] = remaining.splice(idx, 1);
    out.push(toRecommended(picked!, type));
  }
  return out;
}
