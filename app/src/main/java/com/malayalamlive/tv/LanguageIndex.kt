package com.malayalamlive.tv

/**
 * Channel languages from iptv-org's per-language playlists. These are used only as a lookup
 * (channel id → language), not as channel sources, so they add no channels of their own.
 */
object LanguageIndex {
    private const val BASE = "https://iptv-org.github.io/iptv/languages"

    /** In priority order: a channel listed under several languages takes the first one here. */
    val LISTS = listOf(
        "mal" to "Malayalam", "tam" to "Tamil", "tel" to "Telugu", "kan" to "Kannada",
        "hin" to "Hindi", "ben" to "Bengali", "mar" to "Marathi", "guj" to "Gujarati",
        "pan" to "Punjabi", "ori" to "Odia", "asm" to "Assamese", "urd" to "Urdu", "eng" to "English",
    ).map { (code, language) -> "$BASE/$code.m3u" to language }

    /** [playlists] maps a language to that language's playlist text. */
    fun build(playlists: List<Pair<String, String>>): Map<String, String> {
        val index = HashMap<String, String>()
        for ((language, text) in playlists) {
            for (channel in M3uParser.parse(text)) index.putIfAbsent(channel.id, language)
        }
        return index
    }

    /** Fills in the language of channels whose playlist didn't give one. */
    fun annotate(channels: List<Channel>, index: Map<String, String>): List<Channel> =
        channels.map { channel ->
            if (channel.languages.isNotEmpty()) channel
            else index[channel.id]?.let { channel.copy(languages = listOf(it)) } ?: channel
        }
}
