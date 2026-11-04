package no.cloud247.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator

/** Henter installerte apper. Kjøres på bakgrunnstråd. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun loadApps(): List<AppEntry> {
        val self = context.packageName
        val result = LinkedHashMap<String, AppEntry>()

        // TV-apper (har banner og er laget for fjernkontroll) prioriteres.
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { ri ->
            val ai = ri.activityInfo
            if (ai.packageName == self) return@forEach
            val entry = AppEntry(
                label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName },
                component = ComponentName(ai.packageName, ai.name),
                isLeanback = true,
            )
            result.putIfAbsent(ai.packageName, entry)
        }

        // Sideloadede mobilapper uten TV-støtte (f.eks. nettlesere) – kun hvis pakken ikke allerede finnes.
        query(Intent.CATEGORY_LAUNCHER).forEach { ri ->
            val ai = ri.activityInfo
            if (ai.packageName == self || result.containsKey(ai.packageName)) return@forEach
            val entry = AppEntry(
                label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName },
                component = ComponentName(ai.packageName, ai.name),
                isLeanback = false,
            )
            result[ai.packageName] = entry
        }

        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun query(category: String) =
        try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        } catch (e: RuntimeException) {
            emptyList()
        }
}
