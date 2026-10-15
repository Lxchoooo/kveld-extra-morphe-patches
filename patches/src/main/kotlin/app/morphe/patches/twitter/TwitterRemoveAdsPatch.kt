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
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.wrapWithScratchRegisters

private val urtItemPresenterFingerprint = Fingerprint(
    definingClass = "Lcom/x/urt/b;",
    name = "a",
    returnType = "Lcom/x/presenter/a;",
)

private val postDetailPresenterFingerprint = Fingerprint(
    definingClass = "Lcom/x/postdetail/m;",
    name = "v",
    returnType = "Lcom/x/presenter/a;",
    parameters = listOf(
        "I",
        "Lcom/x/models/timelines/items/p0;",
    ),
)

private val googleMobileAdsInitFingerprint = Fingerprint(
    strings = listOf("DefaultMobileAdsInitializer"),
)

private val quickPromoteTrayFingerprint = Fingerprint(
    definingClass = "Lcom/x/urt/items/post/quickpromote/b;",
    name = "a",
    returnType = "V",
)

@Suppress("unused")
val twitterRemoveAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes promoted posts, trends, and ads from timeline feeds.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        var patched = 0

        val adItemTestSmali = """
            :rmad_cond_check_item
            instance-of v1, v0, Lcom/x/models/timelines/items/l1;
            if-nez v1, :rmad_cond_check_post
            goto :rmad_cond_not_post

            :rmad_cond_check_post
            move-object v1, v0
            check-cast v1, Lcom/x/models/timelines/items/l1;
            invoke-virtual {v1}, Lcom/x/models/timelines/items/l1;->y()Lcom/x/models/df;
            move-result-object v1
            if-nez v1, :rmad_cond_suppress_ad

            :rmad_cond_not_post
            instance-of v1, v0, Lcom/x/models/timelines/items/a2;
            if-nez v1, :rmad_cond_check_trend
            goto :rmad_cond_not_trend

            :rmad_cond_check_trend
            move-object v1, v0
            check-cast v1, Lcom/x/models/timelines/items/a2;
            iget-object v1, v1, Lcom/x/models/timelines/items/a2;->a:Lcom/x/models/sf;
            if-eqz v1, :rmad_cond_not_trend
            invoke-virtual {v1}, Lcom/x/models/sf;->b()Lcom/x/models/df;
            move-result-object v1
            if-nez v1, :rmad_cond_suppress_ad

            :rmad_cond_not_trend
            instance-of v1, v0, Lcom/x/models/timelines/items/r1;
            if-nez v1, :rmad_cond_check_rtb
            goto :rmad_cond_not_rtb

            :rmad_cond_check_rtb
            move-object v1, v0
            check-cast v1, Lcom/x/models/timelines/items/r1;
            iget-object v1, v1, Lcom/x/models/timelines/items/r1;->j:Lcom/x/models/df;
            if-nez v1, :rmad_cond_suppress_ad

            :rmad_cond_not_rtb
            instance-of v1, v0, Lcom/x/models/timelines/items/d2;
            if-nez v1, :rmad_cond_check_user
            goto :rmad_cond_not_user

            :rmad_cond_check_user
            move-object v1, v0
            check-cast v1, Lcom/x/models/timelines/items/d2;
            iget-object v1, v1, Lcom/x/models/timelines/items/d2;->h:Lcom/x/models/df;
            if-nez v1, :rmad_cond_suppress_ad

            :rmad_cond_not_user
            instance-of v1, v0, Lcom/x/models/timelines/items/n0;
            if-nez v1, :rmad_cond_check_event
            goto :rmad_cond_not_event

            :rmad_cond_check_event
            move-object v1, v0
            check-cast v1, Lcom/x/models/timelines/items/n0;
            iget-object v1, v1, Lcom/x/models/timelines/items/n0;->a:Lcom/x/models/l2;
            if-eqz v1, :rmad_cond_not_event
            invoke-virtual {v1}, Lcom/x/models/l2;->a()Lcom/x/models/df;
            move-result-object v1
            if-nez v1, :rmad_cond_suppress_ad

            :rmad_cond_not_event
            invoke-interface {v0}, Lcom/x/models/timelines/items/p0;->c()Ljava/lang/String;
            move-result-object v1
            if-eqz v1, :rmad_cond_continue

            :rmad_cond_check_entry_id
            const-string v2, "promoted"
            invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
            move-result v1
            if-nez v1, :rmad_cond_suppress_ad
            goto :rmad_cond_continue

            :rmad_cond_suppress_ad
            sget-object v0, Lcom/x/urt/e0;->a:Lcom/x/urt/e0;
            return-object v0
        """.trimIndent()

        val inlineSmali = """
            move-object/from16 v0, p2
            if-nez v0, :rmad_cond_check_item
            goto :rmad_cond_continue

            $adItemTestSmali

            :rmad_cond_continue
            nop
        """.trimIndent()

        // 1. Intercept timeline items before presenter dispatch in com.x.urt.b.a
        urtItemPresenterFingerprint.method.addInstructionsWithLabels(0, inlineSmali)
        patched++

        // 2. Intercept thread reply items before presenter dispatch in com.x.postdetail.m.v
        val wrapperSmali = """
            move-object/from16 v0, p2
            if-nez v0, :rmad_cond_check_item
            goto :rmad_cond_continue

            $adItemTestSmali

            :rmad_cond_continue
            invoke-virtual {p0, p1, p2}, Lcom/x/postdetail/m;->morphePostDetailPresenterRemoveAds(ILcom/x/models/timelines/items/p0;)Lcom/x/presenter/a;
            move-result-object v0
            return-object v0
        """.trimIndent()

        val wrapped = postDetailPresenterFingerprint.method.wrapWithScratchRegisters(
            mutableClassDefBy(postDetailPresenterFingerprint.method.definingClass),
            "morphePostDetailPresenterRemoveAds",
            3,
            wrapperSmali,
        )
        if (!wrapped) postDetailPresenterFingerprint.method.addInstructionsWithLabels(0, inlineSmali)
        patched++

        // 3. Short-circuit Google Mobile Ads SDK initialization
        googleMobileAdsInitFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """.trimIndent(),
        )
        patched++

        // 4. Suppress QuickPromote booster composable surface
        quickPromoteTrayFingerprint.method.addInstructions(
            0,
            "return-void",
        )
        patched++

        println("[Remove Ads] Applied $patched hooks -> promoted content and timeline ads suppressed.")
    }
}
