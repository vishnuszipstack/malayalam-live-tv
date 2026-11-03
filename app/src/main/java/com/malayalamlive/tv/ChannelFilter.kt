package com.malayalamlive.tv

/** Narrows and orders the channel grid. Everything off/empty means "show all, default order". */
data class ChannelFilter(
    val language: String? = null,
    /** Minimum vertical resolution, e.g. 720 for HD. 0 = any. */
    val minQuality: Int = 0,
    val hideRegionLocked: Boolean = false,
    val hidePartTime: Boolean = false,
    val hideYouTube: Boolean = false,
    val sort: Sort = Sort.DEFAULT,
) {
    enum class Sort { DEFAULT, NAME, QUALITY, SOURCES }

    val isActive: Boolean get() = this != ChannelFilter()

    fun apply(channels: List<Channel>): List<Channel> {
        val kept = channels.filter { channel ->
            (language == null || language in Languages.of(channel)) &&
                (minQuality == 0 || (channel.bestQuality ?: 0) >= minQuality) &&
                !(hideRegionLocked && channel.geoBlocked) &&
                !(hidePartTime && channel.playableStreams.isNotEmpty() && channel.playableStreams.all { it.notAlways }) &&
                !(hideYouTube && channel.isYouTube)
        }
        return when (sort) {
            Sort.DEFAULT -> kept
            Sort.NAME -> kept.sortedBy { it.name.lowercase() }
            Sort.QUALITY -> kept.sortedWith(compareByDescending<Channel> { it.bestQuality ?: 0 }.thenBy { it.name.lowercase() })
            Sort.SOURCES -> kept.sortedWith(compareByDescending<Channel> { it.playableStreams.size }.thenBy { it.name.lowercase() })
        }
    }

    companion object {
        /** Languages present in [channels] with their counts, most common first, [Languages.OTHER] last. */
        fun languageCounts(channels: List<Channel>): List<Pair<String, Int>> =
            channels.flatMap { Languages.of(it) }.groupingBy { it }.eachCount().entries
                .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == Languages.OTHER }.thenByDescending { it.value }.thenBy { it.key })
                .map { it.key to it.value }
    }
}
