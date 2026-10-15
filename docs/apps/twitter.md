# X (Twitter) Patches Guide

Technical documentation and patch catalog for X (Twitter) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | X |
| **Package Name** | `com.twitter.android` |
| **Target Version** | `12.34.0-prod.01` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [X 12.34.0-prod.01 (arm64-v8a, 120-640dpi, versionCode 312340001 / BUNDLE 35 S)](https://www.apkmirror.com/apk/x-corp/x/x-12-34-0-prod-01-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Background Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables background WorkManager tasks, widgets, and Cast/NFC companion services to reduce resource usage. |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | None | Blocks telemetry collection, tracker SDKs, and analytics services across bytecode and manifest. |
| **Block Update Screen** | `bytecodePatch` | `true` (Enabled) | None | Blocks outdated app warning screens and suppresses update prompts on launch. |
| **Control Video Auto Scroll** | `bytecodePatch` | `false` (Opt-in) | None | Prevents automatic scrolling to the next video in immersive media view. |
| **Custom Deep Links** | `resourcePatch` | `false` (Opt-in) | None | Registers intent filters to open supported domains in the app. |
| **Custom Fonts** | `bytecodePatch` | `false` (Opt-in) | None | Applies custom typography to post and profile text. |
| **DM Debloat** | `bytecodePatch` | `false` (Opt-in) | None | Hides call buttons and composer actions in direct message conversations. |
| **Drawer Items** | `bytecodePatch` | `false` (Opt-in) | None | Hides navigation drawer items including Grok, Premium, and secondary shortcuts. |
| **MLKit Vision Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables MLKit components and discovery registrars to reduce binary footprint. |
| **Navigation UI** | `bytecodePatch` | `false` (Opt-in) | None | Customizes navigation bar, unread badges, search tabs, and floating action button. |
| **No Shortened URL** | `bytecodePatch` | `false` (Opt-in) | None | Expands t.co shortened links to their direct destination. |
| **Pause Search Suggestions** | `bytecodePatch` | `false` (Opt-in) | None | Stops saving recent search history locally without breaking search. |
| **Post & Profile UI** | `bytecodePatch` | `false` (Opt-in) | None | Hides post action buttons and customizes profile surfaces. |
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Removes promoted posts, timeline ads, and sponsored trends. |
| **Remove Premium Upsell** | `bytecodePatch` | `true` (Enabled) | None | Removes premium upgrade prompts and profile verification pills. |
| **Round Off Numbers** | `bytecodePatch` | `false` (Opt-in) | None | Displays exact counts instead of abbreviated numbers. |
| **Share Links** | `bytecodePatch` | `false` (Opt-in) | None | Replaces shared post links with a custom domain. |
| **Show Sensitive Media** | `bytecodePatch` | `false` (Opt-in) | None | Displays sensitive media directly without tap-to-reveal blur warnings. |
| **Timeline UI** | `bytecodePatch` | `true` (Enabled) | None | Customizes timeline surfaces including banners, recommendation carousels, and action buttons. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Background Optimization Layer (`resourcePatch`)

1. **Background Slimmer**:
   - **Objective**: Reduce background battery and memory usage.
   - **How**: Disables background periodic jobs, home screen widgets, and Cast/NFC companion services.

2. **Custom Deep Links**:
   - **Objective**: Open custom domain links (inbound routing) directly in the application.
   - **How**: Injects intent filters with specified domains (vxtwitter, fixvx, fxtwitter, fixupx, twittpr, xcancel) into MainActivity in the manifest.

3. **MLKit Vision Slimmer**:
   - **Objective**: Reduce binary overhead from machine learning vision components.
   - **How**: Disables component discovery and removes discovery registrars from the manifest.

### B. Bytecode Modification Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Stop runtime metric and event submission, and strip tracking components.
   - **How**: Injects immediate returns into measurement and performance tracking methods; automatically executes internal manifest purge to strip advertising permissions and disable analytics services.

2. **Block Update Screen**:
   - **Objective**: Prevent outdated-app blocking screens on launch.
   - **How**: Intercepts update checks and suppresses update requirement prompts.

3. **Control Video Auto Scroll**:
   - **Objective**: Stop automatic advancement to the next video in immersive mode.
   - **How**: Modifies video configuration state initialization to disable auto-advance behavior.

4. **Custom Fonts**:
   - **Objective**: Apply user-selected typography to application text.
   - **How**: Replaces font asset paths with configured font file paths.

5. **DM Debloat**:
   - **Objective**: Simplify direct message conversation headers.
   - **How**: Toggles conversation action flags to hide call buttons and composer actions.

6. **Drawer Items**:
   - **Objective**: Declutter the side navigation drawer and sidebar surfaces.
   - **How**: Hides configured navigation entries including Grok assistant, Premium, and secondary shortcuts.

7. **Navigation UI**:
   - **Objective**: Customize navigation bars, floating buttons, and tabs.
   - **How**: Adjusts layout flags to hide unread badges, floating action buttons, and secondary tabs (drawer and sidebar toggles are centralized under Drawer Items).

8. **No Shortened URL**:
   - **Objective**: Bypass t.co link redirection and click tracking.
   - **How**: Replaces shortened link destinations with their full original target URLs.

9. **Pause Search Suggestions**:
   - **Objective**: Stop storing recent search entries without breaking active search.
   - **How**: Suppresses local caching of search queries.

10. **Post & Profile UI**:
    - **Objective**: Remove unwanted action buttons from posts and profiles.
    - **How**: Hides Grok post actions and profile buttons, and toggles profile header tabs.

11. **Remove Ads**:
    - **Objective**: Remove promoted posts and sponsored content.
    - **How**: Filters out promoted tweets and advertising objects in timeline adapters.

12. **Remove Premium Upsell**:
    - **Objective**: Eliminate subscription prompts and badges.
    - **How**: Hides verification upsell pills and premium upgrade banners (drawer Premium entry is owned by Drawer Items).

13. **Round Off Numbers**:
    - **Objective**: Show exact metric counts on posts and profiles.
    - **How**: Disables number abbreviation formatting routines.

14. **Share Links**:
    - **Objective**: Clean and customize links when sharing posts (outbound).
    - **How**: Replaces shared link domains with configured custom domains (inbound link opening is handled by Custom Deep Links).

15. **Show Sensitive Media**:
    - **Objective**: View sensitive media without manual tap-to-reveal prompts.
    - **How**: Sets sensitive media warning flags to display media directly.

16. **Timeline UI**:
    - **Objective**: Clean up home timeline feeds.
    - **How**: Removes banner alerts, recommendation carousels, and video entrypoints.

---

## 4. Behavioral Hazards Warning

1. **MLKit Vision Slimmer**:
   Disabling MLKit component discovery breaks in-app QR and barcode scanning.
2. **DM Debloat**:
   Neutralizing the calling flag disables direct message outgoing call entrypoints.

---

## 5. Known Limitations

None evidenced by patch sources.
