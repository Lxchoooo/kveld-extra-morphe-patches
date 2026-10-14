# X (Twitter) Patches Guide

Technical documentation and patch catalog for X (Twitter) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | X |
| **Package Name** | `com.twitter.android` |
| **Target Version** | `12.33.1-prod.01` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `X Telemetry Manifest Purge` | Neutralizes Google AppMeasurement, Firebase Performance, Braze marketing SDK, and strips advertising identifiers. |
| **X Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips tracking/advertising permissions, disables analytics services and receivers, and injects opt-out metadata in AndroidManifest.xml. |
| **Block Update Screen** | `bytecodePatch` | `true` (Enabled) | None | Blocks the "This app is out of date" screen and suppresses in-app update prompts on launch. |
| **Control Video Auto Scroll** | `bytecodePatch` | `false` (Opt-in) | None | Prevents automatic scrolling to the next video in immersive view. |
| **Custom Deep Links** | `resourcePatch` | `false` (Opt-in) | None | Registers intent filters to open custom links directly in the app. |
| **Custom Fonts** | `bytecodePatch` | `false` (Opt-in) | None | Applies custom typography to post and profile text. |
| **DM Debloat** | `bytecodePatch` | `false` (Opt-in) | None | Hides call buttons and composer actions in direct message conversations. |
| **Drawer Items** | `bytecodePatch` | `false` (Opt-in) | None | Hides individual items in the navigation drawer. |
| **X MLKit Vision Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables MLKit component discovery and registrars. |
| **Navigation UI** | `bytecodePatch` | `false` (Opt-in) | None | Bottom navigation, FAB, tabs and bars customization. |
| **No Shortened URL** | `bytecodePatch` | `false` (Opt-in) | None | Expands t.co shortened URLs, removing the click-tracking intermediary. |
| **Pause Search Suggestions** | `bytecodePatch` | `false` (Opt-in) | None | Stops persisting search suggestions locally without breaking search. |
| **Post & Profile UI** | `bytecodePatch` | `false` (Opt-in) | None | Hides Grok analysis and Imagine action buttons, and toggles profile tabs. |
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Removes promoted posts, trends and ads from timeline. |
| **Remove Premium Upsell** | `bytecodePatch` | `true` (Enabled) | None | Removes premium upsell surfaces including Get Verified profile pills. |
| **Round Off Numbers** | `bytecodePatch` | `false` (Opt-in) | None | Displays full numbers without abbreviation. |
| **Share Links** | `bytecodePatch` | `false` (Opt-in) | None | Substitutes domain with custom domain, handles alternate domains, and restores author username in links. |
| **Show Sensitive Media** | `bytecodePatch` | `false` (Opt-in) | None | Displays sensitive media directly without requiring tap to reveal. |
| **Timeline UI** | `bytecodePatch` | `true` (Enabled) | None | Hides banners, recommendation carousels, video entrypoints, and customizes timeline surfaces. |
| **X Background Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables background WorkManager services, home screen widgets, and Cast/NFC companion services to reduce background resource consumption. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Background Optimization Layer (`resourcePatch`)

1. **Telemetry & Permission Purge**:
   - Strips `AD_ID`, Google Play install referrer, and Privacy Sandbox ad attribution permissions.
   - Disables Braze, Google AppMeasurement, and analytics broadcast receivers and services.
   - Injects Firebase and Google Analytics opt-out metadata.

2. **Background Slimmer**:
   - Disables periodic WorkManager jobs and reschedule receivers.
   - Disables news/search app widgets and remote views services.
   - Disables Google Cast and NFC companion services.

3. **MLKit Suppression**:
   - Disables `MlKitComponentDiscoveryService` and removes discovery registrars.

### B. Bytecode Modification Layer (`bytecodePatch`)

1. **Ad & Upsell Removal**:
   - Hooks timeline adapters to filter out promoted tweets and sponsored trend items.
   - Neutralizes premium upsell prompts, verification banners, and subscription badges.

2. **Telemetry Dispatcher Neutralization**:
   - Short-circuits Google AppMeasurement and Firebase Performance recording methods with immediate returns.

3. **UI Customization & Link Sanitization**:
   - Bypasses update requirement dialogs on launch.
   - Intercepts t.co shortened links to restore full original URLs.
   - Rewrites link share actions to target alternative privacy frontends (e.g., fxtwitter.com).

---

## 4. Behavioral Hazards Warning

1. **X MLKit Vision Slimmer**:
   Disabling MLKit component discovery breaks in-app QR and barcode scanning.
2. **DM Debloat**:
   Neutralizing the calling flag disables direct message outgoing call entrypoints.

---

## 5. Known Limitations

None evidenced by patch sources.
