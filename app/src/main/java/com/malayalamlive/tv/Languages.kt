package com.malayalamlive.tv

/**
 * Works out a channel's language when the playlist doesn't say. Most country playlists carry no
 * `tvg-language`, so this falls back to the name: either the language appears in it
 * ("TV9 Kannada", "Raj News Malayalam") or it is a well-known channel of that language.
 */
object Languages {
    const val OTHER = "Other"

    /** Language word that appears in channel names → language. */
    private val NAME_WORDS = listOf(
        "malayalam" to "Malayalam", "keralam" to "Malayalam", "kerala" to "Malayalam",
        "tamil" to "Tamil", "telugu" to "Telugu", "kannada" to "Kannada",
        "hindi" to "Hindi", "bharat" to "Hindi", "marathi" to "Marathi", "gujarati" to "Gujarati",
        "punjabi" to "Punjabi", "bangla" to "Bengali", "bengali" to "Bengali",
        "odia" to "Odia", "oriya" to "Odia", "assam" to "Assamese", "urdu" to "Urdu",
        "english" to "English", "arabic" to "Arabic",
    )

    /** Well-known channels whose names don't contain their language. Keys are normalized names. */
    private val KNOWN = mapOf(
        "Tamil" to setOf(
            "suntv", "sunnews", "sunmusic", "ktv", "adithyatv", "kalaignartv", "kalaignarnews",
            "polimertv", "polimernews", "puthiyathalaimurai", "thanthitv", "news7tamil", "jayatv",
            "jayaplus", "vijaytv", "starvijay", "zeetamil", "colorstamil", "rajtv", "vasanthtv",
            "makkaltv", "news18tamilnadu", "sathiyamtv", "captainnews",
        ),
        "Telugu" to setOf(
            "etv", "etvtelangana", "etvandhrapradesh", "ntvtelugu", "ntv", "sakshitv", "abnandhrajyothy",
            "geminitv", "geminimovies", "starmaa", "zeetelugu", "tv5news", "v6news", "10tv", "hmtv",
            "news18telugu", "tv9telugu",
        ),
        "Kannada" to setOf(
            "udayatv", "udayanews", "colorskannada", "zeekannada", "publictv", "suvarnanews",
            "asianetsuvarnanews", "tv9kannada", "news18kannada", "dighvijaynews",
        ),
        "Hindi" to setOf(
            "aajtak", "abpnews", "zeenews", "ndtvindia", "indiatv", "republicbharat", "news18india",
            "ddnational", "ddnews", "tv9bharatvarsh", "news24", "goodnewstoday", "zeehindustan",
            "news18uputtarakhand", "ddkisan", "sansadtv", "zeecinema", "starplus", "sonytv",
            "colorstv", "sabtv", "andtv", "zeetv", "9xm", "9xjalwa", "b4umovies",
        ),
        "English" to setOf(
            "ndtv24x7", "cnnnews18", "wion", "indiatoday", "timesnow", "mirrornow", "republictv",
            "ddindia", "ndtvprofit", "cnbctv18", "etnow", "newsx", "bbcnews", "aljazeera",
            "aljazeeraenglish", "cnn", "dwenglish", "france24english", "skynews",
        ),
        "Bengali" to setOf("abpananda", "zee24ghanta", "news18bangla", "tv9bangla"),
        "Marathi" to setOf("tv9marathi", "news18lokmat", "abpmajha", "zee24taas", "saammarathi"),
    )

    fun detect(channel: Channel): String? {
        if (channel.isMalayalam) return Channel.MALAYALAM
        val id = channel.id
        NAME_WORDS.firstOrNull { (word, _) -> word in id }?.let { return it.second }
        return KNOWN.entries.firstOrNull { id in it.value }?.key
    }

    /** Explicit languages from the playlist first, otherwise the detected one, otherwise [OTHER]. */
    fun of(channel: Channel): List<String> {
        val known = channel.languages.ifEmpty { listOfNotNull(detect(channel)) }
        // A known Kerala channel counts as Malayalam even if a playlist tagged it otherwise.
        val withMalayalam = if (channel.isMalayalam && Channel.MALAYALAM !in known) listOf(Channel.MALAYALAM) + known else known
        return withMalayalam.ifEmpty { listOf(OTHER) }
    }
}
