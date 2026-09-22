# Hartmann Studios Dynamic Cross-Promotion Platform — Backend

Cloudflare Worker that dynamically discovers the Hartmann Studios Google Play
catalog, serves weighted cross-promotion recommendations, and collects
impression/click analytics. Zero always-on servers, free-tier friendly
(Worker + Workers KV + Cache API).

Live: `https://hartmann-crosspromo-api.charles-h-hartmann1.workers.dev`

## Architecture

```
Play developer page ──cron 6h──▶ Worker /admin/refresh ──safety gate──▶ KV catalog
                                                                        │
Android SDK ──GET /api/v1/recommendations──▶ read KV ──▶ rank ──▶ JSON   │
Android SDK ──POST /api/v1/events─────────▶ validate ──▶ KV counters ───┘
```

- **Serving never scrapes Google Play.** Recommendations read the normalized
  KV catalog (1–2 reads) plus pre-aggregated CTR counters.
- **Discovery is isolated** in `src/catalog/parser.ts` (+ `provider.ts`).
  Everything downstream consumes the normalized `AppEntry` model. If Google
  changes its HTML, only the parser needs updating.
- **No hardcoded app catalog anywhere.** New Play releases appear
  automatically after the next cron refresh; unpublished apps disappear.
- **Last-known-good safety gate** (`validateRefresh`): a refresh yielding
  0 apps or <50% of the previous count is rejected and logged; serving
  continues from the previous catalog.

## Endpoints (all under `/api/v1`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/catalog` | – | Normalized catalog (cached 5 min) |
| GET | `/api/v1/recommendations?sourcePackage=…&placement=…&limit=…` | – | Weighted picks (cached 5 min) |
| POST | `/api/v1/events` | – | `promo_impression` / `promo_click` / `promo_install` (single or `{events:[…]}` batch ≤25) |
| GET | `/api/v1/health` | – | `status`, `catalogApps`, refresh timestamps, `cacheStatus` |
| GET | `/api/v1/admin/status` | Bearer `ADMIN_TOKEN` | Catalog, refresh log, impressions/clicks/CTR, config, app list |
| POST | `/api/v1/admin/refresh` | Bearer `ADMIN_TOKEN` | Trigger a catalog refresh now |

Recommendations params: `sourcePackage`, `placement` (`[a-z0-9_-]{1,32}`),
`limit` (clamped to server `maxLimit`, default 3), `sessionId`,
`exclude` (comma list, ≤50 — used by clients for rotation), `locale`,
`sdkVersion`. All validated; invalid → 400 JSON, never a stack trace.

Response shape:

```json
{
  "version": 1,
  "requestId": "abc123",
  "generatedAt": "2026-09-22T13:00:00Z",
  "expiresAt": "2026-09-22T19:00:00Z",
  "apps": [
    {
      "packageName": "com.example.app",
      "name": "Example",
      "iconUrl": "https://…",
      "shortDescription": "…",
      "rating": 4.7,
      "ratingCount": 2400,
      "installText": "100K+",
      "storeUrl": "https://play.google.com/store/apps/details?id=com.example.app",
      "selectionType": "popular"
    }
  ]
}
```

Clients must ignore unknown JSON properties (forward compatibility).

## Recommendation algorithm

Popularity (0–1) blends public Play signals with observed CTR:

```
popularity = installs*0.50 + reviews*0.25 + rating*0.15 + promoPerf*0.10
```

Weights renormalize over available signals — metrics are never invented.
Effective weight = `(0.2 + popularity) × promotionMultiplier × newAppBoost`
(`×2.0` for apps first seen within `newAppBoostDays = 14`).

Per slot: roll `r` in [0,1); `r < popularWeight (0.65)` → popularity-weighted
pick, else uniform exploration pick — always without replacement, never the
source app, never disabled/blocked apps. Seeds derive from
`sessionId|source|placement|6h-bucket|exclude`, so recommendations are stable
per session/period without server-side session state.

Future revenue/attributed-install signals plug into `promoPerformanceScore`
and `effectiveWeight` — no client changes needed.

## Dynamic discovery details

- Catalog source: `https://play.google.com/store/apps/developer?id=Hartmann+Studios`
- `discoverPackageIds()` uses 4 independent link patterns (plain hrefs,
  `&amp;`-escaped, JS-escaped `\u003d` AF payloads, slugged URLs), dedupes,
  validates package syntax, and rejects platform namespaces
  (`com.google.android.*`, `com.android.*`, …).
- `parseAppDetail()` prefers stable structured data: LD+JSON
  `SoftwareApplication` → OpenGraph tags → `<h1>` fallback. Rating, review
  count, and install bucket each have multiple patterns (including Play's
  sibling-div markup) and are all nullable.
