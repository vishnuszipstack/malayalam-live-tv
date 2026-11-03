package com.malayalamlive.tv

/** One horizontal row on the home dashboard. */
data class HomeRow(
    val title: String,
    val channels: List<Channel>,
    val spotlight: Boolean = false,
    /** Small label per channel id, shown on spotlight cards ("Continue watching", "Malayalam · News"). */
    val labels: Map<String, String> = emptyMap(),
)

/** Builds the home screen: a featured row of large cards, then themed rows of channels. */
object Dashboard {
    const val HOME = "Home"
    const val CONTINUE = "Continue watching"
    const val FEATURED = "Featured"

    private const val SPOTLIGHT_SIZE = 6
    private const val ROW_LIMIT = 30
    private const val OTHER_GROUP_ROWS = 6
    private const val NEWS = "News"
    private const val RELIGIOUS = "Religious"

    /** The best-known Malayalam channels, featured first when present. */
    private val FLAGSHIPS = listOf(
        "asianetnews", "manoramanews", "mathrubhuminews", "24news", "mediaone", "reportertv",
        "kairalitv", "mazhavilmanorama", "asianet", "amritatv", "safaritv", "kappatv",
    )

    fun rows(channels: List<Channel>, favorites: Set<String>, recent: List<String>): List<HomeRow> {
        val watchable = channels.filterNot { it.isYouTube }
        val malayalam = watchable.filter { it.isMalayalam }
        val recentChannels = Categories.filter(Categories.RECENT, watchable, favorites, recent)

        val rows = mutableListOf<HomeRow>()
        spotlight(malayalam, recentChannels)?.let { rows += it }
        rows += HomeRow(CONTINUE, recentChannels)
        rows += HomeRow(Categories.FAVORITES, watchable.filter { Categories.isFavorite(it, favorites) })
        rows += HomeRow("Malayalam News", malayalam.filter { NEWS in it.groups })
        rows += HomeRow("Malayalam Entertainment", malayalam.filter { NEWS !in it.groups && RELIGIOUS !in it.groups })
        rows += HomeRow("Malayalam Devotional", malayalam.filter { RELIGIOUS in it.groups })

        val others = watchable.filterNot { it.isMalayalam }
        others.flatMap { it.groups }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(OTHER_GROUP_ROWS)
            .forEach { (group, _) -> rows += HomeRow(group, others.filter { group in it.groups }) }

        return rows.filter { it.channels.isNotEmpty() }
            .map { if (it.spotlight) it else it.copy(channels = it.channels.take(ROW_LIMIT)) }
    }

    private fun spotlight(malayalam: List<Channel>, recent: List<Channel>): HomeRow? {
        val labels = LinkedHashMap<String, String>()
        val picks = mutableListOf<Channel>()
        fun add(channel: Channel, label: String) {
            if (picks.size < SPOTLIGHT_SIZE && picks.none { it.id == channel.id }) {
                picks += channel
                labels[channel.id] = label
            }
        }
        recent.firstOrNull()?.let { add(it, CONTINUE) }
        val byId = malayalam.associateBy { it.id }
        FLAGSHIPS.mapNotNull { byId[it] }.forEach { add(it, label(it)) }
        malayalam.forEach { add(it, label(it)) }
        return if (picks.isEmpty()) null else HomeRow(FEATURED, picks, spotlight = true, labels = labels)
    }

    private fun label(channel: Channel): String =
        channel.groups.firstOrNull()?.let { "Malayalam · $it" } ?: Channel.MALAYALAM
}
