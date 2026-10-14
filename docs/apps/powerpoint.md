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

1. **Manifest Sanitization & Permissions**:
   - Removes `android:sharedUserId` and `android:sharedUserLabel` from `<manifest>` root to prevent signature conflict installation errors.
   - Strips advertising and attribution permissions (`AD_ID`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `ACCESS_ADSERVICES_*`).
   - Disables HockeyApp feedback activities and Google DataTransport background services.
   - Injects Firebase and Google Analytics opt-out metadata.

### B. Bytecode Bypass & Feature Unlocking Layer (`bytecodePatch`)

1. **Unlock 365 Family**:
   - Forces consumer premium licensing states in session checks.
   - Sets family, personal, and premium plan flags to true.
   - Synthesizes mock entitlement collections and bypasses enterprise OLS checks.
   - Nullifies premium upsell triggers and storage quota limiters.

2. **Login & Code Transparency Bypasses**:
   - Short-circuits code transparency verification callbacks to report success immediately.
   - Bypasses first-run onboarding task gates to proceed without obligatory sign-in.

3. **Telemetry Neutralization**:
   - Hooks Microsoft OneDS/Aria event submission entrypoints to return immediately without dispatching logs.

### C. Resource & Binary Trimming Axes

1. **Locale & DPI Slimmers (`resourcePatch`)**:
   - Removes unselected locale directories under `res/values-*` (retaining English and base values).
   - Strips unselected density assets while retaining application launcher icons and single-density assets.

2. **Companion Native Slimmer (`rawResourcePatch`)**:
   - In-situ zeroing of React Native and Hermes companion `.so` binaries.

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
