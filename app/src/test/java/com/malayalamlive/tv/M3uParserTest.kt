package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val sample = """
        #EXTM3U x-tvg-url="https://example.com/epg.xml"
        #EXTINF:-1 tvg-id="AsianetNews.in" tvg-logo="https://example.com/asianet.png" group-title="News",Asianet News (720p)
        https://example.com/asianet/index.m3u8
        #EXTINF:-1 tvg-id="Kairali.in" tvg-language="Malayalam" group-title="Entertainment;General",Kairali TV
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 TestAgent
        #EXTVLCOPT:http-referrer=https://kairali.example/
        http://example.com/kairali.m3u8
        #EXTINF:-1 tvg-id="DDNational.in" group-title="Undefined",DD National
        https://example.com/dd.m3u8

        #EXTINF:-1,Broken entry with no URL
        #EXTINF:-1 tvg-name="Name From Attr",
        https://example.com/noname.m3u8
    """.trimIndent()

    @Test
    fun parsesNamesUrlsAndLogos() {
        val channels = M3uParser.parse(sample)
        assertEquals(listOf("Asianet News (720p)", "Kairali TV", "DD National", "Name From Attr"), channels.map { it.name })
        assertEquals("https://example.com/asianet/index.m3u8", channels[0].url)
        assertEquals("https://example.com/asianet.png", channels[0].logo)
        assertNull(channels[1].logo)
    }

    @Test
    fun splitsGroupsAndDropsUndefined() {
        val channels = M3uParser.parse(sample)
        assertEquals(listOf("Entertainment", "General"), channels[1].groups)
        assertTrue(channels[2].groups.isEmpty())
    }

    @Test
    fun readsVlcOptionsOnlyForTheirOwnEntry() {
        val channels = M3uParser.parse(sample)
        assertEquals("Mozilla/5.0 TestAgent", channels[1].userAgent)
        assertEquals("https://kairali.example/", channels[1].referrer)
        assertNull(channels[2].userAgent)
    }

    @Test
    fun detectsMalayalamFromAttributeOrSourceDefault() {
        val plain = M3uParser.parse(sample)
        assertTrue(plain[1].isMalayalam)
        assertFalse(plain[0].isMalayalam)

        val fromMalayalamList = M3uParser.parse(sample, defaultLanguage = "Malayalam")
        assertTrue(fromMalayalamList[0].isMalayalam)
    }

    @Test
    fun categoriesPutMalayalamFirstAndSortMalayalamChannelsToTop() {
        val channels = Categories.sort(M3uParser.parse(sample))
        assertEquals("Kairali TV", channels.first().name)

        val categories = Categories.build(channels, favorites = setOf(channels.last().url))
        assertEquals(Categories.MALAYALAM, categories[0])
        assertEquals(Categories.FAVORITES, categories[1])
        assertEquals(Categories.ALL, categories.last())
        assertEquals(1, Categories.filter(Categories.FAVORITES, channels, setOf(channels.last().url)).size)
        assertEquals(2, Categories.filter("News", channels + channels[1].copy(url = "x", groups = listOf("News")), emptySet()).size)
    }
}
