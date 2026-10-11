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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

private val badgesFingerprint = Fingerprint(
    strings = listOf("grok_android_tab_badge_enabled"),
)

private val communityNotesFingerprint = Fingerprint(
    strings = listOf("birdwatch_consumption_enabled"),
)

private val fabMenuFingerprint = Fingerprint(
    strings = listOf("android_compose_fab_expansion_enabled"),
)

private val inlineActionBarItemFingerprint = Fingerprint(
    definingClass = "Lcom/x/inlineactionbar/d;",
    name = "invoke",
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
val twitterHideElementsPatch = bytecodePatch(
    name = "Hide Elements",
    description = "Hide badges, buttons, metrics and served UI elements",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideBadges by booleanOption(
        key = "hideBadges",
        default = true,
        title = "Hide Badges",
        description = "Hide badges and unread indicator counters from navigation bar icons",
        required = false,
    )

    val hideBookmark by booleanOption(
        key = "hideBookmark",
        default = true,
        title = "Hide Bookmark",
        description = "Hide bookmark action button in post timeline",
        required = false,
    )

    val hideCommunityBadges by booleanOption(
        key = "hideCommunityBadges",
        default = true,
        title = "Hide Community Badges",
        description = "Hide community badge indicators across user and post labels",
        required = false,
    )

    val hideCommunityNotes by booleanOption(
        key = "hideCommunityNotes",
        default = true,
        title = "Hide Community Notes",
        description = "Hide community notes and birdwatch contributions on posts",
        required = false,
    )

    val hideFab by booleanOption(
        key = "hideFab",
        default = true,
        title = "Hide FAB",
        description = "Hide primary floating action button in timeline",
        required = false,
    )

    val hideFabMenu by booleanOption(
        key = "hideFabMenu",
        default = true,
        title = "Hide FAB Menu",
        description = "Hide secondary action expansion menu on floating action button",
        required = false,
    )

    val hideFollowedBy by booleanOption(
        key = "hideFollowedBy",
        default = true,
        title = "Hide Followed By",
        description = "Hide followed-by social context facepile on timeline posts",
        required = false,
    )

    val hideHiddenReplies by booleanOption(
        key = "hideHiddenReplies",
        default = true,
        title = "Hide Hidden Replies",
        description = "Hide hidden and moderated replies banner entrypoint in conversation threads",
        required = false,
    )

    val hideImmersivePlayer by booleanOption(
        key = "hideImmersivePlayer",
        default = true,
        title = "Hide Immersive Player",
        description = "Hide immersive swipe-up video player surfaces",
        required = false,
    )

    val hidePostMetrics by booleanOption(
        key = "hidePostMetrics",
        default = true,
        title = "Hide Post Metrics",
        description = "Hide like, repost, and reply count metrics on timeline posts",
        required = false,
    )

    val hideViewCount by booleanOption(
        key = "hideViewCount",
        default = true,
        title = "Hide View Count",
        description = "Hide post view count metrics on timeline and post detail",
        required = false,
    )

    execute {
        var patched = 0

        // 1. hideBadges (Piko: Badges)
        if (hideBadges ?: true) {
            if (neutralizeFlag(badgesFingerprint, "grok_android_tab_badge_enabled", "Hide Elements")) {
                patched++
            } else {
                error("[Hide Elements] Failed to neutralize 'grok_android_tab_badge_enabled' flag.")
            }
        } else {
            println("[Hide Elements] Skipped hideBadges: disabled by patch option.")
        }

        // 2. hideBookmark (Piko: Bookmark)
        if (hideBookmark ?: true) {
            println("[Hide Elements] Skipped hideBookmark: bookmark action in timeline is served via URT action entries in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideBookmark: disabled by patch option.")
        }

        // 3. hideCommunityBadges (Piko: Community Badges)
        if (hideCommunityBadges ?: true) {
            println("[Hide Elements] Skipped hideCommunityBadges: community badges are served via URT metadata in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideCommunityBadges: disabled by patch option.")
        }

        // 4. hideCommunityNotes (Piko: Community Notes)
        if (hideCommunityNotes ?: true) {
            if (neutralizeFlag(communityNotesFingerprint, "birdwatch_consumption_enabled", "Hide Elements")) {
                patched++
            } else {
                error("[Hide Elements] Failed to neutralize 'birdwatch_consumption_enabled' flag.")
            }
        } else {
            println("[Hide Elements] Skipped hideCommunityNotes: disabled by patch option.")
        }

        // 5. hideFab (Piko: FAB)
        if (hideFab ?: true) {
            println("[Hide Elements] Skipped hideFab: primary compose FAB scaffold is embedded in root Compose UI in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideFab: disabled by patch option.")
        }

        // 6. hideFabMenu (Piko: FAB Menu)
        if (hideFabMenu ?: true) {
            if (neutralizeFlag(fabMenuFingerprint, "android_compose_fab_expansion_enabled", "Hide Elements")) {
                patched++
            } else {
                error("[Hide Elements] Failed to neutralize 'android_compose_fab_expansion_enabled' flag.")
            }
        } else {
            println("[Hide Elements] Skipped hideFabMenu: disabled by patch option.")
        }

        // 7. hideFollowedBy (Piko: Followed By)
        if (hideFollowedBy ?: true) {
            println("[Hide Elements] Skipped hideFollowedBy: followed-by social context is served via URT timeline models in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideFollowedBy: disabled by patch option.")
        }

        // 8. hideHiddenReplies (Piko: Hidden Replies)
        if (hideHiddenReplies ?: true) {
            println("[Hide Elements] Skipped hideHiddenReplies: hidden replies entrypoint is served via URT conversation models in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideHiddenReplies: disabled by patch option.")
        }

        // 9. hideImmersivePlayer (Piko: Immersive Player)
        if (hideImmersivePlayer ?: true) {
            println("[Hide Elements] Skipped hideImmersivePlayer: immersive video viewer entrypoint is bound to media click dispatch in 12.33; no client feature flag gate.")
        } else {
            println("[Hide Elements] Skipped hideImmersivePlayer: disabled by patch option.")
        }

        // 10. hidePostMetrics (Piko: Post Metrics)
        if (hidePostMetrics ?: true) {
            val method = inlineActionBarItemFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Hide Elements] Method Lcom/x/inlineactionbar/d;->invoke has no instructions.")
            val countedPillIndex = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == "CountedPill"
            }
            if (countedPillIndex >= 0) {
                val countedPillIns = instructions[countedPillIndex] as OneRegisterInstruction
                val targetReg = countedPillIns.registerA
                method.replaceInstruction(
                    countedPillIndex,
                    "sget-object v$targetReg, Lcom/x/inlineactionbar/d1;->Countless:Lcom/x/inlineactionbar/d1;",
                )
                patched++
            } else {
                error("[Hide Elements] Failed to find CountedPill instruction in Lcom/x/inlineactionbar/d;->invoke.")
            }
        } else {
            println("[Hide Elements] Skipped hidePostMetrics: disabled by patch option.")
        }

        // 11. hideViewCount (Piko: View Count)
        if (hideViewCount ?: true) {
            val method = inlineActionBarItemFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Hide Elements] Method Lcom/x/inlineactionbar/d;->invoke has no instructions.")
            val invokeIndex = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.INVOKE_STATIC_RANGE &&
                    ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.name == "j"
            }
            if (invokeIndex >= 0) {
                val invokeIns = instructions[invokeIndex]
                val rangeIns = invokeIns as RegisterRangeInstruction
                val startReg = rangeIns.startRegister
                val endReg = startReg + rangeIns.registerCount - 1
                val methodRef = (invokeIns as ReferenceInstruction).reference as MethodReference
                val methodDescriptor = ReferenceUtil.getMethodDescriptor(methodRef)

                method.removeInstruction(invokeIndex)
                method.addInstructionsWithLabels(
                    invokeIndex,
                    """
                        iget-object v3, v$startReg, Lcom/x/models/l4;->a:Lcom/x/models/w5;
                        sget-object v4, Lcom/x/models/w5;->ViewCount:Lcom/x/models/w5;
                        if-eq v3, v4, :skip_view_count
                        invoke-static/range {v$startReg .. v$endReg}, $methodDescriptor
                        :skip_view_count
                        nop
                    """.trimIndent(),
                )
                patched++
            } else {
                error("[Hide Elements] Failed to find Lcom/google/android/gms/dynamite/d;->j invoke in Lcom/x/inlineactionbar/d;->invoke.")
            }
        } else {
            println("[Hide Elements] Skipped hideViewCount: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Hide Elements] Applied $patched hooks -> UI elements and surfaces hidden.")
        } else {
            println("[Hide Elements] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
