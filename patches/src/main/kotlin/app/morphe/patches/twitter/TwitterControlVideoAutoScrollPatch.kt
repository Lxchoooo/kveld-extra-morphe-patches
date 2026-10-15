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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val persistentVideoSettingsFingerprint = Fingerprint(
    strings = listOf(
        "PersistentVideoSettings(hasLockedPlaybackSpeed=",
        ", isAutoAdvanceEnabled=",
    ),
)

@Suppress("unused")
val twitterControlVideoAutoScrollPatch = bytecodePatch(
    name = "Control Video Auto Scroll",
    description = "Prevents automatic scrolling to the next video in immersive view.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val toStringMethod = persistentVideoSettingsFingerprint.method
        val targetClassDescriptor = toStringMethod.definingClass
        val targetClass = mutableClassDefBy(targetClassDescriptor)

        val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
            ?: error("[Control Video Auto Scroll] toString in '$targetClassDescriptor' has no instructions.")

        val playbackSpeedStrIndex = toStringInstructions.indexOfFirst { ins ->
            (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) &&
                ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string == ", playbackSpeed="
        }
        if (playbackSpeedStrIndex < 0) {
            error("[Control Video Auto Scroll] ', playbackSpeed=' string not found in '$targetClassDescriptor' toString.")
        }

        val autoAdvanceGetIns = toStringInstructions
            .subList(playbackSpeedStrIndex + 1, minOf(playbackSpeedStrIndex + 5, toStringInstructions.size))
            .firstOrNull { it.opcode == Opcode.IGET_BOOLEAN }
            ?: error("[Control Video Auto Scroll] iget-boolean not found after ', playbackSpeed=' in '$targetClassDescriptor' toString.")

        val autoAdvanceFieldName = ((autoAdvanceGetIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
            ?: error("[Control Video Auto Scroll] Failed to resolve autoAdvance field name.")

        val initMethod = targetClass.methods.firstOrNull { it.name == "<init>" }
            ?: error("[Control Video Auto Scroll] Constructor not found in '$targetClassDescriptor'.")

        val initInstructions = initMethod.implementation?.instructions?.toList()
            ?: error("[Control Video Auto Scroll] Constructor in '$targetClassDescriptor' has no instructions.")

        val autoAdvanceIputIndex = initInstructions.indexOfFirst { ins ->
            ins.opcode == Opcode.IPUT_BOOLEAN &&
                ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == autoAdvanceFieldName
        }
        if (autoAdvanceIputIndex < 0) {
            error("[Control Video Auto Scroll] iput-boolean for '$autoAdvanceFieldName' not found in '$targetClassDescriptor' constructor.")
        }

        val autoAdvanceReg = (initInstructions[autoAdvanceIputIndex] as? TwoRegisterInstruction)?.registerA
            ?: error("[Control Video Auto Scroll] Failed to resolve autoAdvance register in constructor.")

        val constIns = if (autoAdvanceReg <= 15) "const/4 v$autoAdvanceReg, 0x0" else "const/16 v$autoAdvanceReg, 0x0"
        initMethod.addInstructions(autoAdvanceIputIndex, constIns)
        patched++

        println("[Control Video Auto Scroll] Applied $patched hooks -> video auto scroll disabled.")
    }
}
