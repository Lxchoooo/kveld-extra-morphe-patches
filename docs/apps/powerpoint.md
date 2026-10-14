# PowerPoint Patches Guide

Technical documentation and patch catalog for Microsoft PowerPoint on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | PowerPoint |
| **Package Name** | `com.microsoft.office.powerpoint` |
| **Target Version** | `16.0.20527.20034` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [PowerPoint 16.0.20527.20034 (arm64-v8a, 120-480dpi, versionCode 2005437911 / BUNDLE 5 S, Oct 8)](https://www.apkmirror.com/apk/microsoft-corporation/microsoft-powerpoint/microsoft-powerpoint-16-0-20527-20034-release/microsoft-powerpoint-16-0-20527-20034-arm64-v8a-120-480dpi-android-apk-download/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `PowerPoint Telemetry Manifest Purge` | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement identifiers (AIFA, AppSetId), disables HockeyApp activities and DataTransport components, and strips advertising permissions. |
| **PowerPoint Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips tracking/advertising permissions, disables telemetry, HockeyApp activity and DataTransport components, and injects opt-out metadata in AndroidManifest.xml. |
| **Bypass Code Transparency** | `bytecodePatch` | `true` (Enabled) | None | Bypasses the code transparency checks. |
| **Disable Login Requirement** | `bytecodePatch` | `true` (Enabled) | None | Removes login requirement and FTUX paywall screens. |
| **PowerPoint Companion Native Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Strips optional companion native binaries (React Native and Hermes JavaScript runtime stack) via in-situ zeroing. |
| **PowerPoint DPI Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips drawables for unselected screen densities from PowerPoint base APK while preserving launcher icons and single-density assets. |
| **PowerPoint Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected localized resource directories (res/values-<locale>/) from PowerPoint base APK. English (en, en-us) is always retained. |
| **Remove Shared User ID** | `resourcePatch` | `true` (Enabled) | None | Removes the sharedUserId attribute from the manifest to prevent installation conflicts. |
| **Unlock 365 Family** | `bytecodePatch` | `true` (Enabled) | None | Unlocks Microsoft 365 Family subscription features locally. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Licensing Layer (`resourcePatch`)

1. **Remove Shared User ID**:
   - **Objective**: Prevent installation conflicts on devices with other Microsoft apps installed.
   - **How**: Strips shared user identifier attributes from the root manifest element.

2. **PowerPoint Telemetry Manifest Purge**:
   - **Objective**: Stop tracker initialization and background telemetry services.
   - **How**: Strips advertising identifiers and install referrer permissions, disables telemetry and HockeyApp activities, and injects opt-out flags.

### B. Bytecode Bypass & Feature Unlocking Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Neutralize runtime telemetry and failure logging dispatchers.
   - **How**: Hooks Microsoft OneDS entrypoints to return immediately and nullifies advertising identifiers.

2. **Bypass Code Transparency**:
   - **Objective**: Allow sideloaded packages to launch without code transparency validation errors.
   - **How**: Short-circuits verification checks to report immediate success to the caller.

3. **Disable Login Requirement**:
   - **Objective**: Allow creating and editing presentations without compulsory Microsoft account sign-in.
   - **How**: Bypasses first-run experience task callbacks and short-circuits single sign-on checks.

4. **Unlock 365 Family**:
   - **Objective**: Unlock premium subscription features and editing capabilities locally.
   - **How**: Forces subscription status checks to return true and bypasses enterprise license validation.

### C. Resource & Binary Trimming Axes

1. **PowerPoint Locale Slimmer (`resourcePatch`)**:
   - **Objective**: Reduce package size by discarding unselected languages.
   - **How**: Removes unselected locale directories while retaining English and base fallback strings.

2. **PowerPoint DPI Slimmer (`resourcePatch`)**:
   - **Objective**: Reduce package size by removing unneeded display density drawables.
   - **How**: Strips unselected screen density directories while preserving application icons and single-density assets.

3. **PowerPoint Companion Native Slimmer (`rawResourcePatch`)**:
   - **Objective**: Reduce application disk footprint by discarding companion native libraries.
   - **How**: Empties React Native and Hermes runtime libraries in-situ across target architectures.

---

## 4. Behavioral Hazards Warning

1. **PowerPoint Companion Native Slimmer**:
   Stripped libraries are load-bearing for React Native initialization. Enabling this will crash the app with `UnsatisfiedLinkError` when Copilot or other React Native surfaces start.
2. **PowerPoint Locale Slimmer**:
   Application strings for stripped locales will fall back to English.
3. **PowerPoint DPI Slimmer**:
   Displays matching stripped densities will scale preserved assets.

---

## 5. Known Limitations

None evidenced by patch sources.
