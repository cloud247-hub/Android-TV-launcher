package no.cloud247.tvlauncher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Laster bannere/ikoner på bakgrunnstråd og cacher dem i minnet,
 * slik at rulling og oppstart aldri blokkeres av PackageManager.
 */
class IconLoader(context: Context) {

    class Art(val banner: Drawable?, val icon: Drawable?)

    private val pm: PackageManager = context.packageManager
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "icon-loader").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val cache = LruCache<String, Art>(256)

    fun cached(app: AppEntry): Art? = cache.get(app.key)

    fun load(app: AppEntry, callback: (Art) -> Unit) {
        cache.get(app.key)?.let { callback(it); return }
        executor.execute {
            val art = resolve(app)
            cache.put(app.key, art)
            main.post { callback(art) }
        }
    }

    fun invalidate() = cache.evictAll()

    fun shutdown() = executor.shutdownNow()

    private fun resolve(app: AppEntry): Art {
        var banner: Drawable? = null
        var icon: Drawable? = null
        try {
            banner = pm.getActivityBanner(app.component)
            if (banner == null) banner = pm.getApplicationBanner(app.packageName)
        } catch (_: Exception) {
        }
        if (banner == null) {
            try {
                icon = pm.getActivityIcon(app.component)
            } catch (_: Exception) {
                icon = try { pm.getApplicationIcon(app.packageName) } catch (_: Exception) { null }
            }
        }
        return Art(banner, icon)
    }
}
