package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    private fun channel(name: String, groups: List<String> = emptyList(), languages: List<String> = emptyList()) =
        Channel(name, listOf(Stream("https://x/${Channel.normalize(name)}.m3u8")), groups = groups, languages = languages)

    private val channels = Categories.sort(
        listOf(
            channel("NDTV India", groups = listOf("News")),
            channel("Kairali TV", groups = listOf("Entertainment")),
            channel("Sun TV", languages = listOf("Tamil")),
            channel("Manorama News", groups = listOf("News")),
        ),
    )

    @Test
    fun orderIsMalayalamRecentFavouritesLanguagesGroupsAll() {
        val categories = Categories.build(channels, favorites = setOf("ndtvindia"), recent = listOf("suntv"))
        assertEquals(
            listOf("Malayalam", "Recently watched", "Favourites", "Tamil", "Entertainment", "News", "All channels"),
            categories,
        )
    }

    @Test
    fun hidesRecentAndFavouritesWhenEmpty() {
        val categories = Categories.build(channels, favorites = setOf("gone"), recent = listOf("gone"))
        assertFalse(Categories.RECENT in categories)
        assertFalse(Categories.FAVORITES in categories)
    }

    @Test
    fun recentKeepsWatchOrder() {
        val recent = Categories.filter(Categories.RECENT, channels, emptySet(), listOf("suntv", "kairalitv"))
        assertEquals(listOf("Sun TV", "Kairali TV"), recent.map { it.name })
    }

    @Test
    fun favouritesMatchNewIdsAndOldStreamUrls() {
        val ndtv = channels.first { it.name == "NDTV India" }
        assertTrue(Categories.isFavorite(ndtv, setOf("ndtvindia")))
        assertTrue(Categories.isFavorite(ndtv, setOf(ndtv.url)))
    }

    @Test
    fun filtersByGroupAndLanguage() {
        assertEquals(listOf("Manorama News", "NDTV India"), Categories.filter("News", channels, emptySet(), emptyList()).map { it.name })
        assertEquals(listOf("Sun TV"), Categories.filter("Tamil", channels, emptySet(), emptyList()).map { it.name })
        assertEquals(listOf("Kairali TV", "Manorama News"), Categories.filter("Malayalam", channels, emptySet(), emptyList()).map { it.name })
    }
}
