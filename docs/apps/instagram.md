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
| **Download Target** | [Instagram 447.0.0.55.81 (arm64-v8a, 120-480dpi, versionCode 385311891 / BUNDLE3 S, Sept 20)](https://www.apkmirror.com/apk/instagram/instagram-instagram/instagram-447-0-0-55-81-release/instagram-447-0-0-55-81-8-android-apk-download/) |

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

1. **Instagram Telemetry Manifest Purge**:
   - **Objective**: Prevent tracking permissions and analytics component initialization.
   - **How**: Strips advertising identifiers and install referrer permissions, and disables Facebook analytics services and device identifier providers.

2. **Instagram Crash & Diagnostics Manifest Purge**:
   - **Objective**: Disable diagnostic reporters and background crash loggers.
   - **How**: Disables crash upload services, detector broadcast receivers, and carrier preload tracking services.

3. **MLKit Vision Slimmer**:
   - **Objective**: Reduce binary overhead from machine learning vision components.
   - **How**: Disables the component discovery service and removes vision component registrars from the manifest.

### B. Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Stop runtime telemetry data transmission.
   - **How**: Injects immediate void returns into analytics upload methods and privacy-controlled dispatchers.

2. **Block Crash & Diagnostics**:
   - **Objective**: Stop runtime crash and diagnostic dump dispatching.
   - **How**: Suppresses diagnostic hooks and pairs with manifest purging to neutralize reporting pipelines.

### C. Resource and Binary Debloat Axes (`rawResourcePatch`)

1. **AR Effects Slimmer**:
   - **Objective**: Save storage space by removing augmented reality assets and libraries.
   - **How**: Verifies ELF headers and zeroes companion ARCore native libraries in-situ while removing face tracking models.

---

## 4. Behavioral Hazards Warning

1. **MLKit Vision Slimmer**:
   Disabling MLKit component discovery breaks in-app QR and barcode scanning features.
2. **AR Effects Slimmer**:
   Stripping ARCore binaries and facial geometry models disables camera AR filters, 3D face effects, and body/face tracking.

---

## 5. Known Limitations

None evidenced by patch sources.
