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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private val composeThemeDFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    custom = { methodDef, _ ->
        if (methodDef.name != "invoke") return@Fingerprint false
        val instructions = methodDef.implementation?.instructions ?: return@Fingerprint false
        var hasThemeB = false
        var hasColorScheme = false
        for (ins in instructions) {
            if (ins.opcode == Opcode.CHECK_CAST) {
                val ref = (ins as? ReferenceInstruction)?.reference as? TypeReference
                if (ref?.type?.contains("compose/theme/b") == true) {
                    hasThemeB = true
                }
            } else if (ins.opcode == Opcode.INVOKE_STATIC_RANGE) {
                val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
                if (ref?.definingClass?.contains("material3/j1") == true) {
                    hasColorScheme = true
                }
            }
            if (hasThemeB && hasColorScheme) return@Fingerprint true
        }
        false
    },
)

private data class SchemeTarget(val index: Int, val isDark: Boolean, val startRegister: Int)

@Suppress("unused")
val twitterDynamicColorPatch = bytecodePatch(
    name = "Dynamic Color",
    description = "Replaces the default Twitter Blue with the user's Material You palette.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(sharedExtensionPatch)

    execute {
        val targetMethod = composeThemeDFingerprint.method
        val classDef = mutableClassDefBy(targetMethod.definingClass)
        val method = classDef.methods.firstOrNull {
            it.name == targetMethod.name && it.parameterTypes == targetMethod.parameterTypes
        } ?: run {
            println("[Dynamic Color] Skipped: invoke(Object, Object) method not found in ${targetMethod.definingClass}.")
            return@execute
        }

        val instructions = method.implementation?.instructions?.toList() ?: run {
            println("[Dynamic Color] Skipped: no implementation in invoke().")
            return@execute
        }

        // Locate the two ColorScheme range invoke calls (j1.f = dark, j1.c = light)
        val targets = mutableListOf<SchemeTarget>()
        for ((index, ins) in instructions.withIndex()) {
            if (ins.opcode == Opcode.INVOKE_STATIC_RANGE) {
                val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
                if (ref?.definingClass?.contains("material3/j1") == true) {
                    val rangeIns = ins as? Instruction3rc ?: continue
                    // First call in theme/d instruction order is dark (053f), second call is light (05e5)
                    val isDark = targets.isEmpty()
                    targets.add(SchemeTarget(index, isDark, rangeIns.startRegister))
                }
            }
        }

        if (targets.size != 2) {
            println("[Dynamic Color] Skipped: Expected 2 ColorScheme range invokes in ${targetMethod.definingClass}, found ${targets.size}.")
            return@execute
        }

        // Apply insertions in descending index order to preserve bytecode offsets
        targets.sortByDescending { it.index }

        var patched = 0
        for (target in targets) {
            val primaryMethod = if (target.isDark) "accentDark" else "accentLight"
            val containerMethod = if (target.isDark) "accentContainerDark" else "accentContainerLight"
            val primaryReg = target.startRegister
            val primaryContainerReg = target.startRegister + 4
            val smali = """
                invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->$primaryMethod()I
                move-result v8
                invoke-static {v8}, Landroidx/compose/ui/graphics/n0;->c(I)J
                move-result-wide v$primaryReg
                invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterMonetHelper;->$containerMethod()I
                move-result v8
                invoke-static {v8}, Landroidx/compose/ui/graphics/n0;->c(I)J
                move-result-wide v$primaryContainerReg
            """.trimIndent()
            method.addInstructions(target.index, smali)
            patched += 2
        }

        println("[Dynamic Color] Applied $patched slot overrides (light/dark) -> Material You palette enabled.")
    }
}

