import { describe, expect, it } from "vitest";
import { DEFAULT_CONFIG } from "../src/config";
import {
  computePopularity,
  selectRecommendations,
} from "../src/ranking/engine";
import type { AppEntry, PromoConfig } from "../src/types";

const NOW = Date.parse("2026-09-22T13:00:00.000Z");

function app(overrides: Partial<AppEntry> & { packageName: string }): AppEntry {
  return {
    name: overrides.packageName,
    iconUrl: null,
    storeUrl: `https://play.google.com/store/apps/details?id=${overrides.packageName}`,
    shortDescription: null,
    rating: null,
    reviewCount: null,
    installText: null,
    estimatedMinimumInstalls: null,
    category: null,
    priceText: null,
    isFree: true,
    developer: "Hartmann Studios",
    firstDiscoveredAt: "2026-01-01T00:00:00.000Z",
    lastSeenAt: "2026-09-22T00:00:00.000Z",
    lastMetadataRefresh: null,
    enabledForPromotion: true,
    promotionMultiplier: 1,
    ...overrides,
  };
}

const config: PromoConfig = { ...DEFAULT_CONFIG };

describe("selectRecommendations", () => {
  const catalog = [
    app({ packageName: "com.example.a", estimatedMinimumInstalls: 1_000_000, reviewCount: 50_000, rating: 4.8 }),
    app({ packageName: "com.example.b", estimatedMinimumInstalls: 100_000, reviewCount: 5_000, rating: 4.5 }),
    app({ packageName: "com.example.c", estimatedMinimumInstalls: 10_000, reviewCount: 500, rating: 4.2 }),
    app({ packageName: "com.example.d", estimatedMinimumInstalls: 1_000, reviewCount: 50, rating: 4.0 }),
  ];

  it("excludes the current app and never returns duplicates", () => {
    const out = selectRecommendations({
      apps: catalog,
      sourcePackage: "com.example.a",
      exclude: [],
      blockedTargets: [],
      limit: 3,
      seed: "s1",
      ctx: { config, ctr: new Map(), nowMs: NOW },
    });
    expect(out).toHaveLength(3);
    expect(out.some((a) => a.packageName === "com.example.a")).toBe(false);
    expect(new Set(out.map((a) => a.packageName)).size).toBe(3);
  });

  it("returns fewer than limit when fewer eligible apps exist", () => {
    const out = selectRecommendations({
      apps: catalog,
      sourcePackage: "com.example.a",
      exclude: ["com.example.b", "com.example.c"],
      blockedTargets: [],
      limit: 3,
      seed: "s1",
      ctx: { config, ctr: new Map(), nowMs: NOW },
    });
    expect(out).toHaveLength(1);
    expect(out[0]!.packageName).toBe("com.example.d");
  });

  it("is deterministic for the same seed (session rotation stability)", () => {
    const mk = () =>
      selectRecommendations({
        apps: catalog,
        sourcePackage: "com.example.a",
        exclude: [],
        blockedTargets: [],
        limit: 2,
        seed: "session-abc|com.example.a|settings|1234|",
        ctx: { config, ctr: new Map(), nowMs: NOW },
      });
    expect(mk().map((a) => a.packageName)).toEqual(mk().map((a) => a.packageName));
  });

  it("never shows disabled apps or per-pair blocked targets", () => {
    const withDisabled = [
      ...catalog,
      app({ packageName: "com.example.z", enabledForPromotion: false }),
    ];
    for (let i = 0; i < 50; i++) {
      const out = selectRecommendations({
        apps: withDisabled,
        sourcePackage: "com.example.a",
        exclude: [],
        blockedTargets: ["com.example.b"],
        limit: 3,
        seed: `seed-${i}`,
        ctx: { config, ctr: new Map(), nowMs: NOW },
      });
      expect(out.some((a) => a.packageName === "com.example.z")).toBe(false);
      expect(out.some((a) => a.packageName === "com.example.b")).toBe(false);
    }
  });

  it("honors the exclude list for client-side rotation", () => {
    const out = selectRecommendations({
      apps: catalog,
      sourcePackage: "com.example.a",
      exclude: ["com.example.b", "com.example.c", "com.example.d"],
      blockedTargets: [],
      limit: 3,
      seed: "s1",
      ctx: { config, ctr: new Map(), nowMs: NOW },
    });
    expect(out).toHaveLength(0);
  });
});

describe("computePopularity", () => {
  it("ranks higher installs/reviews/ratings above smaller apps", () => {
    const big = app({ packageName: "com.example.big", estimatedMinimumInstalls: 1_000_000, reviewCount: 20_000, rating: 4.8 });
    const small = app({ packageName: "com.example.small", estimatedMinimumInstalls: 1_000, reviewCount: 20, rating: 3.5 });
    const all = [big, small];
    expect(computePopularity(big, all, new Map())).toBeGreaterThan(
      computePopularity(small, all, new Map()),
    );
  });

  it("falls back gracefully when installs are missing (reviews + rating only)", () => {
    const a = app({ packageName: "com.example.a", reviewCount: 5_000, rating: 4.6 });
    const b = app({ packageName: "com.example.b", reviewCount: 50, rating: 3.2 });
    expect(computePopularity(a, [a, b], new Map())).toBeGreaterThan(
      computePopularity(b, [a, b], new Map()),
    );
  });

  it("returns a neutral score when no signal exists (never NaN)", () => {
    const a = app({ packageName: "com.example.a" });
    const s = computePopularity(a, [a], new Map());
    expect(Number.isFinite(s)).toBe(true);
    expect(s).toBeGreaterThanOrEqual(0);
    expect(s).toBeLessThanOrEqual(1);
  });
});
