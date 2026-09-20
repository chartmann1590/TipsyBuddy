# AdMob setup for TipsyBuddy

Debug builds use **Google's official test unit IDs** so ads load safely in development.

## Where to put production IDs

1. **`app/build.gradle.kts`** — `buildTypes.release` block:
   - `BuildConfig.ADMOB_APP_ID`
   - `BuildConfig.ADMOB_BANNER_ID`
   - `BuildConfig.ADMOB_INTERSTITIAL_ID`
   - `manifestPlaceholders["admobAppId"]` (must match App ID)

2. **`AndroidManifest.xml`** — already wired via:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="${admobAppId}" />
   ```

3. **`AdsConfig.kt`** — documents placeholders and reads BuildConfig at runtime.

## Official Google test IDs (default)

| Slot | Test ID |
|------|---------|
| App ID | `ca-app-pub-3940256099942544~3347511713` |
| Banner | `ca-app-pub-3940256099942544/6300978111` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |

## Placement in app

- **Banner**: Tonight dashboard, Drink Logger, Health Insights, Profile/Settings
- **Interstitial**: after logging a drink (dashboard quick-add & Log screen), with light frequency capping (every 3 actions + 90s minimum gap). Load/show failures are ignored so BAC / logging never breaks.

## Before Play release

Replace release BuildConfig values with real AdMob App + unit IDs from [AdMob console](https://admob.google.com/). Do not leave Google test IDs in a production listing.
