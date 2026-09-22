/**
 * Recommendation simulation: 100,000 selections against a synthetic catalog.
 *
 * Verifies the portfolio-growth contract:
 *  - popular apps receive MORE exposure than lower-ranked apps
 *  - lower-ranked apps still receive NON-TRIVIAL exposure (no starvation)
 *  - newly discovered apps receive a temporary boost
 *  - no single app dominates excessively
 *  - current app is never selected, disabled apps never appear,
 *    duplicates never appear within one response
 *
 * Run: `npm run simulate` (or `npm test`, which includes it).
 */
import { describe, expect, it } from "vitest";
import { DEFAULT_CONFIG } from "../src/config";
import { selectRecommendations } from "../src/ranking/engine";
import type { AppEntry, PromoConfig } from "../src/types";

const NOW = Date.parse("2026-09-22T13:00:00.000Z");
const RUNS = 100_000;
const LIMIT = 3;

function app(pkg: string, installs: number | null, reviews: number | null, rating: number | null): AppEntry {
  return {
    packageName: pkg,
    name: pkg,
    iconUrl: `https://example.com/${pkg}.png`,
    storeUrl: `https://play.google.com/store/apps/details?id=${pkg}`,
    shortDescription: null,
    rating,
    reviewCount: reviews,
    installText: installs == null ? null : `${installs}+`,
    estimatedMinimumInstalls: installs,
    category: "Tools",
    priceText: "0",
    isFree: true,
    developer: "Hartmann Studios",
    firstDiscoveredAt: "2026-01-01T00:00:00.000Z",
    lastSeenAt: "2026-09-22T00:00:00.000Z",
    lastMetadataRefresh: "2026-09-22T00:00:00.000Z",
    enabledForPromotion: true,
    promotionMultiplier: 1,
  };
}

describe("recommendation simulation (100k runs)", () => {
  it("meets the portfolio-growth distribution contract", () => {
    const config: PromoConfig = { ...DEFAULT_CONFIG };
    const catalog: AppEntry[] = [
      app("com.sim.giant", 5_000_000, 120_000, 4.8),
      app("com.sim.big", 1_000_000, 30_000, 4.6),
      app("com.sim.mid", 100_000, 4_000, 4.4),
      app("com.sim.small", 10_000, 400, 4.2),
      app("com.sim.tiny", 1_000, 40, 4.0),
      // Brand-new launch: zero public signal, discovered 2 days ago.
      {
        ...app("com.sim.newlaunch", null, null, null),
        firstDiscoveredAt: new Date(NOW - 2 * 86_400_000).toISOString(),
      },
      // Disabled app must never surface.
      { ...app("com.sim.retired", 2_000_000, 40_000, 4.7), enabledForPromotion: false },
    ];
    const source = "com.sim.host";

    const counts = new Map<string, number>();
    const typeCounts = new Map<string, number>();
    let totalSlots = 0;

    for (let i = 0; i < RUNS; i++) {
      const out = selectRecommendations({
        apps: catalog,
        sourcePackage: source,
        exclude: [],
        blockedTargets: [],
        limit: LIMIT,
        seed: `sim-seed-${i}`,
        ctx: { config, ctr: new Map(), nowMs: NOW },
      });
      // Per-response invariants on every single run.
      expect(out.length).toBe(LIMIT);
      expect(new Set(out.map((a) => a.packageName)).size).toBe(LIMIT);
      expect(out.some((a) => a.packageName === source)).toBe(false);
      expect(out.some((a) => a.packageName === "com.sim.retired")).toBe(false);
      for (const rec of out) {
        counts.set(rec.packageName, (counts.get(rec.packageName) ?? 0) + 1);
        typeCounts.set(rec.selectionType, (typeCounts.get(rec.selectionType) ?? 0) + 1);
        totalSlots += 1;
      }
    }

    const share = (pkg: string) => (counts.get(pkg) ?? 0) / totalSlots;
    const report = [...counts.entries()]
      .map(([pkg, n]) => `${pkg}=${((n / totalSlots) * 100).toFixed(2)}%`)
      .join("  ");
    console.log(`\n[simulation] ${RUNS} runs x ${LIMIT} slots. Exposure: ${report}`);
    console.log(
      `[simulation] selection types: ${[...typeCounts.entries()].map(([k, v]) => `${k}=${((v / totalSlots) * 100).toFixed(1)}%`).join("  ")}`,
    );

    const giant = share("com.sim.giant");
    const tiny = share("com.sim.tiny");
    const small = share("com.sim.small");
    const fresh = share("com.sim.newlaunch");

    // Popular > small: the weighted pool must favor proven apps…
    expect(giant).toBeGreaterThan(small);
    expect(giant).toBeGreaterThan(tiny);
    // …but exploration keeps the tail alive (each > 1% of impressions).
    expect(tiny).toBeGreaterThan(0.01);
    expect(small).toBeGreaterThan(0.01);
    // New launch with zero signal surfaces via the temp boost — above the
    // tail, but never dethroning the proven leader (no permanent favoritism).
    expect(fresh).toBeGreaterThan(tiny);
    expect(fresh).toBeGreaterThan(0.03);
    expect(giant).toBeGreaterThan(fresh);
    // No monopoly: top app stays well under half of all impressions.
    expect(giant).toBeLessThan(0.45);
    // Rough 65/35 split between weighted and exploration picks.
    const popularShare = (typeCounts.get("popular") ?? 0) / totalSlots;
    const randomShare = (typeCounts.get("random") ?? 0) / totalSlots;
    expect(popularShare).toBeGreaterThan(0.45);
    expect(popularShare).toBeLessThan(0.85);
    expect(randomShare).toBeGreaterThan(0.15);
    expect(randomShare).toBeLessThan(0.55);
  }, 120_000);
});
