package no.cloud247.tvlauncher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Én liste med seksjoner: overskrift + rutenett med apper. */
sealed class HomeItem {
    data class Header(val title: String, val hint: String? = null) : HomeItem()
    data class App(val app: AppEntry, val inFavorites: Boolean) : HomeItem()
}

class HomeAdapter(
    private val icons: IconLoader,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry, inFavorites: Boolean) -> Unit,
    private val onNavigate: (position: Int, keyCode: Int) -> Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items: List<HomeItem> = emptyList()
        private set

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<HomeItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    // Kollisjonsfrie stabile ID-er (fokus beholdes når listen oppdateres).
    private val ids = HashMap<String, Long>()

    override fun getItemId(position: Int): Long {
        val key = when (val item = items[position]) {
            is HomeItem.Header -> "h:" + item.title
            is HomeItem.App -> (if (item.inFavorites) "f:" else "a:") + item.app.key
        }
        return ids.getOrPut(key) { ids.size.toLong() }
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is HomeItem.Header -> TYPE_HEADER
        is HomeItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
        } else {
            AppHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeItem.Header -> (holder as HeaderHolder).bind(item)
            is HomeItem.App -> (holder as AppHolder).bind(item)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        private val hint: TextView = view.findViewById(R.id.hint)

        fun bind(item: HomeItem.Header) {
            title.text = item.title
            hint.text = item.hint
            hint.visibility = if (item.hint.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    inner class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: View = view.findViewById(R.id.tile)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val iconBox: View = view.findViewById(R.id.icon_box)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val iconLabel: TextView = view.findViewById(R.id.icon_label)
        private val label: TextView = view.findViewById(R.id.label)
        private var boundKey: String? = null

        init {
            // Avrundede hjørner også på app-bannere
            tile.clipToOutline = true
            tile.setOnKeyListener { _, keyCode, event ->
                val pos = bindingAdapterPosition
                event.action == KeyEvent.ACTION_DOWN && pos != RecyclerView.NO_POSITION && onNavigate(pos, keyCode)
            }
            tile.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) FOCUS_SCALE else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIM_MS).start()
                v.translationZ = if (hasFocus) 8f else 0f
                label.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            }
        }

        fun bind(item: HomeItem.App) {
            val app = item.app
            boundKey = app.key
            label.text = app.label
            iconLabel.text = app.label
            tile.contentDescription = app.label
            tile.setOnClickListener { onClick(app) }
            tile.setOnLongClickListener { onLongClick(app, item.inFavorites); true }

            val cached = icons.cached(app)
            if (cached != null) {
                show(cached)
            } else {
                banner.visibility = View.GONE
                iconBox.visibility = View.VISIBLE
                icon.setImageDrawable(null)
                icons.load(app) { art -> if (boundKey == app.key) show(art) }
            }
        }

        private fun show(art: IconLoader.Art) {
            if (art.banner != null) {
                banner.setImageDrawable(art.banner)
                banner.visibility = View.VISIBLE
                iconBox.visibility = View.GONE
            } else {
                banner.setImageDrawable(null)
                banner.visibility = View.GONE
                iconBox.visibility = View.VISIBLE
                icon.setImageDrawable(art.icon)
            }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
        private const val FOCUS_SCALE = 1.08f
        private const val FOCUS_ANIM_MS = 120L
    }
}
