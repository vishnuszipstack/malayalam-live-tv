package com.malayalamlive.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageIndexTest {

    private val hindi = """
        #EXTM3U
        #EXTINF:-1 tvg-id="Sanskar.in",Sanskar TV (576p)
        https://a/sanskar.m3u8
        #EXTINF:-1,Bilingual News
        https://a/bilingual.m3u8
    """.trimIndent()

    private val english = """
        #EXTM3U
        #EXTINF:-1,Bilingual News
        https://b/bilingual.m3u8
        #EXTINF:-1,Travel XP
        https://b/travel.m3u8
    """.trimIndent()

    private val index = LanguageIndex.build(listOf("Hindi" to hindi, "English" to english))

    @Test
    fun firstListedLanguageWins() {
        assertEquals("Hindi", index["sanskartv"])
        assertEquals("Hindi", index["bilingualnews"])
        assertEquals("English", index["travelxp"])
    }

    @Test
    fun onlyFillsMissingLanguages() {
        val channels = listOf(
            Channel("Sanskar TV", listOf(Stream("https://x/1"))),
            Channel("Travel XP", listOf(Stream("https://x/2")), languages = listOf("Tamil")),
            Channel("Unknown One", listOf(Stream("https://x/3"))),
        )
        val annotated = LanguageIndex.annotate(channels, index)
        assertEquals(listOf(listOf("Hindi"), listOf("Tamil"), emptyList()), annotated.map { it.languages })
    }

    @Test
    fun knownMalayalamChannelKeepsMalayalamEvenIfTaggedEnglish() {
        val kite = Channel("KITE Victers", listOf(Stream("https://x/4")), languages = listOf("English"))
        assertEquals(listOf("Malayalam", "English"), Languages.of(kite))
    }
}
