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
| **Primary Architecture** | `universal` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [Moovit 5.201.1.1809 (universal, 160-640dpi, versionCode 1809 / BUNDLE 31 S, Sept 30)](https://www.apkmirror.com/apk/moovit/moovit-bus-train-live-info/moovit-your-transit-tracker-5-201-1-1809-release/moovit-your-transit-tracker-5-201-1-1809-2-android-apk-download/) |

Note: `versionCode 1809` corresponds to the actual build versionCode assigned on APKMirror, not a trailing versionName fragment.

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Moovit Telemetry Manifest Purge` | Neutralizes AppsFlyer, Braze, and Inneractive DEX dispatchers, disables analytics services/providers, and strips advertising permissions. |
| **Moovit Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions, disables analytics services, providers, and receivers, and injects opt-out metadata in AndroidManifest.xml. |
| **Fix Google Maps** | `bytecodePatch` | `true` (Enabled) | `Moovit Custom Maps API Key` | Restores Google Maps rendering by spoofing the original package signature to Google Play Services. |
| **Moovit Custom Maps API Key** | `resourcePatch` | `false` (Opt-in) | None | Replaces the Google Maps API key in AndroidManifest.xml and web-service strings when a custom key is provided. |
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

---

## 4. Behavioral Hazards Warning

None related to resource trimming.

---

## 5. Known Limitations

None evidenced by patch sources.

---

## 6. Canonical Universal Patches

The generic maintenance patches removed from this guide (screen densities, languages) are covered by the universal patches in the canonical collection [kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches): `Remove Unused Screen Sizes` and `Remove Extra Languages`. For further generic maintenance (packaging leftovers, background sync, native trackers, telemetry) that collection also provides `Clean App Packaging`, `Stop Background Sync`, `Remove Tracking Libraries` and `Block Trackers`. Add that source in Morphe Manager alongside this one to use them; universal patches apply to any APK, including Moovit.
