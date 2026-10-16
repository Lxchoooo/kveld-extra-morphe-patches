/*
 * Copyright (C) 2024-2026 Piko contributors
 * Copyright (C) 2026 kveld9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Under Section 7(b) of GNU GPL version 3, you are required to preserve
 * the original author attributions and legal notices.
 */

package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private val grokPostActionFingerprint = Fingerprint(
    returnType = "Z",
    strings = listOf("grok_android_analyze_timelines_enabled"),
)

private val profileGrokImagineFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("grok_android_imagine_cta_profile_enabled"),
)

private val profileGrokHeaderInitFingerprint = Fingerprint(
    name = "<init>",
    strings = listOf("grok_android_imagine_cta_profile_enabled"),
)

private val profileGrokHeaderResumeFingerprint = Fingerprint(
    name = "onResume",
    returnType = "V",
    strings = listOf("grok_android_imagine_cta_profile_enabled"),
)

private val profileTabsFingerprint = Fingerprint(
    strings = listOf("x_lite_profile_connect_tabs_enabled"),
)


@Suppress("unused")
val twitterPostProfileUiPatch = bytecodePatch(
    name = "Post & Profile UI",
    description = "Customizes post action buttons, Grok surfaces, and user profile tabs.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideGrokPostAction by booleanOption(
        key = "hideGrokPostAction",
        default = true,
        title = "Hide Grok Post Action",
        description = "Hide Grok analysis action button on posts (for Grok drawer and navigation tab, see Drawer Items and Navigation UI).",
        required = false,
    )

    val hideProfileGrokButton by booleanOption(
        key = "hideProfileGrokButton",
        default = true,
        title = "Hide Profile Grok Button",
        description = "Hide Grok Imagine action button on profile header banner (for Grok drawer and navigation tab, see Drawer Items and Navigation UI).",
        required = false,
    )

    val profileTabs by booleanOption(
        key = "profileTabs",
        default = true,
        title = "Hide Profile Tabs",
        description = "Hide extra connect and media tabs on user profiles.",
        required = false,
    )

    execute {
        var patched = 0

        // hideGrokPostAction
        if (hideGrokPostAction ?: true) {
            if (neutralizeFlag(grokPostActionFingerprint, "grok_android_analyze_timelines_enabled", "Post & Profile UI")) {
                patched++
            } else {
                error("[Post & Profile UI] Failed to neutralize 'grok_android_analyze_timelines_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped hideGrokPostAction: disabled by patch option.")
        }

        // hideProfileGrokButton
        if (hideProfileGrokButton ?: true) {
            var ok = false

            // 1. Neutralize the Imagine flag evaluation inside the tabbed
            // content dispatcher (do NOT early-return the whole method: it
            // resolves profile header, upsell and repository objects).
            if (neutralizeFlag(profileGrokImagineFingerprint, "grok_android_imagine_cta_profile_enabled", "Post & Profile UI")) {
                patched++
                ok = true
            }

            // 2. In header init, neutralize the flag
            if (neutralizeFlag(profileGrokHeaderInitFingerprint, "grok_android_imagine_cta_profile_enabled", "Post & Profile UI")) {
                patched++
                ok = true
            }

            // 3. On header resume, neutralize the flag (the CTA reloads on every resume)
            if (neutralizeFlag(profileGrokHeaderResumeFingerprint, "grok_android_imagine_cta_profile_enabled", "Post & Profile UI")) {
                patched++
                ok = true
            }

            if (!ok) {
                error("[Post & Profile UI] Failed to neutralize 'grok_android_imagine_cta_profile_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped hideProfileGrokButton: disabled by patch option.")
        }

        // profileTabs
        if (profileTabs ?: true) {
            if (neutralizeFlag(profileTabsFingerprint, "x_lite_profile_connect_tabs_enabled", "Post & Profile UI")) {
                patched++
            } else {
                error("[Post & Profile UI] Failed to neutralize 'x_lite_profile_connect_tabs_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped profileTabs: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Post & Profile UI] Applied $patched hooks -> post and profile UI customized.")
        } else {
            println("[Post & Profile UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
