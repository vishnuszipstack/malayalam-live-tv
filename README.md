# Malayalam Live TV — Fire TV app

A live-TV player for Amazon Fire TV Stick (and any Android TV). It loads M3U/IPTV playlists,
shows **Malayalam channels first**, and plays HLS/DASH streams full-screen with ExoPlayer.

## What's in it

- **Malayalam first:** the Malayalam category opens by default. Channels are recognised as
  Malayalam from the Malayalam playlist, from `tvg-language`, or from a list of known Kerala
  channel names, so the ones the India playlists list without a language still show up.
- **Sources** (sidebar → *Channel sources*): the public [iptv-org](https://github.com/iptv-org/iptv)
  Malayalam and India lists plus the [Free-TV](https://github.com/Free-TV/IPTV) India list are on
  by default; Tamil, Hindi, Telugu, Kannada, world news, movies, kids and music can be switched on.
- **Your own playlists:** sidebar → *My playlists* takes any M3U link, for example from an IPTV
  subscription you pay for.
- **Backup streams:** the same channel from several lists or qualities is merged into one tile.
  If a stream fails, the player moves to the next one automatically.
- **Categories:** Malayalam, Recently watched, Favourites, languages, playlist groups and All.
- Playlists are cached for 6 hours, so the app still opens if a source is down.
- Per-stream `User-Agent`/`Referer` (`#EXTVLCOPT`) is honoured, which some broadcasters need.
- Channels that only stream on YouTube open in the YouTube app.

## Remote control

| Where | Button | Action |
|---|---|---|
| Channel grid | OK | Play |
| | Hold OK | Add / remove favourite |
| | ◀ (from the first column) or Back | Categories |
| Player | ▲ / ▼ | Previous / next channel |
| | ◀ or OK | Channel list |
| | ▶ | Picture size: Full screen / Original shape / Zoom (remembered per channel) |
| | ☰ Menu | Add / remove favourite |
| | Play/Pause | Pause / resume |

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
