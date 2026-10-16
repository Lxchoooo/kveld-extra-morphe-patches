/*
 * Copyright (C) 2026 aeries0x
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
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.string
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PATCH_TAG = "[Default Reply Sorting]"
private const val STOCK_MODE = "Relevance"
private val RANKING_MODES = setOf("Recency", STOCK_MODE, "Likes")
private val ENUM_CONSTANT_FLAGS =
    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value or AccessFlags.ENUM.value
private const val COMPOSE_RUNTIME = "Landroidx/compose/runtime/"

/** An `sget-object <ranking enum>->Relevance` at [index] in the method of [match]. */
private class RelevanceSeed(val match: Match, val index: Int)

private fun ClassDef.enumConstantNames() =
    staticFields.filter { it.accessFlags == ENUM_CONSTANT_FLAGS && it.type == type }.map { it.name }.toSet()

// TimelineRankingMode (com.x.models.ef in 12.34.0), found by its constants instead of its obfuscated name.
private fun BytecodePatchContext.rankingModeEnum(): String {
    val candidates = mutableListOf<String>()
    classDefForEach { classDef ->
        if (classDef.superclass == "Ljava/lang/Enum;" && classDef.enumConstantNames() == RANKING_MODES) {
            candidates += classDef.type
        }
    }
    return candidates.singleOrNull()
        ?: error("$PATCH_TAG Expected one enum with constants $RANKING_MODES, found $candidates.")
}

private fun Instruction.isRelevanceRead(enum: String): Boolean {
    if (opcode != Opcode.SGET_OBJECT) return false
    val field = (this as ReferenceInstruction).reference as FieldReference
    return field.definingClass == enum && field.name == STOCK_MODE && field.type == enum
}

private fun Method.relevanceReads(enum: String): List<Int> =
    (implementation?.instructions ?: emptyList()).withIndex()
        .filter { (_, instruction) -> instruction.isRelevanceRead(enum) }
        .map { it.index }

private val STATIC_INVOKES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)

/** The only register a plain or `/range` invoke passes, or null for any other argument count. */
private fun Instruction.soleArgumentRegister(): Int? = when (this) {
    is Instruction35c -> registerC.takeIf { registerCount == 1 }
    is Instruction3rc -> startRegister.takeIf { registerCount == 1 }
    else -> null
}

/** Relevance reads shaped `sget-object vX; invoke-static[/range] {vX}, <compose state factory>(Object); move-result-object; return-object`. */
private fun Method.composeStateSeeds(enum: String): List<Int> {
    val instructions = implementation?.instructions?.toList() ?: return emptyList()
    return relevanceReads(enum).filter { index ->
        val register = (instructions[index] as OneRegisterInstruction).registerA
        val call = instructions.getOrNull(index + 1) ?: return@filter false
        val target = (call as? ReferenceInstruction)?.reference as? MethodReference ?: return@filter false
        call.opcode in STATIC_INVOKES &&
            call.soleArgumentRegister() == register &&
            target.definingClass.startsWith(COMPOSE_RUNTIME) &&
            target.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
            target.returnType.startsWith(COMPOSE_RUNTIME) &&
            instructions.getOrNull(index + 2)?.opcode == Opcode.MOVE_RESULT_OBJECT &&
            instructions.getOrNull(index + 3)?.opcode == Opcode.RETURN_OBJECT
    }
}

// Post-detail timeline repository: seeds the ranking mode of the first replies request.
private fun repositorySeedFingerprint(enum: String) = Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(string("timelineRepository")),
    custom = { method, classDef ->
        classDef.superclass == "Lkotlin/coroutines/jvm/internal/SuspendLambda;" &&
            "Lkotlin/jvm/functions/Function2;" in classDef.interfaces &&
            method.relevanceReads(enum).isNotEmpty()
    },
)

// Compose `mutableStateOf(Relevance)` behind the reply-sorting button label and sheet selection.
// The owner is a merged lambda, so only the matching switch case is rewritten.
private fun uiStateSeedFingerprint(enum: String) = Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(fieldAccess(definingClass = enum, name = STOCK_MODE, type = enum, opcode = Opcode.SGET_OBJECT)),
    custom = { method, classDef ->
        "Lkotlin/jvm/functions/Function0;" in classDef.interfaces && method.composeStateSeeds(enum).isNotEmpty()
    },
)

// Conversation prefetch issued by the post-detail component constructor before the repository exists.
private fun prefetchSeedFingerprint(enum: String) = Fingerprint(
    name = "<init>",
    filters = listOf(string("Conversation request started for post ")),
    custom = { method, _ -> method.relevanceReads(enum).isNotEmpty() },
)

private fun BytecodePatchContext.relevanceSeed(
    site: String,
    fingerprint: Fingerprint,
    seedsIn: (Method) -> List<Int>,
): RelevanceSeed {
    val matches = fingerprint.matchAllOrNull().orEmpty()
    val match = matches.singleOrNull()
        ?: error("$PATCH_TAG $site: expected one matching method, found ${matches.map { it.originalMethod }}.")
    val seeds = seedsIn(match.originalMethod)
    val index = seeds.singleOrNull()
        ?: error("$PATCH_TAG $site: expected one $STOCK_MODE read in ${match.originalMethod}, found ${seeds.size}.")
    return RelevanceSeed(match, index)
}

@Suppress("unused")
val twitterDefaultReplySortingPatch = bytecodePatch(
    name = "Default Reply Sorting",
    description = "Opens replies on every post with the selected sort mode instead of Relevance.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val replySortMode by stringOption(
        key = "replySortMode",
        default = "Likes",
        values = mapOf(
            "Likes" to "Likes",
            "Recency" to "Recency",
            "Relevance (stock)" to STOCK_MODE,
        ),
        title = "Reply Sort Mode",
        description = "Sort mode replies open with on every post.",
        required = true,
    )

    execute {
        val mode = replySortMode?.trim().orEmpty()
        if (mode !in RANKING_MODES) {
            error("$PATCH_TAG Invalid replySortMode '$mode': expected one of $RANKING_MODES.")
        }
        if (mode == STOCK_MODE) {
            println("$PATCH_TAG Skipped: replySortMode is the stock $STOCK_MODE.")
            return@execute
        }

        val enum = rankingModeEnum()
        // Resolve every site before rewriting any, so a missing site leaves the app untouched.
        val seeds = listOf(
            relevanceSeed("Repository seed", repositorySeedFingerprint(enum)) { it.relevanceReads(enum) },
            relevanceSeed("UI-state seed", uiStateSeedFingerprint(enum)) { it.composeStateSeeds(enum) },
            relevanceSeed("Prefetch seed", prefetchSeedFingerprint(enum)) { it.relevanceReads(enum) },
        )
        seeds.forEach { seed ->
            val method = seed.match.method
            val register = method.getInstruction<OneRegisterInstruction>(seed.index).registerA
            method.replaceInstruction(seed.index, "sget-object v$register, $enum->$mode:$enum")
        }

        println("$PATCH_TAG Applied ${seeds.size} hooks -> replies open sorted by $mode.")
    }
}
