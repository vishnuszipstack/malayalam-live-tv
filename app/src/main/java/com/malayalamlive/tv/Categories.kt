package com.malayalamlive.tv

/** Builds the left-hand category list: Malayalam first, then favourites, groups, and All. */
object Categories {
    const val MALAYALAM = "Malayalam"
    const val FAVORITES = "Favourites"
    const val ALL = "All channels"

    fun build(channels: List<Channel>, favorites: Set<String>): List<String> {
        val groups = channels.flatMap { it.groups }.distinct().sortedBy { it.lowercase() }
        return buildList {
            if (channels.any { it.isMalayalam }) add(MALAYALAM)
            if (favorites.isNotEmpty()) add(FAVORITES)
            addAll(groups.filter { it != MALAYALAM })
            add(ALL)
        }
    }

    fun filter(category: String, channels: List<Channel>, favorites: Set<String>): List<Channel> =
        when (category) {
            MALAYALAM -> channels.filter { it.isMalayalam }
            FAVORITES -> channels.filter { it.url in favorites }
            ALL -> channels
            else -> channels.filter { category in it.groups }
        }

    /** Malayalam channels float to the top everywhere, then alphabetical. */
    fun sort(channels: List<Channel>): List<Channel> =
        channels.sortedWith(compareBy<Channel>({ !it.isMalayalam }, { it.name.lowercase() }))
}
