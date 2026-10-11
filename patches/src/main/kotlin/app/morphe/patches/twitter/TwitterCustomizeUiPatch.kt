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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val exploreTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_explore_tab_enabled"),
)

private val searchTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_search_tab_enabled"),
)

private val inlineActionsFingerprint = Fingerprint(
    strings = listOf("android_ntab_display_options_inline_actions_enabled"),
)

private val bottomNavFingerprint = Fingerprint(
    strings = listOf("android_x_lite_persistent_bottom_nav_enabled"),
)

private val sideBarFingerprint = Fingerprint(
    strings = listOf("android_grok_bot_sidebar_enabled"),
)

private val grokTabFingerprint = Fingerprint(
    definingClass = "Lcom/x/main/landing/d0;",
    strings = listOf("android_webview_grok_tab_enabled"),
)

private val homeTopBarFingerprint = Fingerprint(
    definingClass = "Lcom/x/home/tabbed/a;",
    name = "i",
)

private val profileTabsFingerprint = Fingerprint(
    strings = listOf("x_lite_profile_connect_tabs_enabled"),
)

context(_: BytecodePatchContext)
private fun neutralizeFlag(
    fingerprint: Fingerprint,
    flagKey: String,
    patchName: String,
): Boolean {
    val method = fingerprint.method
    val instructions = method.implementation?.instructions?.toList() ?: return false
    val stringIndex = instructions.indexOfFirst { ins ->
        (ins as? ReferenceInstruction)?.reference?.let { ref ->
            (ref as? StringReference)?.string == flagKey
        } == true
    }
    if (stringIndex < 0) {
        println("[$patchName] Flag string '$flagKey' not found in instructions.")
        return false
    }

    val invokeIndex = (stringIndex + 1 until minOf(instructions.size, stringIndex + 15)).firstOrNull { i ->
        instructions[i].opcode.name.startsWith("invoke-")
    }
    if (invokeIndex == null || invokeIndex + 1 >= instructions.size) {
        println("[$patchName] No invoke instruction found after flag '$flagKey'.")
        return false
    }

    val resultIns = instructions[invokeIndex + 1]
    when (resultIns.opcode) {
        Opcode.MOVE_RESULT -> {
            val reg = (resultIns as OneRegisterInstruction).registerA
            val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
            method.addInstructions(invokeIndex + 2, constIns)
            return true
        }
        Opcode.MOVE_RESULT_OBJECT -> {
            val reg = (resultIns as OneRegisterInstruction).registerA
            method.addInstructions(
                invokeIndex + 2,
                "sget-object v$reg, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;",
            )

            val updatedInstructions = method.implementation?.instructions?.toList() ?: emptyList()
            val booleanValueInvoke = (invokeIndex + 2 until minOf(updatedInstructions.size, invokeIndex + 10)).firstOrNull { i ->
                val ins = updatedInstructions[i]
                ins.opcode.name.startsWith("invoke-") &&
                    ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.name == "booleanValue"
            }
            if (booleanValueInvoke != null && booleanValueInvoke + 1 < updatedInstructions.size &&
                updatedInstructions[booleanValueInvoke + 1].opcode == Opcode.MOVE_RESULT
            ) {
                val boolReg = (updatedInstructions[booleanValueInvoke + 1] as OneRegisterInstruction).registerA
                val constIns = if (boolReg <= 15) "const/4 v$boolReg, 0x0" else "const/16 v$boolReg, 0x0"
                method.addInstructions(booleanValueInvoke + 2, constIns)
            }
            return true
        }
        else -> {
            val branchIndex = (invokeIndex until minOf(instructions.size, invokeIndex + 5)).firstOrNull { i ->
                instructions[i].opcode.name.startsWith("if-")
            }
            if (branchIndex != null) {
                val branchIns = instructions[branchIndex]
                val reg = (branchIns as? OneRegisterInstruction)?.registerA
                if (reg != null) {
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    method.addInstructions(branchIndex, constIns)
                    return true
                }
            }
            println("[$patchName] Unexpected instruction '${resultIns.opcode}' after invoke for flag '$flagKey'.")
            return false
        }
    }
}

