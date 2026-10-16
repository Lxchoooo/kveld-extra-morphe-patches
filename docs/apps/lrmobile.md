# Lightroom Patches Guide

Technical documentation and patch catalog for Adobe Lightroom on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Lightroom |
| **Package Name** | `com.adobe.lrmobile` |
| **Target Version** | `11.6.01` |
| **Target Package Format** | standalone APK |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | Uptodown (PairIP-free) |
| **Download Target** | [Lightroom 11.6.01 (arm64-v8a, nodpi, standalone APK)](https://adobe-lightroom-mobile.uptodown.com/android) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Lightroom Telemetry Manifest Purge` | Neutralizes Google AppMeasurement, Firebase Crashlytics, Branch attribution, and Adobe Creative SDK analytics dispatchers, redirects Adobe Edge and DTM endpoints to 0.0.0.0, strips advertising permissions, and injects analytics opt-out flags. |
| **Lightroom Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions and injects opt-out metadata in AndroidManifest.xml. Analytics components are preserved to allow application boot. |
| **Sideload Install Support** | `bytecodePatch` | `true` (Enabled) | None | Ensures compatibility for sideloaded lab installs by allowing local installer source checks to report success and completing Play Integrity token requests. |
| **Unlock Premium Features** | `bytecodePatch` | `true` (Enabled) | `Sideload Install Support` | Enables app features locked behind subscription paywalls by activating the internal Limited-Time Premium Unlock (LTPU) gate. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Licensing Layer (`resourcePatch` & `bytecodePatch`)

1. **Lightroom Telemetry Manifest Purge**:
   - **Objective**: Block telemetry collection and measurement permissions.
   - **How**: Strips advertising identifiers and install referrer permissions, injects opt-out metadata, and preserves component declarations to avoid boot failures.

2. **Block Telemetry & Trackers**:
   - **Objective**: Prevent runtime event logging and analytics dispatching.
   - **How**: Gates class presence via `classDefByOrNull` and hooks Google AppMeasurement, Firebase Crashlytics, Branch attribution (`InitSessionBuilder`, `ServerRequestQueue`), and Adobe Creative SDK analytics (`AdobeInternalAnalytics`, `CSDKAnalyticsQueue`) dispatch methods to return immediately. Redirects Adobe Experience Platform Edge collection hostnames (`edge.adobedc.net`, `edge-int.adobedc.net`) in `EdgeEndpoint` and Adobe Dynamic Tag Management configuration fetch URLs (`assets.adobedtm.com`) in `lv.i` to `0.0.0.0`. First-party Adobe analytics view helpers are transitively neutralized at the dispatcher perimeter; functional cloud/identity endpoints (`photos.adobe.io`, `api.lightroom.adobe.com`, `ims-na1.adobelogin.com`, `device.griffon.adobe.com`, `mh.adobe.io`, `adobeid-na1.services.adobe.com`, `cc-api-cp.adobe.io`, `cc-api-behance.adobe.io`) and native libraries are preserved for application boot and sync stability.

3. **Sideload Install Support**:
   - **Objective**: Allow sideloaded packages to launch without redirection or license failures.
   - **How**: Intercepts installer source checks to report success, fulfills Play Integrity token requests with empty responses, and neutralizes PairIP signature verification.

4. **Unlock Premium Features**:
   - **Objective**: Enable subscription-only editing tools locally.
   - **How**: Forces the internal limited-time premium unlock gate check to return true.

---

## 4. Behavioral Hazards Warning

1. **Block Telemetry & Trackers**:
   Manifest components, native growth libraries, and functional cloud/identity endpoints are intentionally preserved to prevent boot crashes and maintain sync/auth operation.

---

## 5. Known Limitations

1. **Sideload Install Support Requirement**:
   Without Sideload Install Support, sideloaded lab installs route directly to the Play Store.

---

## 6. Canonical Universal Patches

The generic maintenance patches removed from this guide (background sync, packaging leftovers) are covered by the universal patches in the canonical collection [kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches): `Stop Background Sync` and `Clean App Packaging`. For further generic maintenance (languages, screen densities, native trackers, telemetry) that collection also provides `Remove Extra Languages`, `Remove Unused Screen Sizes`, `Remove Tracking Libraries` and `Block Trackers`. Add that source in Morphe Manager alongside this one to use them; universal patches apply to any APK, including Lightroom. Note that `Stop Background Sync` carries the same warning as the removed patch: disabling WorkManager can affect background photo uploads, syncing, and exports.
