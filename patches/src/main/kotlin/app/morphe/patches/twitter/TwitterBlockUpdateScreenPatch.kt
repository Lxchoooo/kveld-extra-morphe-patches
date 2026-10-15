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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val blockingUpdateFingerprint = Fingerprint(
    strings = listOf(
        "min_android_xlite_version",
        "x_lite_in_app_update_blocking_versions",
    ),
)

private val inAppUpdateFingerprint = Fingerprint(
    strings = listOf(
        "x_lite_in_app_update_min_releases_behind",
        "x_lite_in_app_update_cold_start_versions",
    ),
)

@Suppress("unused")
val twitterBlockUpdateScreenPatch = bytecodePatch(
    name = "Block Update Screen",
    description = "Blocks the outdated app screen and suppresses in-app update prompts on launch.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val blockingMethod = blockingUpdateFingerprint.method
        val instructions = blockingMethod.implementation?.instructions?.toList()
            ?: error("[Block Update Screen] Blocking update method has no instructions.")

        val minVersionIndex = instructions.indexOfFirst { ins ->
            (ins as? ReferenceInstruction)?.reference?.let { ref ->
                (ref as? StringReference)?.string == "min_android_xlite_version"
            } == true
        }
        if (minVersionIndex < 0) {
            error("[Block Update Screen] 'min_android_xlite_version' string not found.")
        }

        val unitFieldRef = instructions
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? FieldReference }
            .firstOrNull { it.definingClass == "Lkotlin/Unit;" }
        val unitFieldName = unitFieldRef?.name ?: "a"

        blockingMethod.addInstructions(
            minVersionIndex,
            """
                sget-object v0, Lkotlin/Unit;->$unitFieldName:Lkotlin/Unit;
                return-object v0
            """.trimIndent(),
        )
        patched++

        val inAppUpdateMethod = inAppUpdateFingerprint.method
        inAppUpdateMethod.addInstructions(0, "return-void")
        patched++

        println("[Block Update Screen] Applied $patched hooks -> update screen and prompts blocked.")
    }
}
