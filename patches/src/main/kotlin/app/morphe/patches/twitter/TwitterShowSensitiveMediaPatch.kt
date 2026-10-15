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
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private val mediaVisibilityResultsFingerprint = Fingerprint(
    strings = listOf("MediaVisibilityResults(blurImageInterstitial="),
)

@Suppress("unused")
val twitterShowSensitiveMediaPatch = bytecodePatch(
    name = "Show Sensitive Media",
    description = "Displays sensitive media directly without requiring tap to reveal.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val toStringMethod = mediaVisibilityResultsFingerprint.method
        val classDescriptor = toStringMethod.definingClass
        val classDef = mutableClassDefBy(classDescriptor)

        val initMethods = classDef.methods.filter { it.name == "<init>" }
        if (initMethods.isEmpty()) {
            error("[Show Sensitive Media] Constructor methods not found in '$classDescriptor'.")
        }

        var patched = 0
        for (initMethod in initMethods) {
            val instructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Show Sensitive Media] Constructor in '$classDescriptor' has no instructions.")

            if (instructions.isEmpty() || !instructions[0].opcode.name.startsWith("invoke-direct")) {
                error("[Show Sensitive Media] Constructor in '$classDescriptor' does not start with invoke-direct super call.")
            }

            initMethod.addInstructions(1, "return-void")
            patched++
        }

        println("[Show Sensitive Media] Applied $patched hooks -> sensitive media blur warning bypassed.")
    }
}