@Suppress("unused")
val twitterCustomizeUiPatch = bytecodePatch(
    name = "Customize UI",
    description = "Toggle navigation and timeline UI elements",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val inlineActionBar by booleanOption(
        key = "inlineActionBar",
        default = true,
        title = "Inline Action Bar",
        description = "Toggle inline action buttons across notification timeline",
        required = false,
    )

    val navigationBar by booleanOption(
        key = "navigationBar",
        default = true,
        title = "Navigation Bar",
        description = "Toggle persistent bottom navigation bar behavior",
        required = false,
    )

    val grokTab by booleanOption(
        key = "grokTab",
        default = true,
        title = "Grok Tab",
        description = "Hide Grok tab from bottom navigation bar",
        required = false,
    )

    val replySorting by booleanOption(
        key = "replySorting",
        default = true,
        title = "Reply Sorting",
        description = "Customize default reply sorting behavior in conversation threads",
        required = false,
    )

    val exploreTabs by booleanOption(
        key = "exploreTabs",
        default = true,
        title = "Explore Tabs",
        description = "Toggle rich explore tab surfaces",
        required = false,
    )

    val notificationTabs by booleanOption(
        key = "notificationTabs",
        default = true,
        title = "Notification Tabs",
        description = "Toggle notification timeline tab items",
        required = false,
    )

    val profileTabs by booleanOption(
        key = "profileTabs",
        default = true,
        title = "Profile Tabs",
        description = "Toggle extra connect and media tabs on user profiles",
        required = false,
    )

    val searchSuggestions by booleanOption(
        key = "searchSuggestions",
        default = true,
        title = "Search Suggestions",
        description = "Toggle search typeahead and local suggestion persistence",
        required = false,
    )

    val searchTabItems by booleanOption(
        key = "searchTabItems",
        default = true,
        title = "Search Tab Items",
        description = "Toggle rich search tab items and surfaces",
        required = false,
    )

    val sideBarItems by booleanOption(
        key = "sideBarItems",
        default = true,
        title = "Side Bar Items",
        description = "Toggle drawer sidebar items including Grok assistant entry",
        required = false,
    )

    val timelineTopBar by booleanOption(
        key = "timelineTopBar",
        default = true,
        title = "Timeline Top Bar",
        description = "Toggle top bar elements in home and custom timelines",
        required = false,
    )

    val topBarExtra by booleanOption(
        key = "topBarExtra",
        default = true,
        title = "Top Bar Extra",
        description = "Toggle additional top bar and header action surfaces",
        required = false,
    )

    execute {
        var patched = 0

        // 1. inlineActionBar (Piko: Inline action Bar)
        if (inlineActionBar ?: true) {
            if (neutralizeFlag(inlineActionsFingerprint, "android_ntab_display_options_inline_actions_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_ntab_display_options_inline_actions_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped inlineActionBar: disabled by patch option.")
        }

        // 2. navigationBar (Piko: Navigation Bar)
        if (navigationBar ?: true) {
            if (neutralizeFlag(bottomNavFingerprint, "android_x_lite_persistent_bottom_nav_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_x_lite_persistent_bottom_nav_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped navigationBar: disabled by patch option.")
        }

        // grokTab (Hide Grok bottom navigation tab)
        if (grokTab ?: true) {
            if (neutralizeFlag(grokTabFingerprint, "android_webview_grok_tab_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_webview_grok_tab_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped grokTab: disabled by patch option.")
        }

        // 3. replySorting (Piko: Customize default reply sorting)
        if (replySorting ?: true) {
            println("[Customize UI] Skipped replySorting: default conversation reply sorting is server-driven in 12.33 URT; no client feature flag gate.")
        } else {
            println("[Customize UI] Skipped replySorting: disabled by patch option.")
        }

        // 4. exploreTabs (Piko: explore tabs)
        if (exploreTabs ?: true) {
            if (neutralizeFlag(exploreTabsFingerprint, "android_rich_tabs_explore_tab_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_rich_tabs_explore_tab_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped exploreTabs: disabled by patch option.")
        }

        // 5. notificationTabs (Piko: notification tabs)
        if (notificationTabs ?: true) {
            println("[Customize UI] Skipped notificationTabs: notification tab hierarchy is served via URT in 12.33; no client feature flag gate.")
        } else {
            println("[Customize UI] Skipped notificationTabs: disabled by patch option.")
        }

        // 6. profileTabs (Piko: profile tabs)
        if (profileTabs ?: true) {
            if (neutralizeFlag(profileTabsFingerprint, "x_lite_profile_connect_tabs_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'x_lite_profile_connect_tabs_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped profileTabs: disabled by patch option.")
        }

        // 7. searchSuggestions (Piko: search suggestions)
        if (searchSuggestions ?: true) {
            println("[Customize UI] Skipped searchSuggestions: search suggestions persistence is handled by dedicated Pause Search Suggestions patch; no UI toggle gate.")
        } else {
            println("[Customize UI] Skipped searchSuggestions: disabled by patch option.")
        }

        // 8. searchTabItems (Piko: search tab items)
        if (searchTabItems ?: true) {
            if (neutralizeFlag(searchTabsFingerprint, "android_rich_tabs_search_tab_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_rich_tabs_search_tab_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped searchTabItems: disabled by patch option.")
        }

        // 9. sideBarItems (Piko: side bar items)
        if (sideBarItems ?: true) {
            if (neutralizeFlag(sideBarFingerprint, "android_grok_bot_sidebar_enabled", "Customize UI")) {
                patched++
            } else {
                error("[Customize UI] Failed to neutralize 'android_grok_bot_sidebar_enabled' flag.")
            }
        } else {
            println("[Customize UI] Skipped sideBarItems: disabled by patch option.")
        }

        // 10. timelineTopBar (Hide Connect/Follow button from timeline top bar)
        if (timelineTopBar ?: true) {
            val method = homeTopBarFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Customize UI] Method Lcom/x/home/tabbed/a;->i has no instructions.")
            val followButtonFieldIdx = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                        it.definingClass == "Lcom/x/home/tabbed/a;" && it.name == "d"
                    } == true
            }
            if (followButtonFieldIdx > 0) {
                val ifEqzIdx = instructions.subList(0, followButtonFieldIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0) {
                    val reg = (instructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    method.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Customize UI] Preceding if-eqz branch not found before follow button field in Lcom/x/home/tabbed/a;->i.")
                }
            } else {
                error("[Customize UI] Follow button field Lcom/x/home/tabbed/a;->d not found in Lcom/x/home/tabbed/a;->i.")
            }
        } else {
            println("[Customize UI] Skipped timelineTopBar: disabled by patch option.")
        }

        // 11. topBarExtra (Piko: top bar extra)
        if (topBarExtra ?: true) {
            println("[Customize UI] Skipped topBarExtra: top bar extra surfaces are dynamically composed in 12.33; no client feature flag gate.")
        } else {
            println("[Customize UI] Skipped topBarExtra: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Customize UI] Applied $patched hooks -> UI navigation and timeline elements customized.")
        } else {
            println("[Customize UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
