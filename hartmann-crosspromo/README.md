# Hartmann Cross-Promo SDK (`hartmann-crosspromo`)

Reusable Android library that promotes your other Play Store apps inside any
Hartmann Studios app. Jetpack Compose first, Android Views supported, no
hardcoded catalog — everything comes from the backend at runtime.

## 2-minute integration

1. Add the module dependency (already done in TipsyBuddy):

```kotlin
// app/build.gradle.kts
implementation(project(":hartmann-crosspromo"))
```

2. Expose the backend URL (non-secret) via `BuildConfig`:

```kotlin
// defaultConfig / buildTypes — resolve from -Pcrosspromo.api.url,
// $CROSSPROMO_API_URL, local.properties, or "" (SDK stays off)
buildConfigField("String", "CROSS_PROMO_URL", "\"$crossPromoUrl\"")
```

3. Initialize once in `Application.onCreate` (source package is detected
   automatically — never type it by hand):

```kotlin
HartmannCrossPromo.initialize(
    application = this,
    apiBaseUrl = BuildConfig.CROSS_PROMO_URL, // blank = SDK stays inert
)
```

4. Drop the section into any Compose screen (settings/about/home bottom):

```kotlin
HartmannCrossPromoRow(placement = "settings") // limit = 3 by default
```

XML layouts (no Compose migration required):

```xml
<com.hartmann.crosspromo.ui.HartmannPromoView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:hcp_placement="settings"
    app:hcp_limit="3" />
```

That's it. If the backend is unreachable, disabled remotely, or empty, the
section renders nothing — the host app is never blocked, no error UI appears.

## What you get

- **Dynamic catalog** — new Play releases appear automatically; unpublished
  apps disappear. The SDK holds zero package names.
- **Material 3 cards** — "More from Hartmann Studios" carousel with icon,
  description, rating, and an honest "View App" CTA (never styled as a
  Google ad). Light/dark/dynamic-color, phones/tablets, TalkBack labels,
  large fonts.
- **Correct tap behavior** — `market://details?id=` with the Play app,
  HTTPS fallback in a browser, `referrer=utm_source%3D…` attribution
  appended. Never downloads APKs, never installs anything.
- **Stale-while-revalidate cache** (DataStore, no Room needed) — instant
  paint from cache, background refresh, offline-tolerant.
- **Rotation** — recent targets are sent as `exclude=` so users see fresh
  apps; recommendations are stable per session/6h window.
- **Analytics** — `promo_impression` (counted once per card via
  `ImpressionTracker`, not per recomposition) and `promo_click`, posted to
  the backend. Optional `FirebaseAnalyticsAdapter { name, params -> … }`
  lambda forwards `crosspromo_impression/click` to an existing Firebase
  instance — the SDK has no Firebase dependency. `NoOpAnalytics` opts out.

## Privacy & Play policy

- No `QUERY_ALL_PACKAGES`, no advertising ID, no Android ID, no
  fingerprinting. Session = random UUID rotated daily.
- CTA always opens the official Play listing after an explicit user tap.
- Manifest is permission-free.

## Install attribution (optional)

Add `com.android.installreferrer:installreferrer` in the host app, read the
referrer once on first launch, and parse it with
`HartmannInstallAttribution.parseReferrer()` (see its KDoc for the full
snippet). Cross-promo installs arrive with `medium == "crosspromo"` and can
be posted as `promo_install` events.

## Module layout

```
api/          CrossPromoApi.kt      (HttpURLConnection, kotlinx.serialization, ignoreUnknownKeys)
model/        PromoApp.kt / PromoResponse.kt
repository/   CrossPromoRepository.kt (stale-while-revalidate)
cache/        PromoCache.kt         (DataStore: bodies, recents, session)
ui/           HartmannPromoUi.kt    (Row + Card, Compose/Material3)
ui/           HartmannPromoView.kt  (XML View version, framework widgets + Coil)
analytics/    PromoAnalytics.kt     (interface, NoOp, Backend, Firebase-lambda, Composite)
launcher/     PlayStoreLauncher.kt  (market:// → https fallback + UTM referrer)
attribution/  HartmannInstallAttribution.kt (Install Referrer parsing)
util/         RotationTracker.kt, ImpressionTracker.kt
```

## Tests

Pure-JVM unit tests (no device needed):

```bash
./gradlew :hartmann-crosspromo:testDebugUnitTest
```

19 tests: response parsing (incl. unknown-field tolerance + malformed
bodies), rotation/exclude logic, impression dedupe, launcher URIs/referrer,
referrer parsing.
