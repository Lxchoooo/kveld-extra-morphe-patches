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

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Lightroom Telemetry Manifest Purge` | Neutralizes Google AppMeasurement and Firebase Crashlytics dispatchers, strips advertising permissions, and injects analytics opt-out flags. |
| **Lightroom Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions and injects opt-out metadata in AndroidManifest.xml. Analytics components are preserved to allow application boot. |
| **Lightroom Background Sync** | `resourcePatch` | `false` (Opt-in) | None | Disables WorkManager background sync services, scheduled jobs, and boot receiver. The WorkManager startup initializer is preserved to allow application boot. |
| **Lightroom Junk Cleaner** | `rawResourcePatch` | `false` (Opt-in) | None | Purges non-functional build metadata, compiler properties, and duplicate license files from the APK root and META-INF. |
| **Sideload Install Support** | `bytecodePatch` | `true` (Enabled) | None | Ensures compatibility for sideloaded lab installs by allowing local installer source checks to report success and completing Play Integrity token requests. |
| **Unlock Premium Features** | `bytecodePatch` | `true` (Enabled) | `Sideload Install Support` | Enables app features locked behind subscription paywalls by activating the internal Limited-Time Premium Unlock (LTPU) gate. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Licensing Layer (`resourcePatch` & `bytecodePatch`)

1. **Manifest Sanitization**:
   - Strips advertising and attribution permissions (`AD_ID`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `ACCESS_ADSERVICES_*`).
   - Injects Firebase Analytics and Crashlytics opt-out metadata.
   - Preserves analytics components and WorkManager initializers to prevent boot failures.

2. **Sideload Support & Integrity Bypasses**:
   - Forces local installer source checks and check status flags to report success.
   - Completes Play Integrity token requests immediately to allow operation without Google Play Services.
   - Neutralizes PairIP signature verification and dismisses license activity if present.

3. **Premium Feature Unlocking**:
   - Forces the internal Limited-Time Premium Unlock (`isLTPUActive`) gate method to return true, enabling subscription-locked editing features locally.

### B. Cleaning & Background Sync Suppression (`rawResourcePatch` & `resourcePatch`)

1. **Lightroom Junk Cleaner**:
   - Purges build metadata, compiler properties, text proto files, and duplicate license notices from APK root and `META-INF`.
   - Protects critical DEX, manifest, resource tables, and signature structures.

2. **Background Sync Suppression**:
   - Strips boot completion permissions and disables WorkManager services and broadcast receivers.

---

## 4. Behavioral Hazards Warning

1. **Lightroom Background Sync**:
   Disabling WorkManager can affect background photo uploads, syncing, and exports.

---

## 5. Known Limitations

1. **Sideload Install Support Requirement**:
   Without Sideload Install Support, sideloaded lab installs route directly to the Play Store.
