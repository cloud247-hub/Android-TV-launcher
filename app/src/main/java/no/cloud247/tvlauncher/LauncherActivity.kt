package no.cloud247.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class LauncherActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var icons: IconLoader
    private lateinit var adapter: HomeAdapter
    private lateinit var grid: RecyclerView

    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private var apps: List<AppEntry> = emptyList()
    private var dirty = true
    private var receiverRegistered = false
    private var firstRender = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            icons.invalidate()
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        prefs = Prefs(this)
        repo = AppRepository(this)
        icons = IconLoader(this)

        setupBrand()
        setupTopBar()
        setupGrid()
        registerPackageReceiver()
        reload()
    }

    override fun onResume() {
        super.onResume()
        // Billig sikkerhetsnett i tilfelle en pakkeendring ble gått glipp av mens vi var i bakgrunnen.
        if (dirty) reload()
    }

    override fun onStop() {
        super.onStop()
        dirty = true
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(packageReceiver)
        loader.shutdownNow()
        icons.shutdown()
        super.onDestroy()
    }

    /** HOME-knappen mens vi allerede vises: hopp til toppen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) {
            grid.scrollToPosition(0)
            main.post { focusFirstApp() }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // En launcher skal aldri lukkes med tilbake-knappen. Gå heller til toppen.
        grid.smoothScrollToPosition(0)
        focusFirstApp()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val focused = currentFocus
            val holder = focused?.let { grid.findContainingViewHolder(it) }
            val item = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
                ?.let { adapter.items.getOrNull(it) }
            if (item is HomeItem.App) {
                showAppOptions(item.app, item.inFavorites)
            } else {
                showCustomize()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Oppsett ----------

    private fun setupBrand() {
        val cloud = getString(R.string.brand_cloud)
        val text = SpannableString(cloud + getString(R.string.brand_247))
        text.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.c247_accent)),
            cloud.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        findViewById<TextView>(R.id.brand).text = text
    }

    private fun setupTopBar() {
        findViewById<View>(R.id.btn_live_tv).setOnClickListener { openLiveTv() }
        findViewById<View>(R.id.btn_sources).setOnClickListener { showSources() }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            safeStart(Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<View>(R.id.btn_customize).setOnClickListener { showCustomize() }

        // Pil ned fra toppfeltet går alltid til første app.
        val downToGrid = View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusFirstApp()
                true
            } else {
                false
            }
        }
        for (id in TOP_BUTTONS) findViewById<View>(id).setOnKeyListener(downToGrid)
    }

    private fun setupGrid() {
        grid = findViewById(R.id.grid)
        adapter = HomeAdapter(
            icons = icons,
            onClick = ::launch,
            onLongClick = ::showAppOptions,
            onNavigate = ::navigate,
        )
        val lm = GridLayoutManager(this, SPAN_COUNT)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (adapter.items.getOrNull(position) is HomeItem.Header) SPAN_COUNT else 1
        }
        grid.layoutManager = lm
        grid.adapter = adapter
        grid.setHasFixedSize(true)
        grid.itemAnimator = null // ingen animasjoner = raskere og ingen fokushopp
        grid.setItemViewCacheSize(SPAN_COUNT * 4)
        grid.recycledViewPool.setMaxRecycledViews(HomeAdapter.TYPE_APP, SPAN_COUNT * 6)
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        receiverRegistered = true
    }

    // ---------- Data ----------

    private fun reload() {
        dirty = false
        loader.execute {
            val loaded = repo.loadApps()
            main.post {
                if (isDestroyed) return@post
                apps = loaded
                seedFavoritesIfFirstRun()
                render()
            }
        }
    }

    private fun seedFavoritesIfFirstRun() {
        if (prefs.initialized || apps.isEmpty()) return
        val byPackage = apps.associateBy { it.packageName }
        prefs.favorites = DEFAULT_FAVORITES.mapNotNull { byPackage[it]?.key }
        prefs.initialized = true
    }

    private fun render() {
        val hidden = prefs.hidden
        val byKey = apps.associateBy { it.key }
        val favorites = prefs.favorites.mapNotNull { byKey[it] }

        val items = ArrayList<HomeItem>(apps.size + favorites.size + 2)
        items += HomeItem.Header(
            getString(R.string.section_favorites),
            if (favorites.isEmpty()) getString(R.string.favorites_empty_hint) else null,
        )
        favorites.forEach { items += HomeItem.App(it, inFavorites = true) }

        items += HomeItem.Header(getString(R.string.section_all_apps))
        apps.filter { it.key !in hidden }.forEach { items += HomeItem.App(it, inFavorites = false) }

        adapter.submit(items)
        if (firstRender) {
            firstRender = false
            main.post { focusFirstApp() }
        } else {
            main.post { if (currentFocus == null) focusFirstApp() }
        }
    }

    /**
     * Forutsigbar D-pad-navigasjon i rutenettet: høyre/venstre stopper ved radkanten,
     * opp/ned går til samme kolonne i raden over/under (også på tvers av seksjoner),
     * og opp fra øverste rad går til toppfeltet.
     */
    private fun navigate(pos: Int, keyCode: Int): Boolean {
        val items = adapter.items
        val headers = items.indices.filter { items[it] is HomeItem.Header }
        val header = headers.lastOrNull { it < pos } ?: return false
        val start = header + 1
        val end = headers.firstOrNull { it > pos } ?: items.size
        val count = end - start
        val idx = pos - start
        val col = idx % SPAN_COUNT
        val row = idx / SPAN_COUNT
        val lastRow = (count - 1) / SPAN_COUNT

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (col > 0) focusPosition(pos - 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (col < SPAN_COUNT - 1 && idx + 1 < count) focusPosition(pos + 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (row > 0) {
                    focusPosition(pos - SPAN_COUNT)
                    return true
                }
                val prevHeader = headers.lastOrNull { it < header }
                if (prevHeader != null) {
                    val pStart = prevHeader + 1
                    val pCount = header - pStart
                    if (pCount > 0) {
                        val pLastRow = (pCount - 1) / SPAN_COUNT
                        focusPosition(pStart + minOf(pLastRow * SPAN_COUNT + col, pCount - 1))
                        return true
                    }
                }
                grid.scrollToPosition(0)
                findViewById<View>(R.id.btn_live_tv).requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (row < lastRow) {
                    focusPosition(start + minOf((row + 1) * SPAN_COUNT + col, count - 1))
                    return true
                }
                val nextHeader = headers.firstOrNull { it > pos }
                if (nextHeader != null) {
                    val nStart = nextHeader + 1
                    val nEnd = headers.firstOrNull { it > nextHeader } ?: items.size
                    val nCount = nEnd - nStart
                    if (nCount > 0) focusPosition(nStart + minOf(col, nCount - 1))
                }
                return true
            }
        }
        return false
    }

    private fun focusPosition(pos: Int) {
        val tile = grid.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile)
        if (tile != null) {
            tile.requestFocus()
            return
        }
        grid.scrollToPosition(pos)
        grid.post {
            grid.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile)?.requestFocus()
        }
    }

    private fun focusFirstApp() {
        val index = adapter.items.indexOfFirst { it is HomeItem.App }
        if (index < 0) {
            findViewById<View>(R.id.btn_customize).requestFocus()
            return
        }
        val holder = grid.findViewHolderForAdapterPosition(index)
        if (holder != null) {
            holder.itemView.findViewById<View>(R.id.tile)?.requestFocus()
        } else {
            grid.scrollToPosition(index)
            main.post {
                grid.findViewHolderForAdapterPosition(index)
                    ?.itemView?.findViewById<View>(R.id.tile)?.requestFocus()
            }
        }
    }

    // ---------- Handlinger ----------

    private fun launch(app: AppEntry) {
        try {
            startActivity(app.launchIntent())
        } catch (e: Exception) {
            // Fallback: la systemet finne riktig startaktivitet for pakken.
            val fallback = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallback == null || !safeStart(fallback)) {
                Toast.makeText(this, R.string.error_launch, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun safeStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Philips: Watch TV-appen er org.droidtv.playtv. Faller tilbake til andre kjente TV-apper. */
    private fun openLiveTv() {
        for (pkg in LIVE_TV_PACKAGES) {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null && safeStart(intent)) return
        }
        if (safeStart(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI))) return
        Toast.makeText(this, R.string.error_live_tv, Toast.LENGTH_LONG).show()
    }

    /** Lister TV-ens innganger (HDMI, antenne osv.) og bytter direkte til valgt kilde. */
    private fun showSources() {
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val inputs = try {
            tim?.tvInputList.orEmpty().filter { !it.isHidden(this) }
        } catch (e: Exception) {
            emptyList()
        }
        if (inputs.isEmpty()) {
            Toast.makeText(this, R.string.sources_none, Toast.LENGTH_LONG).show()
            return
        }
        val labels = inputs.map { it.loadLabel(this).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.sources_title)
            .setItems(labels) { _, which -> openInput(inputs[which]) }
            .show()
            .styleList()
    }

    private fun openInput(input: TvInputInfo) {
        val uri = if (input.isPassthroughInput) {
            TvContract.buildChannelUriForPassthroughInput(input.id)
        } else {
            TvContract.buildChannelsUriForInput(input.id)
        }
        if (safeStart(Intent(Intent.ACTION_VIEW, uri))) return
        if (!input.isPassthroughInput) openLiveTv() else {
            Toast.makeText(this, R.string.sources_none, Toast.LENGTH_LONG).show()
        }
    }

    private fun openHomeButtonSettings() {
        Toast.makeText(this, R.string.home_service_hint, Toast.LENGTH_LONG).show()
        if (!safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
            safeStart(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showAppOptions(app: AppEntry, inFavorites: Boolean) {
        val favorites = prefs.favorites.toMutableList()
        val isFavorite = app.key in favorites
        val index = favorites.indexOf(app.key)

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        if (isFavorite) {
            if (inFavorites && index > 0) {
                labels += getString(R.string.opt_move_left)
                actions.add { moveFavorite(app.key, -1) }
            }
            if (inFavorites && index < favorites.size - 1) {
                labels += getString(R.string.opt_move_right)
                actions.add { moveFavorite(app.key, +1) }
            }
            labels += getString(R.string.opt_remove_favorite)
            actions.add { prefs.favorites = prefs.favorites - app.key; render() }
        } else {
            labels += getString(R.string.opt_add_favorite)
            actions.add { prefs.favorites = prefs.favorites + app.key; render() }
        }
        if (!inFavorites) {
            labels += getString(R.string.opt_hide)
            actions.add { prefs.hidden = prefs.hidden + app.key; render() }
        }
        labels += getString(R.string.opt_app_info)
        actions.add {
            safeStart(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName)),
            )
        }
        labels += getString(R.string.opt_uninstall)
        actions.add {
            @Suppress("DEPRECATION")
            safeStart(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.packageName)))
        }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
            .styleList()
    }

    private fun moveFavorite(key: String, delta: Int) {
        val list = prefs.favorites.toMutableList()
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.removeAt(from)
        list.add(to, key)
        prefs.favorites = list
        render()
        // Behold fokus på appen som ble flyttet.
        main.post {
            val pos = adapter.items.indexOfFirst { it is HomeItem.App && it.inFavorites && it.app.key == key }
            grid.findViewHolderForAdapterPosition(pos)?.itemView?.findViewById<View>(R.id.tile)?.requestFocus()
        }
    }

    private fun showCustomize() {
        val labels = arrayOf(
            getString(R.string.customize_unhide),
            getString(R.string.customize_reset_favorites),
            getString(R.string.customize_home_button),
            getString(R.string.customize_set_default),
            getString(R.string.btn_settings),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.customize_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> showUnhide()
                    1 -> {
                        prefs.initialized = false
                        seedFavoritesIfFirstRun()
                        render()
                    }
                    2 -> openHomeButtonSettings()
                    3 -> if (!safeStart(Intent(Settings.ACTION_HOME_SETTINGS))) {
                        safeStart(Intent(Settings.ACTION_SETTINGS))
                    }
                    4 -> safeStart(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .show()
            .styleList()
    }

    private fun showUnhide() {
        val hiddenKeys = prefs.hidden
        val hiddenApps = apps.filter { it.key in hiddenKeys }
        if (hiddenApps.isEmpty()) {
            Toast.makeText(this, R.string.customize_no_hidden, Toast.LENGTH_SHORT).show()
            return
        }
        val checked = BooleanArray(hiddenApps.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.unhide_title)
            .setMultiChoiceItems(hiddenApps.map { it.label }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.ok) { _, _ ->
                val unhide = hiddenApps.filterIndexed { i, _ -> checked[i] }.map { it.key }.toSet()
                prefs.hidden = prefs.hidden - unhide
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .styleList()
    }

    /** Lys tekst i dialoglister uansett produsentens standardtema. */
    private fun AlertDialog.styleList() {
        listView?.apply {
            setBackgroundColor(Color.TRANSPARENT)
            selector = ContextCompat.getDrawable(context, R.drawable.bg_list_selector)
        }
    }

    companion object {
        private const val SPAN_COUNT = 6

        private val TOP_BUTTONS = intArrayOf(
            R.id.btn_live_tv, R.id.btn_sources, R.id.btn_settings, R.id.btn_customize,
        )

        private val LIVE_TV_PACKAGES = listOf(
            "org.droidtv.playtv",           // Philips Watch TV
            "com.google.android.tv",        // Google Live Channels
            "com.mediatek.wwtv.tvcenter",   // MediaTek-baserte TV-er
            "com.droidlogic.tvsource",      // Amlogic-baserte TV-er
        )

        /** Legges automatisk i favoritter ved første oppstart – kun hvis de er installert. */
        private val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "no.nrk.tv",
            "no.nrk.nrktv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.viaplay.android",
            "no.tv2.sumo",
            "com.spotify.tv.android",
            "com.plexapp.android",
            "org.xbmc.kodi",
        )
    }
}
