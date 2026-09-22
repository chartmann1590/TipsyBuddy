/**
 * Shared domain types for the Hartmann Studios Dynamic Cross-Promotion Platform.
 *
 * The Android client must ignore unknown JSON properties in every response
 * (forward compatibility), so additive fields here are always safe.
 */

/** Normalized internal representation of one Google Play app. */
export interface AppEntry {
  packageName: string;
  name: string;
  iconUrl: string | null;
  storeUrl: string;
  shortDescription: string | null;
  rating: number | null;
  /** Number of user ratings (Play "reviews" count shown on the listing). */
  reviewCount: number | null;
  /** Raw Play install bucket text, e.g. "10K+". Null when unavailable. */
  installText: string | null;
  /** Lower bound parsed from installText, e.g. "10K+" -> 10000. */
  estimatedMinimumInstalls: number | null;
  category: string | null;
  priceText: string | null;
  isFree: boolean;
  developer: string | null;
  firstDiscoveredAt: string;
  lastSeenAt: string;
  lastMetadataRefresh: string | null;
  enabledForPromotion: boolean;
  promotionMultiplier: number;
}

/** Partial metadata scraped from a Play listing page (all fields optional). */
export interface ScrapedMetadata {
  name: string | null;
  iconUrl: string | null;
  shortDescription: string | null;
  rating: number | null;
  reviewCount: number | null;
  installText: string | null;
  category: string | null;
  priceText: string | null;
  isFree: boolean | null;
  developer: string | null;
}

/** Diagnostics recorded for every catalog refresh attempt. */
export interface RefreshDiagnostics {
  source: string;
  discovered: number;
  accepted: number;
  rejected: number;
  metadataFailures: number;
  rejectedPackages: string[];
  metadataFailedPackages: string[];
}

/** Remote-tunable recommendation behaviour. Served from D1 `config`. */
export interface PromoConfig {
  enabled: boolean;
  popularWeight: number;
  randomWeight: number;
  newAppBoostDays: number;
  newAppBoostMultiplier: number;
  defaultLimit: number;
  maxLimit: number;
  recommendationTtlHours: number;
  catalogRefreshHours: number;
  metadataRefreshHours: number;
}

/** Per-source-app server-side overrides (D1 `app_config`). */
export interface SourceAppConfig {
  enabled: boolean;
  maxCards: number | null;
  placements: string[] | null;
  blockedTargets: string[];
}

export type SelectionType = "popular" | "random" | "new_app_boost";

export interface RecommendedApp {
  packageName: string;
  name: string;
  iconUrl: string | null;
  shortDescription: string | null;
  rating: number | null;
  ratingCount: number | null;
  installText: string | null;
  storeUrl: string;
  selectionType: SelectionType;
}

export interface RecommendationResponse {
  version: 1;
  requestId: string;
  generatedAt: string;
  expiresAt: string;
  apps: RecommendedApp[];
}

export interface CatalogResponse {
  version: 1;
  generatedAt: string;
  count: number;
  apps: RecommendedApp[];
}

export type PromoEventType = "promo_impression" | "promo_click" | "promo_install";

export interface PromoEvent {
  event: PromoEventType;
  eventId?: string;
  sourcePackage: string;
  targetPackage: string;
  placement: string;
  timestamp?: string;
  sessionId?: string;
  rankPosition?: number;
  selectionType?: string;
  recommendationRequestId?: string;
  sdkVersion?: string;
}

export interface WorkerEnv {
  KV: KVNamespace;
  ADMIN_TOKEN?: string;
  DEVELOPER_ID?: string;
  DEVELOPER_PAGE_URL?: string;
  /** Optional: JSON array of package ids treated as authoritative fallback. */
  FALLBACK_PACKAGES?: string;
}
