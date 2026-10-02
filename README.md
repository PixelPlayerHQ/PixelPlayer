# PixelPlayer

<p align="center">
  <img src="assets/PixelPlayer.svg" alt="App Icon" height="200"/>
</p>

<p align="center">
  <strong>A YouTube Music player for Android</strong><br>
  Feed, radio, playlists, and likes — with background playback
</p>

<p align="center">
  <img src="assets/screenshot1.jpg" alt="Home" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot2.jpg" alt="Player" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot3.jpg" alt="Library" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot4.jpg" alt="Search" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
  <a href="https://github.com/kibetmasi/PixelPlayer/releases/latest">
    <img src="https://img.shields.io/github/v/release/kibetmasi/PixelPlayer?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest Release">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-green?style=for-the-badge&logo=android" alt="Android 11+">
</p>

---

## Download & install

1. Open **[Latest Release](https://github.com/kibetmasi/PixelPlayer/releases/latest)**.
2. Under **Assets**, download the APK that matches your phone:
   - **`…-arm64-v8a.apk`** — most phones from ~2017 onward (**recommended**)
   - **`…-armeabi-v7a.apk`** — older 32-bit devices
3. On your phone, open the downloaded file and install.
   - If Android blocks it, allow **Install unknown apps** for your browser / Files app.
4. Open PixelPlayer. Playback continues in the background with the notification controls.

> Tip: each new release can update the app **in place** (same signing key). You usually don’t need to uninstall first — that keeps login and settings.

---

## YouTube Music

PixelPlayer plays YouTube audio through the existing player. It does not scan a local music library.

| Tab | What it does |
|-----|----------------|
| **Feed** | Your Mix, artwork collage, trending songs, radio starters, and playlists |
| **Radio** | Starts a song, then keeps queuing similar tracks |
| **Search** | Songs, albums, and playlists |
| **Playlists** | Today's hits, workout, focus, and party |
| **Liked** | Songs you like on device, plus liked videos from your Google account after sign-in |

### Sign in with Google

Liked videos and the “channels you follow” shelf need the Google account you use on YouTube.

1. On **Feed**, tap the **account** icon.
2. Tap **Sign in with Google** and sign in on the page that opens.
3. Tap **Done** when YouTube finishes signing you in.
4. Open **Liked** to load that account’s liked videos.

**Sign out** is on the same Account screen. The heart on a song still saves a like on the device if you are signed out.

The header also opens the **changelog** and app **settings**.

---

## SoundCloud

SoundCloud is not a bottom-bar tab in this build. The previous web `client_id` and sign-in flow is described in [docs/soundcloud-implementation.md](docs/soundcloud-implementation.md).

---

## Everyday use (quick)

- **Feed** shuffle plays Your Mix and keeps playing in the background.
- **Radio** is a continuous mix based on the song you start, not a live broadcast.
- **Liked** and the heart icon keep songs you like. Sign in to also load YouTube liked videos.
- **Playlists** opens a collection; tap a track to play it.

---

## Requirements

- Android **11** or newer
- Internet for YouTube Music
- Notification permission so background playback can show controls

---

## Support & credits

This build is maintained at **[kibetmasi/PixelPlayer](https://github.com/kibetmasi/PixelPlayer)**. A push to `master` publishes a signed release APK on the Releases page.

Upstream project by [theovilardo](https://github.com/theovilardo/PixelPlayer). Logo by [Aureal](https://github.com/NPSummers).

See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
