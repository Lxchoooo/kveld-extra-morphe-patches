# Moovit Patches Guide

Technical documentation and patch catalog for Moovit on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Moovit |
| **Package Name** | `com.tranzmate` |
| **Target Version** | `5.201.1.1809` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Moovit Telemetry Manifest Purge` | Neutralizes AppsFlyer, Braze, and Inneractive DEX dispatchers, disables analytics services/providers, and strips advertising permissions. |
| **Moovit Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions, disables analytics services, providers, and receivers, and injects opt-out metadata in AndroidManifest.xml. |
| **Screen Density Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved. |
| **Fix Google Maps** | `bytecodePatch` | `true` (Enabled) | `Moovit Custom Maps API Key` | Restores Google Maps rendering by spoofing the original package signature to Google Play Services. |
| **Moovit Custom Maps API Key** | `resourcePatch` | `false` (Opt-in) | None | Replaces the Google Maps API key in AndroidManifest.xml and web-service strings when a custom key is provided. |
| **Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected language string tables and resources from base APK. Base fallback and English are always preserved. |
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Hides banner and inline ads and neutralizes ad unit ID lookups. |
| **Suppress Paywalls** | `bytecodePatch` | `true` (Enabled) | None | Suppresses subscription paywalls, onboarding upgrade dialogs, and promotional cards. |
| **Unlock Moovit+** | `bytecodePatch` | `true` (Enabled) | None | Unlocks Moovit+ premium subscription features locally, including Safe Ride and address search in favorites. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Telemetry Purge Layer (`resourcePatch`)

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID`
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
   - `android.permission.ACCESS_ADSERVICES_AD_ID`
   - `android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE`
   - `android.permission.ACCESS_ADSERVICES_TOPICS`
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`

2. **Component Disabling & Opt-Out Flags**:
   - Disables AppsFlyer install receivers and tracking services.
   - Disables Google DataTransport background scheduling services.
   - Injects Firebase Analytics and Crashlytics opt-out metadata flags into `<application>`.

### B. Bytecode Dispatcher & Premium Unlocking Layer (`bytecodePatch`)

1. **Unlock Moovit+**:
   - Forces subscription check methods to return true.
   - Sets package and Safe Ride state evaluations to active.
   - Unlocks address search within favorite location editors.

2. **Paywall Suppression**:
   - Suppresses Moovit+ onboarding flows and dismisses package purchase dialogs automatically.
   - Collapses promotional cards and upgrade banners across navigation and account views.

3. **Maps Restoration**:
   - Intercepts package signature checks communicated to Google Play Services, preserving map tile rendering on re-signed builds.

### C. Resource Trimming Axes (`resourcePatch`)

1. **Locale Slimmer**:
   - Strips unselected `res/values-*` locale tables while protecting base strings and English.

2. **Screen Density Slimmer**:
   - Purges non-phone qualifiers (`watch`, `television`, `car`, `vrheadset`) and strips non-target densities while retaining launcher icons and single-density assets.

---

## 4. Behavioral Hazards Warning

1. **Screen Density Slimmer**:
   Stripping density resources may affect asset scaling on devices with display densities differing from the preserved selection.
2. **Locale Slimmer**:
   Stripped application languages fall back to base or English translations.

---

## 5. Known Limitations

None evidenced by patch sources.
