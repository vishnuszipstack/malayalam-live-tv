package com.malayalamlive.tv

/**
 * Parses extended M3U playlists (the format used by iptv-org, Free-TV and most IPTV providers).
 *
 * Supported: `#EXTINF` attributes `tvg-logo`, `group-title`, `tvg-language`, and the VLC
 * per-stream options `#EXTVLCOPT:http-user-agent` / `http-referrer`. Names are cleaned of the
 * "(720p)", "[Geo-blocked]" and "Ⓨ" decorations, which are kept as structured fields instead.
 */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val qualityRegex = Regex("""\((\d{3,4})p\)""")
    private val tagRegex = Regex("""\[([^\]]*)\]""")
    private val spaces = Regex("""\s+""")

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
        val rawName = inf.replace(attrRegex, "").substringAfter(',', "").trim()
            .ifEmpty { attrs["tvg-name"].orEmpty() }
        val quality = qualityRegex.find(rawName)?.groupValues?.get(1)?.toIntOrNull()
        val tags = tagRegex.findAll(rawName).map { it.groupValues[1].trim().lowercase() }.toList()
        val name = rawName.replace(qualityRegex, "").replace(tagRegex, "").replace("Ⓨ", "")
            .replace(spaces, " ").trim()
            .ifEmpty { url }

        return Channel(
            name = name,
            streams = listOf(
                Stream(
                    url = url,
                    userAgent = userAgent ?: attrs["http-user-agent"]?.takeIf { it.isNotBlank() },
                    referrer = referrer ?: attrs["http-referrer"]?.takeIf { it.isNotBlank() },
                    quality = quality,
                    notAlways = "not 24/7" in tags,
                ),
            ),
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            groups = splitList(attrs["group-title"]),
            languages = splitList(attrs["tvg-language"]).ifEmpty { listOfNotNull(defaultLanguage) },
            geoBlocked = "geo-blocked" in tags,
        )
    }

    private fun splitList(value: String?): List<String> =
        value?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() && it != "Undefined" }
            ?: emptyList()
}
