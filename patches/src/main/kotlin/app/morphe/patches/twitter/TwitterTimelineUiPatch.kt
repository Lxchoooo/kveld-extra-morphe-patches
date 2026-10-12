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
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.util.ReferenceUtil

private val urtShowInstructionsStateFingerprint = Fingerprint(
    strings = listOf("UrtShowInstructionsState(showInstructions="),
)

private val quickPromoteEligibilityFingerprint = Fingerprint(
    strings = listOf(
        "x_lite_quick_promote_enabled",
        "x_lite_quick_promote_premium_paywall_enabled",
    ),
    returnType = "Z",
)

private val focalPostStateFingerprint = Fingerprint(
    strings = listOf("FocalPostState(timelinePostState="),
)

private val urtItemPresenterFingerprint = Fingerprint(
    definingClass = "Lcom/x/urt/b;",
    name = "a",
    returnType = "Lcom/x/presenter/a;",
    parameters = listOf(
        "I",
        "Lcom/x/models/timelines/items/p0;",
        "Lcom/x/navigation/wl;",
        "Lcom/x/repositories/urt/t1;",
        "Lcom/x/urt/t0;",
    ),
)

private val postDetailPresenterFingerprint = Fingerprint(
    definingClass = "Lcom/x/postdetail/c0;",
    name = "r",
    returnType = "Lcom/x/presenter/a;",
    parameters = listOf(
        "I",
        "Lcom/x/models/timelines/items/p0;",
        "Lcom/x/urt/t0;",
    ),
)

private val profileFollowRecommendationResponseFingerprint = Fingerprint(
    strings = listOf("ProfileFollowRecommendationResponse(style="),
)

private val fleetlineApiResponseFingerprint = Fingerprint(
    strings = listOf("FleetlineApiResponse(threads="),
)

private val communityNotesFingerprint = Fingerprint(
    strings = listOf("birdwatch_consumption_enabled"),
)

private val inlineActionBarItemFingerprint = Fingerprint(
    definingClass = "Lcom/x/inlineactionbar/d;",
    name = "invoke",
)

private val inlineActionsFingerprint = Fingerprint(
    strings = listOf("android_ntab_display_options_inline_actions_enabled"),
)

private val homeTopBarFingerprint = Fingerprint(
    definingClass = "Lcom/x/home/tabbed/a;",
    name = "i",
)

private val homeTabBarPagerFingerprint = Fingerprint(
    definingClass = "Lcom/x/home/tabbed/a;",
    name = "j",
)

private val homeTabBarHeaderFingerprint = Fingerprint(
    definingClass = "Lcom/x/home/tabbed/a;",
    name = "f",
)

