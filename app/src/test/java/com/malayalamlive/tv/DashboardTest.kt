package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTest {

    private fun channel(name: String, vararg groups: String, url: String = "https://x/${Channel.normalize(name)}.m3u8") =
        Channel(name, listOf(Stream(url)), groups = groups.toList())

    private val channels = Categories.sort(
        listOf(
            channel("Kairali TV", "Entertainment"),
            channel("Asianet News", "News"),
            channel("Manorama News", "News"),
            channel("Shalom", "Religious"),
            channel("NDTV India", "News"),
            channel("Zee News", "News"),
            channel("Cartoon Network", "Kids"),
            channel("DD National", "General", url = "https://www.youtube.com/doordarshan/live"),
        ),
    )

    @Test
    fun spotlightLeadsWithContinueWatchingThenFlagships() {
        val rows = Dashboard.rows(channels, emptySet(), recent = listOf("ndtvindia"))
        val featured = rows.first()
        assertTrue(featured.spotlight)
        assertEquals(listOf("NDTV India", "Asianet News", "Manorama News", "Kairali TV", "Shalom"), featured.channels.map { it.name })
        assertEquals(Dashboard.CONTINUE, featured.labels["ndtvindia"])
        assertEquals("Malayalam · News", featured.labels["asianetnews"])
    }

    @Test
    fun rowsAreThemedAndSkipEmptyOnes() {
        val rows = Dashboard.rows(channels, favorites = emptySet(), recent = emptyList())
        assertEquals(
            listOf("Featured", "Malayalam News", "Malayalam Entertainment", "Malayalam Devotional", "News", "Kids"),
            rows.map { it.title },
        )
        assertEquals(listOf("Asianet News", "Manorama News"), rows[1].channels.map { it.name })
        assertEquals(listOf("NDTV India", "Zee News"), rows[4].channels.map { it.name })
    }

    @Test
    fun includesHistoryAndFavourites() {
        val rows = Dashboard.rows(channels, favorites = setOf("shalom"), recent = listOf("zeenews", "kairalitv"))
        val byTitle = rows.associateBy { it.title }
        assertEquals(listOf("Zee News", "Kairali TV"), byTitle.getValue(Dashboard.CONTINUE).channels.map { it.name })
        assertEquals(listOf("Shalom"), byTitle.getValue(Categories.FAVORITES).channels.map { it.name })
    }

    @Test
    fun youTubeOnlyChannelsStayOffTheDashboard() {
        val rows = Dashboard.rows(channels, emptySet(), emptyList())
        assertFalse(rows.flatMap { it.channels }.any { it.isYouTube })
    }
}
