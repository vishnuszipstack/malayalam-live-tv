package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFilterTest {

    private fun channel(
        name: String,
        quality: Int? = null,
        geo: Boolean = false,
        partTime: Boolean = false,
        languages: List<String> = emptyList(),
        url: String = "https://x/${Channel.normalize(name)}.m3u8",
        extraStreams: Int = 0,
    ) = Channel(
        name,
        listOf(Stream(url, quality = quality, notAlways = partTime)) +
            (1..extraStreams).map { Stream("https://y/$it/${Channel.normalize(name)}.m3u8") },
        languages = languages,
        geoBlocked = geo,
    )

    private val channels = listOf(
        channel("Asianet News", quality = 1080, extraStreams = 2),
        channel("Sun TV", quality = 576),
        channel("TV9 Kannada", quality = 720, geo = true),
        channel("Aaj Tak", partTime = true),
        channel("Mystery Channel"),
        channel("Kalaignar TV", languages = listOf("Tamil")),
        channel("DD National", url = "https://www.youtube.com/doordarshan/live"),
    )

    @Test
    fun detectsLanguageFromNameOrWellKnownChannel() {
        assertEquals("Malayalam", Languages.detect(channels[0]))
        assertEquals("Tamil", Languages.detect(channels[1]))
        assertEquals("Kannada", Languages.detect(channels[2]))
        assertEquals("Hindi", Languages.detect(channels[3]))
        assertNull(Languages.detect(channels[4]))
        assertEquals(listOf(Languages.OTHER), Languages.of(channels[4]))
    }

    @Test
    fun filtersByLanguage() {
        val tamil = ChannelFilter(language = "Tamil").apply(channels)
        assertEquals(listOf("Sun TV", "Kalaignar TV"), tamil.map { it.name })
    }

    @Test
    fun filtersByQualityAndFlags() {
        assertEquals(listOf("Asianet News", "TV9 Kannada"), ChannelFilter(minQuality = 720).apply(channels).map { it.name })
        val clean = ChannelFilter(hideRegionLocked = true, hidePartTime = true, hideYouTube = true).apply(channels)
        assertEquals(listOf("Asianet News", "Sun TV", "Mystery Channel", "Kalaignar TV"), clean.map { it.name })
    }

    @Test
    fun sorts() {
        assertEquals("Aaj Tak", ChannelFilter(sort = ChannelFilter.Sort.NAME).apply(channels).first().name)
        assertEquals("Asianet News", ChannelFilter(sort = ChannelFilter.Sort.QUALITY).apply(channels).first().name)
        assertEquals("Asianet News", ChannelFilter(sort = ChannelFilter.Sort.SOURCES).apply(channels).first().name)
    }

    @Test
    fun defaultFilterKeepsEverythingInOrder() {
        assertFalse(ChannelFilter().isActive)
        assertTrue(ChannelFilter(hideYouTube = true).isActive)
        assertEquals(channels, ChannelFilter().apply(channels))
    }

    @Test
    fun languageCountsPutOtherLast() {
        val counts = ChannelFilter.languageCounts(channels)
        // DD National counts as Hindi; ties are alphabetical.
        assertEquals(listOf("Hindi" to 2, "Tamil" to 2), counts.take(2))
        assertEquals(Languages.OTHER, counts.last().first)
    }
}
