package app.morphe.patches.lrmobile

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.clearTryBlocks
import app.morphe.patches.shared.replaceWithReturnBoolean
import app.morphe.patches.shared.replaceWithReturnVoid

private object PerformLocalInstallerCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "performLocalInstallerCheck",
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("Local install check failed due to wrong installer."),
)

private object IsLocalCheckPassedFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "isLocalCheckPassed",
    returnType = "Z",
    parameters = emptyList(),
)

private object VerifyIntegrityFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/SignatureCheck;",
    name = "verifyIntegrity",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("Apk signature is invalid."),
)

private object LicenseActivityOnStartFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseActivity;",
    name = "onStart",
    returnType = "V",
    parameters = emptyList(),
)

private object RequestIntegrityTokenFingerprint : Fingerprint(
    definingClass = "Ljw/m;",
    name = "l",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "I",
        "Ljava/lang/String;",
        "Ljava/util/concurrent/CompletableFuture;",
    ),
    strings = listOf(" to fetch Integrity Token..."),
)

@Suppress("unused")
val lightroomSideloadSupportPatch = bytecodePatch(
    name = "Sideload Install Support",
    description = "Ensures compatibility for sideloaded lab installs by allowing local installer source checks to report success and completing Play Integrity token requests. Required for sideloaded lab installs: without it the app routes to the Play Store.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)

    execute {
        var patched = 0

        // 1. Force local installer source check to succeed
        PerformLocalInstallerCheckFingerprint.method.replaceWithReturnBoolean(true)
        patched++

        // 2. Force local check status flag to always report passed
        IsLocalCheckPassedFingerprint.method.replaceWithReturnBoolean(true)
        patched++

        // 3. Neutralize signature verification check
        VerifyIntegrityFingerprint.method.replaceWithReturnVoid()
        patched++

        // 4. Dismiss license error activity immediately if invoked
        LicenseActivityOnStartFingerprint.method.apply {
            clearTryBlocks()
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(
                0,
                """
                    invoke-super {p0}, Landroid/app/Activity;->onStart()V
                    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                    return-void
                """.trimIndent(),
            )
            patched++
        }

        // 5. Complete Play Integrity token request immediately on sideloaded/no-GMS environments
        RequestIntegrityTokenFingerprint.method.apply {
            clearTryBlocks()
            implementation?.let { removeInstructions(0, it.instructions.count()) }
            addInstructions(
                0,
                """
                    const-string v0, ""
                    move-object v1, p4
                    invoke-virtual {v1, v0}, Ljava/util/concurrent/CompletableFuture;->complete(Ljava/lang/Object;)Z
                    return-void
                """.trimIndent(),
            )
            patched++
        }

        println("[Sideload Install Support] Applied $patched hooks -> checks report success for sideloaded builds.")
    }
}
