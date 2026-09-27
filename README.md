# Malayalam Live TV — Fire TV app

A live-TV player for Amazon Fire TV Stick (and any Android TV). It loads M3U/IPTV playlists,
shows **Malayalam channels first**, and plays HLS/DASH streams full-screen with ExoPlayer.

## What's in it

- **Built-in sources:** the public [iptv-org](https://github.com/iptv-org/iptv) lists of
  free-to-air streams: Malayalam-language (`languages/mal.m3u`) and India (`countries/in.m3u`).
- **Your own playlists:** *Playlists → Add playlist URL* takes any M3U link, for example
  from an IPTV subscription you pay for.
- Categories: Malayalam, Favourites, every playlist group (News, Movies, Music…), and All.
- Remote-friendly: D-pad navigation, **▲/▼ changes channel** in the player, **hold OK** on a
  tile (or **Menu** in the player) to add a favourite.
- Playlists are cached for 6 hours, so the app still opens if a source is down.
- Per-stream `User-Agent`/`Referer` (`#EXTVLCOPT`) is honoured, which some broadcasters need.

## Build the APK

Push this repo to GitHub. The **Build APK** workflow runs the tests and uploads
`MalayalamLiveTV-apk` as an artifact (Actions → latest run → Artifacts).

Or build locally with Android Studio / the Android SDK installed:

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it can be side-loaded without a keystore.

## Install on the Fire TV Stick

1. On the Fire TV: **Settings → My Fire TV → Developer options**. On newer devices, first
   open *My Fire TV → About* and press OK on the device name 7 times to show this menu.
2. Install the **Downloader** app (by AFTVnews) from the Amazon Appstore and allow it under
   *Install unknown apps*.
3. Put the APK somewhere with a direct link (a GitHub Release asset works well), enter
   that URL in Downloader, and install.

   Alternatively over adb: turn on *ADB debugging*, then from a computer on the same network
   run `adb connect <fire-tv-ip>` and `adb install app-release.apk`.

## About channel availability

The app is a player; it ships no streams. The built-in lists are community-maintained
links to channels that broadcasters publish for free. Streams come and go, and some are
geo-blocked to India. Paid channels (Asianet, Surya, Mazhavil Manorama, Flowers…) are
only available through a subscription: add the playlist your provider gives you, or use
their own apps (JioHotstar, Sun NXT, ManoramaMAX, etc.).
