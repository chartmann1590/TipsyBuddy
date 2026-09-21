# AdMob setup for TipsyBuddy

Debug builds use **Google's official test unit IDs** so ads load safely in development.

## Where production IDs live (never hardcoded)

Release builds resolve production IDs at build time — no real publisher IDs
appear in committed source. Priority (first non-blank wins):

1. **Gradle property** `-P ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID`
2. **Environment** `ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID`
   — GitHub Actions maps the repository Secrets of the same names to env
   (see `.github/workflows/android-release.yml`).
3. **`local.properties`** (gitignored, local dev only):
   ```properties
   admob.app.id=ca-app-pub-XXXX~YYYY
   admob.banner.id=ca-app-pub-XXXX/BBBB
   admob.interstitial.id=ca-app-pub-XXXX/IIII
   ```
4. **Fallback**: Google official test IDs (build never breaks without Secrets).

`app/build.gradle.kts` (`buildTypes.release`) wires the resolved values into:

- `BuildConfig.ADMOB_APP_ID`
- `BuildConfig.ADMOB_BANNER_ID`
- `BuildConfig.ADMOB_INTERSTITIAL_ID`
- `manifestPlaceholders["admobAppId"]` (must match App ID)

**`AndroidManifest.xml`** — already wired via:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
```

**`AdsConfig.kt`** — documents placeholders and reads BuildConfig at runtime.

## GitHub Secrets setup

Create these repository Secrets (Settings → Secrets and variables → Actions):

| Secret | Value shape |
|--------|-------------|
| `ADMOB_APP_ID` | `ca-app-pub-XXXX~YYYY` |
| `ADMOB_BANNER_ID` | `ca-app-pub-XXXX/BBBB` |
| `ADMOB_INTERSTITIAL_ID` | `ca-app-pub-XXXX/IIII` |

```bash
gh secret set ADMOB_APP_ID --body "ca-app-pub-XXXX~YYYY" --repo <owner>/<repo>
gh secret set ADMOB_BANNER_ID --body "ca-app-pub-XXXX/BBBB" --repo <owner>/<repo>
gh secret set ADMOB_INTERSTITIAL_ID --body "ca-app-pub-XXXX/IIII" --repo <owner>/<repo>
```

The release workflow passes them as env vars; Gradle picks them up
automatically. Debug builds always use test IDs regardless of Secrets.

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
