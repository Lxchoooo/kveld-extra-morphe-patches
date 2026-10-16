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
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val autoRefreshDispatcherFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/x/urt/u1;"),
    strings = listOf(
        "x_lite_infra_home_jtt_clear_cache_enabled",
        "home_timeline_navigation_clear_cache_after_manual_jtt_enabled",
    ),
)

@Suppress("unused")
val twitterDisableAutoRefreshPatch = bytecodePatch(
    name = "Disable Auto Refresh",
    description = "Stops the home timeline from automatically jumping to new posts.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val method = autoRefreshDispatcherFingerprint.method
        val instructions = method.implementation?.instructions?.toList()
            ?: error("[Disable Auto Refresh] Target method in '${method.definingClass}' has no instructions.")

        val autoRefreshFieldIndices = instructions.indices.filter { i ->
            val ins = instructions[i]
            ins.opcode == Opcode.SGET_OBJECT &&
                ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.let { fieldRef ->
                    fieldRef.name == "AUTO_REFRESH" && fieldRef.type == "Lcom/x/models/timelines/d;"
                } == true
        }

        val candidateInvokeIndices = autoRefreshFieldIndices.mapNotNull { sgetIndex ->
            val invokeIndex = ((sgetIndex + 1)..minOf(sgetIndex + 3, instructions.lastIndex)).firstOrNull { i ->
                val ins = instructions[i]
                ins.opcode == Opcode.INVOKE_INTERFACE &&
                    ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.let { methodRef ->
                        methodRef.name == "k" && methodRef.definingClass == "Lcom/x/repositories/urt/r1;"
                    } == true
            }
            invokeIndex
        }

        if (candidateInvokeIndices.isEmpty()) {
            error("[Disable Auto Refresh] AUTO_REFRESH fetch invoke-interface call site not found in '${method.definingClass}->${method.name}'.")
        }
        if (candidateInvokeIndices.size > 1) {
            error("[Disable Auto Refresh] Multiple AUTO_REFRESH fetch invoke-interface call sites found (${candidateInvokeIndices.size}) in '${method.definingClass}->${method.name}'.")
        }

        val targetInvokeIndex = candidateInvokeIndices.single()
        method.replaceInstruction(targetInvokeIndex, "nop")
        patched++

        println("[Disable Auto Refresh] Applied $patched hooks -> timeline auto refresh disabled.")
    }
}
