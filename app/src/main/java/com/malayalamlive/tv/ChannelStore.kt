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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
