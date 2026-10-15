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

private val composerUpsellFingerprint = Fingerprint(
    strings = listOf("subscriptions_upsells_premium_home_nav_enabled"),
)

private val homeNavOfferFingerprint = Fingerprint(
    strings = listOf("subscriptions_upsells_premium_home_nav_offer_enabled"),
)

private val ownProfileUpsellsFingerprint = Fingerprint(
    strings = listOf("OwnProfileUpsells(showAnalyticsUpsell="),
)


@Suppress("unused")
val twitterRemovePremiumUpsellPatch = bytecodePatch(
    name = "Remove Premium Upsell",
    description = "Removes Premium subscription prompts and verification upsell surfaces across the app (for drawer entry, see Drawer Items).",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideProfileGetVerified by booleanOption(
        key = "hideProfileGetVerified",
        default = true,
        title = "Hide Profile Get Verified",
        description = "Hide Get Verified subscription pill on user profiles (for drawer Premium entry, see Drawer Items).",
        required = false,
    )

    execute {
        var patched = 0

        if (hideProfileGetVerified ?: true) {
            val constructor = ownProfileUpsellsFingerprint.classDef.methods.firstOrNull {
                it.name == "<init>" && it.parameterTypes == listOf("Z", "Z", "Z")
            } ?: error("[Remove Premium Upsell] OwnProfileUpsells constructor not found.")

            constructor.addInstructions(
                0,
                """
                    const/4 p2, 0x0
                    const/4 p3, 0x0
                """.trimIndent(),
            )
            patched++
        } else {
            println("[Remove Premium Upsell] Skipped hideProfileGetVerified: disabled by patch option.")
        }

        if (neutralizeFlag(composerUpsellFingerprint, "subscriptions_upsells_premium_home_nav_enabled", "Remove Premium Upsell")) {
            patched++
        } else {
            error("[Remove Premium Upsell] Failed to neutralize 'subscriptions_upsells_premium_home_nav_enabled' flag.")
        }

        if (neutralizeFlag(homeNavOfferFingerprint, "subscriptions_upsells_premium_home_nav_offer_enabled", "Remove Premium Upsell")) {
            patched++
        } else {
            error("[Remove Premium Upsell] Failed to neutralize 'subscriptions_upsells_premium_home_nav_offer_enabled' flag.")
        }

        println("[Remove Premium Upsell] Applied $patched hooks -> premium upsell surfaces suppressed.")
    }
}
