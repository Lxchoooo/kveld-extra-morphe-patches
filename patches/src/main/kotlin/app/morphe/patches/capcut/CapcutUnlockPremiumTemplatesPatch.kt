package app.morphe.patches.capcut

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants

private object CutSamePurchaseViewModelU6Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/libcutsame/viewmodel/CutSamePurchaseViewModel;",
    name = "u6",
    returnType = "Z",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

private object CutSamePurchaseViewModelX6Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/libcutsame/viewmodel/CutSamePurchaseViewModel;",
    name = "x6",
    returnType = "Z",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

private object CutSamePurchaseViewModelW6Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/libcutsame/viewmodel/CutSamePurchaseViewModel;",
    name = "w6",
    returnType = "Z",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

private object CutSamePurchaseViewModelZ6Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/libcutsame/viewmodel/CutSamePurchaseViewModel;",
    name = "z6",
    returnType = "Z",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

private object CutSamePurchaseViewModelA6Fingerprint : Fingerprint(
    definingClass = "Lcom/vega/libcutsame/viewmodel/CutSamePurchaseViewModel;",
    name = "A6",
    returnType = "Z",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

@Suppress("unused")
val capcutUnlockPremiumTemplatesPatch = bytecodePatch(
    name = "Unlock Premium Templates",
    description = "Marks premium template styles as available so locked template features stay usable.",
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

        forceTrue(CutSamePurchaseViewModelU6Fingerprint)
        forceTrue(CutSamePurchaseViewModelX6Fingerprint)
        forceTrue(CutSamePurchaseViewModelW6Fingerprint)
        forceFalse(CutSamePurchaseViewModelZ6Fingerprint)
        forceFalse(CutSamePurchaseViewModelA6Fingerprint)

        println("[Unlock Premium Templates] Applied $patched hooks -> premium template flags enabled.")
    }
}
