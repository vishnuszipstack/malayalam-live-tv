package com.malayalamlive.tv

/**
 * Finds channels for a typed or spoken query. Every word must match something about the channel:
 * the start of a word in its name, any part of its squashed name ("media one" finds "MediaOne"),
 * its language or its category. So "tamil news" finds Tamil news channels and "manorama" finds
 * both Manorama News and Mazhavil Manorama.
 */
object ChannelSearch {

    fun search(channels: List<Channel>, query: String): List<Channel> {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val squashed = Channel.normalize(query)

        return channels
            .filter { channel -> words.all { matches(channel, it) } || (squashed.length >= 3 && squashed in channel.id) }
            .sortedWith(
                compareBy<Channel>(
                    { rank(it, words, squashed) },
                    { !it.isMalayalam },
                    { it.name.lowercase() },
                ),
            )
    }

    private fun matches(channel: Channel, word: String): Boolean {
        val nameWords = channel.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        return nameWords.any { it.startsWith(word) } ||
            (word.length >= 3 && word in channel.id) ||
            Languages.of(channel).any { it.lowercase().startsWith(word) } ||
            channel.groups.any { it.lowercase().startsWith(word) }
    }

    /** 0 = name starts with the query, 1 = a name word starts with it, 2 = matched elsewhere. */
    private fun rank(channel: Channel, words: List<String>, squashed: String): Int = when {
        channel.id.startsWith(squashed) -> 0
        channel.name.lowercase().split(' ').any { it.startsWith(words.first()) } -> 1
        else -> 2
    }
}
