package com.malayalamlive.tv

import android.content.Context

/** In-memory hand-off of the current channel list to the player, plus persisted favourites. */
object ChannelStore {
    var playing: List<Channel> = emptyList()

    private const val PREFS = "channels"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_LAST = "last_url"

    fun favorites(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITES, emptySet())!!.toSet()

    /** Returns true if the channel is now a favourite. */
    fun toggleFavorite(context: Context, channel: Channel): Boolean {
        val current = favorites(context)
        val added = channel.url !in current
        val updated = if (added) current + channel.url else current - channel.url
        prefs(context).edit().putStringSet(KEY_FAVORITES, updated).apply()
        return added
    }

    fun lastUrl(context: Context): String? = prefs(context).getString(KEY_LAST, null)

    fun setLastUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_LAST, url).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
