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
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private val dmCallFingerprint = Fingerprint(
    returnType = "Z",
    strings = listOf("xcall_xchat_enabled"),
)

@Suppress("unused")
val twitterDmDebloatPatch = bytecodePatch(
    name = "DM Debloat",
    description = "Hides call buttons and composer actions in direct message conversations.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val hideDmCalls by booleanOption(
        key = "hideDmCalls",
        default = true,
        title = "Hide DM Calls",
        description = "Hide audio and video call buttons in direct message conversations (warning: disables outgoing direct message calls).",
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
