# SoundCloud in PixelPlayer — Implementation Guide

This document describes how PixelPlayer talks to SoundCloud **without** an official
SoundCloud developer OAuth app. It covers NewPipe Extractor, the unofficial web
`api-v2` client, WebView sign-in, playback (including HLS), downloads, and known
limits.

> **Status:** experimental, reverse-engineered against SoundCloud’s public website.
> Endpoints, cookies, and `client_id` values can change without notice.

---

## Table of contents

1. [Goals and non-goals](#1-goals-and-non-goals)
2. [Architecture overview](#2-architecture-overview)
3. [Two stacks: NewPipe vs api-v2](#3-two-stacks-newpipe-vs-api-v2)
4. [Credentials model (the “auth bypass”)](#4-credentials-model-the-auth-bypass)
5. [Obtaining a web `client_id`](#5-obtaining-a-web-client_id)
6. [Sign-in via in-app WebView](#6-sign-in-via-in-app-webview)
7. [NewPipe Extractor deep dive](#7-newpipe-extractor-deep-dive)
8. [SoundCloud `api-v2` deep dive](#8-soundcloud-api-v2-deep-dive)
9. [Playback pipeline](#9-playback-pipeline)
10. [Downloads, likes, playlists, feed](#10-downloads-likes-playlists-feed)
11. [UI surface](#11-ui-surface)
12. [Dependencies, ProGuard, build config](#12-dependencies-proguard-build-config)
13. [Failure modes and limitations](#13-failure-modes-and-limitations)
14. [File index](#14-file-index)
15. [End-to-end sequence diagrams](#15-end-to-end-sequence-diagrams)

---

## 1. Goals and non-goals

### Goals

- Browse Discover / Search / public profiles via NewPipe (no official SDK).
- When the user signs in (web session), show **personal** Feed, Likes, Tracks, Playlists.
- Resolve a track to a playable HTTP(S) audio URL and hand it to Media3 / ExoPlayer.
- Prefer progressive MP3 when available; fall back to plain (non-DRM) HLS.
- Optional progressive downloads into `Music/PixelPlayer/SoundCloud`.

### Non-goals

- Official SoundCloud developer portal apps / registered OAuth clients.
- Playing DRM-only / encrypted HLS tracks.
- Guaranteed long-lived stream URLs (CDN links expire; we re-resolve).
- Using SoundCloud as a licensed commercial streaming partner.

---

## 2. Architecture overview

SoundCloud is **not** a separate Gradle module. It lives inside `:app` under
`com.theveloper.pixelplay.soundcloud`, with UI under `presentation` and auth under
`presentation.soundcloud.auth`.

```
┌─────────────────────────────────────────────────────────────────────────┐
│  SoundCloudScreen (Compose)                                             │
│    sections: FEED · DISCOVER · SEARCH · LIKES · TRACKS · PLAYLISTS · DL │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  SoundCloudViewModel                                                    │
│    load sections · resolve start track · prefetch queue · like/download │
└───────────────┬───────────────────────────────┬─────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────┐     ┌───────────────────────────────────────┐
│  SoundCloudClient         │     │  SoundCloudSessionApi                 │
│  (NewPipe Extractor)      │     │  (OkHttp → api-v2.soundcloud.com)      │
│  discover · search ·      │     │  /me · /stream · /users/{id}/…        │
│  channel tabs · playlist  │     │  resolve · track_likes PUT/DELETE     │
│  StreamInfo resolve       │     │                                       │
└─────────────┬─────────────┘     └──────────────────┬────────────────────┘
              │                                      │
              ▼                                      │
┌───────────────────────────┐                        │
│  SoundCloudDownloader     │◄── oauth + cookies ────┘
│  (NewPipe Downloader impl)│
└───────────────────────────┘

                credentials Persist via SoundCloudSettings (DataStore)
                playback    → PlayerViewModel → MusicService / Media3
                downloads   → SoundCloudDownloadService → MediaStore
```

### Core types

| Type | Role |
|------|------|
| `SoundCloudSearchHit` | List row: permalink URL, title, artist, art, track vs playlist |
| `SoundCloudResolvedTrack` | After NewPipe resolve: stream URL + metadata |
| `SoundCloudFeedShelf` | Named horizontal/vertical shelf on Feed |
| `SoundCloudSection` | Tab/section enum |
| `SoundCloudSession` | Token + cookies + display name + permalink (in settings) |
| `Song` | Shared library model; SoundCloud songs use `id = sc_{hash}`, `path`/`contentUriString` = stream URL |

### Entry points

| Entry | How |
|-------|-----|
| Bottom nav “SoundCloud” | `MainActivity` → `Screen.SoundCloud` → `SoundCloudScreen` (tab stays) |
| Home cloud sheet | SoundCloud row → `Screen.SoundCloudSettings` |
| Gear on SoundCloud | Same settings page (`Screen.SoundCloudSettings`) |
| Sign in | Starts `SoundCloudLoginActivity` (not exported) |

---

## 3. Two stacks: NewPipe vs api-v2

PixelPlayer deliberately uses **two** backends.

| Concern | Stack | Why |
|---------|-------|-----|
| Discover kiosk, search, public channel tabs, playlist track lists, **stream URL resolve** | **NewPipe Extractor** (`SoundcloudService`) | Already understands SoundCloud page/API scraping; exposes `StreamInfo` with progressive + HLS |
| Signed-in Feed, Likes, Tracks, Playlists, like/unlike | **Direct `api-v2`** (`SoundCloudSessionApi`) | NewPipe does not own the user’s private library; web `/users/{id}/…` endpoints do |

Rule of thumb:

- **Need a playable URL** → NewPipe `StreamInfo.getInfo(permalink)`.
- **Need “my” library while signed in** → SessionApi with OAuth + cookies + `client_id`.
- **Signed out** → NewPipe public profile URL `soundcloud.com/{username}/likes|tracks|sets` (public only).

---

## 4. Credentials model (the “auth bypass”)

PixelPlayer does **not** register an OAuth application with SoundCloud. Instead it
reuses what the **website** already uses:

| Credential | What it is | Required for |
|------------|------------|--------------|
| **`client_id`** | Public web app id SoundCloud embeds in front-end JS / Network calls (`?client_id=…`) | Almost all api-v2 and media calls; **streaming** |
| **`oauth_token`** | Cookie set after browser login (`oauth_token=…`) | Personal Feed / Likes / Tracks / Playlists / liking |
| **Cookie jar** | Full `Cookie` header from `soundcloud.com` + `api-v2.soundcloud.com` | Same as token; some requests expect both |
| **Username / permalink** | Public handle | Fallback when signed out (public tabs only) |

### Why this works

SoundCloud’s website is itself a client of `https://api-v2.soundcloud.com`. Every
browser request carries:

```http
GET /me?client_id=<web_client_id> HTTP/1.1
Host: api-v2.soundcloud.com
Authorization: OAuth <oauth_token>
Cookie: oauth_token=<…>; …other cookies…
User-Agent: <desktop browser>
Origin: https://soundcloud.com
Referer: https://soundcloud.com/
```

PixelPlayer copies that pattern. That is the “bypass”: **session reuse**, not a
secret exploit of a private API. It is fragile and unofficial.

### What is stored (DataStore `soundcloud_settings`)

Keys in `SoundCloudSettings`:

| Preference key | Purpose |
|----------------|---------|
| `client_id` | Web client id (survives sign-out) |
| `username` | Public handle / permalink sync |
| `oauth_token` | Web session OAuth |
| `cookie_header` | Merged cookie string |
| `display_name` | From `/me` |
| `permalink` | From `/me` |

`clearSession()` drops token/cookies/display/permalink but **keeps** `client_id`
so streaming still works after sign-out for public content.

### Optional build-time seed

`local.properties`:

```properties
soundcloud.clientId=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

exposed as `BuildConfig.SOUNDCLOUD_CLIENT_ID`. Runtime DataStore override always
wins when the user pastes a value in SoundCloud settings.

---

## 5. Obtaining a web `client_id`

### Manual (documented in README)

1. Open [soundcloud.com](https://soundcloud.com) on a desktop browser and play any track.
2. DevTools → **Network** → filter `client_id`.
3. Open a request to `api-v2.soundcloud.com` (often `/me` or media).
4. Copy the query value after `client_id=`.
5. Paste into **SoundCloud settings → Web client_id → Save**.

Screenshot asset: `assets/soundcloud-devtools-request.png` (redacted).

### Automatic (NewPipe scrape)

`SoundCloudClient.refreshClientIdFromWeb()`:

1. Clears any previously injected NewPipe client id.
2. Calls `SoundcloudParsingHelper.clientId()` (NewPipe scrapes SoundCloud’s JS bundles).
3. Injects the result into NewPipe **and** SessionApi.

Triggered when:

- Prefs / runtime override is empty (`ensureClientIdReady`).
- Resolve/playlist fails with messages suggesting client_id / 429 / recaptcha
  (`looksLikeClientIdOrRateLimit` → refresh once → retry).

### Reflective injection into NewPipe

NewPipe stores the client id in a **private static** field:

```kotlin
// SoundCloudClient.injectClientId
val field = SoundcloudParsingHelper::class.java.getDeclaredField("clientId")
field.isAccessible = true
synchronized(SoundcloudParsingHelper::class.java) {
    field.set(null, clientId)
}
```

ProGuard keeps that field so release builds still work:

```proguard
-keepclassmembers class org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper {
    static java.lang.String clientId;
}
```

---

## 6. Sign-in via in-app WebView

Class: `SoundCloudLoginActivity`  
Route: started from SoundCloud settings (“Sign in to SoundCloud”).

### Flow

```
SoundCloud settings → startActivity(SoundCloudLoginActivity)
                    │
                    ▼
         WebView loads https://soundcloud.com/signin
         Desktop Chrome UA · JS · DOM storage · 3rd-party cookies
                    │
                    ▼
         User logs in (email/Google/etc. as on the website)
                    │
         onPageFinished → try extractSoundCloudSession()
         or user taps “I’m signed in”
                    │
                    ▼
         CookieManager cookies from:
           https://soundcloud.com
           https://api-v2.soundcloud.com
                    │
                    ▼
         Require cookie key oauth_token
         Build cookie_header = all key=value pairs
                    │
                    ▼
         SoundCloudPrefsViewModel.saveSession(token, cookies)
           → DataStore
           → sessionApi.fetchMe() → displayName + permalink
           → sync username to permalink
                    │
                    ▼
         Activity RESULT_OK + finish
```

### Session extraction (`extractSoundCloudSession`)

```kotlin
val main = CookieManager.getInstance().getCookie("https://soundcloud.com")
val api  = CookieManager.getInstance().getCookie("https://api-v2.soundcloud.com")
// merge, parse key=value, require oauth_token
return token to cookieHeader
```

If `oauth_token` is missing, the UI shows “sign-in incomplete” and does not save.

### After sign-in

`SoundCloudViewModel` collects `settings.session` and calls:

```kotlin
client.setSession(session.oauthToken, session.cookieHeader)
```

which updates both:

- `SoundCloudDownloader` (NewPipe HTTP layer) — Authorization + Cookie headers
- `SoundCloudSessionApi` — same credentials + current `client_id`

Personal sections then use SessionApi. Default landing section becomes **FEED**
when signed in, otherwise **DISCOVER**.

### Sign-out

Clears session prefs (keeps `client_id`). UI returns to public Discover / username
tabs.

---

## 7. NewPipe Extractor deep dive

### Dependency

```toml
# gradle/libs.versions.toml
newpipeExtractor = "v0.26.5"
newpipe-extractor = { module = "com.github.teamnewpipe:NewPipeExtractor", version.ref = "newpipeExtractor" }
```

Resolved via JitPack (`teamnewpipe` allowed in `settings.gradle.kts`).

### Initialization

Once per process:

```kotlin
NewPipe.init(SoundCloudDownloader())
```

Service handle: `ServiceList.SoundCloud` → `SoundcloudService`.

### Isolated HTTP downloader (critical)

`SoundCloudDownloader` extends NewPipe’s `Downloader` with its **own** OkHttpClient.

**Why not reuse the app OkHttpClient?**  
`AppModule` forces a PixelPlayer User-Agent that SoundCloud rejects or mishandles
for api-v2 / media. The SoundCloud downloader uses a Firefox desktop UA plus:

- `Origin: https://soundcloud.com`
- `Referer: https://soundcloud.com/`
- Optional `Authorization: OAuth …`
- Optional `Cookie: …`

HTTP **429** is mapped to `IOException("SoundCloud rate limited…")` so the UI can
show a friendly message.

### Operations mapped to NewPipe

| App method | NewPipe API |
|------------|-------------|
| `loadDiscover` | Default kiosk → `KioskInfo.getInfo` |
| `search(query)` | `getSearchExtractor` → `initialPage.items` |
| `loadUserLikes/Tracks/Playlists(username)` | `ChannelTabInfo` on `/likes`, `/tracks`, `/sets` |
| `loadPlaylistTracks(url)` | `PlaylistInfo.getInfo` |
| `resolveTrack(url)` | `StreamInfo.getInfo` → `audioStreams` |

Info items become `SoundCloudSearchHit` (tracks + playlists).

### Resolve path (`resolveTrack`)

```
permalink URL
    → ensureClientIdReady()          // prefs or scrape + inject
    → StreamInfo.getInfo(service, url)
    → pickBestAudioStream(audioStreams)
    → SoundCloudResolvedTrack(streamUrl, mime, art, …)
    → cache in ConcurrentHashMap (TTL 15 minutes)
```

On client_id / 429 / recaptcha-looking errors: refresh client_id from web once and
retry.

### Stream selection policy (`pickBestAudioStream`)

1. **Progressive HTTP** preferred (single-file MP3/M4A — fastest first buffer).
2. Else **plain HLS** (`DeliveryMethod.HLS`, URL must not contain `encrypted`).
   Prefer **lower** bitrate for quicker start.
3. Else any non-encrypted stream; else fail (“DRM-protected…”).

Encrypted / DRM-only tracks are intentionally unplayable.

### Song id stability

```kotlin
fun songIdForUrl(url: String) = "sc_${url.trim().hashCode().toUInt()}"
```

`toSong(..., permalinkUrl = hit.url)` always keys the `Song.id` off the **list
permalink**, so the UI can highlight the now-playing row without waiting for
NewPipe’s canonical URL.

---

## 8. SoundCloud `api-v2` deep dive

Class: `SoundCloudSessionApi`  
Base host: `https://api-v2.soundcloud.com`

### Request shaping

Every call:

1. Append `client_id` query param when known.
2. If session present: `Authorization: OAuth {token}`.
3. If cookies present: `Cookie: {cookieHeader}`.
4. Browser-like `User-Agent`, `Origin`, `Referer`.

`hasSession` ≡ non-blank oauth token.

### Endpoints

| Feature | Method | Path | Auth |
|---------|--------|------|------|
| Current user | GET | `/me` | required |
| Home stream | GET | `/stream?limit=&linked_partitioning=1` | required |
| Play history | GET | `/me/play-history/tracks` (fallback `/me/play-history`) | required |
| Likes | GET | `/users/{id}/likes/tracks` (fallback `/users/{id}/track_likes`) | required |
| Uploads | GET | `/users/{id}/tracks` | required |
| Playlists | GET | `/users/{id}/playlists_without_albums` (fallback `/playlists`) | required |
| Resolve URL → JSON | GET | `/resolve?url=` | like: auth; DRM probe: optional |
| Like | PUT | `/users/{id}/track_likes/{trackId}` | required |
| Unlike | DELETE | `/users/{id}/track_likes/{trackId}` | required |

Important: the web client uses **`/users/{id}/…`**, not `/me/likes` / `/me/tracks`
(those 404). User id is cached from `/me` via `requireUserId()`.

### DRM filtering in library lists

When parsing track JSON, SessionApi inspects `media.transcodings`. If **every**
protocol looks encrypted, the track is skipped (`isDrmOnlyTrack`) so the list
does not show unplayable DRM-only items.

### Like toggle

```
hit.url
  → GET /resolve?url=…     → track id
  → PUT|DELETE /users/{me}/track_likes/{trackId}
  → update local likedTrackUrls in ViewModel
```

---

## 9. Playback pipeline

### Why the official app feels instant

SoundCloud’s app already holds stream manifests and uses first-party CDNs.  
PixelPlayer must **resolve** each permalink through NewPipe (network + scrape)
before ExoPlayer can buffer.

### Historical latency bug (fixed)

Early queue building called `StreamInfo.getInfo` for **up to ~40** tracks
**before** `playSongs`. That dominated start latency. HLS support alone did not
fix it.

### Current fast path

```
User taps track
  → resolveStartForPlayback(hit)     // ONE NewPipe resolve
  → PlayerViewModel.playSongs([startSong], startSong, "SoundCloud")
  → setIdle()                        // clear spinner
  → prefetchQueueAfter(hit, list)    // background: resolve next ≤24
       └─ each success → addSongToQueue(song)
```

Shuffle:

```
resolveShuffleStart() → first playable Song + remaining hits
playSongsShuffled([startSong], …)
prefetchHits(remaining) → addSongToQueue
```

### Media3 / ExoPlayer

- `Song.path` / `contentUriString` = HTTPS progressive URL **or** `.m3u8` HLS URL.
- Shared `MediaItemBuilder` / `DualPlayerEngine` uses `DefaultMediaSourceFactory`.
- **`media3-exoplayer-hls` must be on the classpath** so Media3 can load
  `HlsMediaSource$Factory` reflectively. Missing that dependency caused
  `ClassNotFoundException` and an endless “loading” state.
- ProGuard: `-keep class androidx.media3.exoplayer.hls.** { *; }`

There is **no** on-disk SoundCloud stream cache. Only:

- 15‑minute resolve cache in `SoundCloudClient`
- ExoPlayer in-memory buffering
- Optional download files under Music/

### Now-playing highlight

`isSoundCloudHitCurrent(hit, currentSong)`:

1. `currentSong.id == songIdForUrl(hit.url)`, or
2. Fallback: `album == "SoundCloud"` and title/artist match (older queue ids).

UI: primary tint + `PlayingEqIcon` on list/tiles.

---

## 10. Downloads, likes, playlists, feed

### Downloads

`SoundCloudDownloadService`:

- Resolves via NewPipe, then HTTP GET of **progressive** URL only.
- Rejects `.m3u8` (“HLS-only — download needs a progressive stream”).
- Writes to `Music/PixelPlayer/SoundCloud` (MediaStore `RELATIVE_PATH` on Q+).
- Remembers artwork URLs in SharedPreferences for list hydration.
- Downloads section supports Library-style multi-select (play, queue, playlist, delete, share ZIP).

### Likes

Requires signed-in session. Optimistic UI update of `likedTrackUrls`; unliking on
the Likes section removes the row.

### Playlists

- Signed in: SessionApi playlist list → tap → NewPipe `PlaylistInfo` for tracks.
- Signed out: NewPipe channel tab `/sets`.
- Nested browse uses an in-tab nav stack (`BackHandler` stays inside SoundCloud).

### Feed shelves (signed in)

`SoundCloudViewModel.loadFeed` builds shelves from parallel `runCatching` loads:

| Shelf id (approx.) | Source |
|--------------------|--------|
| More of what you like | Session likes |
| Recently played | Play history |
| Mixed / curated | Discover (NewPipe) + session mixes |
| Playlists for you | Session playlists |
| Latest from people you follow | `/stream` |

Empty shelves are omitted; Discover remains available when APIs fail.

---

## 11. UI surface

### `SoundCloudScreen`

- Library-style section switcher (compact pill / sheet).
- Search field always available (section switches to SEARCH at ≥2 chars).
- Tiles on wide screens; list on phone (override toggle).
- Pull-to-refresh; transparent loading indicator.
- Share action sends the **permalink**, not a local file.

### SoundCloud settings (`SoundCloudSettingsScreen`)

- Help copy: client_id required for streaming; sign-in for personal library.
- Fields: Web client_id, username.
- Actions: Save, Sign in, Sign out.
- Status of session (display name when signed in).

### User-facing README

`README.md` § “SoundCloud setup” mirrors the DevTools `client_id` steps and
common error table (rate limit, DRM, empty feed).

---

## 12. Dependencies, ProGuard, build config

### Gradle (relevant)

| Artifact | Role |
|----------|------|
| `NewPipeExtractor` v0.26.5 | SoundCloud scrape / resolve |
| `media3-exoplayer` 1.10.1 | Playback |
| `media3-exoplayer-hls` 1.10.1 | HLS streams |
| `media3-session` / `ui` | Notification / controls |
| OkHttp | SessionApi + NewPipe downloader |
| DataStore Preferences | Credentials |

### ProGuard highlights

- Keep Media3 HLS classes (reflective factory).
- Keep NewPipe timeago / Rhino patterns; dontwarn extractor optional APIs.
- Keep `SoundcloudParsingHelper.clientId` for reflective inject.

---

## 13. Failure modes and limitations

| Symptom / limit | Cause | Mitigation |
|-----------------|-------|------------|
| Endless load / `HlsMediaSource$Factory` CNFE | HLS dep missing (historical) | Ship `media3-exoplayer-hls` |
| Long delay before sound | Resolving many tracks before play (historical) | Resolve one → play → prefetch |
| “No playable stream” | DRM-only / encrypted HLS only | Skip; show message |
| “Rate limited” / 429 | Too many api-v2 / media calls | Wait; auto client_id refresh once |
| Streaming dies after weeks | Web `client_id` rotated | Re-scrape or paste fresh id |
| Empty Feed while signed in | Bad/expired cookies or missing client_id | Re-sign-in; Save client_id |
| Private likes missing when signed out | Public profile scrape only | Sign in |
| Download fails on some tracks | HLS-only | Play in-app instead |
| Stream URL stops working later | CDN expiry | Resolve again (cache TTL 15m) |
| App UA breaks SoundCloud | Shared OkHttp UA | Isolated `SoundCloudDownloader` |

### Legal / ToS note

Reusing web `client_id` and session cookies is **unofficial**. SoundCloud’s terms
may prohibit third-party clients. This feature is experimental and may break or
be removed if the site changes.

---

## 14. File index

### Domain

| File | Responsibility |
|------|----------------|
| `soundcloud/SoundCloudClient.kt` | NewPipe facade, resolve, inject client_id, `toSong` |
| `soundcloud/SoundCloudSessionApi.kt` | Authenticated api-v2 |
| `soundcloud/SoundCloudDownloader.kt` | NewPipe HTTP downloader |
| `soundcloud/SoundCloudDownloadService.kt` | Progressive downloads |
| `soundcloud/SoundCloudSettings.kt` | DataStore credentials |
| `soundcloud/SoundCloudModels.kt` | Hits, shelves, sections, resolved track |
| `soundcloud/SoundCloudViewModel.kt` | UI state, prefetch, feed, likes |
| `soundcloud/SoundCloudPrefsViewModel.kt` | Settings save / session / `/me` |

### UI / auth

| File | Responsibility |
|------|----------------|
| `presentation/screens/SoundCloudScreen.kt` | Main tab UI + play wiring |
| `presentation/screens/SoundCloudSettingsScreen.kt` | client_id / sign-in UI |
| `presentation/soundcloud/auth/SoundCloudLoginActivity.kt` | WebView login + cookie extract |

### Shared playback

| File | Responsibility |
|------|----------------|
| `presentation/viewmodel/PlayerViewModel.kt` | `playSongs`, `addSongToQueue` |
| `data/service/player/DualPlayerEngine.kt` | ExoPlayer + `DefaultMediaSourceFactory` |
| `utils/MediaItemBuilder.kt` | `Song` → `MediaItem` |

### Build / docs

| File | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml` | NewPipe + Media3 versions |
| `app/build.gradle.kts` | Deps + `SOUNDCLOUD_CLIENT_ID` |
| `app/proguard-rules.pro` | HLS + NewPipe keep rules |
| `README.md` | End-user setup |
| `docs/soundcloud-implementation.md` | This document |

---

## 15. End-to-end sequence diagrams

### A. First-time setup

```
User                 SoundCloud settings          Browser / WebView           DataStore
 |                           |                          |                        |
 |-- open gear ------------->|                          |                        |
 |-- paste client_id ------->|-- saveClientId ---------------------------------->|
 |-- Sign in --------------->|-- start LoginActivity -->|                        |
 |                           |                          |-- soundcloud.com/signin |
 |                           |                          |<-- oauth_token cookie --|
 |                           |<-- saveSession(token,cookies) ------------------->|
 |                           |-- fetchMe(/me) ---------> api-v2                  |
 |<-- signed-in UI ----------|                          |                        |
```

### B. Tap to play

```
User          SoundCloudScreen       ViewModel            Client/NewPipe         PlayerViewModel
 |                  |                    |                      |                      |
 |-- tap track ---->|-- resolveStart --->|-- StreamInfo.getInfo-|                      |
 |                  |                    |<-- stream URL -------|                      |
 |                  |-- playSongs([one]) ------------------------------------------>|
 |                  |                    |                      |   ExoPlayer prepare |
 |                  |-- prefetchQueue -->|-- resolve next… -----|                      |
 |                  |                    |-- addSongToQueue -------------------------->|
```

### C. Signed-in likes

```
ViewModel                    SessionApi                         api-v2
   |-- loadMyLikes --------->|-- GET /me (cache id) ----------->|
   |                         |-- GET /users/{id}/likes/tracks ->|
   |<-- SearchHit list ------|<-- JSON collection --------------|
   |-- toggleLike ---------->|-- GET /resolve?url= ------------>|
   |                         |-- PUT /users/{id}/track_likes/X->|
```

---

## Appendix: “Bypass auth” in one paragraph

PixelPlayer never obtains a developer OAuth client secret. It (1) uses the same
public web **`client_id`** the SoundCloud SPA uses, optionally scraped by NewPipe;
(2) optionally captures the user’s **browser session** (`oauth_token` + cookies)
via an in-app WebView login; and (3) calls the same **`api-v2`** JSON endpoints the
website calls, while NewPipe resolves permalinks into progressive or plain HLS
URLs for Media3. Sign-in unlocks personal libraries; **`client_id` is still
required to stream**.
