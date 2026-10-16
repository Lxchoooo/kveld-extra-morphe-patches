package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags

private object WatermarkBusinessFunctionA4Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "A4",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/vega/subscriptionapi/biz/function/WatermarkSceneParams;"),
)

private object WatermarkBusinessFunctionQ0Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "Q0",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/vega/subscriptionapi/biz/function/WatermarkSceneParams;"),
)

private object WatermarkBusinessFunctionB4Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "B4",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/lemon/lv/clipmonetize/wrapper/BenefitResource;"),
)

private object WatermarkBusinessFunctionE4Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "E4",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/vega/subscriptionapi/biz/function/WatermarkBusinessParam;", "Lcom/lemon/lv/clipmonetize/wrapper/BenefitResource;"),
)

private object WatermarkBusinessFunctionU4Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "u4",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Lcom/lemon/lv/clipmonetize/wrapper/BenefitResource;"),
)

private object WatermarkBusinessFunctionY2Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/subscription/biz/function/WatermarkBusinessFunction;",
    name = "y2",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
)

@Suppress("unused")
val capcutRemoveWatermarkPatch = bytecodePatch(
    name = "Remove Watermark",
    description = "Forces CapCut watermark-removal gates to allow export without watermark (WatermarkBusinessFunction).",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    execute {
        var patched = 0

        fun forceTrue(fp: Fingerprint) {
            fp.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
            patched++
        }

        fun forceFalse(fp: Fingerprint) {
            fp.method.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
            patched++
        }

        forceTrue(WatermarkBusinessFunctionA4Fingerprint)
        forceTrue(WatermarkBusinessFunctionQ0Fingerprint)
        forceTrue(WatermarkBusinessFunctionB4Fingerprint)
        forceTrue(WatermarkBusinessFunctionE4Fingerprint)
        forceTrue(WatermarkBusinessFunctionU4Fingerprint)
        forceFalse(WatermarkBusinessFunctionY2Fingerprint)

        println("[Remove Watermark] Applied $patched hooks -> watermark gates forced to allow removal.")
    }
}
