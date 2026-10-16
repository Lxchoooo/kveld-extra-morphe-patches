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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
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

private val autoRefreshWorkerFingerprint = Fingerprint(
    definingClass = "Lcom/x/android/main/helpers/e3;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
)

private val autoRefreshPageCallbackFingerprint = Fingerprint(
    definingClass = "Lcom/arkivanov/decompose/extensions/compose/pages/c;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
)

private val automaticFetchTypes = setOf("AUTO_REFRESH", "VIEWPORT_AWARE_AUTO_REFRESH")

private fun isAutomaticFetchSget(ins: ReferenceInstruction): Boolean {
    val fieldRef = ins.reference as? FieldReference ?: return false
    return fieldRef.type == "Lcom/x/models/timelines/d;" && fieldRef.name in automaticFetchTypes
}

private fun isTimelineFetchInvoke(ins: ReferenceInstruction): Boolean {
    val methodRef = ins.reference as? MethodReference ?: return false
    return methodRef.name == "k" && methodRef.definingClass == "Lcom/x/repositories/urt/r1;"
}

private fun findAutomaticFetchInvokes(method: MutableMethod): List<Int> {
    val instructions = method.implementation?.instructions?.toList()
        ?: error("[Disable Auto Refresh] Target method in '${method.definingClass}' has no instructions.")
    val sgetIndices = instructions.indices.filter { i ->
        instructions[i].opcode == Opcode.SGET_OBJECT &&
            (instructions[i] as? ReferenceInstruction)?.let(::isAutomaticFetchSget) == true
    }
    return sgetIndices.mapNotNull { sgetIndex ->
        ((sgetIndex + 1)..minOf(sgetIndex + 3, instructions.lastIndex)).firstOrNull { i ->
            instructions[i].opcode == Opcode.INVOKE_INTERFACE &&
                (instructions[i] as? ReferenceInstruction)?.let(::isTimelineFetchInvoke) == true
        }
    }.distinct()
}

private fun nopAutomaticFetches(method: MutableMethod, expectedSites: Int): Int {
    val targetInvokeIndices = findAutomaticFetchInvokes(method)
    if (targetInvokeIndices.isEmpty()) {
        error("[Disable Auto Refresh] Automatic timeline fetch call site not found in '${method.definingClass}->${method.name}'.")
    }
    if (targetInvokeIndices.size != expectedSites) {
        error("[Disable Auto Refresh] Expected $expectedSites automatic fetch call sites, found ${targetInvokeIndices.size} in '${method.definingClass}->${method.name}'.")
    }
    targetInvokeIndices.forEach { method.replaceInstruction(it, "nop") }
    return targetInvokeIndices.size
}

@Suppress("unused")
val twitterDisableAutoRefreshPatch = bytecodePatch(
    name = "Disable Auto Refresh",
    description = "Stops the home timeline from automatically jumping to new posts.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        patched += nopAutomaticFetches(autoRefreshDispatcherFingerprint.method, 1)
        patched += nopAutomaticFetches(autoRefreshWorkerFingerprint.method, 2)
        patched += nopAutomaticFetches(autoRefreshPageCallbackFingerprint.method, 2)

        println("[Disable Auto Refresh] Applied $patched hooks -> timeline auto refresh disabled.")
    }
}
