package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.BuilderSwitchPayload
import com.android.tools.smali.dexlib2.builder.MethodLocation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

context(_: BytecodePatchContext)
internal fun neutralizeFlag(
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
    return when (resultIns.opcode) {
        Opcode.MOVE_RESULT -> handleMoveResult(method, invokeIndex, resultIns)
        Opcode.MOVE_RESULT_OBJECT -> handleMoveResultObject(method, invokeIndex, resultIns)
        else -> handleBranch(method, instructions, invokeIndex, flagKey, patchName, resultIns)
    }
}

private fun handleMoveResult(
    method: MutableMethod,
    invokeIndex: Int,
    resultIns: Instruction,
): Boolean {
    val reg = (resultIns as OneRegisterInstruction).registerA
    val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
    method.addInstructions(invokeIndex + 2, constIns)
    return true
}

private fun handleMoveResultObject(
    method: MutableMethod,
    invokeIndex: Int,
    resultIns: Instruction,
): Boolean {
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

private fun handleBranch(
    method: MutableMethod,
    instructions: List<Instruction>,
    invokeIndex: Int,
    flagKey: String,
    patchName: String,
    resultIns: Instruction,
): Boolean {
    val branchIndex = (invokeIndex until minOf(instructions.size, invokeIndex + 5)).firstOrNull { i ->
        instructions[i].opcode.name.startsWith("if-")
    }
    if (branchIndex != null) {
        val branchIns = instructions[branchIndex]
        val reg = (branchIns as? OneRegisterInstruction)?.registerA
        if (reg != null) {
            val constIns = if (reg <= 15) "const/4 v$reg, 0x0" else "const/16 v$reg, 0x0"
            requireNotJumpTarget(method, branchIndex, patchName)
            method.addInstructions(branchIndex, constIns)
            return true
        }
    }
    println("[$patchName] Unexpected instruction '${resultIns.opcode}' after invoke for flag '$flagKey'.")
    return false
}

/**
 * Ensures the injection point is not a jump target before inserting in front of
 * an instruction that consumes a value (if-*, iput, return). Code inserted right
 * after a move-result only covers that read path and does not need this check.
 */
internal fun requireNotJumpTarget(method: MutableMethod, index: Int, patchName: String) {
    val instructions = method.implementation?.instructions ?: return
    if (index !in instructions.indices) return
    val targetLocation = instructions[index].location
    if (instructions.any { isJumpTarget(it, targetLocation) }) {
        error("[$patchName] Injection point $index in ${method.definingClass}->${method.name} is a jump target; the injected override would be skipped on that path.")
    }
}

private fun isJumpTarget(instruction: BuilderInstruction, location: MethodLocation): Boolean =
    when (instruction) {
        is BuilderOffsetInstruction -> instruction.target.location == location
        is BuilderSwitchPayload -> instruction.switchElements.any { it.target.location == location }
        else -> false
    }
