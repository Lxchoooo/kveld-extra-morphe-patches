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
| **Download Target** | [Moovit 5.201.1.1809 (arm64-v8a, 160-640dpi, versionCode 1809 / BUNDLE 31 S, Sept 30)](https://www.apkmirror.com/apk/moovit/moovit-your-transit-tracker/moovit-your-transit-tracker-5-201-1-1809-release/moovit-your-transit-tracker-5-201-1-1809-160-640dpi-android-7-0-android-apk-download/) |

Note: `versionCode 1809` corresponds to the actual build versionCode assigned on APKMirror, not a trailing versionName fragment.

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

1. **Moovit Telemetry Manifest Purge**:
   - **Objective**: Prevent tracker initialization and background analytics collection.
   - **How**: Strips advertising identifiers and install referrer permissions, disables AppsFlyer and DataTransport services, and injects opt-out metadata flags.

2. **Moovit Custom Maps API Key**:
   - **Objective**: Allow using a custom Google Maps Platform key.
   - **How**: Replaces the Google Maps key in the manifest and application strings when provided.

### B. Bytecode Dispatcher & Premium Unlocking Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Neutralize runtime telemetry and tracking calls.
   - **How**: Short-circuits AppsFlyer, Braze, and Inneractive dispatchers and strips tracking permissions.

2. **Fix Google Maps**:
   - **Objective**: Prevent map tile rendering failures on re-signed builds.
   - **How**: Spoofs original package signatures communicated to Google Play Services.

3. **Remove Ads**:
   - **Objective**: Remove banner and inline advertisements.
   - **How**: Hides advertisement views and returns empty strings for ad unit lookups.

4. **Suppress Paywalls**:
   - **Objective**: Prevent subscription dialogs and upgrade popups from interrupting navigation.
   - **How**: Bypasses onboarding upgrade flows, dismisses purchase dialogs, and hides premium promotional cards.

5. **Unlock Moovit+**:
   - **Objective**: Enable Moovit+ premium features locally.
   - **How**: Forces subscription status checks to return true and activates Safe Ride and favorite address search flags.

### C. Resource Trimming Axes (`resourcePatch`)

1. **Locale Slimmer**:
   - **Objective**: Reduce package size by discarding unused language resources.
   - **How**: Strips unselected locale value tables from the base APK while retaining English and base fallback strings.

2. **Screen Density Slimmer**:
   - **Objective**: Reduce package size by removing unneeded display densities.
   - **How**: Purges non-phone UI qualifiers and strips unselected density assets while retaining launcher icons and single-density assets.

---

## 4. Behavioral Hazards Warning

1. **Screen Density Slimmer**:
   Stripping density resources may affect asset scaling on devices with display densities differing from the preserved selection.
2. **Locale Slimmer**:
   Stripped application languages fall back to base or English translations.

---

## 5. Known Limitations

None evidenced by patch sources.
