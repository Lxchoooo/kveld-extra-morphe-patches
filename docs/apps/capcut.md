# CapCut Patches Guide

Technical documentation and patch catalog for CapCut on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | CapCut |
| **Package Name** | `com.lemon.lvoverseas` |
| **Target Version** | `19.7.0` |
| **Target Package Format** | standalone APK |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [CapCut 19.7.0 (arm64-v8a, nodpi, versionCode 19701600, Oct 8)](https://www.apkmirror.com/apk/bytedance-pte-ltd/capcut/capcut-video-editor-19-7-0-release/capcut-video-editor-19-7-0-android-apk-download/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `CapCut Telemetry Manifest Purge` | Neutralizes Google AppMeasurement event dispatchers and strips advertising identifiers. |
| **CapCut Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips 6 advertising/tracking permissions, disables measurement/analytics/ad components, prunes analytics discovery registrars (MLKit/messaging/crashlytics preserved), injects 8 opt-out metadata flags. |
| **Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected res/values-* tables and assets/locales/*.json bundles via locales option (default en). Base fallback and English always preserved. |
| **Screen Density Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected drawable/mipmap density assets and purges watch/television/car/vrheadset UI modes via density option (default xxhdpi). Launcher icons, nodpi/anydpi and single-density orphans preserved. |
| **Bypass Effects Region Restriction** | `bytecodePatch` | `true` (Enabled) | None | Spoofs device_platform to "windows" and resets deviceId in EffectConfiguration to bypass ByteDance Shark WAF effect loading blocks. |
| **Bypass GeoBlock Region Restriction** | `bytecodePatch` | `true` (Enabled) | None | Forces CapCut GeoBlock config lists (cert, dock, entrance, page, scheme) to empty so region-blocked surfaces stay enabled. Server-refused content may still fail to load. |
| **Bypass Signature Check** | `bytecodePatch` | `true` (Enabled) | None | Spoofs official APK SHA-256 signature, suppresses signature blacklist and abnormal-source toasts, and disables Godzilla inject detection. |
| **Bypass Unsafe Package Modal** | `bytecodePatch` | `true` (Enabled) | None | Rewrites Lynx schema default fallback URL from unsafe-package modal to template portal to bypass security notice dialogs. |
| **Unlock Premium** | `bytecodePatch` | `true` (Enabled) | None | Forces VIP and subscription gates to return true (SubscribeImpl, PayVipImpl, UserVipInfo, UserDetailInfo, VipUserServiceImpl, SubscribeCloudImpl). |
| **Native Bloat Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Zeroes AppLovin crash-reporter .so in-situ across arm64-v8a/armeabi-v7a; optional trimSpeechEngines toggle zeroes speech .so libs (breaks voice features). |

---

## 3. Deep Technical Breakdown

### A. Manifest Purge Layer (`resourcePatch`)

1. **CapCut Telemetry Manifest Purge**:
   - **Objective**: Prevent tracker initialization and background telemetry collection.
   - **How**: Strips advertising and attribution permissions from the manifest, disables tracking and dynamic ad components, prunes discovery registrars, and injects opt-out flags.

### B. Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Stop runtime telemetry event dispatching.
   - **How**: Intercepts event logging entrypoints and injects immediate void returns.

2. **Bypass Effects Region Restriction**:
   - **Objective**: Restore loading of effects, transitions, and templates blocked by region firewalls.
   - **How**: Spoofs the device platform parameter to "windows" and resets the device identifier to zero in effect requests.

3. **Bypass GeoBlock Region Restriction**:
   - **Objective**: Keep region-restricted application entrypoints and pages accessible.
   - **How**: Forces internal geographic restriction configuration lists to return empty lists.

4. **Bypass Signature Check**:
   - **Objective**: Allow re-signed application packages to run without integrity errors or tamper alerts.
   - **How**: Returns the official APK signature hash during telemetry checks, clears the signature blacklist, silences tamper toasts, and disables injection detection gates.

5. **Bypass Unsafe Package Modal**:
   - **Objective**: Prevent blocking security warning dialogs on startup.
   - **How**: Rewrites Lynx schema default fallback addresses to point directly to the template portal instead of the security notice dialog.

6. **Unlock Premium**:
   - **Objective**: Enable VIP editing capabilities and premium user interface elements locally.
   - **How**: Forces subscription status and VIP account entitlement checks to return true.

### C. Resource and Binary Debloat Axes

1. **Locale Slimmer (`resourcePatch`)**:
   - **Objective**: Reduce package size by removing unneeded languages.
   - **How**: Deletes unselected values directories and JSON language bundles while preserving base and English fallbacks.

2. **Screen Density Slimmer (`resourcePatch`)**:
   - **Objective**: Reduce package size by removing unneeded display densities.
   - **How**: Purges non-phone layout modes and strips non-target drawable folders while retaining launcher icons and single-density assets.

3. **Native Bloat Slimmer (`rawResourcePatch`)**:
   - **Objective**: Reduce disk space by stripping unused native libraries.
   - **How**: Empties target crash reporter and optional speech recognition binaries directly within the native library directories.

---

## 4. Behavioral Hazards Warning

1. **Opt-in Status**:
   Telemetry and resource slimmer patches are opt-in and disabled by default.
2. **Screen Density Slimmer**:
   Stripping density resources may degrade visual assets or cause layout clipping on devices whose display densities differ from the preserved density.
3. **Locale Slimmer**:
   Application strings for stripped languages will fall back to base or English strings.
4. **Native Bloat Slimmer**:
   Stripping speech recognition and synthesis engines disables voice effects, text-to-speech, and automatic captions.

---

## 5. Known Limitations

1. **Templates Catalog**:
   The templates feed returns an empty list on re-signed builds due to remote signature verification.
2. **Security Modal Re-prompting**:
   The unsafe package modal dialog is bypassed on initial startup, but may reappear when accessing screens where remote verdicts are evaluated.
3. **Cloud VIP Assets**:
   Remote cloud-hosted VIP assets and server-validated processing remain locked.
