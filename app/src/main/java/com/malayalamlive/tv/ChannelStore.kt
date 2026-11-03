package com.malayalamlive.tv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

/** In-memory hand-off to the player, plus persisted favourites, history and picture settings. */
@OptIn(UnstableApi::class)
object ChannelStore {
    var playing: List<Channel> = emptyList()
    var playingTitle: String = ""

    private const val PREFS = "channels"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_RECENT = "recent"
    private const val MAX_RECENT = 15
    private const val KEY_HIDDEN = "hidden"

    fun favorites(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITES, emptySet())!!.toSet()

    /** Returns true if the channel is now a favourite. */
    fun toggleFavorite(context: Context, channel: Channel): Boolean {
        val current = favorites(context)
        val added = !Categories.isFavorite(channel, current)
        // Older versions stored stream URLs; remove those too so un-favouriting always works.
        val updated = if (added) current + channel.id else current - channel.id - channel.streams.map { it.url }.toSet()
        prefs(context).edit().putStringSet(KEY_FAVORITES, updated).apply()
        return added
    }

    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun addRecent(context: Context, channel: Channel) {
        val updated = (listOf(channel.id) + recent(context).filter { it != channel.id }).take(MAX_RECENT)
        prefs(context).edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    val RESIZE_MODES = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )

    /**
     * Defaults to "fill": Indian SD channels are commonly 16:9 sent as 720x576 without the aspect
     * flag, which "fit" shows as a narrow 5:4 picture with black bars.
     */
    fun resizeMode(context: Context, channel: Channel): Int =
        prefs(context).getInt("resize_${channel.id}", RESIZE_MODES[0])

    fun setResizeMode(context: Context, channel: Channel, mode: Int) {
        prefs(context).edit().putInt("resize_${channel.id}", mode).apply()
    }

    /** Channels the viewer removed; kept by id so they stay gone after playlist refreshes. */
    fun hidden(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet())!!.toSet()

    fun hide(context: Context, channel: Channel) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, hidden(context) + channel.id).apply()
    }

    fun unhide(context: Context, ids: Collection<String>) {
        prefs(context).edit().putStringSet(KEY_HIDDEN, hidden(context) - ids.toSet()).apply()
    }

    fun filter(context: Context): ChannelFilter {
        val p = prefs(context)
        return ChannelFilter(
            language = p.getString("filter_language", null),
            minQuality = p.getInt("filter_min_quality", 0),
            hideRegionLocked = p.getBoolean("filter_hide_region", false),
            hidePartTime = p.getBoolean("filter_hide_part_time", false),
            hideYouTube = p.getBoolean("filter_hide_youtube", false),
            sort = runCatching { ChannelFilter.Sort.valueOf(p.getString("filter_sort", null)!!) }
                .getOrDefault(ChannelFilter.Sort.DEFAULT),
        )
    }

    fun saveFilter(context: Context, filter: ChannelFilter) {
        prefs(context).edit()
            .putString("filter_language", filter.language)
            .putInt("filter_min_quality", filter.minQuality)
            .putBoolean("filter_hide_region", filter.hideRegionLocked)
            .putBoolean("filter_hide_part_time", filter.hidePartTime)
            .putBoolean("filter_hide_youtube", filter.hideYouTube)
            .putString("filter_sort", filter.sort.name)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
