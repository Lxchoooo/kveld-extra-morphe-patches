<p align="center">
  <a href="https://github.com/kveld9/kveld-extra-morphe-patches/releases/latest"><img src="https://img.shields.io/github/v/release/kveld9/kveld-extra-morphe-patches?color=7928CA&label=Release&logo=github&style=flat-square" alt="Latest Release" /></a>
  <a href="https://github.com/kveld9/kveld-extra-morphe-patches/releases"><img src="https://img.shields.io/github/downloads/kveld9/kveld-extra-morphe-patches/total?style=flat-square&logo=github" alt="Total Downloads" /></a>
  <img src="https://img.shields.io/badge/Runtime-Morphe_Patcher_1.8.0-8A2BE2?style=flat-square" alt="Runtime" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=flat-square" alt="License" />
</p>

<h1 align="center">kveld9 Extra Patches</h1>

<p align="center">
  Standalone, app-specific patches for use with the <b><a href="https://morphe.software">Morphe</a></b> patcher framework.
  Companion to <a href="https://github.com/kveld9/kveld-morphe-patches">kveld-morphe-patches</a>.
</p>

<p align="center">
  <a href="https://morphe.software/add-source?github=kveld9/kveld-extra-morphe-patches"><img src="https://img.shields.io/badge/Morphe_Manager-Add_Patch_Source-8A2BE2?style=for-the-badge&logo=android&logoColor=white" alt="Add Source to Morphe Manager" /></a>
  &nbsp;&nbsp;
  <a href="https://github.com/kveld9/kveld-extra-morphe-patches/releases/latest"><img src="https://img.shields.io/badge/Direct_Download-Get_.MPP_Bundle-0070F3?style=for-the-badge&logo=github&logoColor=white" alt="Download Latest Release" /></a>
  &nbsp;&nbsp;
  <a href="https://t.me/kveldmorphe"><img src="https://img.shields.io/badge/Telegram-Official_Support-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Official Telegram Support Group" /></a>
</p>

---

## Key Advantages

- **Zero Runtime Overhead**: compile-time Dalvik bytecode manipulation and XML transformation without background daemons, proxy servers, or Xposed frameworks.
- **Client-Side Telemetry Neutralization**: disables tracking, analytics, and crash reporting at method call sites and manifest components instead of fragile network-level blackholing.
- **Rootless & Standalone**: operates directly on userland APKs and APKM split bundles; no Magisk, KernelSU, or root privileges required.
- **Strict Upstream Parity**: each app targets exactly one version, the latest supported release, with exact fingerprint assertions.

## Supported Apps

