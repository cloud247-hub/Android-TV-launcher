package no.cloud247.tvlauncher

import android.content.Context

/** Lagrer favoritter (rekkefølge betyr noe) og skjulte apper. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, null)
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString(SEP)).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, HashSet(value)).apply()

    var initialized: Boolean
        get() = sp.getBoolean(KEY_INIT, false)
        set(value) = sp.edit().putBoolean(KEY_INIT, value).apply()

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_INIT = "initialized"
        private const val SEP = "|"
    }
}
