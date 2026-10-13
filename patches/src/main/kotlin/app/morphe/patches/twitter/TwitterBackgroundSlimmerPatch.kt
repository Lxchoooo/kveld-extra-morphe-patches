package app.morphe.patches.twitter

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.disableComponentsByName
import org.w3c.dom.Element

private val disableWorkManager by booleanOption(
    key = "disableWorkManager",
    default = true,
    title = "Disable WorkManager and boot receivers",
    description = "Disables background WorkManager periodic job services and reschedule receivers.",
)

private val disableWidgets by booleanOption(
    key = "disableWidgets",
    default = true,
    title = "Disable home screen widgets",
    description = "Disables news, search widget receivers, and Glance remote views service.",
)

private val disableCastNfc by booleanOption(
    key = "disableCastNfc",
    default = true,
    title = "Disable Cast and NFC companion services",
    description = "Disables Google Cast reconnection service, media router receivers, and NFC profile tag APDU service.",
)

@Suppress("unused")
val twitterBackgroundSlimmerPatch = resourcePatch(
    name = "X Background Slimmer",
    description = "Disables background WorkManager services, home screen widgets, and Cast/NFC companion services to reduce background resource consumption.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[X Background Slimmer] AndroidManifest.xml not found - skipping background slimmer.")
            return@execute
        }

        if (!(disableWorkManager ?: true) && !(disableWidgets ?: true) && !(disableCastNfc ?: true)) {
            println("[X Background Slimmer] All background slimmer options disabled - skipping.")
            return@execute
        }

        var wmDisabled = 0
        var widgetsDisabled = 0
        var castNfcDisabled = 0

        document(manifestFile.absolutePath).use { doc ->
            val application = doc.documentElement.getElementsByTagName("application").item(0) as? Element
                ?: return@execute

            if (disableWorkManager ?: true) {
                // Evaluated and excluded from v1: com.x.appupgrade.XAppUpgradeReceiver (MY_PACKAGE_REPLACED only)
                // and com.x.android.ComponentStateRepairReceiver (MY_PACKAGE_REPLACED+BOOT_COMPLETED)
                // to prevent post-update migration and component repair failures.
                val wmComponents = arrayOf(
                    "androidx.work.impl.background.systemjob.SystemJobService",
                    "androidx.work.impl.foreground.SystemForegroundService",
                    "androidx.work.impl.background.systemalarm.RescheduleReceiver",
                    "androidx.work.impl.diagnostics.DiagnosticsReceiver",
                )
                wmDisabled = application.disableComponentsByName(*wmComponents)
            }

            if (disableWidgets ?: true) {
                val widgetComponents = arrayOf(
                    "com.x.widget.impl.news.NewsWidgetSmallReceiver",
                    "com.x.widget.impl.news.NewsWidgetMediumReceiver",
                    "com.x.widget.impl.news.NewsWidgetLargeReceiver",
                    "com.x.widget.impl.search.SearchWidgetReceiver",
                    "androidx.glance.appwidget.GlanceRemoteViewsService",
                )
                widgetsDisabled = application.disableComponentsByName(*widgetComponents)
            }

            if (disableCastNfc ?: true) {
                val castNfcComponents = arrayOf(
                    "com.google.android.gms.cast.framework.ReconnectionService",
                    "com.google.android.gms.cast.framework.media.MediaIntentReceiver",
                    "androidx.mediarouter.media.MediaTransferReceiver",
                    "com.x.share.impl.nfc.ProfileTagApduService",
                )
                castNfcDisabled = application.disableComponentsByName(*castNfcComponents)
            }
        }

        val totalDisabled = wmDisabled + widgetsDisabled + castNfcDisabled
        println("[X Background Slimmer] Disabled $totalDisabled background and companion components ($wmDisabled WorkManager, $widgetsDisabled widget, $castNfcDisabled Cast/NFC).")
    }
}
