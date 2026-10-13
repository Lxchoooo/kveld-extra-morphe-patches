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
import app.morphe.patcher.LiteralFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private enum class ResourceType {
    INTEGER,
    STRING,
}

private val resourceIds = mapOf(
    "abbr_number_divider_billions" to 0x7f0b0000L,
    "abbr_number_divider_millions" to 0x7f0b0001L,
    "abbr_number_divider_thousands" to 0x7f0b0002L,
    "abbr_number_unit_billions" to 0x7f130024L,
    "abbr_number_unit_millions" to 0x7f130025L,
    "abbr_number_unit_thousands" to 0x7f130026L,
)

private fun resourceLiteral(type: ResourceType, name: String): LiteralFilter {
    return literal(
        literal = {
            resourceIds[name] ?: error("[Round Off Numbers] Unknown resource literal: $name")
        },
    )
}

private val roundOffNumbersFingerprint = Fingerprint(
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.INTEGER, "abbr_number_divider_billions"),
        resourceLiteral(ResourceType.INTEGER, "abbr_number_divider_millions"),
        resourceLiteral(ResourceType.INTEGER, "abbr_number_divider_thousands"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_billions"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_millions"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_thousands"),
    ),
)

@Suppress("unused")
val twitterRoundOffNumbersPatch = bytecodePatch(
    name = "Round Off Numbers",
    description = "Enable or disable rounding off numbers",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val showFullNumbers by booleanOption(
        key = "showFullNumbers",
        default = true,
        title = "Show Full Numbers",
        description = "Display full numbers without abbreviation (e.g. 1,234 instead of 1.2K)",
        required = false,
    )

    execute {
        if (!(showFullNumbers ?: true)) {
            println("[Round Off Numbers] Skipped showFullNumbers: disabled by patch option.")
            return@execute
        }

        val enclosingClass = roundOffNumbersFingerprint.classDef
        val formatMethod = enclosingClass.methods.firstOrNull { method ->
            method.returnType == "Ljava/lang/String;" &&
                method.parameterTypes == listOf("J", "Ljava/lang/Integer;")
        } ?: error("[Round Off Numbers] Format method not found in ${enclosingClass.type}.")

        val instructions = formatMethod.instructions
        val sizeInvokeIndex = instructions.indexOfFirst { ins ->
            ins.opcode.name.startsWith("invoke-") &&
                ((ins as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
                    ref.name == "size" && ref.returnType == "I" && ref.parameterTypes.isEmpty()
                } == true
        }
        if (sizeInvokeIndex < 0 || sizeInvokeIndex + 1 >= instructions.size) {
            error("[Round Off Numbers] Collection.size() instruction not found in ${formatMethod.name}.")
        }

        val moveResultIns = instructions[sizeInvokeIndex + 1]
        if (moveResultIns.opcode != Opcode.MOVE_RESULT) {
            error("[Round Off Numbers] Expected MOVE_RESULT after size() at index ${sizeInvokeIndex + 1}, found ${moveResultIns.opcode}.")
        }

        val reg = (moveResultIns as OneRegisterInstruction).registerA
        val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"

        formatMethod.addInstructions(sizeInvokeIndex + 2, constIns)

        println("[Round Off Numbers] Applied 1 hook -> forced units size to 0 in v$reg.")
    }
}
