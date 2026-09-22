/**
 * PlayStoreCatalogProvider abstraction.
 *
 * Priority order (see factory at the bottom):
 *   1. GoogleAuthenticatedCatalogSource — authoritative, when configured.
 *   2. PublicPlayStoreCatalogSource     — public developer page scraping.
 *   3. CachedCatalogSource              — last-known-good catalog from D1.
 *
 * The rest of the system never touches Google HTML directly; it only sees
 * normalized package ids + {@link ScrapedMetadata}.
 */
import { discoverPackageIds, parseAppDetail } from "./parser";
import type { ScrapedMetadata } from "../types";

export interface DiscoveryResult {
  packageIds: string[];
  diagnostics: {
    bytes: number;
    patternsMatched: number;
  };
}

export interface PlayStoreCatalogProvider {
  readonly sourceName: string;
  discoverApps(): Promise<DiscoveryResult>;
  fetchAppMetadata(packageName: string): Promise<ScrapedMetadata>;
}

const FETCH_TIMEOUT_MS = 15_000;
const DETAIL_CONCURRENCY = 4;

function fetchWithTimeout(url: string, init?: RequestInit): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  return fetch(url, {
    ...init,
    signal: ctrl.signal,
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
      "Accept-Language": "en-US,en;q=0.9",
      ...(init?.headers ?? {}),
    },
  }).finally(() => clearTimeout(timer));
}

/**
 * Public developer-page scraping source.
 * Used by the scheduled cron refresh — NEVER on the recommendation hot path.
 */
export class PublicPlayStoreCatalogSource implements PlayStoreCatalogProvider {
  readonly sourceName = "public-play-store";
  constructor(
    private readonly developerPageUrl: string,
    private readonly developerId: string = "Hartmann Studios",
  ) {}

  async discoverApps(): Promise<DiscoveryResult> {
    const res = await fetchWithTimeout(this.developerPageUrl);
    if (!res.ok) throw new Error(`developer page HTTP ${res.status}`);
    const html = await res.text();
    const packageIds = discoverPackageIds(html);
    return {
      packageIds,
      diagnostics: { bytes: html.length, patternsMatched: packageIds.length },
    };
  }

  async fetchAppMetadata(packageName: string): Promise<ScrapedMetadata> {
    const url = `https://play.google.com/store/apps/details?id=${encodeURIComponent(packageName)}&hl=en`;
    const res = await fetchWithTimeout(url);
    if (!res.ok) throw new Error(`detail page HTTP ${res.status} for ${packageName}`);
    return parseAppDetail(await res.text(), packageName);
  }

  getDeveloperId(): string {
    return this.developerId;
  }
}

/**
 * Authenticated official-source extension point.
 *
 * Google's Play Developer Publishing API currently offers no public
 * "list my apps" method suitable for this use-case; if Google adds one (or
 * you wire a private catalog feed), implement it here and set a
 * GOOGLE_CATALOG_URL secret. Until then this source reports "not
 * configured" and the factory skips it. Authenticated calls run ONLY
 * server-side — credentials must never ship in an APK.
 */
export class GoogleAuthenticatedCatalogSource implements PlayStoreCatalogProvider {
  readonly sourceName = "google-authenticated";
  constructor(private readonly feedUrl: string | null) {}

  isConfigured(): boolean {
    return Boolean(this.feedUrl);
  }

  async discoverApps(): Promise<DiscoveryResult> {
    if (!this.feedUrl) throw new Error("google-authenticated source not configured");
    // Expected feed shape: { "packageNames": ["com.example.app", ...] }.
    // Kept intentionally narrow so any future official API can adapt here.
    const res = await fetchWithTimeout(this.feedUrl);
    if (!res.ok) throw new Error(`authenticated catalog HTTP ${res.status}`);
    const body = (await res.json()) as { packageNames?: unknown };
    const ids = Array.isArray(body.packageNames)
      ? body.packageNames.filter((x): x is string => typeof x === "string")
      : [];
    return { packageIds: ids, diagnostics: { bytes: 0, patternsMatched: ids.length } };
  }

  async fetchAppMetadata(): Promise<ScrapedMetadata> {
    throw new Error("authenticated source delegates metadata to the public source");
  }
}

/** Fetch detail pages with bounded concurrency; failures stay per-app. */
export async function fetchAllMetadata(
  provider: PlayStoreCatalogProvider,
  packageIds: string[],
  concurrency = DETAIL_CONCURRENCY,
): Promise<{ meta: Map<string, ScrapedMetadata>; failures: string[] }> {
  const meta = new Map<string, ScrapedMetadata>();
  const failures: string[] = [];
  let cursor = 0;
  const workers = Array.from(
    { length: Math.min(concurrency, Math.max(1, packageIds.length)) },
    async () => {
      while (cursor < packageIds.length) {
        const pkg = packageIds[cursor++]!;
        try {
          meta.set(pkg, await provider.fetchAppMetadata(pkg));
        } catch {
          failures.push(pkg);
        }
      }
    },
  );
  await Promise.all(workers);
  return { meta, failures };
}