| App | Package | Target Version | Variant | Download Source | Guide |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Instagram | `com.instagram.android` | 447.0.0.55.81 | APKM bundle | [APKMirror](https://www.apkmirror.com/apk/instagram/instagram-instagram/instagram-447-0-0-55-81-release/) | [Instagram Guide](docs/apps/instagram.md) |
| X | `com.twitter.android` | 12.33.1-prod.01 | APKM bundle (`arm64-v8a`) | [APKMirror](https://www.apkmirror.com/apk/x-corp/twitter/x-12-33-1-prod-01-release/) | [X Guide](docs/apps/twitter.md) |
| Moovit | `com.tranzmate` | 5.201.1.1809 | APKM bundle (`arm64-v8a`) | [APKMirror](https://www.apkmirror.com/apk/moovit/moovit-bus-train-live-info/moovit-your-transit-tracker-5-201-1-1809-release/) | [Moovit Guide](docs/apps/moovit.md) |
| PowerPoint | `com.microsoft.office.powerpoint` | 16.0.20527.20034 | APKM bundle | [APKMirror](https://www.apkmirror.com/apk/microsoft-corporation/powerpoint/microsoft-powerpoint-16-0-20527-20034-release/) | [PowerPoint Guide](docs/apps/powerpoint.md) |
| Lightroom | `com.adobe.lrmobile` | 11.6.01 | APK (`arm64-v8a`, PairIP-free variant) | [Uptodown](https://adobe-lightroom-mobile.uptodown.com/android) | [Lightroom Guide](docs/apps/lrmobile.md) |
| CapCut | `com.lemon.lvoverseas` | 19.7.0 | APK (`arm64-v8a`, `armeabi-v7a`, `nodpi`) | [APKMirror](https://www.apkmirror.com/apk/bytedance-pte-ltd/capcut/capcut-video-editor-19-7-0-release) | [CapCut Guide](docs/apps/capcut.md) |
| Copilot | `com.microsoft.office.officehubrow` | 16.0.20527.20022 | APKM bundle (`arm64-v8a`) | [APKMirror](https://www.apkmirror.com/apk/microsoft-corporation/microsoft-copilot/microsoft-copilot-16-0-20527-20022-release/) | [Copilot Guide](docs/apps/officehub.md) |

## Patches

<!-- PATCHES_START -->
<details>
<summary>Instagram&nbsp;&nbsp;•&nbsp;&nbsp;<b>5 patches</b></summary>
<br>

**Supported versions:**

| 447.0.0.55.81 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **AR Effects Slimmer** | Strips ARCore native binaries (libarcore_sdk_c.so, libarcore_sdk_jni.so) and AR effect/face tracking asset models. WARNING: Disables camera AR filters, 3D face effects, and body/face tracking. |  |
| **Block Crash & Diagnostics** | Neutralizes crash-dump uploaders, Lacrima detector broadcasts, debug diagnostics, carrier preload trackers, and lead-ads entrypoints. |  |
| **Block Telemetry & Trackers** | Neutralizes Facebook Analytics2, DataTransport, FDID/PhoneId providers, and strips AD_ID permissions. |  |
| **Breakpad Crash Reporter Slimmer** | Zeroes Google Breakpad native crash reporter binaries (libbreakpad.so, libbreakpad_cpp_helper.so) in-situ. WARNING: Native crash reporting is silenced. If the host application loads these libraries unconditionally without exception guards, startup crashes could occur. |  |
| **MLKit Vision Slimmer** | Disable MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning. |  |

</details>

<details>
<summary>CapCut&nbsp;&nbsp;•&nbsp;&nbsp;<b>6 patches</b></summary>
<br>

**Supported versions:**

| 19.7.0 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Google AppMeasurement event dispatchers. |  |
| **Bypass Effects Region Restriction** | Fixes effects, transitions, and templates failing to load (ByteDance Shark WAF block) by spoofing device_platform to 'windows' and resetting deviceId in effect requests. |  |
| **Locale Slimmer** | Strips unselected language string tables, resources, and asset JSON files from the APK. Base fallback and English are always preserved. | • Locales to keep |
| **Native Bloat Slimmer** | Strips non-essential companion native libraries by zeroing bytes in-situ. Always zeroes AppLovin ad crash reporter. WARNING: Stripping speech recognition and synthesis engines (libspeech*.so) disables voice recognition, voiceover captions, and speech-to-text features. | • Trim Speech Engines (Breaks Voice Features) |
| **Screen Density Slimmer** | Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved. | • Target screen density |
| **Unlock Premium** | Forces CapCut VIP gates to return true (SubscribeImpl, PayVipImpl, UserVipInfo, UserDetailInfo, VipUserServiceImpl, SubscribeCloudImpl). Cloud-gated assets may still fail. |  |

</details>

<details>
<summary>Lightroom&nbsp;&nbsp;•&nbsp;&nbsp;<b>5 patches</b></summary>
<br>

**Supported versions:**

| 11.6.01 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Google AppMeasurement and Firebase Crashlytics dispatchers, strips advertising permissions, and injects analytics opt-out flags. |  |
| **Lightroom Background Sync** | Disables WorkManager background sync services, scheduled jobs, and boot receiver. The WorkManager startup initializer is intentionally preserved because removing it prevents application boot. WARNING: Disabling WorkManager can affect background photo uploads, syncing, and exports. |  |
| **Lightroom Junk Cleaner** | Purges non-functional build metadata, compiler properties, and duplicate license files from the APK root and META-INF. |  |
| **Sideload Install Support** | Ensures compatibility for sideloaded lab installs by allowing local installer source checks to report success and completing Play Integrity token requests. Required for sideloaded lab installs: without it the app routes to the Play Store. |  |
| **Unlock Premium Features** | Enables app features locked behind subscription paywalls by activating the internal Limited-Time Premium Unlock (LTPU) gate. Requires Sideload Install Support (applied automatically as dependency). |  |

</details>

<details>
<summary>Moovit&nbsp;&nbsp;•&nbsp;&nbsp;<b>7 patches</b></summary>
<br>

**Supported versions:**

| 5.201.1.1809 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes AppsFlyer, Braze, and Inneractive DEX dispatchers, disables analytics services/providers, and strips advertising permissions. |  |
| **Fix Google Maps** | Restores Google Maps rendering by spoofing the original package signature to Google Play Services. | • Google Maps Platform API key |
| **Locale Slimmer** | Strips unselected language string tables and resources from base APK. Base fallback and English are always preserved. | • Locales to keep |
| **Remove Ads** | Hides banner and inline ads and neutralizes ad unit ID lookups. |  |
| **Screen Density Slimmer** | Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved. | • Target screen density |
| **Suppress Paywalls** | Suppresses subscription paywalls, onboarding upgrade dialogs, and promotional cards. |  |
| **Unlock Moovit+** | Unlocks Moovit+ premium subscription features locally, including Safe Ride and address search in favorites. |  |

</details>

<details>
<summary>Copilot&nbsp;&nbsp;•&nbsp;&nbsp;<b>6 patches</b></summary>
<br>

**Supported versions:**

| 16.0.20527.20022 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement platform identifiers (AIFA, AppSetId), rewrites default telemetry endpoints inside telemetry packages and custom blocked hosts unscoped to 0.0.0.0, disables cross-sell, Floodgate, HockeyApp and DataTransport components, and strips advertising permissions. | • Custom Blocked Hosts |
| **Bypass Code Transparency** | Skips the code-transparency failure dialog and reports local verification success, unblocking sideloaded installs. |  |
| **Copilot Companion Native Slimmer** | Strips optional companion native binaries (React Native, Hermes, SlimCV) via in-situ zeroing. WARNING: Hermes and React Native are load-bearing for React Native initialization - enabling this WILL crash Copilot React Native surfaces (Copilot chat host) with UnsatisfiedLinkError. The HockeyApp native exception handler (proven UnsatisfiedLinkError in OfficeApplication.onMAMCreate) and voice/dictation SDKs (libofficevoicesdk, libofficevoicetranscriptionsdk; proven dlopen FATAL on boot path) are load-bearing at startup and are therefore never stripped. |  |
| **Copilot DPI Slimmer** | Strips drawables for unselected screen densities from Copilot base APK while preserving launcher icons and single-density assets. Non-phone UI mode qualifiers (watch, television, car, vrheadset) are purged from drawable/mipmap resources. WARNING: Displays matching stripped densities will scale preserved assets. | • Target screen density |
| **Copilot Junk Cleaner** | Purges non-functional build metadata, properties, proto descriptors, and duplicate license notices from APK root and META-INF while strictly protecting runtime assets and signatures. |  |
| **Disable Login Requirement** | Removes login requirement and FTUX paywall screens. Cloud-backed features still require sign-in server-side. |  |

</details>

<details>
<summary>PowerPoint&nbsp;&nbsp;•&nbsp;&nbsp;<b>8 patches</b></summary>
<br>

**Supported versions:**

| 16.0.20527.20034 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement platform identifiers (AIFA, AppSetId), disables HockeyApp activities and DataTransport components, and strips advertising permissions. |  |
| **Bypass Code Transparency** | Bypasses the code transparency checks. |  |
| **Disable Login Requirement** | Removes login requirement and FTUX paywall screens. |  |
| **PowerPoint Companion Native Slimmer** | Strips optional companion native binaries (React Native and Hermes JavaScript runtime stack) via in-situ zeroing. WARNING: stripped libraries are load-bearing for React Native initialization - enabling this WILL crash the app with UnsatisfiedLinkError when Copilot or other React Native surfaces start, not merely hide those features. |  |
| **PowerPoint DPI Slimmer** | Strips drawables for unselected screen densities from PowerPoint base APK while preserving launcher icons and single-density assets. WARNING: Displays matching stripped densities will scale preserved assets. | • Target screen density |
| **PowerPoint Locale Slimmer** | Strips unselected localized resource directories (res/values-<locale>/) from PowerPoint base APK. English (en, en-us) is always retained. WARNING: Application strings for stripped locales will fall back to English. | • Locales to keep |
| **Remove Shared User ID** | Removes the sharedUserId attribute from the manifest to prevent installation conflicts. |  |
| **Unlock 365 Family** | Unlocks Microsoft 365 Family subscription features locally. |  |

</details>

<details>
<summary>X&nbsp;&nbsp;•&nbsp;&nbsp;<b>15 patches</b></summary>
<br>

**Supported versions:**

| 12.33.1-prod.01 |
| :---: |

| Patch | Description | Options |
|----------|----------------|-----------|
| **Block Telemetry & Trackers** | Neutralizes Google AppMeasurement and Firebase Performance Trace dispatchers, and strips advertising identifiers. |  |
| **Block Update Screen** | Blocks the 'This app is out of date' screen and suppresses in-app update prompts on launch |  |
| **Control Video Auto Scroll** | Prevents automatic scrolling to the next video in immersive view |  |
| **DM Debloat** | Hide call buttons and composer actions in direct message conversations | • Hide DM Audio Call<br>• Hide DM Video Call |
| **Drawer Items** | Hide individual items in the navigation drawer | • Hide Profile<br>• Hide Premium<br>• Hide Communities<br>• Hide Bookmarks<br>• Hide Lists<br>• Hide Spaces<br>• Hide Creator Studio<br>• Hide Get Grok<br>• Hide Help Center |
| **Navigation UI** | Bottom navigation, FAB, tabs and bars | • Hide Badges<br>• Hide FAB Menu<br>• Navigation Bar<br>• Grok Tab<br>• Explore Tabs<br>• Search Tab Items<br>• Side Bar Items |
| **No Shortened URL** | Expands t.co shortened URLs, removing the click-tracking intermediary |  |
| **Pause Search Suggestions** | Stops persisting search suggestions locally without breaking search |  |
| **Post & Profile UI** | Post actions, profile header and Grok surfaces | • Hide Grok Post Action<br>• Hide Profile Grok Button<br>• Profile Tabs |
| **Remove Ads** | Removes promoted posts, trends and ads from timeline |  |
| **Remove Premium Upsell** | Removes premium upsell surfaces | • Hide Profile Get Verified |
| **Share Links** | Custom sharing domain, handle custom links and legacy share links | • Custom Sharing Domain<br>• Custom Sharing Domain<br>• Handle Custom Links<br>• Legacy Share Links |
| **Show Sensitive Media** | Displays sensitive media directly without requiring tap to reveal |  |
| **Timeline UI** | Timeline surfaces: posts, threads, banners, recommendations and video entry points | • Hide Banner<br>• Hide Promote Button<br>• Hide Nudge Button<br>• Hide Recommendation Items<br>• Hide Discover More<br>• Hide Recommended Users<br>• Hide Live Threads<br>• Hide Community Notes<br>• Hide Post Metrics<br>• Hide View Count<br>• Inline Action Bar<br>• Timeline Add Tab<br>• Timeline Top Bar<br>• Hide Live Banner |
| **X MLKit Vision Slimmer** | Disables MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning. |  |

</details>

<!-- PATCHES_END -->

## Documentation

| Guide | Description |
| :--- | :--- |
| [Building & Development](docs/building.md) | Toolchain prerequisites, Gradle build tasks, in-situ patching gate, and catalog synchronization. |
| [Project Scope](docs/out-of-scope.md) | Compile-time invariants, single-version policy, and rejected feature categories. |

## Contributing

Before proposing new features, review the [Project Scope](docs/out-of-scope.md). Every patch change must pass the in-situ verification gate with 0 failed patches, 0 fingerprint mismatches, and 0 smali compile errors:

```bash
./gradlew runPatchTest -Papp=<targetApp>   # e.g. instagram, twitter, moovit, powerpoint
./gradlew check
```

## Community & Support

- Telegram Support Group: [t.me/kveldmorphe](https://t.me/kveldmorphe)

## Legal Disclaimer

**kveld9 Extra Patches** is an independent, community-driven open-source project and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Meta Platforms, Inc., X Corp., Tranzit (Moovit), Microsoft Corporation, or any of their subsidiaries or affiliates.

All product names, logos, brands, and registered trademarks mentioned in this repository are the property of their respective holders. Their inclusion does not imply affiliation with or endorsement by them.

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
