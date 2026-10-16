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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val premiumCheckFingerprint = Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf(
        "feature/premium_basic",
        "feature/twitter_blue_verified",
        "feature/premium_plus",
    ),
)

private val mediaViewerDownloadFingerprint = Fingerprint(
    returnType = "V",
    strings = listOf(
        "offline_videos_download_fallback",
        "video_download",
    ),
)

private val postMenuDownloadFingerprint = Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("video_download"),
)

private val videoTabDownloadFingerprint = Fingerprint(
    returnType = "V",
    strings = listOf("download_video"),
)

@Suppress("unused")
val twitterUnlockVideoDownloadPatch = bytecodePatch(
    name = "Unlock Video Downloads",
    description = "Lets you save videos and GIFs with the app's built-in download option without Premium.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val premiumMethod = premiumCheckFingerprint.method
        val target = MethodSignature(
            definingClass = premiumMethod.definingClass,
            name = premiumMethod.name,
            parameterTypes = premiumMethod.parameterTypes.map { it.toString() },
            returnType = premiumMethod.returnType,
        )

        var patched = 0

        val callSites = listOf(
            mediaViewerDownloadFingerprint to "media viewer",
            postMenuDownloadFingerprint to "timeline post menu",
            videoTabDownloadFingerprint to "video tab",
        )

        for ((fingerprint, siteLabel) in callSites) {
            patchCallSite(fingerprint.method, siteLabel, target)
            patched++
        }

        println("[Unlock Video Downloads] Applied $patched hooks -> native video download unlocked.")
    }
}

private data class MethodSignature(
    val definingClass: String,
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
)

private fun patchCallSite(
    method: MutableMethod,
    siteLabel: String,
    target: MethodSignature,
) {
    val instructions = method.implementation?.instructions?.toList()
        ?: error("[Unlock Video Downloads] Method for '$siteLabel' has no instructions.")

    val matchingIndices = instructions.indices.filter { i ->
        val ins = instructions[i]
        ins.opcode.name.startsWith("invoke-") &&
            isTargetMethod((ins as? ReferenceInstruction)?.reference as? MethodReference, target)
    }

    if (matchingIndices.size != 1) {
        error("[Unlock Video Downloads] Expected 1 Premium check invoke in '$siteLabel', found ${matchingIndices.size}.")
    }

    val invokeIndex = matchingIndices.single()
    if (invokeIndex + 1 >= instructions.size) {
        error("[Unlock Video Downloads] Premium check invoke in '$siteLabel' is at the end of method instructions.")
    }

    val resultIns = instructions[invokeIndex + 1]
    if (resultIns.opcode != Opcode.MOVE_RESULT) {
        error("[Unlock Video Downloads] Instruction following Premium check invoke in '$siteLabel' is not move-result (was ${resultIns.opcode}).")
    }

    val reg = (resultIns as OneRegisterInstruction).registerA
    val constIns = if (reg <= 15) "const/4 v$reg, 0x1" else "const/16 v$reg, 0x1"
    method.addInstructions(invokeIndex + 2, constIns)
}

private fun isTargetMethod(
    ref: MethodReference?,
    target: MethodSignature,
): Boolean {
    if (ref == null) return false
    return ref.definingClass == target.definingClass &&
        ref.name == target.name &&
        ref.returnType == target.returnType &&
        ref.parameterTypes.map { it.toString() } == target.parameterTypes
}
