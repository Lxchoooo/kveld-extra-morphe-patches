# Copilot (OfficeHub) Patches Guide

Technical documentation and patch catalog for Microsoft Copilot (OfficeHub) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Copilot |
| **Package Name** | `com.microsoft.office.officehubrow` |
| **Target Version** | `16.0.20604.20012` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [Copilot 16.0.20604.20012 (arm64-v8a, 120-480dpi, versionCode 45489613 / BUNDLE 32 S, Oct 15)](https://www.apkmirror.com/apk/microsoft-corporation/office-mobile-2/microsoft-copilot-16-0-20604-20012-release/microsoft-copilot-16-0-20604-20012-android-apk-download/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Copilot Telemetry Manifest Purge` | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, stubs Singular attribution SDK initializers and dispatchers, nullifies ad measurement identifiers (AIFA, AppSetId), sinkholes AppCenter, events, and default telemetry endpoints to 0.0.0.0, disables cross-sell, Floodgate, HockeyApp and DataTransport components, and strips advertising and preinstall attribution permissions. |
| **Copilot Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions, disables DataTransport, cross-sell, and HockeyApp components, and injects opt-out metadata in AndroidManifest.xml. |
| **Bypass Code Transparency** | `bytecodePatch` | `true` (Enabled) | None | Skips the code-transparency failure dialog and reports local verification success, unblocking sideloaded installs. |
| **Disable Login Requirement** | `bytecodePatch` | `true` (Enabled) | None | Removes login requirement and FTUX paywall screens. Cloud-backed features still require sign-in server-side. |
| **Copilot Companion Native Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Strips optional companion native binaries (React Native, Hermes, SlimCV) via in-situ zeroing. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Telemetry Sanitization Layer (`resourcePatch` & `bytecodePatch`)

1. **Copilot Telemetry Manifest Purge**:
   - **Objective**: Prevent tracker initialization and background data transport.
   - **How**: Strips advertising identifiers and install referrer permissions, disables cross-sell, HockeyApp, and DataTransport components, and injects analytics opt-out flags.

2. **Block Telemetry & Trackers**:
   - **Objective**: Neutralize runtime telemetry and metric dispatching.
   - **How**: Hooks Microsoft OneDS event dispatchers to return immediately, nullifies advertising identifiers, and redirects telemetry endpoints to 0.0.0.0.

### B. Sideload & Login Bypass Layer (`bytecodePatch`)

1. **Bypass Code Transparency**:
   - **Objective**: Enable sideloaded installations without code transparency validation failures.
   - **How**: Intercepts verification checks and notifies the application that transparency checks passed successfully.

2. **Disable Login Requirement**:
   - **Objective**: Allow using local application functions without compulsory Microsoft account sign-in.
   - **How**: Bypasses initial first-run onboarding task gates and short-circuits single sign-on checks.

### C. Resource and Native Slimming Axes

1. **Copilot Companion Native Slimmer (`rawResourcePatch`)**:
   - **Objective**: Reduce package footprint by removing optional companion native libraries.
   - **How**: Empties companion native binaries in-situ across target architectures while preserving boot-critical voice and exception handler binaries.

---

## 4. Behavioral Hazards Warning

1. **Copilot Companion Native Slimmer**:
   Hermes and React Native are load-bearing for React Native initialization. Enabling this will crash Copilot React Native surfaces (Copilot chat host) with `UnsatisfiedLinkError`.

---

## 5. Known Limitations

1. **Cloud-Backed Features**:
   Cloud-backed features still require sign-in server-side.

---

## 6. Canonical Universal Patches

The generic maintenance patches removed from this guide (screen densities, packaging leftovers) are covered by the universal patches in the canonical collection [kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches): `Remove Unused Screen Sizes` and `Clean App Packaging`. For further generic maintenance (languages, background sync, native trackers, telemetry) that collection also provides `Remove Extra Languages`, `Stop Background Sync`, `Remove Tracking Libraries` and `Block Trackers`. Add that source in Morphe Manager alongside this one to use them; universal patches apply to any APK, including Copilot.
