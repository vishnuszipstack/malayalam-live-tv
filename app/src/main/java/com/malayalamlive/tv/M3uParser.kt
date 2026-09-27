package com.malayalamlive.tv

/**
 * Parses extended M3U playlists (the format used by iptv-org and most IPTV providers).
 *
 * Supported tags: `#EXTINF` attributes `tvg-logo`, `group-title`, `tvg-language`, plus the
 * VLC per-stream options `#EXTVLCOPT:http-user-agent` / `http-referrer`.
 */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String, defaultLanguage: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        var info: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    userAgent = null
                    referrer = null
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val value = opt.substringAfter('=', "").trim()
                    when (opt.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val inf = info
                    if (inf != null && isStreamUrl(line)) {
                        channels += toChannel(inf, line, userAgent, referrer, defaultLanguage)
                    }
                    info = null
                }
            }
        }
        return channels
    }

    private fun isStreamUrl(line: String): Boolean =
        line.startsWith("http://", true) || line.startsWith("https://", true) ||
            line.startsWith("rtmp://", true) || line.startsWith("rtsp://", true)

    private fun toChannel(
        inf: String,
        url: String,
        userAgent: String?,
        referrer: String?,
        defaultLanguage: String?,
    ): Channel {
        val attrs = attrRegex.findAll(inf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val name = inf.replace(attrRegex, "").substringAfter(',', "").trim()
            .ifEmpty { attrs["tvg-name"].orEmpty() }
            .ifEmpty { url }
        val languages = splitList(attrs["tvg-language"])
            .ifEmpty { listOfNotNull(defaultLanguage) }
        return Channel(
            name = name,
            url = url,
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            groups = splitList(attrs["group-title"]),
            languages = languages,
            userAgent = userAgent ?: attrs["http-user-agent"]?.takeIf { it.isNotBlank() },
            referrer = referrer ?: attrs["http-referrer"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun splitList(value: String?): List<String> =
        value?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() && it != "Undefined" }
            ?: emptyList()
}
