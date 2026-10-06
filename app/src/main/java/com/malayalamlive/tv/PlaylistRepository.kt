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

/**
 * Downloads every enabled playlist, caches each one on disk so the app still opens when a
 * source is down, and merges them into one de-duplicated channel list.
 */
class PlaylistRepository(private val context: Context) {

    private companion object {
        const val PREFS = "playlists"
        const val KEY_CUSTOM = "custom_urls"
        const val KEY_ENABLED = "enabled_sources"
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

    fun enabledSourceIds(): Set<String> =
        prefs.getStringSet(KEY_ENABLED, null)?.toSet()
            ?: Sources.BUILT_IN.filter { it.enabledByDefault }.mapTo(HashSet()) { it.id }

    fun setEnabledSourceIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED, ids).apply()
    }

    private fun sources(): List<PlaylistSource> {
        val enabled = enabledSourceIds()
        return Sources.BUILT_IN.filter { it.id in enabled } +
            customUrls().map { PlaylistSource(it, it, it) }
    }

    suspend fun load(forceRefresh: Boolean): Result = coroutineScope {
        val results = sources().map { source ->
            async(Dispatchers.IO) { source to fetch(source.url, forceRefresh) }
        }.awaitAll()

        val failed = results.filter { it.second == null }.map { it.first.title }
        val channels = withContext(Dispatchers.Default) {
            ChannelMerger.merge(
                results.mapNotNull { (source, text) -> text?.let { M3uParser.parse(it, source.defaultLanguage) } },
            )
        }
        Result(channels, failed)
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
