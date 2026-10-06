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
        #EXTINF:-1 tvg-id="Kairali.in" tvg-language="Malayalam" group-title="Entertainment;General",Kairali TV (576p) [Not 24/7]
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 TestAgent
        #EXTVLCOPT:http-referrer=https://kairali.example/
        http://example.com/kairali.m3u8
        #EXTINF:-1 tvg-id="DDNational.in" group-title="Undefined",DD National Ⓨ
        https://www.youtube.com/doordarshan/live
        #EXTINF:-1,Zee News (1080p) [Geo-blocked]
        https://example.com/zee.m3u8

        #EXTINF:-1,Broken entry with no URL
        #EXTINF:-1 tvg-name="Name From Attr",
        https://example.com/noname.m3u8
    """.trimIndent()

    @Test
    fun cleansNamesAndKeepsQualityAndTags() {
        val channels = M3uParser.parse(sample)
        assertEquals(
            listOf("Asianet News", "Kairali TV", "DD National", "Zee News", "Name From Attr"),
            channels.map { it.name },
        )
        assertEquals(720, channels[0].streams.single().quality)
        assertTrue(channels[1].streams.single().notAlways)
        assertTrue(channels[3].geoBlocked)
        assertFalse(channels[0].geoBlocked)
        assertEquals("https://example.com/asianet.png", channels[0].logo)
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
        assertEquals("Mozilla/5.0 TestAgent", channels[1].streams.single().userAgent)
        assertEquals("https://kairali.example/", channels[1].streams.single().referrer)
        assertNull(channels[2].streams.single().userAgent)
    }

    @Test
    fun detectsYouTubeOnlyChannels() {
        val channels = M3uParser.parse(sample)
        assertTrue(channels[2].isYouTube)
        assertFalse(channels[0].isYouTube)
    }

    @Test
    fun detectsMalayalamFromAttributeSourceOrKnownName() {
        val plain = M3uParser.parse(sample)
        assertTrue(plain[0].isMalayalam) // "Asianet News" is a known Malayalam channel
        assertTrue(plain[1].isMalayalam) // tvg-language
        assertFalse(plain[3].isMalayalam)

        val fromMalayalamList = M3uParser.parse(sample, defaultLanguage = "Malayalam")
        assertTrue(fromMalayalamList[3].isMalayalam)
    }

    @Test
    fun knownNamesDoNotCatchOtherLanguageSisterChannels() {
        assertTrue(MalayalamChannels.matches(Channel.normalize("Kappa TV")))
        assertTrue(MalayalamChannels.matches(Channel.normalize("Raj News Malayalam")))
        assertTrue(MalayalamChannels.matches(Channel.normalize("Harvest TV Keralam")))
        assertFalse(MalayalamChannels.matches(Channel.normalize("Asianet Suvarna News")))
        assertFalse(MalayalamChannels.matches(Channel.normalize("Asianet News Telugu")))
    }
}
