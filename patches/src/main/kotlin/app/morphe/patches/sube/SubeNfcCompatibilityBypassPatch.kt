package app.morphe.patches.sube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants.COMPATIBILITY_SUBE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private val nfcCompatibilityGateFingerprint = Fingerprint(
    classFingerprint = Fingerprint(
        strings = listOf("com.nxp.mifare"),
    ),
    returnType = "Lcom/nacionservicios/nssacommons/system/nfc/NfcCompatibility;",
    strings = listOf("com.nxp.mifare"),
)

@Suppress("unused")
val subeNfcCompatibilityBypassPatch = bytecodePatch(
    name = "Bypass SUBE NFC antenna check",
    description = "Bypasses the com.nxp.mifare system feature check in SUBE so the app runs on devices whose ROM does not declare NXP Mifare support.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SUBE)

    execute {
        var patched = 0

        val method = nfcCompatibilityGateFingerprint.method
        val implementation = method.implementation
        if (implementation == null) {
            println("[Bypass SUBE NFC antenna check] Skipped: gate method implementation is null.")
            return@execute
        }

        val instructions = implementation.instructions
        val targetIndices = instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.SGET_OBJECT) return@mapIndexedNotNull null
            val fieldRef = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return@mapIndexedNotNull null
            val isTargetEnum = fieldRef.definingClass.startsWith("Lcom/nacionservicios/nssacommons/system/nfc/NfcCompatibility")
            if (isTargetEnum && fieldRef.name == "d") {
                index
            } else {
                null
            }
        }

        check(targetIndices.size == 1) {
            "Expected exactly 1 sget-object instruction for NfcCompatibility.d in gate method ${method.name}, found ${targetIndices.size}."
        }

        for (targetIndex in targetIndices) {
            val targetInstruction = instructions[targetIndex] as OneRegisterInstruction
            val register = targetInstruction.registerA
            val fieldRef = (targetInstruction as ReferenceInstruction).reference as FieldReference
            val definingClass = fieldRef.definingClass
            val fieldType = fieldRef.type

            method.replaceInstruction(
                targetIndex,
                "sget-object v$register, $definingClass->b:$fieldType",
            )
            patched++
        }

        println("[Bypass SUBE NFC antenna check] Applied $patched hooks -> NFC antenna compatibility check bypassed.")
    }
}
