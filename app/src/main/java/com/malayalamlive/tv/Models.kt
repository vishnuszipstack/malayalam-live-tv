package com.malayalamlive.tv

/** One playable address for a channel. A channel can have several (different hosts / qualities). */
data class Stream(
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** Vertical resolution parsed from the name, e.g. 720 for "(720p)". */
    val quality: Int? = null,
    /** Marked "[Not 24/7]" by the playlist: only live at certain hours. */
    val notAlways: Boolean = false,
) {
    val isYouTube: Boolean get() = YOUTUBE.containsMatchIn(url)

    private companion object {
        val YOUTUBE = Regex("""^https?://(www\.|m\.)?(youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE)
    }
}

data class Channel(
    val name: String,
    val streams: List<Stream>,
    val logo: String? = null,
    val groups: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val geoBlocked: Boolean = false,
) {
    /** Stable key used to merge the same channel across playlists and to store favourites. */
    val id: String = normalize(name).ifEmpty { streams.firstOrNull()?.url.orEmpty() }

    val url: String get() = streams.first().url
    val playableStreams: List<Stream> get() = streams.filterNot { it.isYouTube }
    val isYouTube: Boolean get() = playableStreams.isEmpty()
    val bestQuality: Int? get() = playableStreams.mapNotNull { it.quality }.maxOrNull()

    val isMalayalam: Boolean
        get() = languages.any { it.equals(MALAYALAM, ignoreCase = true) } ||
            groups.any { it.equals(MALAYALAM, ignoreCase = true) } ||
            MalayalamChannels.matches(id)

    companion object {
        const val MALAYALAM = "Malayalam"
        fun normalize(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
    }
}

/**
 * Malayalam channels that the country-wide playlists list without a language tag.
 * Keys are [Channel.normalize]d names.
 */
object MalayalamChannels {
    private val KNOWN = setOf(
        "asianet", "asianethd", "asianetplus", "asianetmovies", "asianetmovieshd", "asianetnews",
        "asianetmiddleeast", "kairali", "kairalitv", "kairaliwe", "kairalinews", "kairaliarabia",
        "kairalipeople", "manoramanews", "mazhavilmanorama", "mazhavilmanoramahd", "mathrubhuminews",
        "mediaone", "mediaonetv", "reportertv", "24news", "in24news", "twentyfournews", "amritatv",
        "jaihindtv", "janamtv", "kaumudytv", "safaritv", "shalom", "shalomtv", "goodnesstv",
        "kappatv", "flowers", "flowerstv", "suryatv", "suryamovies", "suryamusic", "suryacomedy",
        "kochutv", "zeekeralam", "zeekeralamhd", "news18kerala", "kitevicters", "harvesttv",
        "powervisiontv", "ddmalayalam", "newsmalayalam", "news24malayalam", "keralavision",
    )

    fun matches(id: String): Boolean =
        id in KNOWN || "malayalam" in id || "kerala" in id
}
