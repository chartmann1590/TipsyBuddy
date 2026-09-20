# 🍻 TipsyBuddy — Your Fun & Safe Drinking Companion

> **"Party smart, get home safe."**  
> TipsyBuddy is a modern Android app and companion web service designed for nightlife harm reduction, drink tracking, sobriety estimation, and real-time location sharing with friends.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Hosting](https://img.shields.io/badge/Hosting-Firebase%20Spark-orange.svg)](https://tipsybuddy.web.app)
[![Maps](https://img.shields.io/badge/Maps-OpenStreetMap%20(100%25%20Free)-green.svg)](https://www.openstreetmap.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🌐 Live Web Companion

- **Public Web App**: [https://tipsybuddy.web.app](https://tipsybuddy.web.app)
- **Live Session Tracking Link**: `https://tipsybuddy.web.app/?session=<SESSION_CODE>`
- **Hosting & Database**: Firebase Spark Free Tier with Firestore REST API.

Friends don't need to install any app to track you. When you turn on Live Sharing, TipsyBuddy generates a link that friends can open in any browser to see your live GPS location on an OpenStreetMap, current venue, estimated BAC, battery level, and direct buttons to dispatch an Uber or Lyft to your location.

---

## ✨ Features

### 🍸 1. Tonight Dashboard
- **Live Circular BAC Gauge**: Real-time bio-alcohol estimation powered by the scientific Widmark formula.
- **Sobriety Clock**: Calculates countdown hours and exact estimated time of return to 0.00% BAC.
- **Smart Taxi Suggestion**: Automatically prompts a ride home when approaching "Tipsy" ($0.04\%$) or "Intoxicated" ($0.08\%$) levels.
- **Quick-Add Bar**: Instant 1-tap buttons for Beer, Wine, Cocktails, Shots, and Water.

### 📝 2. Drink Logger & Custom Drink Builder
- Preset categories with standard drink calculations based on ABV and volume (oz).
- Custom drink modal to specify beverage name, category, volume, ABV %, and cost for tab tracking.
- Interactive timeline with timestamps and delete functionality.

### 📅 3. Drinking Calendar & History
- Visual monthly grid color-coded by drinking intensity:
  - 🟢 **Light** ($< 0.04\%$ BAC)
  - 🟡 **Moderate** ($0.04\% - 0.08\%$ BAC)
  - 🔴 **Heavy** ($> 0.08\%$ BAC)
  - 🟣 **Sober Days**
- Monthly recap: Total drinking days, sober streaks, and total monthly spending tab.
- Tap any calendar day to inspect the exact drinks logged and peak BAC.

### 🩺 4. Health & Sobriety Insights
- **Widmark Bio-Alcohol Formula**: Tailored to user body weight and biological sex.
- **Hydration Score**: Tracks water intake vs. alcohol consumption with clinical guidance.
- **Hangover Risk Index**: Next-day risk forecast with evidence-based recovery tips (electrolytes, carbohydrates, sleep).

### 📍 5. Bar Check-In & Live Location Sharing
- **1-Tap Bar Check-In**: Uses device GPS and Android's native `Geocoder` for free reverse geocoding.
- **Embedded OpenStreetMap**: In-app native map view via OSMDroid (`org.osmdroid:osmdroid-android`).
- **Live Sharing Timer**: Share your live location with friends for **30 min, 1 hour, 2 hours, or 4 hours**.
- **Real-Time REST Sync**: Pushes coordinates, battery %, and BAC directly to Firebase Firestore via REST.
- **Android Share Sheet**: 1-tap SMS or WhatsApp sharing of your live web tracker link.

### 🚖 6. Safe Ride Home (Uber & Lyft Integration)
- **Pre-filled Home Address**: Pulls your saved home address from your profile.
- **Deep Links**: Direct dispatch intents (`uber://` and `lyft://`) with web fallback.
- **In-App Fare Estimates**: Calibrated fare estimates for UberX, UberXL, Lyft, and Lyft XL with late-night surge pricing models.
- **Emergency / Designated Driver Quick Dial**: 1-tap phone dialer for a trusted contact.

### 👤 7. Profile & Settings
- Customizable user biometrics (weight in lbs, biological sex).
- Saved home destination address with automatic geocoding.
- Designated friend emergency contact name and phone number.
- Reset session ID and data management tools.

---

## 💰 100% Free Architecture (Zero Paid APIs)

| Component | Free Solution | Cost |
| :--- | :--- | :--- |
| **Companion Web Map** | Leaflet.js + CartoDB Dark Matter / OpenStreetMap | **$0.00** |
| **Android In-App Map** | OSMDroid (`org.osmdroid:osmdroid-android`) | **$0.00** |
| **Geocoding** | Android Native `android.location.Geocoder` | **$0.00** |
| **Live Database & Sync** | Firebase Firestore REST API on Spark Free Tier | **$0.00** |
| **Web Hosting** | Firebase Hosting Free Tier (`tipsybuddy.web.app`) | **$0.00** |
| **Rides & Navigation** | Native Deep Link URI Intents (`uber://` & `lyft://`) | **$0.00** |

---

## 🏗️ Technical Stack

- **Android Client**:
  - Language: Kotlin 1.9.23
  - Framework: Jetpack Compose with Material 3
  - Architecture: Unidirectional Data Flow (UDF), MVVM
  - Database: Room SQLite with Kotlin Coroutines & Flow
  - Networking: OkHttp 4.12.0 (Direct REST to Firestore)
  - Mapping: OSMDroid 6.1.18 (OpenStreetMap)
- **Web Companion**:
  - HTML5, CSS3 (Midnight Dark Theme), Vanilla JavaScript
  - Map Engine: Leaflet.js 1.9.4 with CartoDB Dark Matter tiles
  - API: Firestore v1 REST API (Spark Free Tier)
  - CLI: Firebase CLI 15.x

---

## 🚀 Getting Started

### Android App

1. **Prerequisites**: Android Studio Ladybug / Koala or Android SDK with command-line tools.
2. **Clone Repository**:
   ```bash
   git clone https://github.com/chartmann1590/TipsyBuddy.git
   cd TipsyBuddy
   ```
3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Install on Connected Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Firebase Web Companion

1. **Install Firebase CLI**:
   ```bash
   npm install -g firebase-tools
   ```
2. **Deploy Hosting & Security Rules**:
   ```bash
   firebase deploy --only hosting,firestore:rules
   ```

---

## ⚠️ Disclaimer

BAC calculations and sobriety times provided by TipsyBuddy are mathematical estimates based on the Widmark formula and are intended solely for personal harm reduction and informational purposes. Individual metabolism, food consumption, medications, and other physiological factors affect alcohol absorption and clearance. **Never drink and drive under any circumstances.**
