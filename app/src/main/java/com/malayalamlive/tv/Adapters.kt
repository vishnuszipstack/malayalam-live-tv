package com.malayalamlive.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.malayalamlive.tv.databinding.ItemChannelBinding
import com.malayalamlive.tv.databinding.ItemHomeRowBinding
import com.malayalamlive.tv.databinding.ItemPlayerChannelBinding
import com.malayalamlive.tv.databinding.ItemSidebarBinding
import com.malayalamlive.tv.databinding.ItemSpotlightBinding

data class SidebarItem(val key: String, val label: String, val count: Int? = null, val isAction: Boolean = false)

class SidebarAdapter(private val onClick: (SidebarItem) -> Unit) : RecyclerView.Adapter<SidebarAdapter.Holder>() {

    var items: List<SidebarItem> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    /** The category whose channels are showing; highlighted even when focus is elsewhere. */
    var activeKey: String? = null
        set(value) {
            val old = items.indexOfFirst { it.key == field }
            field = value
            // Rebind only the two affected rows: a full refresh would steal D-pad focus.
            if (old >= 0) notifyItemChanged(old)
            items.indexOfFirst { it.key == value }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    class Holder(val binding: ItemSidebarBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSidebarBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            label.text = item.label
            count.text = item.count?.toString().orEmpty()
            divider.visibility = if (item.isAction && items.getOrNull(position - 1)?.isAction == false) View.VISIBLE else View.GONE
            row.isActivated = item.key == activeKey
            row.setOnClickListener { onClick(item) }
        }
    }
}

class ChannelAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
    /** Fixed card width for horizontal dashboard rows; null fills the grid column. */
    private val cardWidthPx: Int? = null,
    private val showNumbers: Boolean = true,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

    var items: List<Channel> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var favorites: Set<String> = emptySet()

    class Holder(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        cardWidthPx?.let { holder.binding.root.layoutParams.width = it }
        holder.binding.root.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 12f else 0f).setDuration(150).start()
        }
        return holder
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = items[position]
        with(holder.binding) {
            number.text = (position + 1).toString()
            number.visibility = if (showNumbers) View.VISIBLE else View.GONE
            name.text = channel.name
            meta.text = channelMeta(root.context, channel)
            favorite.visibility = if (Categories.isFavorite(channel, favorites)) View.VISIBLE else View.GONE
            badge.text = when {
                channel.isYouTube -> "YouTube"
                (channel.bestQuality ?: 0) >= 720 -> "HD"
                else -> ""
            }
            badge.visibility = if (badge.text.isEmpty()) View.GONE else View.VISIBLE
            logo.load(channel.logo) {
                crossfade(true)
                size(320, 180)
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
            root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            root.setOnLongClickListener { onLongClick(holder.bindingAdapterPosition); true }
        }
    }

    private fun channelMeta(context: android.content.Context, channel: Channel): String {
        val parts = mutableListOf<String>()
        channel.groups.firstOrNull()?.let { parts += it }
        val sources = channel.playableStreams.size
        if (sources > 1) parts += context.getString(R.string.sources_count, sources)
        if (channel.geoBlocked) parts += context.getString(R.string.india_only)
        return parts.joinToString(" · ")
    }
}

class PlayerListAdapter(private val onClick: (Int) -> Unit) : RecyclerView.Adapter<PlayerListAdapter.Holder>() {

    var items: List<Channel> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var current: Int = -1
        set(value) {
            val old = field
            field = value
            if (old in items.indices) notifyItemChanged(old)
            if (value in items.indices) notifyItemChanged(value)
        }

    class Holder(val binding: ItemPlayerChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPlayerChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = items[position]
        with(holder.binding) {
            number.text = (position + 1).toString()
            name.text = channel.name
            row.isActivated = position == current
            logo.load(channel.logo) {
                size(160, 90)
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
            row.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        }
    }
}

/** Large featured cards for the top of the dashboard. */
class SpotlightAdapter(
    private val row: HomeRow,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<SpotlightAdapter.Holder>() {

    class Holder(val binding: ItemSpotlightBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(ItemSpotlightBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        holder.binding.root.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            v.animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) 16f else 0f).setDuration(150).start()
        }
        return holder
    }

    override fun getItemCount() = row.channels.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = row.channels[position]
        with(holder.binding) {
            label.text = row.labels[channel.id].orEmpty()
            label.visibility = if (label.text.isEmpty()) View.GONE else View.VISIBLE
            label.isActivated = row.labels[channel.id] == Dashboard.CONTINUE
            name.text = channel.name
            meta.text = buildList {
                channel.bestQuality?.let { add(if (it >= 720) "HD" else "${it}p") }
                if (channel.playableStreams.size > 1) add(root.context.getString(R.string.sources_count, channel.playableStreams.size))
            }.joinToString(" · ")
            logo.load(channel.logo) {
                crossfade(true)
                size(480, 270)
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
            root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            root.setOnLongClickListener { onLongClick(holder.bindingAdapterPosition); true }
        }
    }
}

/** The dashboard: one titled, horizontally scrolling row per [HomeRow]. */
class HomeAdapter(
    private val onPlay: (HomeRow, Int) -> Unit,
    private val onFavorite: (Channel) -> Unit,
) : RecyclerView.Adapter<HomeAdapter.Holder>() {

    var rows: List<HomeRow> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var favorites: Set<String> = emptySet()

    class Holder(val binding: ItemHomeRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemHomeRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.binding.root.context
        holder.binding.title.text = row.title
        holder.binding.count.text = if (row.spotlight) "" else row.channels.size.toString()
        holder.binding.row.adapter = if (row.spotlight) {
            SpotlightAdapter(row, { onPlay(row, it) }, { row.channels.getOrNull(it)?.let(onFavorite) })
        } else {
            ChannelAdapter(
                onClick = { onPlay(row, it) },
                onLongClick = { row.channels.getOrNull(it)?.let(onFavorite) },
                cardWidthPx = context.resources.getDimensionPixelSize(R.dimen.home_card_width),
                showNumbers = false,
            ).also {
                it.favorites = favorites
                it.items = row.channels
            }
        }
        holder.binding.row.selectedPosition = 0
    }
}
