/*
 * Copyright (C) 2024-2026 Piko contributors
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

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringsOption
import app.morphe.patches.shared.Constants
import org.w3c.dom.Element

internal val DEFAULT_CUSTOM_LINK_HOSTS = listOf(
    "vxtwitter.com",
    "fixvx.com",
    "fxtwitter.com",
    "fixupx.com",
    "twittpr.com",
    "xcancel.com",
)

@Suppress("unused")
val twitterCustomDeepLinksPatch = resourcePatch(
    name = "Custom Deep Links",
    description = "Opens supported custom and alternative domain links directly in the app (for shared link domains, see Share Links).",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    val customLinkHosts by stringsOption(
        key = "customLinkHosts",
        default = DEFAULT_CUSTOM_LINK_HOSTS,
        title = "Custom Link Hosts",
        description = "Custom domains to register for opening directly in the app.",
        required = false,
    )

    execute {
        val hosts = customLinkHosts ?: DEFAULT_CUSTOM_LINK_HOSTS

        document("AndroidManifest.xml").use { doc ->
            val activities = doc.getElementsByTagName("activity")
            val mainActivity = (0 until activities.length)
                .mapNotNull { activities.item(it) as? Element }
                .firstOrNull { it.getAttribute("android:name") == "com.x.android.main.MainActivity" }
                ?: error("[Custom Deep Links] Activity com.x.android.main.MainActivity not found in AndroidManifest.xml.")

            val intentFilter = doc.createElement("intent-filter").apply {
                appendChild(doc.createElement("action").apply { setAttribute("android:name", "android.intent.action.VIEW") })
                appendChild(doc.createElement("category").apply { setAttribute("android:name", "android.intent.category.DEFAULT") })
                appendChild(doc.createElement("category").apply { setAttribute("android:name", "android.intent.category.BROWSABLE") })
                appendChild(doc.createElement("data").apply { setAttribute("android:scheme", "http") })
                appendChild(doc.createElement("data").apply { setAttribute("android:scheme", "https") })
                appendChild(doc.createElement("data").apply { setAttribute("android:pathPattern", "/..*") })

                for (host in hosts) {
                    appendChild(doc.createElement("data").apply { setAttribute("android:host", host) })
                    appendChild(doc.createElement("data").apply { setAttribute("android:host", "*.$host") })
                }
            }
            mainActivity.appendChild(intentFilter)
        }

        println("[Custom Deep Links] Appended intent filter for ${hosts.size} host(s) to MainActivity (inbound routing relies on manifest deep-link resolution).")
    }
}
