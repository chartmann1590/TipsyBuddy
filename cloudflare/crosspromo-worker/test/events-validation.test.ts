import { describe, expect, it } from "vitest";
import {
  MAX_BATCH_EVENTS,
  validateEventShape,
  validateRecommendationParams,
} from "../src/api/validation";
import { extractBatch } from "../src/analytics/events";
import { DEFAULT_CONFIG } from "../src/config";

describe("validateRecommendationParams", () => {
  const v = (q: string) =>
    validateRecommendationParams(
      new URLSearchParams(q),
      DEFAULT_CONFIG.defaultLimit,
      DEFAULT_CONFIG.maxLimit,
    );

  it("accepts a full valid query", () => {
    const { params, error } = v(
      "sourcePackage=com.example.app&placement=settings&limit=3&sessionId=abc-123&exclude=com.example.x,com.example.y&locale=en-US&sdkVersion=1.0.0",
    );
    expect(error).toBeNull();
    expect(params).toMatchObject({
      sourcePackage: "com.example.app",
      placement: "settings",
      limit: 3,
      sessionId: "abc-123",
      locale: "en-US",
      sdkVersion: "1.0.0",
    });
    expect(params!.exclude).toEqual(["com.example.x", "com.example.y"]);
  });

  it("clamps limit to the configured max", () => {
    expect(v("limit=50").params!.limit).toBe(DEFAULT_CONFIG.maxLimit);
  });

  it("rejects malformed sourcePackage / placement / limit / sessionId", () => {
    expect(v("sourcePackage=not valid!!").error).toBeTruthy();
    expect(v("sourcePackage=" + "a".repeat(300)).error).toBeTruthy();
    expect(v("placement=<script>").error).toBeTruthy();
    expect(v("limit=0").error).toBeTruthy();
    expect(v("limit=abc").error).toBeTruthy();
    expect(v("sessionId=" + "x".repeat(100)).error).toBeTruthy();
    expect(v("exclude=com.example.ok,evil!!").error).toBeTruthy();
    expect(v("locale=english-latin-variant-x").error).toBeTruthy();
  });
});

describe("validateEventShape", () => {
  const base = {
    event: "promo_click",
    sourcePackage: "com.example.source",
    targetPackage: "com.example.target",
    placement: "settings",
    rankPosition: 1,
    selectionType: "popular",
    sessionId: "sess-1",
    recommendationRequestId: "req-1",
    sdkVersion: "1.0.0",
  };

  it("accepts a complete valid event", () => {
    const { event, error } = validateEventShape(base);
    expect(error).toBeNull();
    expect(event!.targetPackage).toBe("com.example.target");
  });

  it("rejects bad types, self-promo shapes, oversized payloads", () => {
    expect(validateEventShape({ ...base, event: "ad_view" }).error).toBeTruthy();
    expect(validateEventShape({ ...base, targetPackage: "nope!!" }).error).toBeTruthy();
    expect(validateEventShape({ ...base, placement: "a".repeat(64) }).error).toBeTruthy();
    expect(validateEventShape({ ...base, rankPosition: 0 }).error).toBeTruthy();
    expect(validateEventShape({ ...base, selectionType: "viral" }).error).toBeTruthy();
    expect(validateEventShape({ ...base, extra: "x".repeat(10_000) }).error).toBeTruthy();
    expect(validateEventShape(null).error).toBeTruthy();
    expect(validateEventShape("string").error).toBeTruthy();
  });
});

describe("extractBatch", () => {
  it("supports single-event bodies and {events:[...]} batches", () => {
    const single = extractBatch({ event: "promo_click" });
    expect(single.error).toBeNull();
    expect(single.raw).toHaveLength(1);
    const batch = extractBatch({ events: [{}, {}] });
    expect(batch.error).toBeNull();
    expect(batch.raw).toHaveLength(2);
  });

  it("caps batch size", () => {
    const { error } = extractBatch({ events: new Array(MAX_BATCH_EVENTS + 1).fill({}) });
    expect(error).toBeTruthy();
  });
});
