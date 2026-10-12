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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val dmCallFingerprint = Fingerprint(
    strings = listOf("xcall_xchat_enabled"),
)

context(_: BytecodePatchContext)
private fun handleMoveResult(
    method: MutableMethod,
    resultIns: Instruction,
    invokeIndex: Int,
): Boolean {
    val reg = (resultIns as OneRegisterInstruction).registerA
    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
    method.addInstructions(invokeIndex + 2, constIns)
    return true
}

context(_: BytecodePatchContext)
private fun handleMoveResultObject(
    method: MutableMethod,
    resultIns: Instruction,
    invokeIndex: Int,
): Boolean {
    val reg = (resultIns as OneRegisterInstruction).registerA
    method.addInstructions(
        invokeIndex + 2,
        "sget-object v$reg, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;",
    )

    val updatedInstructions = method.implementation?.instructions?.toList() ?: emptyList<Instruction>()
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

context(_: BytecodePatchContext)
private fun handleBranchResult(
    method: MutableMethod,
    instructions: List<Instruction>,
    invokeIndex: Int,
    patchName: String,
    flagKey: String,
): Boolean {
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
    println("[$patchName] Unexpected instruction after invoke for flag '$flagKey'.")
    return false
}

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
    return when (resultIns.opcode) {
        Opcode.MOVE_RESULT -> handleMoveResult(method, resultIns, invokeIndex)
        Opcode.MOVE_RESULT_OBJECT -> handleMoveResultObject(method, resultIns, invokeIndex)
        else -> handleBranchResult(method, instructions, invokeIndex, patchName, flagKey)
    }
}

@Suppress("unused")
val twitterDmDebloatPatch = bytecodePatch(
    name = "DM Debloat",
    description = "Hide call buttons and composer actions in direct message conversations",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideDmCalls by booleanOption(
        key = "hideDmCalls",
        default = true,
        title = "Hide DM Calls",
        description = "Hide audio and video call buttons in direct message conversation header. WARNING: neutralizing the unified xchat calling flag disables DM outgoing-call entrypoints, not just the icons.",
        required = false,
    )

    execute {
        var patched = 0

        // Call buttons (unified xchat calling pipeline hides both together)
        if (hideDmCalls ?: true) {
            if (neutralizeFlag(dmCallFingerprint, "xcall_xchat_enabled", "DM Debloat")) {
                patched++
                println("[DM Debloat] Neutralized 'xcall_xchat_enabled': unified xchat pipeline hides both audio and video call header buttons together.")
            } else {
                error("[DM Debloat] Failed to neutralize 'xcall_xchat_enabled' flag.")
            }
        } else {
            println("[DM Debloat] Skipped call buttons: hideDmCalls disabled by patch option.")
        }

        if (patched > 0) {
            println("[DM Debloat] Applied $patched hooks -> direct message conversation surfaces debloated.")
        } else {
            println("[DM Debloat] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
