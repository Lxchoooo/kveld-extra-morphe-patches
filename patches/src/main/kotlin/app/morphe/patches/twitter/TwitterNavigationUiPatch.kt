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

private val badgesFingerprint = Fingerprint(
    strings = listOf("grok_android_tab_badge_enabled"),
)

private val fabFingerprint = Fingerprint(
    strings = listOf("android_composer_fab_always_visible_enabled"),
)

private val fabMenuFingerprint = Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Z"),
    strings = listOf("android_compose_fab_expansion_enabled"),
)

private val bottomNavFingerprint = Fingerprint(
    strings = listOf("android_x_lite_persistent_bottom_nav_enabled"),
)

private val grokTabFingerprint = Fingerprint(
    definingClass = "Lcom/x/main/landing/f0;",
    strings = listOf("android_webview_grok_tab_enabled"),
)

private val exploreTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_explore_tab_enabled"),
)

private val searchTabsFingerprint = Fingerprint(
    strings = listOf("android_rich_tabs_search_tab_enabled"),
)


@Suppress("unused")
val twitterNavigationUiPatch = bytecodePatch(
    name = "Navigation UI",
    description = "Customizes bottom navigation bar tabs, unread badges, and search surfaces.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideBadges by booleanOption(
        key = "hideBadges",
        default = true,
        title = "Hide Badges",
        description = "Hide unread indicator badges from Grok and navigation bar tabs (for Grok drawer and post actions, see Drawer Items and Post & Profile UI).",
        required = false,
    )

    val hideFab by booleanOption(
        key = "hideFab",
        default = true,
        title = "Hide FAB",
        description = "Hide floating action button entirely.",
        required = false,
    )

    val hideFabMenu by booleanOption(
        key = "hideFabMenu",
        default = true,
        title = "Hide FAB Menu",
        description = "Hide secondary action expansion menu on floating action button.",
        required = false,
    )

    val navigationBar by booleanOption(
        key = "navigationBar",
        default = true,
        title = "Hide Navigation Bar",
        description = "Hide persistent bottom navigation bar.",
        required = false,
    )

    val grokTab by booleanOption(
        key = "grokTab",
        default = true,
        title = "Hide Grok Tab",
        description = "Hide Grok tab from bottom navigation bar (for Grok drawer and post actions, see Drawer Items and Post & Profile UI).",
        required = false,
    )

    val exploreTabs by booleanOption(
        key = "exploreTabs",
        default = true,
        title = "Hide Explore Tabs",
        description = "Hide rich explore tab surfaces.",
        required = false,
    )

    val searchTabItems by booleanOption(
        key = "searchTabItems",
        default = true,
        title = "Hide Search Tab Items",
        description = "Hide rich search tab items and surfaces.",
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

        if (patched > 0) {
            println("[Navigation UI] Applied $patched hooks -> navigation, tabs and bar elements customized.")
        } else {
            println("[Navigation UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
