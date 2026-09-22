/**
 * Analytics ingestion: validated events -> KV raw log (7-day TTL) +
 * pre-aggregated counters. Writes use single-key puts exclusively. Unknown
 * target packages are rejected (anti-spam); unknown *source* packages are
 * accepted but flagged, because a brand-new (unpublished) host app must
 * still be able to report its promos.
 *
 * Abuse economics: per-IP rate limits (see index.ts) + a 25-event batch cap
 * + tiny payload cap bound the write rate; counters are approximate
 * read-modify-write, raw events retained for recompute.
 */
import { MAX_BATCH_EVENTS, newEventId, validateEventShape } from "../api/validation";
import { addToCounter, indexPlace, indexTarget } from "../catalog/store";
import type { PromoEvent } from "../types";

export interface IngestResult {
  accepted: number;
  rejected: number;
  errors: string[];
}

const EVENT_TTL_SECONDS = 7 * 24 * 3600;

export function extractBatch(body: unknown): { raw: unknown[]; error: string | null } {
  if (!body || typeof body !== "object") return { raw: [], error: "Body must be a JSON object." };
  const b = body as Record<string, unknown>;
  if (Array.isArray(b["events"])) {
    if (b["events"].length > MAX_BATCH_EVENTS) {
      return { raw: [], error: `At most ${MAX_BATCH_EVENTS} events per request.` };
    }
    return { raw: b["events"] as unknown[], error: null };
  }
  return { raw: [body], error: null };
}

export async function ingestEvents(
  kv: KVNamespace,
  rawEvents: unknown[],
  knownTargets: Set<string>,
  nowIso: string,
): Promise<IngestResult> {
  const result: IngestResult = { accepted: 0, rejected: 0, errors: [] };
  const valid: PromoEvent[] = [];
  for (const raw of rawEvents.slice(0, MAX_BATCH_EVENTS)) {
    const { event, error } = validateEventShape(raw);
    if (!event || error) {
      result.rejected += 1;
      if (result.errors.length < 5) result.errors.push(error ?? "Invalid event.");
      continue;
    }
    if (event.sourcePackage === event.targetPackage) {
      result.rejected += 1;
      if (result.errors.length < 5) result.errors.push("sourcePackage must differ from targetPackage.");
      continue;
    }
    if (!knownTargets.has(event.targetPackage)) {
      result.rejected += 1;
      if (result.errors.length < 5) result.errors.push("Unknown targetPackage.");
      continue;
    }
    if (!event.eventId) event.eventId = newEventId();
    if (!event.timestamp) event.timestamp = nowIso;
    valid.push(event);
  }
  if (valid.length === 0) return result;

  // Raw audit log (idempotent on eventId — second PUT overwrites identically).
  await Promise.all(
    valid.map((e) =>
      kv.put(`v1:evt:${e.eventId}`, JSON.stringify(e), { expirationTtl: EVENT_TTL_SECONDS }).catch(() => undefined),
    ),
  );

  // Pre-aggregated counters so dashboards/CTR never scan raw events.
  const deltas = new Map<string, { i: number; c: number; n: number }>();
  const placeDeltas = new Map<string, { i: number; c: number }>();
  for (const e of valid) {
    const a = deltas.get(e.targetPackage) ?? { i: 0, c: 0, n: 0 };
    if (e.event === "promo_impression") a.i += 1;
    else if (e.event === "promo_click") a.c += 1;
    else a.n += 1;
    deltas.set(e.targetPackage, a);
    if (e.event === "promo_impression" || e.event === "promo_click") {
      const k = `${e.placement}|${e.sourcePackage}|${e.targetPackage}`;
      const p = placeDeltas.get(k) ?? { i: 0, c: 0 };
      if (e.event === "promo_impression") p.i += 1;
      else p.c += 1;
      placeDeltas.set(k, p);
    }
  }
  await Promise.all([
    ...[...deltas.entries()].map(async ([target, a]) => {
      try {
        await addToCounter(kv, `v1:ctr:${target}`, a.i, a.c, a.n);
        await indexTarget(kv, target);
      } catch {
        // best-effort
      }
    }),
    ...[...placeDeltas.entries()].map(async ([key, p]) => {
      try {
        await addToCounter(kv, `v1:place:${key}`, p.i, p.c, 0);
        await indexPlace(kv, key);
      } catch {
        // best-effort
      }
    }),
  ]);
  result.accepted = valid.length;
  return result;
}
