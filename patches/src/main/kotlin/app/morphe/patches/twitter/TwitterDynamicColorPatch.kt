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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private val composeThemeSwitchFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("com.x.composer.autocomplete.SuggestionsDrawerState.Collapsed"),
)

@Suppress("unused")
val twitterDynamicColorPatch = bytecodePatch(
    name = "Dynamic Color",
    description = "Replaces the default Twitter Blue with the user's Material You palette.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(sharedExtensionPatch)

    execute {
        val classDescriptor = composeThemeSwitchFingerprint.method.definingClass
        val classDef = mutableClassDefBy(classDescriptor)
        val method = classDef.methods.firstOrNull {
            it.name == "invoke" && it.parameterTypes.isEmpty()
        } ?: run {
            println("[Dynamic Color] Skipped: invoke() method not found in $classDescriptor.")
            return@execute
        }

        val instructions = method.implementation?.instructions?.toList() ?: run {
            println("[Dynamic Color] Skipped: no implementation in invoke().")
            return@execute
        }

        // Locate the 3 HorizonThemeColors (b) constructor instantiations
        val themeConstructors = mutableListOf<Int>()
        for ((index, ins) in instructions.withIndex()) {
            if (ins.opcode == Opcode.NEW_INSTANCE) {
                val ref = (ins as? ReferenceInstruction)?.reference as? TypeReference
                if (ref?.type?.contains("compose/theme/b") == true) {
                    themeConstructors.add(index)
                }
            }
        }

        if (themeConstructors.size != 3) {
            println("[Dynamic Color] Skipped: Expected 3 theme constructions in $classDescriptor, found ${themeConstructors.size}.")
            return@execute
        }

        // triple of (insertionIndex, smaliCode, argOverrideCount)
        val insertions = mutableListOf<Triple<Int, String, Int>>()

        val case1Start = themeConstructors[0]
        val case2Start = themeConstructors[1]
        val case3Start = themeConstructors[2]
        val case3End = instructions.size

        // Case 1: Lights-Out (black backgrounds, dynamic primary accent and unread container)
        // Uses scratch v0 for accent, and v2 for unread container (v2 is dead throughout Case 1)
        for (i in case1Start until case2Start) {
            val ins = instructions[i]
            if (ins.opcode == Opcode.SGET_WIDE) {
                val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                if (fieldRef?.name == "J1") {
                    val reg = (ins as OneRegisterInstruction).registerA
                    val smali = """
                        invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accent()I
                        move-result v0
                        invoke-static {v0}, Landroidx/compose/ui/graphics/n0;->c(I)J
                        move-result-wide v0
                        move-wide/from16 v$reg, v0
                    """.trimIndent()
                    // Replaces primary (v6) and link (v16, copied from v6 at 0188)
                    insertions.add(Triple(i + 1, smali, 2))
                }
            } else if (ins.opcode == Opcode.CONST_WIDE) {
                val lit = (ins as? WideLiteralInstruction)?.wideLiteral?.and(0xFFFFFFFFL)
                if (lit == 0x051D2BL || lit == 0xFF051D2BL) {
                    // Subsequent move-result-wide holds unread container
                    for (j in i + 1 until (i + 5).coerceAtMost(case2Start)) {
                        val subIns = instructions[j]
                        if (subIns.opcode == Opcode.MOVE_RESULT_WIDE) {
                            val reg = (subIns as OneRegisterInstruction).registerA
                            val smali = """
                                invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accentContainer()I
                                move-result v2
                                invoke-static {v2}, Landroidx/compose/ui/graphics/n0;->c(I)J
                                move-result-wide v2
                                move-wide/from16 v$reg, v2
                            """.trimIndent()
                            insertions.add(Triple(j + 1, smali, 1))
                            break
                        }
                    }
                }
            }
        }

        // Case 2: Dim (navy FF15202B replaced with dynamic dark surface, accent with Monet)
        // Uses scratch v3 (dead throughout Case 2)
        for (i in case2Start until case3Start) {
            val ins = instructions[i]
            if (ins.opcode == Opcode.SGET_WIDE) {
                val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                if (fieldRef?.name == "J1") {
                    val reg = (ins as OneRegisterInstruction).registerA
                    val smali = """
                        invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accent()I
                        move-result v3
                        invoke-static {v3}, Landroidx/compose/ui/graphics/n0;->c(I)J
                        move-result-wide v3
                        move-wide/from16 v$reg, v3
                    """.trimIndent()
                    // Replaces primary (v7) and link (v17, copied from v7 at 01fa)
                    insertions.add(Triple(i + 1, smali, 2))
                }
            } else if (ins.opcode == Opcode.CONST_WIDE) {
                val lit = (ins as? WideLiteralInstruction)?.wideLiteral?.and(0xFFFFFFFFL)
                when (lit) {
                    0x15202BL, 0xFF15202BL -> {
                        // Cell background and app background (copied from v21 at 01fc)
                        for (j in i + 1 until (i + 5).coerceAtMost(case3Start)) {
                            val subIns = instructions[j]
                            if (subIns.opcode == Opcode.MOVE_RESULT_WIDE) {
                                val reg = (subIns as OneRegisterInstruction).registerA
                                val smali = """
                                    invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->dimSurface()I
                                    move-result v3
                                    invoke-static {v3}, Landroidx/compose/ui/graphics/n0;->c(I)J
                                    move-result-wide v3
                                    move-wide/from16 v$reg, v3
                                """.trimIndent()
                                insertions.add(Triple(j + 1, smali, 2))
                                break
                            }
                        }
                    }
                    0xBF15202BL -> {
                        // Cell background translucent
                        for (j in i + 1 until (i + 5).coerceAtMost(case3Start)) {
                            val subIns = instructions[j]
                            if (subIns.opcode == Opcode.MOVE_RESULT_WIDE) {
                                val reg = (subIns as OneRegisterInstruction).registerA
                                val smali = """
                                    invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->dimSurfaceTranslucent()I
                                    move-result v3
                                    invoke-static {v3}, Landroidx/compose/ui/graphics/n0;->c(I)J
                                    move-result-wide v3
                                    move-wide/from16 v$reg, v3
                                """.trimIndent()
                                insertions.add(Triple(j + 1, smali, 1))
                                break
                            }
                        }
                    }
                    0x163043L, 0xFF163043L -> {
                        // Unread badge container
                        for (j in i + 1 until (i + 5).coerceAtMost(case3Start)) {
                            val subIns = instructions[j]
                            if (subIns.opcode == Opcode.MOVE_RESULT_WIDE) {
                                val reg = (subIns as OneRegisterInstruction).registerA
                                val smali = """
                                    invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accentContainer()I
                                    move-result v3
                                    invoke-static {v3}, Landroidx/compose/ui/graphics/n0;->c(I)J
                                    move-result-wide v3
                                    move-wide/from16 v$reg, v3
                                """.trimIndent()
                                insertions.add(Triple(j + 1, smali, 1))
                                break
                            }
                        }
                    }
                    0xCC15202BL -> {
                        // Glass background
                        for (j in i + 1 until (i + 5).coerceAtMost(case3Start)) {
                            val subIns = instructions[j]
                            if (subIns.opcode == Opcode.MOVE_RESULT_WIDE) {
                                val reg = (subIns as OneRegisterInstruction).registerA
                                val smali = """
                                    invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->dimSurfaceGlass()I
                                    move-result v3
                                    invoke-static {v3}, Landroidx/compose/ui/graphics/n0;->c(I)J
                                    move-result-wide v3
                                    move-wide/from16 v$reg, v3
                                """.trimIndent()
                                insertions.add(Triple(j + 1, smali, 1))
                                break
                            }
                        }
                    }
                }
            }
        }

        // Case 3: Light (standard light palette with dynamic primary, link, and unread container)
        // Uses scratch v4 (dead throughout Case 3)
        for (i in case3Start until case3End) {
            val ins = instructions[i]
            if (ins.opcode == Opcode.SGET_WIDE) {
                val fieldRef = (ins as? ReferenceInstruction)?.reference as? FieldReference
                when (fieldRef?.name) {
                    "J1" -> {
                        val reg = (ins as OneRegisterInstruction).registerA
                        val smali = """
                            invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accent()I
                            move-result v4
                            invoke-static {v4}, Landroidx/compose/ui/graphics/n0;->c(I)J
                            move-result-wide v4
                            move-wide/from16 v$reg, v4
                        """.trimIndent()
                        insertions.add(Triple(i + 1, smali, 1))
                    }
                    "I1" -> {
                        val reg = (ins as OneRegisterInstruction).registerA
                        val smali = """
                            invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accent()I
                            move-result v4
                            invoke-static {v4}, Landroidx/compose/ui/graphics/n0;->c(I)J
                            move-result-wide v4
                            move-wide/from16 v$reg, v4
                        """.trimIndent()
                        insertions.add(Triple(i + 1, smali, 1))
                    }
                    "a" -> {
                        val reg = (ins as OneRegisterInstruction).registerA
                        val smali = """
                            invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->accentContainer()I
                            move-result v4
                            invoke-static {v4}, Landroidx/compose/ui/graphics/n0;->c(I)J
                            move-result-wide v4
                            move-wide/from16 v$reg, v4
                        """.trimIndent()
                        insertions.add(Triple(i + 1, smali, 1))
                    }
                }
            }
        }

        // Apply all insertions in descending index order to preserve lower offsets
        insertions.sortByDescending { it.first }

        var patched = 0
        for (ins in insertions) {
            method.addInstructions(ins.first, ins.second)
            patched += ins.third
        }

        println("[Dynamic Color] Applied $patched arg overrides across 3 schemes -> Material You palette enabled.")
    }
}
