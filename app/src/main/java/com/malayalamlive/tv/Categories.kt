package com.malayalamlive.tv

/** Builds the sidebar categories and the channel list behind each one. */
object Categories {
    const val MALAYALAM = Channel.MALAYALAM
    const val RECENT = "Recently watched"
    const val FAVORITES = "Favourites"
    const val ALL = "All channels"

    fun isFavorite(channel: Channel, favorites: Set<String>): Boolean =
        channel.id in favorites || channel.streams.any { it.url in favorites }

    fun build(channels: List<Channel>, favorites: Set<String>, recent: List<String>): List<String> {
        val ids = channels.mapTo(HashSet()) { it.id }
        val languages = channels.flatMap { it.languages }.distinct()
            .filterNot { it.equals(MALAYALAM, ignoreCase = true) }.sortedBy { it.lowercase() }
        val groups = channels.flatMap { it.groups }.distinct()
            .filterNot { it.equals(MALAYALAM, ignoreCase = true) || it in languages }.sortedBy { it.lowercase() }
        return buildList {
            if (channels.any { it.isMalayalam }) add(MALAYALAM)
            if (recent.any { it in ids }) add(RECENT)
            if (channels.any { isFavorite(it, favorites) }) add(FAVORITES)
            addAll(languages)
            addAll(groups)
            add(ALL)
        }
    }

    fun filter(category: String, channels: List<Channel>, favorites: Set<String>, recent: List<String>): List<Channel> =
        when (category) {
            MALAYALAM -> channels.filter { it.isMalayalam }
            RECENT -> {
                val byId = channels.associateBy { it.id }
                recent.mapNotNull { byId[it] }
            }
            FAVORITES -> channels.filter { isFavorite(it, favorites) }
            ALL -> channels
            else -> channels.filter { category in it.groups || category in it.languages }
        }

    /** Malayalam channels float to the top everywhere, then alphabetical. */
    fun sort(channels: List<Channel>): List<Channel> =
        channels.sortedWith(compareBy<Channel>({ !it.isMalayalam }, { it.name.lowercase() }))
}
