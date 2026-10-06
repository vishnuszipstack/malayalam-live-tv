package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergerTest {

    private val malayalamList = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://logo/kairali.png" group-title="Entertainment",Kairali TV (576p)
        https://a.example/kairali.m3u8
    """.trimIndent()

    private val indiaList = """
        #EXTM3U
        #EXTINF:-1 group-title="General",Kairali TV (1080p) [Not 24/7]
        https://b.example/kairali-hd.m3u8
        #EXTINF:-1 group-title="General",Kairali TV (720p)
        https://c.example/kairali.m3u8
        #EXTINF:-1 group-title="News",Media One (576p) [Geo-blocked]
        https://d.example/mediaone.m3u8
        #EXTINF:-1 group-title="News",MediaOne Ⓨ
        https://www.youtube.com/@MediaoneTVLive/live
        #EXTINF:-1 group-title="News",NDTV India
        https://e.example/ndtv.m3u8
        #EXTINF:-1 group-title="News",Kairali TV (576p)
        https://a.example/kairali.m3u8
    """.trimIndent()

    private val merged = ChannelMerger.merge(
        listOf(M3uParser.parse(malayalamList, "Malayalam"), M3uParser.parse(indiaList)),
    )

    @Test
    fun mergesSameChannelAcrossListsAndQualities() {
        assertEquals(listOf("Kairali TV", "Media One", "NDTV India"), merged.map { it.name })
        val kairali = merged.first()
        assertEquals(3, kairali.streams.size) // duplicate URL dropped
        assertEquals("https://logo/kairali.png", kairali.logo)
        assertEquals(listOf("Entertainment", "General", "News"), kairali.groups)
        assertTrue(kairali.isMalayalam)
    }

    @Test
    fun ordersStreamsAlwaysOnFirstThenByQuality() {
        val urls = merged.first().streams.map { it.url }
        assertEquals(
            listOf("https://c.example/kairali.m3u8", "https://a.example/kairali.m3u8", "https://b.example/kairali-hd.m3u8"),
            urls,
        )
    }

    @Test
    fun youTubeStreamIsAFallbackNotTheMainStream() {
        val mediaOne = merged.first { it.name == "Media One" }
        assertFalse(mediaOne.isYouTube)
        assertEquals(1, mediaOne.playableStreams.size)
        assertTrue(mediaOne.streams.last().isYouTube)
        // One of the two listings is open, so it is not flagged as region locked.
        assertFalse(mediaOne.geoBlocked)
    }

    @Test
    fun malayalamChannelsSortFirst() {
        assertEquals(listOf(true, true, false), merged.map { it.isMalayalam })
    }
}
