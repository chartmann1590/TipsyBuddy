import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import {
  canonicalStoreUrl,
  discoverPackageIds,
  isValidPackageId,
  normalizeApp,
  parseAppDetail,
  parseInstallBucket,
  validateRefresh,
} from "../src/catalog/parser";
import type { AppEntry } from "../src/types";

const dir = dirname(fileURLToPath(import.meta.url));
const readFixture = (name: string) =>
  readFileSync(join(dir, "fixtures", name), "utf-8");

describe("discoverPackageIds", () => {
  it("finds canonical links, dedupes, and preserves first-seen order", () => {
    const ids = discoverPackageIds(readFixture("developer-page.sample.html"));
    expect(ids).toContain("com.auracast.weather");
    expect(ids).toContain("com.charles.qrcode");
    expect(ids).toContain("com.charles.messenger.v2");
    expect(ids).toContain("com.hartmann.pixeldream");
    expect(ids).toContain("com.charles.ollama.client.play");
    // Duplicate link appears only once.
    expect(ids.filter((i) => i === "com.charles.qrcode")).toHaveLength(1);
  });

  it("rejects unrelated developer/app links and invalid ids", () => {
    const ids = discoverPackageIds(readFixture("developer-page.sample.html"));
    expect(ids).not.toContain("com.google.android.gms");
    expect(ids).not.toContain("com.android.vending");
    expect(ids.every((i) => isValidPackageId(i))).toBe(true);
  });

  it("finds JS-escaped AF payload ids", () => {
    const ids = discoverPackageIds(
      `x["/store/apps/details/Some_App?id\\u003dcom.example.someapp\\u0026hl\\u003den"]y`,
    );
    expect(ids).toEqual(["com.example.someapp"]);
  });

  it("returns [] for pages with no app links instead of throwing", () => {
    expect(discoverPackageIds("<html><body>no apps here</body></html>")).toEqual([]);
    expect(discoverPackageIds("")).toEqual([]);
  });
});

describe("isValidPackageId", () => {
  it.each([
    ["com.example.app", true],
    ["com.charles.messenger.v2", true],
    ["com.hartmann.pixeldream", true],
    ["single", false],
    ["1bad.start", false],
    ["not-a-valid!!id", false],
    ["com.google.android.gms", false],
    ["com.android.vending", false],
    ["", false],
  ])("%s -> %s", (pkg, expected) => {
    expect(isValidPackageId(pkg)).toBe(expected);
  });
});

describe("parseAppDetail", () => {
  it("parses the full LD+JSON listing", () => {
    const meta = parseAppDetail(readFixture("app-detail-qrcode.sample.html"), "com.charles.qrcode");
    expect(meta.name).toBe("QR Scanner: Code Reader");
    expect(meta.iconUrl).toBe(
      "https://play-lh.googleusercontent.com/ZoSPFGQRBcjFuAgMHgk12uj2GPfMQfhC1uzbsqMNJoVn-MuwFLN1ukTgCyj4b7mH4XJPqNsP80TB0gBY5fgScg",
    );
    expect(meta.shortDescription).toContain("Lightning-fast");
    expect(meta.rating).toBe(4.7);
    expect(meta.reviewCount).toBe(2400);
    expect(meta.installText).toBe("10+");
    expect(meta.category).toBe("Tools");
    expect(meta.isFree).toBe(true);
    expect(meta.developer).toBe("Hartmann Studios");
  });

  it("parses contiguous install buckets too", () => {
    const meta = parseAppDetail(
      `<html><head><title>T - Apps on Google Play</title></head><body><h1>T</h1><div>10K+ Downloads</div></body></html>`,
      "com.example.t",
    );
    expect(meta.installText).toBe("10K+");
  });

  it("falls back to OG tags + h1 and stays nullable-safe on minimal pages", () => {
    const meta = parseAppDetail(readFixture("app-detail-minimal.sample.html"), "com.example.minimal");
    expect(meta.name).toBe("Minimal App");
    expect(meta.iconUrl).toBeNull();
    expect(meta.rating).toBeNull();
    expect(meta.reviewCount).toBeNull();
    expect(meta.installText).toBeNull();
    expect(meta.isFree).toBe(true);
  });

  it("never throws on garbage input", () => {
    const meta = parseAppDetail("<<<<< not html", "com.example.app");
    expect(meta.name).toBeNull();
  });
});

describe("parseInstallBucket", () => {
  it.each([
    ["10+", "10+", 10],
    ["10K+", "10K+", 10_000],
    ["100K+", "100K+", 100_000],
    ["1M+", "1M+", 1_000_000],
    ["5,000+", "5,000+", 5000],
    [null, null, null],
    ["garbage", null, null],
  ])("%s -> %s / %s", (input, text, installs) => {
    expect(parseInstallBucket(input)).toEqual({
      installText: text,
      estimatedMinimumInstalls: installs,
    });
  });
});

describe("normalizeApp", () => {
  it("builds the canonical store URL and keeps previous discovery state", () => {
    const app = normalizeApp(
      "com.charles.qrcode",
      parseAppDetail(readFixture("app-detail-qrcode.sample.html"), "com.charles.qrcode"),
      "2026-09-22T13:00:00.000Z",
      { firstDiscoveredAt: "2026-01-01T00:00:00.000Z", enabledForPromotion: false, promotionMultiplier: 2 },
    );
    expect(app?.storeUrl).toBe(canonicalStoreUrl("com.charles.qrcode"));
    expect(app?.firstDiscoveredAt).toBe("2026-01-01T00:00:00.000Z");
    expect(app?.enabledForPromotion).toBe(false);
    expect(app?.promotionMultiplier).toBe(2);
    expect(app?.estimatedMinimumInstalls).toBe(10);
  });

  it("returns null when the name is missing or the package is invalid", () => {
    expect(normalizeApp("bad", { name: "X" } as never, "2026-09-22T13:00:00.000Z")).toBeNull();
    expect(
      normalizeApp("com.example.ok", { name: null } as never, "2026-09-22T13:00:00.000Z"),
    ).toBeNull();
  });
});

describe("validateRefresh (catalog safety gate)", () => {
  const mk = (n: number): AppEntry[] =>
    Array.from({ length: n }, (_, i) => ({
      packageName: `com.example.app${i}`,
      name: `App ${i}`,
      iconUrl: null,
      storeUrl: canonicalStoreUrl(`com.example.app${i}`),
      shortDescription: null,
      rating: null,
      reviewCount: null,
      installText: null,
      estimatedMinimumInstalls: null,
      category: null,
      priceText: null,
      isFree: true,
      developer: "Hartmann Studios",
      firstDiscoveredAt: "2026-09-22T00:00:00.000Z",
      lastSeenAt: "2026-09-22T00:00:00.000Z",
      lastMetadataRefresh: null,
      enabledForPromotion: true,
      promotionMultiplier: 1,
    }));

  it("rejects an empty discovery that would wipe promotions", () => {
    expect(validateRefresh(20, [], { discovered: 0 }).ok).toBe(false);
  });

  it("rejects a dramatic unexplained drop (20 -> 1)", () => {
    expect(validateRefresh(20, mk(1), { discovered: 1 }).ok).toBe(false);
  });

  it("accepts genuine shrinkage within tolerance (20 -> 12)", () => {
    expect(validateRefresh(20, mk(12), { discovered: 12 }).ok).toBe(true);
  });

  it("accepts the first-ever catalog (previous=0)", () => {
    expect(validateRefresh(0, mk(3), { discovered: 3 }).ok).toBe(true);
  });
});
