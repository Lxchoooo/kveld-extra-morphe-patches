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
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.sharedExtensionPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private val customFontHookFingerprint = Fingerprint(
    definingClass = "emoji2/text",
    strings = listOf("end should be < than charSequence length"),
)

@Suppress("unused")
val twitterCustomFontPatch = bytecodePatch(
    name = "Custom Fonts",
    description = "Applies custom typography to post and profile text.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(sharedExtensionPatch)

    val fontTtfPath by stringOption(
        key = "fontTtfPath",
        title = "Custom Font Path",
        description = "Absolute device path to a TTF font applied to post and profile text, e.g. /sdcard/fonts/myfont.ttf.",
        default = "",
        required = false,
    )

    val emojiFontTtfPath by stringOption(
        key = "emojiFontTtfPath",
        title = "Custom Emoji Font Path",
        description = "Absolute device path to a TTF font applied to emojis, e.g. /sdcard/fonts/myemojifont.ttf.",
        default = "",
        required = false,
    )

    execute {
        val fontPath = validateTtfPath(fontTtfPath, "fontTtfPath")
        val emojiFontPath = validateTtfPath(emojiFontTtfPath, "emojiFontTtfPath")

        if (fontPath == null && emojiFontPath == null) {
            println("[Custom Fonts] Skipped fontTtfPath: disabled by patch option.")
            println("[Custom Fonts] Skipped emojiFontTtfPath: disabled by patch option.")
            return@execute
        }

        if (fontPath == null) {
            println("[Custom Fonts] Skipped fontTtfPath: disabled by patch option.")
        }
        if (emojiFontPath == null) {
            println("[Custom Fonts] Skipped emojiFontTtfPath: disabled by patch option.")
        }

        val method = customFontHookFingerprint.method
        val impl = method.implementation
            ?: error("[Custom Fonts] Target method has no implementation.")

        val instructions = impl.instructions.toList()
        val invokeInterface = instructions.firstOrNull { it.opcode == Opcode.INVOKE_INTERFACE } as? Instruction35c
            ?: error("[Custom Fonts] Target method has no invoke-interface instruction.")

        val charSeqReg = invokeInterface.registerC
        val totalRegisters = impl.registerCount
        val paramCount = getParameterRegisterCount(method)
        val paramStart = totalRegisters - paramCount

        checkRegisterCapacity(paramStart, charSeqReg, totalRegisters)

        val smali = buildSmaliHook(charSeqReg, fontPath ?: "", emojiFontPath ?: "")
        method.addInstructions(0, smali)

        println("[Custom Fonts] Applied 1 hook(s) -> custom font(s) enabled.")
    }
}

private fun validateTtfPath(path: String?, optionName: String): String? {
    if (path.isNullOrEmpty()) return null
    if (path.isBlank()) {
        error("[Custom Fonts] $optionName cannot be blank.")
    }
    if (path.contains('"') || path.contains('\\')) {
        error("[Custom Fonts] $optionName contains invalid characters.")
    }
    if (path.any { it.isWhitespace() || it.isISOControl() }) {
        error("[Custom Fonts] $optionName cannot contain whitespace or control characters: '$path'.")
    }
    if (!path.endsWith(".ttf", ignoreCase = true)) {
        error("[Custom Fonts] $optionName must end with .ttf: '$path'.")
    }
    return path
}

private fun getParameterRegisterCount(method: Method): Int {
    val isStatic = AccessFlags.STATIC.isSet(method.accessFlags)
    var count = if (isStatic) 0 else 1
    for (paramType in method.parameterTypes) {
        count += if (paramType == "J" || paramType == "D") 2 else 1
    }
    return count
}

private fun checkRegisterCapacity(paramStart: Int, charSeqReg: Int, totalRegisters: Int) {
    if (paramStart < 2 || (charSeqReg > 15 && paramStart < 3)) {
        error("[Custom Fonts] Target method register frame cannot host the call: totalRegisters=$totalRegisters, paramStart=$paramStart, charSeqReg=$charSeqReg")
    }
}

private fun buildSmaliHook(charSeqReg: Int, fontPath: String, emojiFontPath: String): String {
    if (charSeqReg <= 15) {
        return """
            const-string v0, "$fontPath"
            const-string v1, "$emojiFontPath"
            invoke-static {v$charSeqReg, v0, v1}, Lcom/kveld9/morphe/extra/extension/TwitterFontHelper;->process(Ljava/lang/CharSequence;Ljava/lang/String;Ljava/lang/String;)Landroid/text/Spannable;
            move-result-object v$charSeqReg
        """.trimIndent()
    }

    return """
        move-object/16 v2, v$charSeqReg
        const-string v0, "$fontPath"
        const-string v1, "$emojiFontPath"
        invoke-static {v2, v0, v1}, Lcom/kveld9/morphe/extra/extension/TwitterFontHelper;->process(Ljava/lang/CharSequence;Ljava/lang/String;Ljava/lang/String;)Landroid/text/Spannable;
        move-result-object v2
        move-object/16 v$charSeqReg, v2
    """.trimIndent()
}