- Authenticated-source extension point: `GoogleAuthenticatedCatalogSource`
  (server-side only). Google currently offers no public "list my apps" API,
  so the public page is primary and the last-known-good catalog is the
  final fallback. Credentials must never ship in an APK.
- Metadata that is genuinely absent from server HTML (e.g. ratings for
  low-review apps) stays `null` and the ranker falls back gracefully.

## Remote configuration (no app release needed)

Stored in KV (`v1:config`), editable via `wrangler kv key put`:

```bash
npx wrangler kv key put --binding KV v1:config \
  '{"enabled":true,"popularWeight":0.65,"randomWeight":0.35,"newAppBoostDays":14,"newAppBoostMultiplier":2.0,"defaultLimit":3,"maxLimit":6,"recommendationTtlHours":6}'
```

Per-source-app overrides (`v1:appcfg:<package>`):

```bash
npx wrangler kv key put --binding KV v1:appcfg:com.example.app \
  '{"enabled":1,"max_cards":2,"placements":"[\"settings\",\"home\"]","blocked_targets":"[\"com.example.other\"]"}'
```

To kill-switch one app: `{"enabled":0,…}`. Omitted keys mean defaults.

## Setup & deployment (exact commands)

Prerequisites: Node 18+, a Cloudflare account, an API token with
Workers Script Edit + KV Storage Edit (Workers KV + D1 templates).

```bash
cd cloudflare/crosspromo-worker
npm install

# 1) Authenticate (non-interactive)
export CLOUDFLARE_API_TOKEN="<your token>"   # PowerShell: $env:CLOUDFLARE_API_TOKEN='<token>'

# 2) Create the KV namespace (once) and put its id in wrangler.jsonc
npx wrangler kv namespace create CROSSPROMO_KV

# 3) Protect admin routes (once; never commit this value)
echo "<long-random-string>" | npx wrangler secret put ADMIN_TOKEN

# 4) Deploy (cron 0 */6 * * * is configured in wrangler.jsonc)
npx wrangler deploy

# 5) First catalog refresh + verify (backend URL from deploy output)
curl -X POST "https://<worker>/api/v1/admin/refresh" -H "Authorization: Bearer <ADMIN_TOKEN>"
curl "https://<worker>/api/v1/health"
curl "https://<worker>/api/v1/catalog" | head -c 600
curl "https://<worker>/api/v1/recommendations?sourcePackage=com.tipsybuddy.app&placement=settings&limit=3"
```

Local dev: `npx wrangler dev` (uses local KV simulation).

## Testing

```bash
npm run typecheck   # tsc --noEmit, must be clean
npm test            # vitest: parser (30), ranking (8), events/validation (7), 100k simulation (1)
npm run simulate    # just the distribution simulation
```

The simulation runs 100,000 selections and asserts: popular > tail exposure,
tail >1% each (no starvation), new-app boost lands between tail and leader,
leader <45% (no monopoly), source/disabled never selected, no duplicates,
~65/35 weighted/exploration split.

## Analytics & attribution

Raw events persist 7 days (`v1:evt:<uuid>`); counters (`v1:ctr:<pkg>`,
`v1:place:<p>|<s>|<t>`) feed CTR = clicks/impressions into ranking (10%
blend — an early winner cannot permanently dominate). Dashboards read
`GET /api/v1/admin/status` (Bearer token).

Install attribution uses the documented
[Play Install Referrer API](https://developer.android.com/google/play/installreferrer):
taps append `referrer=utm_source%3D…%26utm_medium%3Dcrosspromo…` to the Play
URL; the target app optionally reads it on first launch (see
`hartmann-crosspromo/.../attribution/HartmannInstallAttribution.kt`) and
posts `promo_install`.

## Troubleshooting

- `catalogApps: 0` after deploy → cron hasn't run yet; POST
  `/api/v1/admin/refresh` manually.
- Refresh `status: rejected` → safety gate tripped (see `detail`);
  serving continues from last-known-good. Check `recentRefreshes` in admin
  status. If Google changed markup, update `src/catalog/parser.ts`,
  add/refresh fixtures in `test/fixtures/`, run `npm test`, redeploy.
- All apps show `selectionType: new_app_boost` → expected for ~14 days
  after the very first refresh (everything is newly discovered); it decays
  automatically.
- `installText: "0+"/"1+"` → Play renders tiny buckets oddly; treated as ~0
  installs by the ranker (harmless).
- 429s → per-IP in-memory rate limits (120/min/recs, 120/min events);
  pair with Cloudflare Rate Limiting rules for production hardening.
