# Instagram Patches Guide

Technical documentation and patch catalog for Instagram on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Instagram |
| **Package Name** | `com.instagram.android` |
| **Target Version** | `447.0.0.55.81` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Instagram Telemetry Manifest Purge` | Neutralizes Facebook Analytics2, DataTransport, FDID/PhoneId providers, and strips AD_ID permissions. |
| **Instagram Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips tracking and advertising permissions, disables analytics services, content providers, and receivers in AndroidManifest.xml. |
| **Block Crash & Diagnostics** | `bytecodePatch` | `false` (Opt-in) | `Instagram Crash & Diagnostics Manifest Purge` | Neutralizes crash-dump uploaders, Lacrima detector broadcasts, debug diagnostics, carrier preload trackers, and lead-ads entrypoints. |
| **Instagram Crash & Diagnostics Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Disables crash-dump upload services, Lacrima broadcast receivers, debug diagnostics, carrier preload trackers, and lead-ads activities in AndroidManifest.xml. |
| **MLKit Vision Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables MLKit component discovery and removes discovery registrars in AndroidManifest.xml. |
| **AR Effects Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Strips ARCore native binaries (libarcore_sdk_c.so, libarcore_sdk_jni.so) and AR effect/face tracking asset models. |

---

## 3. Deep Technical Breakdown

### A. Manifest Purge & Diagnostics Layer (`resourcePatch`)

Declarative transformations applied to `AndroidManifest.xml`:

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID`
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
   - `android.permission.ACCESS_ADSERVICES_AD_ID`
   - `android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE`
   - `android.permission.ACCESS_ADSERVICES_TOPICS`
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`

2. **Component Disabling**:
   - Analytics and install referrer providers (`AsyncInstagramFDIDLiteProvider`, `AsyncInstagramPhoneIdProvider`, `InstallReferrerProvider`).
   - Analytics upload and scheduler services across Facebook Analytics2, DataTransport, and Google Play services.
   - Crash and diagnostic reporting: Lacrima detector broadcast receivers, Dumper upload services, Flipper diagnostic activities, and Oxygen carrier preload services.

3. **MLKit Discovery Suppression**:
   - Disables `MlKitComponentDiscoveryService` and strips Firebase component registrars.

### B. Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

Dalvik bytecode entrypoint hooks neutralize telemetry upload dispatchers:
- Injects early `return-void` instructions into task-based and privacy-controlled upload dispatchers.

### C. Resource and Binary Debloat Axes (`rawResourcePatch`)

1. **AR Effects Slimmer**:
   - Verifies ELF headers, size, and SHA-256 fingerprints before zeroing companion native libraries (`libarcore_sdk_c.so`, `libarcore_sdk_jni.so`).
   - Removes facial tracking and segmentation asset models under `assets/app_modules/contents/` and `assets/ReferenceFaceShapeConstants/`.

---

## 4. Behavioral Hazards Warning

1. **MLKit Vision Slimmer**:
   Disabling MLKit component discovery breaks in-app QR and barcode scanning features.
2. **AR Effects Slimmer**:
   Stripping ARCore binaries and facial geometry models disables camera AR filters, 3D face effects, and body/face tracking.

---

## 5. Known Limitations

None evidenced by patch sources.
