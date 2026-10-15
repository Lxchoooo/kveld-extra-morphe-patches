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

private val grokPostActionFingerprint = Fingerprint(
    returnType = "Z",
    strings = listOf("grok_android_analyze_timelines_enabled"),
)

private val profileGrokImagineFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("grok_android_imagine_cta_profile_enabled"),
)

private val profileGrokHeaderInitFingerprint = Fingerprint(
    name = "<init>",
    strings = listOf("grok_android_imagine_cta_profile_enabled"),
)

private val profileTabsFingerprint = Fingerprint(
    strings = listOf("x_lite_profile_connect_tabs_enabled"),
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
val twitterPostProfileUiPatch = bytecodePatch(
    name = "Post & Profile UI",
    description = "Customizes post action buttons, Grok surfaces, and user profile tabs.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideGrokPostAction by booleanOption(
        key = "hideGrokPostAction",
        default = true,
        title = "Hide Grok Post Action",
        description = "Hide Grok analysis action button on posts (for Grok drawer and navigation tab, see Drawer Items and Navigation UI).",
        required = false,
    )

    val hideProfileGrokButton by booleanOption(
        key = "hideProfileGrokButton",
        default = true,
        title = "Hide Profile Grok Button",
        description = "Hide Grok Imagine action button on profile header banner (for Grok drawer and navigation tab, see Drawer Items and Navigation UI).",
        required = false,
    )

    val profileTabs by booleanOption(
        key = "profileTabs",
        default = true,
        title = "Hide Profile Tabs",
        description = "Hide extra connect and media tabs on user profiles.",
        required = false,
    )

    execute {
        var patched = 0

        // hideGrokPostAction
        if (hideGrokPostAction ?: true) {
            if (neutralizeFlag(grokPostActionFingerprint, "grok_android_analyze_timelines_enabled", "Post & Profile UI")) {
                patched++
            } else {
                error("[Post & Profile UI] Failed to neutralize 'grok_android_analyze_timelines_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped hideGrokPostAction: disabled by patch option.")
        }

        // hideProfileGrokButton
        if (hideProfileGrokButton ?: true) {
            var ok = false

            // 1. Neutralize the Imagine flag evaluation inside the tabbed
            // content dispatcher (do NOT early-return the whole method: it
            // resolves profile header, upsell and repository objects).
            if (neutralizeFlag(profileGrokImagineFingerprint, "grok_android_imagine_cta_profile_enabled", "Post & Profile UI")) {
                patched++
                ok = true
            }

            // 2. In header init, neutralize the flag
            if (neutralizeFlag(profileGrokHeaderInitFingerprint, "grok_android_imagine_cta_profile_enabled", "Post & Profile UI")) {
                patched++
                ok = true
            }

            if (!ok) {
                error("[Post & Profile UI] Failed to neutralize 'grok_android_imagine_cta_profile_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped hideProfileGrokButton: disabled by patch option.")
        }

        // profileTabs
        if (profileTabs ?: true) {
            if (neutralizeFlag(profileTabsFingerprint, "x_lite_profile_connect_tabs_enabled", "Post & Profile UI")) {
                patched++
            } else {
                error("[Post & Profile UI] Failed to neutralize 'x_lite_profile_connect_tabs_enabled' flag.")
            }
        } else {
            println("[Post & Profile UI] Skipped profileTabs: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Post & Profile UI] Applied $patched hooks -> post and profile UI customized.")
        } else {
            println("[Post & Profile UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
