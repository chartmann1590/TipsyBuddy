/**
 * Remote configuration: defaults + KV overrides.
 *
 * Android clients never fetch this directly; the recommendation endpoint
 * applies it server-side, so tuning weights needs no app release.
 *
 * KV keys:
 *   v1:config            JSON object of raw config values
 *   v1:appcfg:<package>  JSON row {enabled, max_cards, placements, blocked_targets}
 */
import type { PromoConfig, SourceAppConfig } from "./types";

export const DEFAULT_CONFIG: PromoConfig = {
  enabled: true,
  popularWeight: 0.65,
  randomWeight: 0.35,
  newAppBoostDays: 14,
  newAppBoostMultiplier: 2.0,
  defaultLimit: 3,
  maxLimit: 6,
  recommendationTtlHours: 6,
  catalogRefreshHours: 6,
  metadataRefreshHours: 18,
};

const NUM_KEYS: Array<[keyof PromoConfig, number, number]> = [
  ["popularWeight", 0, 1],
  ["randomWeight", 0, 1],
  ["newAppBoostDays", 0, 90],
  ["newAppBoostMultiplier", 1, 10],
  ["defaultLimit", 1, 10],
  ["maxLimit", 1, 10],
  ["recommendationTtlHours", 1, 72],
  ["catalogRefreshHours", 1, 72],
  ["metadataRefreshHours", 1, 168],
];

/** Merge raw JSON config values over the defaults with range clamping. */
export function normalizeConfig(raw: Record<string, unknown>): PromoConfig {
  const cfg: PromoConfig = { ...DEFAULT_CONFIG };
  for (const [key, min, max] of NUM_KEYS) {
    const v = raw[key];
    if (typeof v === "number" && Number.isFinite(v)) {
      cfg[key] = Math.min(max, Math.max(min, v)) as never;
    }
  }
  if (typeof raw["enabled"] === "boolean") cfg.enabled = raw["enabled"];
  // Keep weights summing to 1 when both are provided and positive.
  const sum = cfg.popularWeight + cfg.randomWeight;
  if (sum > 0) {
    cfg.popularWeight = cfg.popularWeight / sum;
    cfg.randomWeight = cfg.randomWeight / sum;
  } else {
    cfg.popularWeight = DEFAULT_CONFIG.popularWeight;
    cfg.randomWeight = DEFAULT_CONFIG.randomWeight;
  }
  if (cfg.maxLimit < cfg.defaultLimit) cfg.maxLimit = cfg.defaultLimit;
  return cfg;
}

export async function loadConfig(kv: KVNamespace): Promise<PromoConfig> {
  try {
    const raw = await kv.get("v1:config", "json");
    if (!raw || typeof raw !== "object") return { ...DEFAULT_CONFIG };
    return normalizeConfig(raw as Record<string, unknown>);
  } catch {
    return { ...DEFAULT_CONFIG };
  }
}

export async function saveConfig(kv: KVNamespace, raw: Record<string, unknown>): Promise<PromoConfig> {
  const cfg = normalizeConfig(raw);
  await kv.put("v1:config", JSON.stringify(raw));
  return cfg;
}

export function parseSourceAppConfig(row: {
  enabled: number;
  max_cards: number | null;
  placements: string | null;
  blocked_targets: string | null;
}): SourceAppConfig {
  let placements: string[] | null = null;
  let blockedTargets: string[] = [];
  try {
    if (row.placements) {
      const p: unknown = JSON.parse(row.placements);
      if (Array.isArray(p)) placements = p.filter((x): x is string => typeof x === "string");
    }
  } catch {
    placements = null;
  }
  try {
    if (row.blocked_targets) {
      const b: unknown = JSON.parse(row.blocked_targets);
      if (Array.isArray(b)) blockedTargets = b.filter((x): x is string => typeof x === "string");
    }
  } catch {
    blockedTargets = [];
  }
  return {
    enabled: row.enabled !== 0,
    maxCards: typeof row.max_cards === "number" ? row.max_cards : null,
    placements,
    blockedTargets,
  };
}

export async function loadSourceAppConfig(
  kv: KVNamespace,
  sourcePackage: string | null,
): Promise<SourceAppConfig | null> {
  if (!sourcePackage) return null;
  try {
    const row = await kv.get(`v1:appcfg:${sourcePackage}`, "json");
    if (!row || typeof row !== "object") return null;
    const r = row as {
      enabled?: unknown;
      max_cards?: unknown;
      placements?: unknown;
      blocked_targets?: unknown;
    };
    return parseSourceAppConfig({
      enabled: r.enabled === 0 ? 0 : 1,
      max_cards: typeof r.max_cards === "number" ? r.max_cards : null,
      placements: typeof r.placements === "string" ? r.placements : null,
      blocked_targets: typeof r.blocked_targets === "string" ? r.blocked_targets : null,
    });
  } catch {
    return null;
  }
}
