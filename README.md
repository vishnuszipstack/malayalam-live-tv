# Malayalam Live TV — Fire TV and Android app

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

## Home dashboard

The app opens on *Home*: a Featured row of large cards (the channel you last watched, then the
best-known Malayalam channels), followed by Continue watching, Favourites, Malayalam News,
Malayalam Entertainment, Malayalam Devotional and the largest other categories.

## On phones and tablets

The same APK installs on Android phones (open the release link in the phone's browser). Narrow
screens get a top bar with a ☰ button that slides out the categories, and a search button; the
grid fits the screen width. In the player: tap for the channel banner and buttons (Channels,
Previous, Next, Picture, Favourite), swipe up/down to change channel, swipe right for the
channel list, double-tap to change the picture size. The player turns to landscape.

## Search

*Search* (second in the sidebar, or the remote's search key where it has one) finds channels by
name, part of a name, language or category as you type: "asianet", "media one", "tamil news".
**Voice** uses the device's speech recogniser when there is one; Fire TV generally doesn't give
apps one, so the app opens the keyboard instead, where holding the remote's microphone button
dictates on many models.

## Filters

Every category except Home has a filter bar above the grid (press ▲ from the top row):
**Language** (with channel counts for the current category), **Quality** (any / HD / Full HD),
**Sort** (Malayalam first, name, best quality, most backup streams), and switches to hide
region-locked, part-time and YouTube-only channels. Filters are remembered.

Languages come from the playlist when it says, otherwise from iptv-org's per-language lists
(used only as a lookup) and finally from the channel name.

## Icon and banner

`art/make_art.py` renders the launcher icon (`mipmap-*/ic_launcher.png`) and the Fire TV banner
(`drawable-xhdpi/banner.png`). It needs Pillow and the Noto Sans Malayalam Bold font:
`python3 art/make_art.py <font dir> app/src/main/res preview.png`.

## Remote control

| Where | Button | Action |
|---|---|---|
| Channel grid | OK | Play |
| | Hold OK | Menu: add to / remove from favourites, or remove the channel (sidebar → *Removed channels* brings it back) |
| | ◀ (from the first column) or Back | Categories |
| Player | ▲ / ▼ | Previous / next channel |
| | ◀ or OK | Channel list |
| | ▶ | Picture size: Full screen / Original shape / Zoom (remembered per channel) |
| | ☰ Menu | Add / remove favourite |
| | Play/Pause | Pause / resume |

## Build the APK

Every push to `main` runs the tests, builds the APK and publishes it as the latest
[release](../../releases/latest). The plain APK is always at
`releases/latest/download/MalayalamLiveTV.apk` (no zip to extract).

Or build locally with Android Studio / the Android SDK installed:

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it can be side-loaded without a keystore.

## Updates

The app checks GitHub for a newer release each time it starts and offers to install it; the
sidebar also has *Check for updates*. The first time, Fire TV asks you to allow "Install unknown
apps" for Malayalam Live TV.

Releases are signed with `signing/release.p12`, which the build creates once and encrypts with the
`SIGNING_PASSWORD` repository secret. Without that secret the build still runs but publishes
nothing, so every published APK can install over the previous one.

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
