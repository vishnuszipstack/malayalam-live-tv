package com.malayalamlive.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class PlaylistSource(val url: String, val defaultLanguage: String? = null)

/**
 * Downloads every configured M3U playlist, caches each one on disk so the app still opens
 * when a source is down, and merges them into one de-duplicated channel list.
 */
class PlaylistRepository(private val context: Context) {

    companion object {
        /** Public, community-maintained lists of free-to-air streams (github.com/iptv-org/iptv). */
        val DEFAULT_SOURCES = listOf(
            PlaylistSource("https://iptv-org.github.io/iptv/languages/mal.m3u", "Malayalam"),
            PlaylistSource("https://iptv-org.github.io/iptv/countries/in.m3u"),
        )
        private const val PREFS = "playlists"
        private const val KEY_CUSTOM = "custom_urls"
    }

    data class Result(val channels: List<Channel>, val failedSources: List<String>)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun customUrls(): List<String> = prefs.getStringSet(KEY_CUSTOM, emptySet())!!.sorted()

    fun addCustomUrl(url: String) {
        prefs.edit().putStringSet(KEY_CUSTOM, customUrls().toSet() + url.trim()).apply()
    }

    fun removeCustomUrl(url: String) {
        prefs.edit().putStringSet(KEY_CUSTOM, customUrls().toSet() - url).apply()
        cacheFile(url).delete()
    }

    fun sources(): List<PlaylistSource> = DEFAULT_SOURCES + customUrls().map { PlaylistSource(it) }

    suspend fun load(forceRefresh: Boolean): Result = coroutineScope {
        val results = sources().map { source ->
            async(Dispatchers.IO) { source to fetch(source.url, forceRefresh) }
        }.awaitAll()

        val failed = results.filter { it.second == null }.map { it.first.url }
        val merged = LinkedHashMap<String, Channel>()
        for ((source, text) in results) {
            if (text == null) continue
            for (channel in M3uParser.parse(text, source.defaultLanguage)) {
                val existing = merged[channel.url]
                // The same stream often appears in both the language and the country list;
                // keep one entry but remember every language/group it was listed under.
                merged[channel.url] = if (existing == null) channel else existing.copy(
                    logo = existing.logo ?: channel.logo,
                    groups = (existing.groups + channel.groups).distinct(),
                    languages = (existing.languages + channel.languages).distinct(),
                )
            }
        }
        Result(Categories.sort(merged.values.toList()), failed)
    }

    private suspend fun fetch(url: String, forceRefresh: Boolean): String? = withContext(Dispatchers.IO) {
        val cache = cacheFile(url)
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < TimeUnit.HOURS.toMillis(6)
        if (fresh && !forceRefresh) return@withContext cache.readText()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body!!.string().also { cache.writeText(it) }
            }
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else null
        }
    }

    private fun cacheFile(url: String) = File(context.cacheDir, "playlist_${url.hashCode()}.m3u")
}
