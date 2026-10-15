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
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val sharePostIntentFingerprint = Fingerprint(
    strings = listOf(
        "https://x.com/i/trending/",
        "https://x.com/i/lists/",
        "https://x.com/i/status/",
        "image/*",
    ),
)

private val postUrlFingerprint = Fingerprint(
    strings = listOf(
        "https://x.com/unavailable/status/",
        "https://x.com/",
        "/status/",
    ),
)

private val customDomainRegex = Regex("^https?://[A-Za-z0-9.-]+(:[0-9]+)?(/.*)?$")

@Suppress("unused")
val twitterShareLinksPatch = bytecodePatch(
    name = "Share Links",
    description = "Customizes domain and format for shared post links (for opening custom links in app, see Custom Deep Links).",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val customSharingDomain by booleanOption(
        key = "customSharingDomain",
        default = true,
        title = "Custom Sharing Domain",
        description = "Replace x.com with the selected custom domain when sharing post links.",
        required = false,
    )

    val sharingDomain by stringOption(
        key = "sharingDomain",
        default = "https://${DEFAULT_CUSTOM_LINK_HOSTS[2]}",
        values = mapOf(
            "FixTweet (${DEFAULT_CUSTOM_LINK_HOSTS[2]})" to "https://${DEFAULT_CUSTOM_LINK_HOSTS[2]}",
            "VXTwitter (${DEFAULT_CUSTOM_LINK_HOSTS[0]})" to "https://${DEFAULT_CUSTOM_LINK_HOSTS[0]}",
            "FixUpX (${DEFAULT_CUSTOM_LINK_HOSTS[3]})" to "https://${DEFAULT_CUSTOM_LINK_HOSTS[3]}",
        ),
        title = "Sharing Domain",
        description = "Domain replacing x.com when sharing post links, e.g. https://fxtwitter.com.",
        required = false,
    )

    val legacyShareLinks by booleanOption(
        key = "legacyShareLinks",
        default = true,
        title = "Legacy Share Links",
        description = "Include post author username in shared links instead of the modern generic status format.",
        required = false,
    )

    execute {
        var patched = 0

        if (customSharingDomain ?: true) {
            val normalizedDomain = (sharingDomain ?: "https://fxtwitter.com").trim().trimEnd('/')
            if (normalizedDomain.isBlank()) {
                error("[Share Links] Invalid custom domain '$normalizedDomain': domain must not be blank.")
            }
            if (normalizedDomain.any { it == '"' || it == '\\' || it.isWhitespace() || it.isISOControl() }) {
                error("[Share Links] Invalid custom domain '$normalizedDomain': domain contains invalid characters.")
            }
            if (!customDomainRegex.matches(normalizedDomain)) {
                error("[Share Links] Invalid custom domain '$normalizedDomain': domain does not match valid URL format.")
            }

            val statusReplacement = "$normalizedDomain/i/status/"
            val postReplacement = "$normalizedDomain/"

            // 1. Replace share link fallback in sharePostIntent dispatcher (com.x.share.impl.x.c)
            val shareMethod = sharePostIntentFingerprint.method
            val shareInstructions = shareMethod.implementation?.instructions?.toList()
                ?: error("[Share Links] Method sharePostIntent has no instructions.")

            val statusIndex = shareInstructions.indexOfFirst { ins ->
                (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) &&
                    ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string == "https://x.com/i/status/"
            }
            if (statusIndex < 0) {
                error("[Share Links] 'https://x.com/i/status/' instruction not found in sharePostIntent.")
            }

            val statusReg = (shareInstructions[statusIndex] as? OneRegisterInstruction)?.registerA
                ?: error("[Share Links] Failed to resolve register for share status string.")

            shareMethod.replaceInstruction(statusIndex, "const-string v$statusReg, \"$statusReplacement\"")
            patched++

            // 2. Replace post URL prefix in canonical post model (com.x.models.u5.getUrl)
            val urlMethod = postUrlFingerprint.method
            val urlInstructions = urlMethod.implementation?.instructions?.toList()
                ?: error("[Share Links] Method postUrl has no instructions.")

            val xComIndex = urlInstructions.indexOfFirst { ins ->
                (ins.opcode == Opcode.CONST_STRING || ins.opcode == Opcode.CONST_STRING_JUMBO) &&
                    ((ins as? ReferenceInstruction)?.reference as? StringReference)?.string == "https://x.com/"
            }
            if (xComIndex < 0) {
                error("[Share Links] 'https://x.com/' instruction not found in postUrl.")
            }

            val xComReg = (urlInstructions[xComIndex] as? OneRegisterInstruction)?.registerA
                ?: error("[Share Links] Failed to resolve register for post url string.")

            urlMethod.replaceInstruction(xComIndex, "const-string v$xComReg, \"$postReplacement\"")
            patched++
        } else {
            println("[Share Links] Skipped customSharingDomain: disabled by patch option.")
        }

        if (legacyShareLinks ?: true) {
            println("[Share Links] Skipped legacyShareLinks: post author username in share links was removed upstream post-11.4x; no local gate reproduced.")
        }

        if (patched > 0) {
            println("[Share Links] Applied $patched hooks -> custom sharing domain configured.")
        } else {
            println("[Share Links] Applied 0 hooks -> options skipped or disabled.")
        }
    }
}
