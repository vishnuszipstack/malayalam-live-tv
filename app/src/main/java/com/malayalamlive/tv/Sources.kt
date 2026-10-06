package com.malayalamlive.tv

data class PlaylistSource(
    val id: String,
    val title: String,
    val url: String,
    val defaultLanguage: String? = null,
    val enabledByDefault: Boolean = false,
)

/** Public, community-maintained playlists of free-to-air streams. */
object Sources {
    private const val IPTV_ORG = "https://iptv-org.github.io/iptv"

    val BUILT_IN = listOf(
        PlaylistSource("iptv-mal", "Malayalam channels", "$IPTV_ORG/languages/mal.m3u", "Malayalam", true),
        PlaylistSource("iptv-in", "Indian channels (iptv-org)", "$IPTV_ORG/countries/in.m3u", enabledByDefault = true),
        PlaylistSource(
            "freetv-in", "Indian channels (Free-TV)",
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_india.m3u8",
            enabledByDefault = true,
        ),
        PlaylistSource("iptv-tam", "Tamil channels", "$IPTV_ORG/languages/tam.m3u", "Tamil"),
        PlaylistSource("iptv-hin", "Hindi channels", "$IPTV_ORG/languages/hin.m3u", "Hindi"),
        PlaylistSource("iptv-tel", "Telugu channels", "$IPTV_ORG/languages/tel.m3u", "Telugu"),
        PlaylistSource("iptv-kan", "Kannada channels", "$IPTV_ORG/languages/kan.m3u", "Kannada"),
        PlaylistSource("iptv-news", "World news", "$IPTV_ORG/categories/news.m3u"),
        PlaylistSource("iptv-movies", "World movies", "$IPTV_ORG/categories/movies.m3u"),
        PlaylistSource("iptv-kids", "Kids (worldwide)", "$IPTV_ORG/categories/kids.m3u"),
        PlaylistSource("iptv-music", "Music (worldwide)", "$IPTV_ORG/categories/music.m3u"),
    )
}
