package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchTest {

    private fun channel(name: String, vararg groups: String, languages: List<String> = emptyList()) =
        Channel(name, listOf(Stream("https://x/${Channel.normalize(name)}.m3u8")), groups = groups.toList(), languages = languages)

    private val channels = listOf(
        channel("Manorama News", "News"),
        channel("Mazhavil Manorama", "Entertainment"),
        channel("MediaOne", "News"),
        channel("Sun News", "News"),
        channel("Sun TV", "Entertainment"),
        channel("Asianet News", "News"),
        channel("NDTV India", "News", languages = listOf("Hindi")),
    )

    private fun names(query: String) = ChannelSearch.search(channels, query).map { it.name }

    @Test
    fun matchesWordStartsAndRanksNameStartFirst() {
        assertEquals(listOf("Manorama News", "Mazhavil Manorama"), names("manorama"))
        assertEquals(listOf("Sun News", "Sun TV"), names("sun"))
    }

    @Test
    fun spokenSpacingStillFindsSquashedNames() {
        assertEquals(listOf("MediaOne"), names("media one"))
        assertEquals(listOf("Asianet News"), names("Asia net news"))
    }

    @Test
    fun combinesNameLanguageAndCategory() {
        assertEquals(listOf("Sun News"), names("tamil news"))
        assertEquals(listOf("NDTV India"), names("hindi"))
        assertTrue("Asianet News" in names("malayalam news"))
    }

    @Test
    fun emptyOrPunctuationOnlyQueryFindsNothing() {
        assertTrue(names("").isEmpty())
        assertTrue(names("  ?? ").isEmpty())
    }
}
