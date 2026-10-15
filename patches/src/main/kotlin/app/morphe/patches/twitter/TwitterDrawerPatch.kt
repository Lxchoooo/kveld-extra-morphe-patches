/*
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

private val drawerDiscountPillFingerprint = Fingerprint(
    strings = listOf("DrawerDiscountPillAlpha"),
)

private val sideBarFingerprint = Fingerprint(
    strings = listOf("android_grok_bot_sidebar_enabled"),
)


@Suppress("unused")
val twitterDrawerPatch = bytecodePatch(
    name = "Drawer Items",
    description = "Hides individual menu items, badges, and sidebar entries in the navigation drawer.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideProfile by booleanOption(
        key = "hideProfile",
        default = true,
        title = "Hide Profile",
        description = "Hide Profile entry in navigation drawer.",
        required = false,
    )

    val hidePremium by booleanOption(
        key = "hidePremium",
        default = true,
        title = "Hide Premium",
        description = "Hide Premium subscription entry and discount badges in navigation drawer (for profile and home upsells, see Remove Premium Upsell).",
        required = false,
    )

    val hideCommunities by booleanOption(
        key = "hideCommunities",
        default = true,
        title = "Hide Communities",
        description = "Hide Communities entry in navigation drawer.",
        required = false,
    )

    val hideBookmarks by booleanOption(
        key = "hideBookmarks",
        default = true,
        title = "Hide Bookmarks",
        description = "Hide Bookmarks entry in navigation drawer.",
        required = false,
    )

    val hideLists by booleanOption(
        key = "hideLists",
        default = true,
        title = "Hide Lists",
        description = "Hide Lists entry in navigation drawer.",
        required = false,
    )

    val hideSpaces by booleanOption(
        key = "hideSpaces",
        default = true,
        title = "Hide Spaces",
        description = "Hide Spaces entry in navigation drawer.",
        required = false,
    )

    val hideCreatorStudio by booleanOption(
        key = "hideCreatorStudio",
        default = true,
        title = "Hide Creator Studio",
        description = "Hide Creator Studio entry in navigation drawer.",
        required = false,
    )

    val hideGetGrok by booleanOption(
        key = "hideGetGrok",
        default = true,
        title = "Hide Grok",
        description = "Hide Grok assistant entry in navigation drawer and sidebar (for Grok navigation tab and post actions, see Navigation UI and Post & Profile UI).",
        required = false,
    )

    val hideHelpCenter by booleanOption(
        key = "hideHelpCenter",
        default = true,
        title = "Hide Help Center",
        description = "Hide Help Center and feedback entries in navigation drawer.",
        required = false,
    )

    execute {
        val discountPillMethod = drawerDiscountPillFingerprint.method
        val classDescriptor = discountPillMethod.definingClass
        val classDef = mutableClassDefBy(classDescriptor)

        val methodE = classDef.methods.firstOrNull { it.name == "e" && it.parameterTypes.size == 4 }
            ?: error("[Drawer Items] Discount pill method 'e' not found in '$classDescriptor'.")
        val methodO = classDef.methods.firstOrNull { it.name == "o" && it.parameterTypes.size == 4 }
            ?: error("[Drawer Items] Get Grok method 'o' not found in '$classDescriptor'.")
        val methodP = classDef.methods.firstOrNull { it.name == "p" && it.parameterTypes.size == 8 }
            ?: error("[Drawer Items] Standard item method 'p' not found in '$classDescriptor'.")
        val methodQ = classDef.methods.firstOrNull { it.name == "q" && it.parameterTypes.size == 10 }
            ?: error("[Drawer Items] Item method 'q' not found in '$classDescriptor'.")

        var patched = 0

        // 1. Hide Premium discount pill badge
        if (hidePremium ?: true) {
            methodE.addInstructions(0, "return-void")
            patched++
        }

        // 2. Hide Grok (drawer item and sidebar bot entry)
        if (hideGetGrok ?: true) {
            methodO.addInstructions(0, "return-void")
            patched++
            if (neutralizeFlag(sideBarFingerprint, "android_grok_bot_sidebar_enabled", "Drawer Items")) {
                patched++
            } else {
                error("[Drawer Items] Failed to neutralize 'android_grok_bot_sidebar_enabled' flag.")
            }
        }

        // 3. Hide Help Center / Feedback via method p
        if (hideHelpCenter ?: true) {
            val smaliP = """
                move-object/from16 v0, p1
                if-eqz v0, :cond_keep_p
                iget v1, v0, Lcom/x/icons/b;->a:I
                const v2, 2131231765
                if-ne v1, v2, :cond_check_p_feedback
                return-void
                :cond_check_p_feedback
                const v2, 2131231848
                if-ne v1, v2, :cond_keep_p
                return-void
                :cond_keep_p
            """.trimIndent()
            methodP.addInstructions(0, smaliP)
            patched++
        }

        // 4. Hide item entries via method q
        val qChecks = mutableListOf<String>()
        if (hideProfile ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131232081
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hidePremium ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131232424
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideCommunities ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131231666
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideBookmarks ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131231531
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideLists ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131231902
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideSpaces ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131232265
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }
        if (hideCreatorStudio ?: true) {
            val label = "check_q_${qChecks.size}"
            qChecks.add("""
                const v2, 2131232183
                if-ne v1, v2, :$label
                return-void
                :$label
            """.trimIndent())
        }

        if (qChecks.isNotEmpty()) {
            val smaliQ = """
                move-object/from16 v0, p1
                if-eqz v0, :cond_keep_q
                iget v1, v0, Lcom/x/icons/b;->a:I
                ${qChecks.joinToString("\n")}
                :cond_keep_q
            """.trimIndent()
            methodQ.addInstructions(0, smaliQ)
            patched++
        }

        if (patched > 0) {
            println("[Drawer Items] Applied $patched hooks -> drawer items customized.")
        } else {
            println("[Drawer Items] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
