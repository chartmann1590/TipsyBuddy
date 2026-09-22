/**
 * PlayStoreCatalogProvider parsing layer.
 *
 * Google Play's public website is NOT a stable API, so this module isolates
 * every scrap of HTML knowledge in one place:
 *
 *   - `discoverPackageIds(html)` finds canonical `details?id=<package>` links
 *     using several independent patterns (raw hrefs, &amp;-escaped hrefs,
 *     JS-escaped `id\u003d` payloads). No CSS selectors are used at all.
 *   - `parseAppDetail(html, packageName)` prefers stable structured data:
 *     1. `application/ld+json` SoftwareApplication block (schema.org)
 *     2. OpenGraph meta tags (og:title / og:description / og:image)
 *     3. `<h1>` title + icon heuristics as a last resort
 *     Optional signals (rating, review count, install bucket) each have
 *     multiple fallback patterns and are ALL nullable — one missing field
 *     must never fail the whole catalog.
 *
 * If Google changes its markup, only this file needs updating. Everything
 * downstream consumes the normalized {@link AppEntry} model.
 */
import type { AppEntry, ScrapedMetadata } from "../types";

export const DEVELOPER_PAGE_URL =
  "https://play.google.com/store/apps/developer?id=Hartmann+Studios";

export function canonicalStoreUrl(packageName: string): string {
  return `https://play.google.com/store/apps/details?id=${packageName}`;
}

/** Strict Android package validation (also rejects obvious non-app links). */
export function isValidPackageId(pkg: string): boolean {
  if (!pkg || pkg.length > 255 || pkg.length < 3) return false;
  if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(pkg)) return false;
  const lower = pkg.toLowerCase();
  // Reject platform / Google first-party namespaces that can appear in
  // "More by" rails or footer links but are never Hartmann Studios apps.
  const blockedPrefixes = [
    "com.google.android.",
    "com.android.",
    "com.google.",
    "android.",
  ];
  if (blockedPrefixes.some((p) => lower.startsWith(p))) return false;
  if (lower === "com.google.android.gms" || lower === "com.android.vending") return false;
  return true;
}

/**
 * Discover candidate app package IDs from a Play developer-listing page.
 * Returns deduplicated, validated package names in first-seen order.
 */
