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

The resource patch executes declarative transformations on `AndroidManifest.xml`:

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID`
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
   - `android.permission.ACCESS_ADSERVICES_AD_ID`
   - `android.permission.ACCESS_ADSERVICES_TOPICS`
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`
   - `com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE`

2. **Component Disabling**:
   Disables analytics, tracking, measurement, and advertising providers, services, and receivers. Dynamic ad activities matching AppLovin, MaxDebugger, and Vungle are also disabled.

3. **Discovery Registrar Pruning**:
   Removes entries matching analytics, measurement, crashlytics, perf, remoteconfig, sessions, or abt from `ComponentDiscoveryService`. MLKit, push messaging, and Crashlytics runtime registrars are preserved.

4. **Opt-Out Metadata Injection**:
   Injects declarative configuration tags under `<application>` disabling Firebase and Google Analytics collection.

### B. Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes Google AppMeasurement event submission:

1. **Targeted Entrypoint Hooks**:
   - `Lcom/google/android/gms/measurement/AppMeasurement;->logEventInternal`: Injects `return-void` at instruction index 0.

### C. Resource and Binary Debloat Axes

1. **Locale Slimmer (`resourcePatch`)**:
   - Recursively removes unselected `res/values-*` directories matching unselected language qualifiers.
   - Deletes unselected JSON locale bundles in `assets/locales/*.json`.
   - Preserves base `res/values`, non-language qualifiers, and English fallback assets.

2. **Screen Density Slimmer (`resourcePatch`)**:
   - Purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`).
   - Strips unselected densities across `drawable-*` and `mipmap-*` families.
   - Preserves `nodpi`/`anydpi`, launcher application icons, and single-density orphan assets.

3. **Native Bloat Slimmer (`rawResourcePatch`)**:
   - Zeroes target companion `.so` files in-situ across `lib/<abi>/` architectures (`arm64-v8a`, `armeabi-v7a`).
   - Always zeroes `libapplovin-native-crash-reporter.so`.
   - Optional `trimSpeechEngines` toggle zeroes speech synthesis and recognition binaries (`libspeechengine.so`, `libspeechepg.so`, `libspeechsdk.so`).

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
