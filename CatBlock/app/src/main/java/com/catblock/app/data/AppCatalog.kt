package com.catblock.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSocialSuggestion: Boolean
)

object AppCatalog {

    /** Common social/media packages we surface at the top of the picker. */
    private val SOCIAL_HINTS = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok intl
        "com.twitter.android",
        "com.x.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.pinterest",
        "com.linkedin.android",
        "tv.twitch.android.app",
        "com.discord",
        "com.whatsapp",
        "org.telegram.messenger"
    )

    fun listLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return installed
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != context.packageName }
            .map { info: ApplicationInfo ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    isSocialSuggestion = SOCIAL_HINTS.contains(info.packageName)
                )
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.isSocialSuggestion }
                    .thenBy { it.label.lowercase() }
            )
    }
}
