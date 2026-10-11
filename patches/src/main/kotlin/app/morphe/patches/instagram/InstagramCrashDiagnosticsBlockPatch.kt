package app.morphe.patches.instagram

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.disableComponentsByName
import org.w3c.dom.Element

private val instagramCrashDiagnosticsResourcePatch = resourcePatch(
    name = "Instagram Crash & Diagnostics Manifest Purge",
    description = "Disables crash-dump upload services, Lacrima broadcast receivers, debug diagnostics, carrier preload trackers, and lead-ads activities in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_INSTAGRAM)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[Instagram Crash & Diagnostics] Skipped: AndroidManifest.xml not found.")
            return@execute
        }

        val blockedComponents = setOf(
            // Crash dump and error reporting services
            "com.facebook.common.errorreporting.memory.service.jobschedulercompat.igsvc.DumperUploadService",

            // Lacrima error reporting broadcast receivers
            "com.facebook.errorreporting.lacrima.detector.broadcast.SecureShutdownBootBroadcastReceiver",
            "com.facebook.errorreporting.lacrima.detector.broadcast.ProtectedLockScreenBroadcastReceiver",
            "com.facebook.errorreporting.lacrima.detector.broadcast.PublicLockScreenBroadcastReceiver",

            // Debug diagnostics activities
            "com.facebook.flipper.android.diagnostics.FlipperDiagnosticActivity",

            // Oxygen carrier preload tracking services and receivers
            "com.facebook.oxygen.preloads.sdk.firstparty.managedappcache.IsManagedAppCacheService",
            "com.facebook.oxygen.preloads.sdk.firstparty.managedappcache.IsManagedAppCacheJobService",
            "com.facebook.oxygen.preloads.sdk.firstparty.managedappcache.IsManagedAppFlag",
            "com.facebook.oxygen.preloads.sdk.firstparty.managedappcache.IsManagedAppReceiver",
            "com.facebook.oxygen.preloads.integration.appstartup.flag.FirstAppOpenFlag",
            "com.facebook.oxygen.preloads.integration.install.ig4a.xavfullscreen.XavFbInstallVariantActivity",

            // Lead-ads interaction surface
            "com.instagram.leadads.activity.LeadAdsActivity",
        )

        var disabledComponents = 0

        document(manifestFile.absolutePath).use { doc ->
            val application = doc.documentElement.getElementsByTagName("application").item(0) as? Element
            if (application != null) {
                disabledComponents = application.disableComponentsByName(*blockedComponents.toTypedArray())
            }
        }

        println("[Instagram Crash & Diagnostics] Disabled $disabledComponents crash, diagnostic, and preload components in AndroidManifest.xml.")
    }
}

@Suppress("unused")
val instagramCrashDiagnosticsBlockPatch = bytecodePatch(
    name = "Block Crash & Diagnostics",
    description = "Neutralizes crash-dump uploaders, Lacrima detector broadcasts, debug diagnostics, carrier preload trackers, and lead-ads entrypoints.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_INSTAGRAM)
    dependsOn(instagramCrashDiagnosticsResourcePatch)

    execute {
        println("[Instagram Crash & Diagnostics] Crash and diagnostics patch execution completed.")
    }
}
