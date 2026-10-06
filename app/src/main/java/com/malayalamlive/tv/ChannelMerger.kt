package com.malayalamlive.tv

/**
 * Collapses the same channel listed several times (across playlists, or once per quality) into one
 * entry whose streams act as fallbacks: if the first address fails the player tries the next.
 */
object ChannelMerger {

    fun merge(lists: List<List<Channel>>): List<Channel> {
        val merged = LinkedHashMap<String, Channel>()
        for (channel in lists.flatten()) {
            val existing = merged[channel.id]
            merged[channel.id] = if (existing == null) channel else existing.copy(
                streams = (existing.streams + channel.streams).distinctBy { it.url },
                logo = existing.logo ?: channel.logo,
                groups = (existing.groups + channel.groups).distinct(),
                languages = (existing.languages + channel.languages).distinct(),
                // Only flag it when every listing says so; one open stream is enough to watch.
                geoBlocked = existing.geoBlocked && channel.geoBlocked,
            )
        }
        return Categories.sort(merged.values.map { it.copy(streams = orderStreams(it.streams)) })
    }

    /** Always-on streams first, then highest quality; YouTube links last (they open externally). */
    private fun orderStreams(streams: List<Stream>): List<Stream> =
        streams.sortedWith(compareBy<Stream>({ it.isYouTube }, { it.notAlways }, { -(it.quality ?: 0) }))
}
