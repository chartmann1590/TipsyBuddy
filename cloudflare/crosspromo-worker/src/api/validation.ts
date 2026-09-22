/**
 * Strict server-side input validation. Client values are never trusted:
 * lengths capped, formats allow-listed, result counts bounded, event
 * payloads size-limited.
 */
import type { PromoEvent, PromoEventType } from "../types";

export const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
export const MAX_PACKAGE_LEN = 255;
const PLACEMENT_RE = /^[a-z0-9_-]{1,32}$/i;
const SESSION_RE = /^[A-Za-z0-9_-]{1,64}$/;
const REQUEST_ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
const SDK_VERSION_RE = /^[A-Za-z0-9.+_-]{1,16}$/;
const LOCALE_RE = /^[a-z]{2}(-[A-Z]{2})?$/;
const SELECTION_TYPES = new Set(["popular", "random", "new_app_boost", "boosted"]);
export const ALLOWED_EVENTS: PromoEventType[] = [
  "promo_impression",
  "promo_click",
  "promo_install",
];
export const MAX_EVENT_BYTES = 4 * 1024;
export const MAX_BATCH_EVENTS = 25;

export function isValidPackage(pkg: string): boolean {
  return (
    typeof pkg === "string" &&
    pkg.length >= 3 &&
    pkg.length <= MAX_PACKAGE_LEN &&
    PACKAGE_RE.test(pkg)
  );
}

export interface RecommendationParams {
  sourcePackage: string | null;
  placement: string;
  limit: number;
  sessionId: string | null;
  exclude: string[];
  locale: string | null;
  sdkVersion: string | null;
}

export function validateRecommendationParams(
  search: URLSearchParams,
  defaultLimit: number,
  maxLimit: number,
): { params: RecommendationParams | null; error: string | null } {
  const rawSource = (search.get("sourcePackage") ?? "").trim();
  if (rawSource && !isValidPackage(rawSource)) {
    return { params: null, error: "Invalid sourcePackage." };
  }
  const rawPlacement = (search.get("placement") ?? "home").trim().slice(0, 32);
  if (!PLACEMENT_RE.test(rawPlacement)) {
    return { params: null, error: "Invalid placement." };
  }
  const rawLimit = search.get("limit");
  let limit = defaultLimit;
  if (rawLimit !== null && rawLimit !== "") {
    const n = Number(rawLimit);
    if (!Number.isInteger(n) || n < 1 || n > 50) {
      return { params: null, error: "Invalid limit." };
    }
    limit = Math.min(n, maxLimit);
  }
  const rawSession = (search.get("sessionId") ?? "").trim();
  if (rawSession && !SESSION_RE.test(rawSession)) {
    return { params: null, error: "Invalid sessionId." };
  }
  const excludeRaw = (search.get("exclude") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  if (excludeRaw.length > 50) return { params: null, error: "Too many excluded packages." };
  for (const e of excludeRaw) {
    if (!isValidPackage(e)) return { params: null, error: "Invalid exclude package." };
  }
  const rawLocale = (search.get("locale") ?? "").trim().slice(0, 10);
  if (rawLocale && !LOCALE_RE.test(rawLocale)) {
    return { params: null, error: "Invalid locale." };
  }
  const rawSdk = (search.get("sdkVersion") ?? "").trim().slice(0, 16);
  if (rawSdk && !SDK_VERSION_RE.test(rawSdk)) {
    return { params: null, error: "Invalid sdkVersion." };
  }
  return {
    params: {
      sourcePackage: rawSource || null,
      placement: rawPlacement,
      limit,
      sessionId: rawSession || null,
      exclude: [...new Set(excludeRaw)],
      locale: rawLocale || null,
      sdkVersion: rawSdk || null,
    },
    error: null,
  };
}

export interface EventCheck {
  event: PromoEvent | null;
  error: string | null;
}

/** Validate one event's shape + size. Catalog-membership is checked by the caller. */
export function validateEventShape(raw: unknown): EventCheck {
  if (!raw || typeof raw !== "object") return { event: null, error: "Event must be an object." };
  const e = raw as Record<string, unknown>;
  if (JSON.stringify(e).length > MAX_EVENT_BYTES) {
    return { event: null, error: "Event payload too large." };
  }
  if (typeof e["event"] !== "string" || !(ALLOWED_EVENTS as string[]).includes(e["event"])) {
    return { event: null, error: "Invalid event type." };
  }
  if (typeof e["sourcePackage"] !== "string" || !isValidPackage(e["sourcePackage"])) {
    return { event: null, error: "Invalid sourcePackage." };
  }
  if (typeof e["targetPackage"] !== "string" || !isValidPackage(e["targetPackage"])) {
    return { event: null, error: "Invalid targetPackage." };
  }
  if (typeof e["placement"] !== "string" || !PLACEMENT_RE.test(e["placement"].trim())) {
    return { event: null, error: "Invalid placement." };
  }
  const event: PromoEvent = {
    event: e["event"] as PromoEventType,
    sourcePackage: e["sourcePackage"],
    targetPackage: e["targetPackage"],
    placement: (e["placement"] as string).trim(),
  };
  if (e["eventId"] !== undefined) {
    if (typeof e["eventId"] !== "string" || !REQUEST_ID_RE.test(e["eventId"])) {
      return { event: null, error: "Invalid eventId." };
    }
    event.eventId = e["eventId"];
  }
  if (e["sessionId"] !== undefined && e["sessionId"] !== null && e["sessionId"] !== "") {
    if (typeof e["sessionId"] !== "string" || !SESSION_RE.test(e["sessionId"])) {
      return { event: null, error: "Invalid sessionId." };
    }
    event.sessionId = e["sessionId"];
  }
  if (e["rankPosition"] !== undefined) {
    if (typeof e["rankPosition"] !== "number" || !Number.isInteger(e["rankPosition"]) || e["rankPosition"] < 1 || e["rankPosition"] > 100) {
      return { event: null, error: "Invalid rankPosition." };
    }
    event.rankPosition = e["rankPosition"];
  }
  if (e["selectionType"] !== undefined && e["selectionType"] !== null && e["selectionType"] !== "") {
    if (typeof e["selectionType"] !== "string" || !SELECTION_TYPES.has(e["selectionType"])) {
      return { event: null, error: "Invalid selectionType." };
    }
    event.selectionType = e["selectionType"];
  }
  if (e["recommendationRequestId"] !== undefined && e["recommendationRequestId"] !== null && e["recommendationRequestId"] !== "") {
    if (typeof e["recommendationRequestId"] !== "string" || !REQUEST_ID_RE.test(e["recommendationRequestId"])) {
      return { event: null, error: "Invalid recommendationRequestId." };
    }
    event.recommendationRequestId = e["recommendationRequestId"];
  }
  if (e["sdkVersion"] !== undefined && e["sdkVersion"] !== null && e["sdkVersion"] !== "") {
    if (typeof e["sdkVersion"] !== "string" || !SDK_VERSION_RE.test(e["sdkVersion"])) {
      return { event: null, error: "Invalid sdkVersion." };
    }
    event.sdkVersion = e["sdkVersion"];
  }
  return { event, error: null };
}

export function newEventId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}
