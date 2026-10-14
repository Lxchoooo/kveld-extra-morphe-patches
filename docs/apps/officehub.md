# Copilot (OfficeHub) Patches Guide

Technical documentation and patch catalog for Microsoft Copilot (OfficeHub) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Copilot |
| **Package Name** | `com.microsoft.office.officehubrow` |
| **Target Version** | `16.0.20527.20022` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Copilot Telemetry Manifest Purge` | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement identifiers (AIFA, AppSetId), rewrites default telemetry endpoints to 0.0.0.0, disables cross-sell, Floodgate, HockeyApp and DataTransport components, and strips advertising permissions. |
| **Copilot Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions, disables DataTransport, cross-sell, and HockeyApp components, and injects opt-out metadata in AndroidManifest.xml. |
| **Bypass Code Transparency** | `bytecodePatch` | `true` (Enabled) | None | Skips the code-transparency failure dialog and reports local verification success, unblocking sideloaded installs. |
| **Disable Login Requirement** | `bytecodePatch` | `true` (Enabled) | None | Removes login requirement and FTUX paywall screens. Cloud-backed features still require sign-in server-side. |
| **Copilot DPI Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips drawables for unselected screen densities from Copilot base APK while preserving launcher icons and single-density assets. Non-phone UI mode qualifiers (watch, television, car, vrheadset) are purged. |
| **Copilot Junk Cleaner** | `rawResourcePatch` | `false` (Opt-in) | None | Purges non-functional build metadata, properties, proto descriptors, and duplicate license notices from APK root and META-INF while strictly protecting runtime assets and signatures. |
| **Copilot Companion Native Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Strips optional companion native binaries (React Native, Hermes, SlimCV) via in-situ zeroing. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Telemetry Sanitization Layer (`resourcePatch` & `bytecodePatch`)

1. **Manifest Stripping**:
   - Strips `AD_ID` and install referrer permissions.
   - Disables Microsoft cross-sell broadcast receivers, HockeyApp activities, and Google DataTransport background services.
   - Injects Firebase Analytics and Crashlytics opt-out metadata.

2. **Telemetry Dispatcher Neutralization**:
   - Hooks Microsoft OneDS/Aria event dispatchers to return immediately.
   - Spoofs advertising identifiers (`AIFA`, `AppSetId`) to empty or zeroed strings.
   - Rewrites telemetry endpoint URLs to `0.0.0.0`.

### B. Sideload & Login Bypass Layer (`bytecodePatch`)

1. **Bypass Code Transparency**:
   - Intercepts code transparency verification checks and records verification success in shared preferences to allow execution on sideloaded installs.

2. **Disable Login Requirement**:
   - Completes first-run experience task callbacks without requiring Microsoft account credentials.
   - Short-circuits SSO checks to allow unauthenticated access to local surfaces.

### C. Resource and Native Slimming Axes

1. **Copilot DPI Slimmer (`resourcePatch`)**:
   - Strips drawables matching unselected screen densities.
   - Purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`).
   - Protects application launcher icons and single-density orphan drawables.

2. **Copilot Junk Cleaner (`rawResourcePatch`)**:
   - Deletes build metadata, properties, compiler leftovers, and duplicate license declarations in APK root and `META-INF`.

3. **Copilot Companion Native Slimmer (`rawResourcePatch`)**:
   - Zeroes companion `.so` binaries (`libhermes.so`, `libreactnative.so`, `libSlimCV.so`, `libhermestooling.so`, `libfbjni.so`) across arm64-v8a and armeabi-v7a.
   - Never touches HockeyApp or voice SDK binaries, which are load-bearing on the boot path.

---

## 4. Behavioral Hazards Warning

1. **Copilot Companion Native Slimmer**:
   Hermes and React Native are load-bearing for React Native initialization. Enabling this will crash Copilot React Native surfaces (Copilot chat host) with `UnsatisfiedLinkError`.
2. **Copilot DPI Slimmer**:
   Displays matching stripped densities will scale preserved assets.

---

## 5. Known Limitations

1. **Cloud-Backed Features**:
   Cloud-backed features still require sign-in server-side.