private val liveBannerFingerprint = Fingerprint(
    strings = listOf("x_lite_livestream_pill_enabled"),
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
val twitterTimelineUiPatch = bytecodePatch(
    name = "Timeline UI",
    description = "Timeline surfaces: posts, threads, banners, recommendations and video entry points",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideBanner by booleanOption(
        key = "hideBanner",
        default = true,
        title = "Hide Banner",
        description = "Hides new post banner",
        required = false,
    )

    val hidePromoteButton by booleanOption(
        key = "hidePromoteButton",
        default = true,
        title = "Hide Promote Button",
        description = "Hides promote button under self posts",
        required = false,
    )

    val hideNudgeButton by booleanOption(
        key = "hideNudgeButton",
        default = true,
        title = "Hide Nudge Button",
        description = "Hides follow/subscribe/follow back buttons on posts",
        required = false,
    )

    val hideRecommendationItems by booleanOption(
        key = "hideRecommendationItems",
        default = true,
        title = "Hide Recommendation Items",
        description = "Hides recommendation items such as Who to follow and Today's news in timeline, search, and replies",
        required = false,
    )

    val hideDiscoverMore by booleanOption(
        key = "hideDiscoverMore",
        default = true,
        title = "Hide Discover More",
        description = "Hides Discover more recommendations in tweet reply threads",
        required = false,
    )

    val hideRecommendedUsers by booleanOption(
        key = "hideRecommendedUsers",
        default = true,
        title = "Hide Recommended Users",
        description = "Hides recommended users popup shown when following someone",
        required = false,
    )

    val hideLiveThreads by booleanOption(
        key = "hideLiveThreads",
        default = true,
        title = "Hide Live Threads",
        description = "Hides live threads",
        required = false,
    )

    val hideCommunityNotes by booleanOption(
        key = "hideCommunityNotes",
        default = true,
        title = "Hide Community Notes",
        description = "Hide community notes and birdwatch contributions on posts",
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

    val inlineActionBar by booleanOption(
        key = "inlineActionBar",
        default = true,
        title = "Inline Action Bar",
        description = "Toggle inline action buttons across notification timeline",
        required = false,
    )

    val timelineAddTab by booleanOption(
        key = "timelineAddTab",
        default = true,
        title = "Timeline Add Tab",
        description = "Hide add tab (+) button in home timeline tab header",
        required = false,
    )

    val timelineTopBar by booleanOption(
        key = "timelineTopBar",
        default = true,
        title = "Timeline Top Bar",
        description = "Toggle top bar elements in home and custom timelines",
        required = false,
    )

    val hideLiveBanner by booleanOption(
        key = "hideLiveBanner",
        default = true,
        title = "Hide Live Banner",
        description = "Hides live and broadcast pill at top of home timeline",
        required = false,
    )

    execute {
        var patched = 0

        // 1. hideBanner
        if (hideBanner ?: true) {
            val stateMethod = urtShowInstructionsStateFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
                ?: error("[Timeline UI] Constructor not found in '$stateClassDescriptor'.")

            val instructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

            val iputIndex = instructions.indexOfFirst { it.opcode == Opcode.IPUT_OBJECT }
            if (iputIndex < 0) {
                error("[Timeline UI] iput-object instruction not found in '$stateClassDescriptor' constructor.")
            }

            val listReg = (instructions[iputIndex] as? TwoRegisterInstruction)?.registerA ?: 1

            val boolRegs = instructions
                .filter { it.opcode == Opcode.IPUT_BOOLEAN }
                .mapNotNull { (it as? TwoRegisterInstruction)?.registerA }
                .distinct()

            val smali = buildString {
                appendLine("invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;")
                appendLine("move-result-object v$listReg")
                for (reg in boolRegs) {
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    appendLine(constIns)
                }
            }.trimEnd()

            initMethod.addInstructions(iputIndex, smali)
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Banner disabled.")
        }

        // 2. hidePromoteButton
        if (hidePromoteButton ?: true) {
            quickPromoteEligibilityFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """.trimIndent(),
            )
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Promote Button disabled.")
        }

        // 3. hideNudgeButton
        if (hideNudgeButton ?: true) {
            val stateMethod = focalPostStateFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var followFieldName: String? = null
            var subscribeFieldName: String? = null
            var statefulFollowFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    when (str) {
                        ", followButtonState=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                    followFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                        ", isSubscribeEligible=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_BOOLEAN) {
                                    subscribeFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                        ", useStatefulFollowButton=" -> {
                            for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                                val nextIns = toStringInstructions[j]
                                if (nextIns.opcode == Opcode.IGET_BOOLEAN) {
                                    statefulFollowFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (followFieldName == null) {
                error("[Timeline UI] followButtonState field not found in '$stateClassDescriptor'.")
            }
            if (subscribeFieldName == null) {
                error("[Timeline UI] isSubscribeEligible field not found in '$stateClassDescriptor'.")
            }

            val initMethod = stateClass.methods.firstOrNull { it.name == "<init>" }
                ?: error("[Timeline UI] Constructor not found in '$stateClassDescriptor'.")

            val initInstructions = initMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

            val followIputIndex = initInstructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IPUT_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == followFieldName
            }
            if (followIputIndex < 0) {
                error("[Timeline UI] iput-object for '$followFieldName' not found in '$stateClassDescriptor' constructor.")
            }

            val subscribeIputIndex = initInstructions.indexOfFirst { ins ->
                ins.opcode == Opcode.IPUT_BOOLEAN &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == subscribeFieldName
            }
            if (subscribeIputIndex < 0) {
                error("[Timeline UI] iput-boolean for '$subscribeFieldName' not found in '$stateClassDescriptor' constructor.")
            }

            val statefulIputIndex = statefulFollowFieldName?.let { name ->
                initInstructions.indexOfFirst { ins ->
                    ins.opcode == Opcode.IPUT_BOOLEAN &&
                        ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == name
                }
            }

            val subscribeReg = (initInstructions[subscribeIputIndex] as? TwoRegisterInstruction)?.registerA
                ?: error("[Timeline UI] Failed to resolve subscribe register in constructor.")

            // followButtonState intentionally left untouched: zeroing the object
            // field to null crashes post-detail init with an NPE (getClass on null)
            // on flows that reuse the state (e.g. DM-shared tweets). Suppression
            // is achieved via the boolean flags below.
            val targets = mutableListOf(
                subscribeIputIndex to subscribeReg,
            )

            if (statefulIputIndex != null && statefulIputIndex >= 0) {
                val statefulReg = (initInstructions[statefulIputIndex] as? TwoRegisterInstruction)?.registerA
                if (statefulReg != null) {
                    targets.add(statefulIputIndex to statefulReg)
                }
            }

            targets.sortByDescending { it.first }

            for ((index, reg) in targets) {
                val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                initMethod.addInstructions(index, constIns)
                patched++
            }
        } else {
            println("[Timeline UI] Skipped: Hide Nudge Button disabled.")
        }

        // 4. hideRecommendationItems
        if (hideRecommendationItems ?: true) {
            urtItemPresenterFingerprint.method.addInstructionsWithLabels(
                0,
                """
                    move-object/from16 v0, p2
                    if-nez v0, :cond_check_item
                    goto :cond_continue

                    :cond_check_item
                    invoke-interface {v0}, Lcom/x/models/timelines/items/p0;->c()Ljava/lang/String;
                    move-result-object v1
                    if-eqz v1, :cond_continue

                    :cond_check_entry_id
                    const-string v2, "who-to-follow"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "who_to_follow"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "who-to-subscribe"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "stories"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "eventsummary"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "toptabsrpusermodule"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    const-string v2, "community-to-join"
                    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                    move-result v2
                    if-nez v2, :cond_suppress_rec

                    goto :cond_continue

                    :cond_suppress_rec
                    sget-object v0, Lcom/x/urt/b0;->a:Lcom/x/urt/b0;
                    return-object v0

                    :cond_continue
                    nop
                """.trimIndent(),
            )
            patched++
        } else {
            println("[Timeline UI] Skipped: Hide Recommendation Items disabled.")
        }

        // 5. hideDiscoverMore
        if (hideDiscoverMore ?: true) {
            val suppressionSmali = """
                move-object/from16 v0, p2
                if-nez v0, :cond_check_item
                goto :cond_continue

                :cond_check_item
                invoke-interface {v0}, Lcom/x/models/timelines/items/p0;->c()Ljava/lang/String;
                move-result-object v1
                if-eqz v1, :cond_continue

                :cond_check_entry_id
                const-string v2, "discover"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_discover

                const-string v2, "Discover"
                invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v2
                if-nez v2, :cond_suppress_discover

                goto :cond_continue

                :cond_suppress_discover
                sget-object v0, Lcom/x/urt/b0;->a:Lcom/x/urt/b0;
                return-object v0

                :cond_continue
                nop
            """.trimIndent()

            val discBefore = patched
            urtItemPresenterFingerprint.method.addInstructionsWithLabels(0, suppressionSmali)
            patched++

            postDetailPresenterFingerprint.method.addInstructionsWithLabels(0, suppressionSmali)
            patched++

            if (patched == discBefore) {
                error("[Timeline UI] No hooks applied for Hide Discover More.")
            }
        } else {
            println("[Timeline UI] Skipped: Hide Discover More disabled.")
        }

        // 6. hideRecommendedUsers
        if (hideRecommendedUsers ?: true) {
            val stateMethod = profileFollowRecommendationResponseFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var recommendedUsersFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (str == ", recommendedUsers=") {
                        for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                            val nextIns = toStringInstructions[j]
                            if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                recommendedUsersFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                break
                            }
                        }
                    }
                }
            }

            if (recommendedUsersFieldName == null) {
                error("[Timeline UI] recommendedUsers field not found in '$stateClassDescriptor'.")
            }

            val initMethods = stateClass.methods.filter { it.name == "<init>" }
            if (initMethods.isEmpty()) {
                error("[Timeline UI] No constructors found in '$stateClassDescriptor'.")
            }

            var usersPatched = 0
            for (initMethod in initMethods) {
                val initInstructions = initMethod.implementation?.instructions?.toList()
                    ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

                val targets = mutableListOf<Pair<Int, Int>>()
                for ((index, ins) in initInstructions.withIndex()) {
                    if (ins.opcode == Opcode.IPUT_OBJECT) {
                        val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        if (fieldRef?.name == recommendedUsersFieldName) {
                            val reg = (ins as? TwoRegisterInstruction)?.registerA
                                ?: error("[Timeline UI] Failed to resolve value register in constructor.")
                            targets.add(index to reg)
                        }
                    }
                }

                targets.sortByDescending { it.first }

                for ((index, reg) in targets) {
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    initMethod.addInstructions(index, constIns)
                    usersPatched++
                }
            }

            if (usersPatched == 0) {
                error("[Timeline UI] No iput-object instructions found for '$recommendedUsersFieldName' in '$stateClassDescriptor' constructors.")
            }
            patched += usersPatched
        } else {
            println("[Timeline UI] Skipped: Hide Recommended Users disabled.")
        }

        // 7. hideLiveThreads
        if (hideLiveThreads ?: true) {
            val stateMethod = fleetlineApiResponseFingerprint.method
            val stateClassDescriptor = stateMethod.definingClass
            val stateClass = mutableClassDefBy(stateClassDescriptor)

            val toStringMethod = stateClass.methods.firstOrNull { it.name == "toString" }
                ?: error("[Timeline UI] toString not found in '$stateClassDescriptor'.")

            val toStringInstructions = toStringMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] toString in '$stateClassDescriptor' has no instructions.")

            var threadsFieldName: String? = null

            for (i in toStringInstructions.indices) {
                val ins = toStringInstructions[i]
                if (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) {
                    val str = ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string
                    if (str == "FleetlineApiResponse(threads=") {
                        for (j in (i + 1)..minOf(i + 5, toStringInstructions.lastIndex)) {
                            val nextIns = toStringInstructions[j]
                            if (nextIns.opcode == Opcode.IGET_OBJECT) {
                                threadsFieldName = ((nextIns as? ReferenceInstruction)?.reference as? FieldReference)?.name
                                break
                            }
                        }
                    }
                }
            }

            if (threadsFieldName == null) {
                error("[Timeline UI] threads field not found in '$stateClassDescriptor'.")
            }

            val initMethods = stateClass.methods.filter { it.name == "<init>" }
            if (initMethods.isEmpty()) {
                error("[Timeline UI] No constructors found in '$stateClassDescriptor'.")
            }

            var threadsPatched = 0
            for (initMethod in initMethods) {
                val initInstructions = initMethod.implementation?.instructions?.toList()
                    ?: error("[Timeline UI] Constructor in '$stateClassDescriptor' has no instructions.")

                val targets = mutableListOf<Pair<Int, Int>>()
                for ((index, ins) in initInstructions.withIndex()) {
                    if (ins.opcode == Opcode.IPUT_OBJECT) {
                        val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                        if (fieldRef?.name == threadsFieldName) {
                            val reg = (ins as? TwoRegisterInstruction)?.registerA
                                ?: error("[Timeline UI] Failed to resolve value register in constructor.")
                            targets.add(index to reg)
                        }
                    }
                }

                targets.sortByDescending { it.first }

                for ((index, reg) in targets) {
                    val smali = """
                        invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
                        move-result-object v$reg
                    """.trimIndent()
                    initMethod.addInstructions(index, smali)
                    threadsPatched++
                }
            }

            if (threadsPatched == 0) {
                error("[Timeline UI] No iput-object instructions found for '$threadsFieldName' in '$stateClassDescriptor' constructors.")
            }
            patched += threadsPatched
        } else {
            println("[Timeline UI] Skipped: Hide Live Threads disabled.")
        }

        // 9. hideCommunityNotes
        if (hideCommunityNotes ?: true) {
            if (neutralizeFlag(communityNotesFingerprint, "birdwatch_consumption_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'birdwatch_consumption_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped hideCommunityNotes: disabled by patch option.")
        }

        // 13. hidePostMetrics
        if (hidePostMetrics ?: true) {
            val method = inlineActionBarItemFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method Lcom/x/inlineactionbar/d;->invoke has no instructions.")
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
                error("[Timeline UI] Failed to find CountedPill instruction in Lcom/x/inlineactionbar/d;->invoke.")
            }
        } else {
            println("[Timeline UI] Skipped hidePostMetrics: disabled by patch option.")
        }

        // 14. hideViewCount
        if (hideViewCount ?: true) {
            val method = inlineActionBarItemFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method Lcom/x/inlineactionbar/d;->invoke has no instructions.")
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
                error("[Timeline UI] Failed to find Lcom/google/android/gms/dynamite/d;->j invoke in Lcom/x/inlineactionbar/d;->invoke.")
            }
        } else {
            println("[Timeline UI] Skipped hideViewCount: disabled by patch option.")
        }

        // 15. inlineActionBar
        if (inlineActionBar ?: true) {
            if (neutralizeFlag(inlineActionsFingerprint, "android_ntab_display_options_inline_actions_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'android_ntab_display_options_inline_actions_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped inlineActionBar: disabled by patch option.")
        }

        // 17. timelineAddTab
        if (timelineAddTab ?: true) {
            val pagerMethod = homeTabBarPagerFingerprint.method
            val pagerInstructions = pagerMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method Lcom/x/home/tabbed/a;->j has no instructions.")
            val pagerConstIdx = pagerInstructions.indexOfFirst { ins ->
                (ins as? NarrowLiteralInstruction)?.narrowLiteral == -585845020
            }
            if (pagerConstIdx > 0) {
                val ifEqzIdx = pagerInstructions.subList(0, pagerConstIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0 && pagerConstIdx - ifEqzIdx <= 3) {
                    val reg = (pagerInstructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    pagerMethod.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before const group in Lcom/x/home/tabbed/a;->j.")
                }
            } else {
                error("[Timeline UI] Target group constant -585845020 not found in Lcom/x/home/tabbed/a;->j.")
            }

            val headerMethod = homeTabBarHeaderFingerprint.method
            val headerInstructions = headerMethod.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method Lcom/x/home/tabbed/a;->f has no instructions.")
            val headerConstIdx = headerInstructions.indexOfFirst { ins ->
                (ins as? NarrowLiteralInstruction)?.narrowLiteral == -868946867
            }
            if (headerConstIdx > 0) {
                val ifEqzIdx = headerInstructions.subList(0, headerConstIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0 && headerConstIdx - ifEqzIdx <= 3) {
                    val reg = (headerInstructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    headerMethod.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before const group in Lcom/x/home/tabbed/a;->f.")
                }
            } else {
                error("[Timeline UI] Target group constant -868946867 not found in Lcom/x/home/tabbed/a;->f.")
            }
        } else {
            println("[Timeline UI] Skipped timelineAddTab: disabled by patch option.")
        }

        // 18. timelineTopBar
        if (timelineTopBar ?: true) {
            val method = homeTopBarFingerprint.method
            val instructions = method.implementation?.instructions?.toList()
                ?: error("[Timeline UI] Method Lcom/x/home/tabbed/a;->i has no instructions.")
            val followButtonFieldIdx = instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                        it.definingClass == "Lcom/x/home/tabbed/a;" && it.name == "d"
                    } == true
            }
            if (followButtonFieldIdx > 0) {
                val ifEqzIdx = instructions.subList(0, followButtonFieldIdx).indexOfLast {
                    it.opcode == Opcode.IF_EQZ
                }
                if (ifEqzIdx >= 0) {
                    val reg = (instructions[ifEqzIdx] as OneRegisterInstruction).registerA
                    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
                    method.addInstructions(ifEqzIdx, constIns)
                    patched++
                } else {
                    error("[Timeline UI] Preceding if-eqz branch not found before follow button field in Lcom/x/home/tabbed/a;->i.")
                }
            } else {
                error("[Timeline UI] Follow button field Lcom/x/home/tabbed/a;->d not found in Lcom/x/home/tabbed/a;->i.")
            }
        } else {
            println("[Timeline UI] Skipped timelineTopBar: disabled by patch option.")
        }

        // 20. hideLiveBanner
        if (hideLiveBanner ?: true) {
            if (neutralizeFlag(liveBannerFingerprint, "x_lite_livestream_pill_enabled", "Timeline UI")) {
                patched++
            } else {
                error("[Timeline UI] Failed to neutralize 'x_lite_livestream_pill_enabled' flag.")
            }
        } else {
            println("[Timeline UI] Skipped hideLiveBanner: disabled by patch option.")
        }

        if (patched > 0) {
            println("[Timeline UI] Applied $patched hooks -> timeline surfaces customized.")
        } else {
            println("[Timeline UI] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
