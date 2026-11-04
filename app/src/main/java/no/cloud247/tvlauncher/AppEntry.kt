package no.cloud247.tvlauncher

import android.content.ComponentName
import android.content.Intent

/** En startbar app. [key] = "pakke/klasse" og brukes til lagring av favoritter/skjulte. */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val isLeanback: Boolean,
) {
    val key: String get() = component.flattenToShortString()
    val packageName: String get() = component.packageName

    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}