export function discoverPackageIds(html: string): string[] {
  const found: string[] = [];
  const seen = new Set<string>();
  const push = (pkg: string) => {
    const clean = pkg.trim().replace(/[."';)\]]+$/, "");
    if (!isValidPackageId(clean) || seen.has(clean)) return;
    seen.add(clean);
    found.push(clean);
  };

  // Strategy 1: plain hrefs — /store/apps/details?id=com.example.app
  const hrefRe = /\/store\/apps\/details\?id=([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/g;
  let m: RegExpExecArray | null;
  while ((m = hrefRe.exec(html)) !== null) {
    if (m[1]) push(m[1]);
  }

  // Strategy 2: HTML-escaped hrefs — details?id=...&amp;hl=en
  const escRe = /\/store\/apps\/details\?id=([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)&(?:amp|quot);/g;
  while ((m = escRe.exec(html)) !== null) {
    if (m[1]) push(m[1]);
  }

  // Strategy 3: JS-escaped AF_initData payloads — details?id\u003dcom.example.app
  const jsRe = /store\/apps\/details\/[^"'\s]*\?id\\u003d([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/g;
  while ((m = jsRe.exec(html)) !== null) {
    if (m[1]) push(m[1]);
  }

  // Strategy 4: slugged detail URLs — /store/apps/details/App_Name?id=com.example.app
  const slugRe = /store\/apps\/details\/[A-Za-z0-9_~.%-]+\?id=([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/g;
  while ((m = slugRe.exec(html)) !== null) {
    if (m[1]) push(m[1]);
  }

  return found;
}

// ---------------------------------------------------------------------------
// Detail-page parsing helpers (each defensive, each nullable)
// ---------------------------------------------------------------------------

function decodeEntities(s: string): string {
  return s
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/\\u0026/g, "&")
    .replace(/\\u003d/g, "=")
    .replace(/\\"/g, '"')
    .trim();
}

function firstLdJsonBlock(html: string): Record<string, unknown> | null {
  const re =
    /<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html)) !== null) {
    const block = m[1];
    if (!block) continue;
    try {
      const parsed: unknown = JSON.parse(block);
      const candidates = Array.isArray(parsed) ? parsed : [parsed];
      for (const c of candidates) {
        if (
          c !== null &&
          typeof c === "object" &&
          (c as Record<string, unknown>)["@type"] === "SoftwareApplication"
        ) {
          return c as Record<string, unknown>;
        }
      }
    } catch {
      // Malformed block — try the next one.
    }
  }
  return null;
}

function metaContent(html: string, property: string): string | null {
  // Handles both <meta property="og:x" content="..."> orders.
  const re1 = new RegExp(
    `<meta[^>]*property=["']${property}["'][^>]*content=["']([^"']+)["']`,
    "i",
  );
  const re2 = new RegExp(
    `<meta[^>]*content=["']([^"']+)["'][^>]*property=["']${property}["']`,
    "i",
  );
  const m = html.match(re1) ?? html.match(re2);
  const raw = m?.[1];
  return raw ? decodeEntities(raw) : null;
}

function h1Text(html: string): string | null {
  const m = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
  if (!m?.[1]) return null;
  const text = decodeEntities(m[1].replace(/<[^>]+>/g, " ").replace(/\s+/g, " "));
  return text || null;
}

function parseCompactNumber(raw: string): number | null {
  // "1,234" | "12.5K" | "3M" | "1.2B"
  const cleaned = raw.replace(/,/g, "").trim();
  const m = cleaned.match(/^([\d.]+)\s*([KMB])?$/i);
  if (!m?.[1]) return null;
  const base = Number(m[1]);
  if (!Number.isFinite(base)) return null;
  const mult = { K: 1e3, M: 1e6, B: 1e9 }[(m[2] ?? "").toUpperCase()] ?? 1;
  const v = Math.floor(base * mult);
  return Number.isSafeInteger(v) ? v : null;
}

/** "10K+" / "100K+" / "1M+" / "10+" / "5,000+" -> lower-bound installs. */
export function parseInstallBucket(text: string | null): {
  installText: string | null;
  estimatedMinimumInstalls: number | null;
} {
  if (!text) return { installText: null, estimatedMinimumInstalls: null };
  const m = text.trim().match(/^([\d,.]+\s*[KMB]?\+)\s*(downloads?|installs?)?$/i);
  if (!m?.[1]) return { installText: null, estimatedMinimumInstalls: null };
  const installText = m[1].replace(/\s+/g, "");
  const n = parseCompactNumber(installText.replace(/\+$/, ""));
  return { installText, estimatedMinimumInstalls: n };
}

function findRating(html: string, ld: Record<string, unknown> | null): number | null {
  // 1) schema.org aggregateRating (most stable when present).
  try {
    const agg = ld?.["aggregateRating"] as Record<string, unknown> | undefined;
    const v = agg?.["ratingValue"];
    const n = typeof v === "string" ? Number(v) : typeof v === "number" ? v : NaN;
    if (Number.isFinite(n) && n >= 1 && n <= 5) return Math.round(n * 10) / 10;
  } catch {
    // fall through
  }
  // 2) "4.6★" glyph pattern.
  const star = html.match(/([1-5](?:\.\d)?)\s*★/);
  if (star?.[1]) {
    const n = Number(star[1]);
    if (Number.isFinite(n) && n >= 1 && n <= 5) return n;
  }
  // 3) "Rated 4.6 stars" accessible label.
  const rated = html.match(/Rated\s+([1-5](?:\.\d)?)\s+stars?/i);
  if (rated?.[1]) {
    const n = Number(rated[1]);
    if (Number.isFinite(n) && n >= 1 && n <= 5) return Math.round(n * 10) / 10;
  }
  return null;
}

function findReviewCount(html: string, ld: Record<string, unknown> | null): number | null {
  try {
    const agg = ld?.["aggregateRating"] as Record<string, unknown> | undefined;
    const v = agg?.["ratingCount"] ?? agg?.["reviewCount"];
    if (typeof v === "number" && Number.isFinite(v)) return Math.floor(v);
    if (typeof v === "string") {
      const n = parseCompactNumber(v);
      if (n !== null) return n;
    }
  } catch {
    // fall through
  }
  const patterns = [
    /([\d,.]+\s*[KMB]?)\s+reviews?/i,
    /([\d,.]+\s*[KMB]?)\s+ratings?/i,
    // Play wraps count and label in sibling divs:
    // <div class="ClM7O">1.2K</div><div …>reviews</div>
    /([\d,.]+\s*[KMB]?)(?:<[^>]{0,200}>|\s){1,10}reviews?/i,
    /([\d,.]+\s*[KMB]?)(?:<[^>]{0,200}>|\s){1,10}ratings?/i,
  ];
  for (const re of patterns) {
    const m = html.match(re);
    if (m?.[1]) {
      const n = parseCompactNumber(m[1]);
      if (n !== null) return n;
    }
  }
  return null;
}

function findInstallText(html: string): string | null {
  const patterns = [
    /([\d,.]+\s*[KMB]?\+)\s*Downloads/i,
    /([\d,.]+\s*[KMB]?\+)\s*installs?/i,
    // Sibling-div markup: <div class="ClM7O">10+</div><div …>Downloads</div>
    /([\d,.]+\s*[KMB]?\+)(?:<[^>]{0,200}>|\s){1,10}Downloads/i,
  ];
  for (const re of patterns) {
    const m = html.match(re);
    if (m?.[1]) return m[1].replace(/\s+/g, "");
  }
  return null;
}

function normalizeCategory(raw: string | null): string | null {
  if (!raw) return null;
  const lower = raw.trim().toLowerCase();
  if (!lower) return null;
  // Play LD+JSON uses SCREAMING category ids ("TOOLS"); prettify them.
  if (/^[a-z_]+$/.test(lower)) {
    return lower
      .split("_")
      .map((w) => (w ? w.charAt(0).toUpperCase() + w.slice(1) : w))
      .join(" ");
  }
  return raw.trim().slice(0, 64);
}

/**
 * Parse a Play app detail page into nullable scraped metadata.
 * Never throws for malformed HTML — returns all-null fields instead.
 */
export function parseAppDetail(html: string, packageName: string): ScrapedMetadata {
  const empty: ScrapedMetadata = {
    name: null,
    iconUrl: null,
    shortDescription: null,
    rating: null,
    reviewCount: null,
    installText: null,
    category: null,
    priceText: null,
    isFree: null,
    developer: null,
  };
  if (!html || !packageName) return empty;
  try {
    const ld = firstLdJsonBlock(html);
    const str = (v: unknown): string | null =>
      typeof v === "string" && v.trim() ? v.trim() : null;

    const ldName = str(ld?.["name"]);
    const ogTitle = metaContent(html, "og:title")?.replace(/\s*-\s*Apps on Google Play\s*$/i, "") ?? null;
    const name = ldName ?? ogTitle ?? h1Text(html);

    let iconUrl = str(ld?.["image"]);
    if (!iconUrl) {
      const ogImage = metaContent(html, "og:image");
      // Strip Play's size suffix (=s0 / =w240-h480-rw) for a stable canonical icon.
      iconUrl = ogImage ? (ogImage.split("=")[0] ?? null) : null;
    }
    if (iconUrl && !/^https:\/\//i.test(iconUrl)) iconUrl = null;

    const shortDescription =
      str(ld?.["description"]) ?? metaContent(html, "og:description");

    // Price / free detection via schema.org offers.
    let priceText: string | null = null;
    let isFree: boolean | null = null;
    try {
      const offers = ld?.["offers"];
      const first: unknown = Array.isArray(offers) ? offers[0] : offers;
      if (first !== null && typeof first === "object") {
        const price = (first as Record<string, unknown>)["price"];
        if (typeof price === "string" || typeof price === "number") {
          priceText = String(price);
          isFree = Number(price) === 0;
        }
      }
    } catch {
      // ignore, stay null
    }
    if (isFree === null && /Contains ads|In-app purchases/i.test(html)) {
      // Informational only; free-to-install with monetization is still free.
      isFree = true;
    }
    if (isFree === null) isFree = true; // Play listings default to free unless priced.

    let developer: string | null = null;
    try {
      const author = ld?.["author"] as Record<string, unknown> | undefined;
      developer = str(author?.["name"]);
    } catch {
      developer = null;
    }

    return {
      name: name ? decodeEntities(name).slice(0, 120) : null,
      iconUrl,
      shortDescription: shortDescription ? shortDescription.slice(0, 500) : null,
      rating: findRating(html, ld),
      reviewCount: findReviewCount(html, ld),
      installText: findInstallText(html),
      category: normalizeCategory(str(ld?.["applicationCategory"])),
      priceText,
      isFree,
      developer,
    };
  } catch {
    return empty;
  }
}

/**
 * Merge scraped metadata into the normalized internal model.
 * Missing optional fields stay null — the catalog entry is still valid as
 * long as packageName + name + storeUrl are present.
 */
export function normalizeApp(
  packageName: string,
  meta: ScrapedMetadata,
  nowIso: string,
  previous?: { firstDiscoveredAt?: string; enabledForPromotion?: boolean; promotionMultiplier?: number },
): AppEntry | null {
  const name = meta.name?.trim();
  if (!isValidPackageId(packageName) || !name) return null;
  const { installText, estimatedMinimumInstalls } = parseInstallBucket(meta.installText);
  return {
    packageName,
    name: name.slice(0, 120),
    iconUrl: meta.iconUrl,
    storeUrl: canonicalStoreUrl(packageName),
    shortDescription: meta.shortDescription,
    rating: meta.rating,
    reviewCount: meta.reviewCount,
    installText,
    estimatedMinimumInstalls,
    category: meta.category,
    priceText: meta.priceText,
    isFree: meta.isFree ?? true,
    developer: meta.developer,
    firstDiscoveredAt: previous?.firstDiscoveredAt ?? nowIso,
    lastSeenAt: nowIso,
    lastMetadataRefresh: nowIso,
    enabledForPromotion: previous?.enabledForPromotion ?? true,
    promotionMultiplier: previous?.promotionMultiplier ?? 1.0,
  };
}

/**
 * Catalog safety gate: decide whether a fresh discovery result may replace
 * the currently served catalog. A Google markup change must never wipe
 * promotions, so empty or dramatically shrunken results are rejected.
 */
export function validateRefresh(
  previousCount: number,
  nextApps: AppEntry[],
  diagnostics: { discovered: number },
): { ok: boolean; reason: string } {
  if (nextApps.length === 0) {
    return { ok: false, reason: `rejected: discovery returned 0 apps (previous=${previousCount})` };
  }
  if (previousCount > 0) {
    // Allow genuine shrinkage (unpublished apps) down to 50%, but flag
    // anything more dramatic as a probable scraper failure.
    if (nextApps.length < previousCount * 0.5) {
      return {
        ok: false,
        reason: `rejected: suspicious drop previous=${previousCount} next=${nextApps.length}`,
      };
    }
    // Sanity: the refresh should not claim to discover fewer raw ids than
    // the valid apps it produced.
    if (diagnostics.discovered > 0 && diagnostics.discovered < nextApps.length) {
      return { ok: false, reason: "rejected: inconsistent discovery diagnostics" };
    }
  }
  return { ok: true, reason: "accepted" };
}
