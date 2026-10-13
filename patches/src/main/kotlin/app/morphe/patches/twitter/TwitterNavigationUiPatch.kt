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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val badgesFingerprint = Fingerprint(
    strings = listOf("grok_android_tab_badge_enabled"),
)

private val fabFingerprint = Fingerprint(
    strings = listOf("android_composer_fab_always_visible_enabled"),
)

private val fabMenuFingerprint = Fingerprint(
    strings = listOf("android_compose_fab_expansion_enabled"),
)

private val bottomNavFingerprint = Fingerprint(
    strings = listOf("android_x_lite_persistent_bottom_nav_enabled"),
)

private val grokTabFingerprint = Fingerprint(
    definingClass = "Lcom/x/main/landing/d0;",
    strings = listOf("android_webview_grok_tab_enabled"),
)

private val exploreTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_explore_tab_enabled"),
)

private val searchTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_search_tab_enabled"),
)

private val sideBarFingerprint = Fingerprint(
    strings = listOf("android_grok_bot_sidebar_enabled"),
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
val twitterNavigationUiPatch = bytecodePatch(
    name = "Navigation UI",
    description = "Bottom navigation, FAB, tabs and bars",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideBadges by booleanOption(
        key = "hideBadges",
        default = true,
        title = "Hide Badges",
        description = "Hide badges and unread indicator counters from navigation bar icons",
        required = false,
    )

    val hideFab by booleanOption(
        key = "hideFab",
        default = true,
        title = "Hide FAB",
        description = "Hide floating action button entirely",
        required = false,
    )

    val hideFabMenu by booleanOption(
        key = "hideFabMenu",
        default = true,
        title = "Hide FAB Menu",
        description = "Hide secondary action expansion menu on floating action button",
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

    val exploreTabs by booleanOption(
        key = "exploreTabs",
        default = true,
        title = "Explore Tabs",
        description = "Toggle rich explore tab surfaces",
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

    execute {
        var patched = 0

        // 1. hideBadges
        if (hideBadges ?: true) {
            if (neutralizeFlag(badgesFingerprint, "grok_android_tab_badge_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'grok_android_tab_badge_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped hideBadges: disabled by patch option.")
        }

        // 2. hideFab
        if (hideFab ?: true) {
            if (neutralizeFlag(fabFingerprint, "android_composer_fab_always_visible_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_composer_fab_always_visible_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped hideFab: disabled by patch option.")
        }

        // 3. hideFabMenu
        if (hideFabMenu ?: true) {
            if (neutralizeFlag(fabMenuFingerprint, "android_compose_fab_expansion_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_compose_fab_expansion_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped hideFabMenu: disabled by patch option.")
        }

        // 4. navigationBar
        if (navigationBar ?: true) {
            if (neutralizeFlag(bottomNavFingerprint, "android_x_lite_persistent_bottom_nav_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_x_lite_persistent_bottom_nav_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped navigationBar: disabled by patch option.")
        }

        // 5. grokTab
        if (grokTab ?: true) {
            if (neutralizeFlag(grokTabFingerprint, "android_webview_grok_tab_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_webview_grok_tab_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped grokTab: disabled by patch option.")
        }

        // 6. exploreTabs
        if (exploreTabs ?: true) {
            if (neutralizeFlag(exploreTabsFingerprint, "android_rich_tabs_explore_tab_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_rich_tabs_explore_tab_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped exploreTabs: disabled by patch option.")
        }

        // 9. searchTabItems
        if (searchTabItems ?: true) {
            if (neutralizeFlag(searchTabsFingerprint, "android_rich_tabs_search_tab_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_rich_tabs_search_tab_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped searchTabItems: disabled by patch option.")
        }

        // 10. sideBarItems
        if (sideBarItems ?: true) {
            if (neutralizeFlag(sideBarFingerprint, "android_grok_bot_sidebar_enabled", "Navigation UI")) {
                patched++
            } else {
                error("[Navigation UI] Failed to neutralize 'android_grok_bot_sidebar_enabled' flag.")
            }
        } else {
            println("[Navigation UI] Skipped sideBarItems: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Navigation UI] Applied $patched hooks -> navigation, tabs and bar elements customized.")
        } else {
            println("[Navigation UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
