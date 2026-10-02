package com.theveloper.pixelplay.youtube

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeClient @Inject constructor(
    private val session: YoutubeSession,
) {
    private val downloader = YoutubeDownloader()
    private val resolveCache = ConcurrentHashMap<String, CachedResolve>()
    private val inFlightResolves = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    @Volatile var feedShelves: List<YoutubeShelf> = emptyList()
    @Volatile var playlistHits: List<YoutubeHit> = emptyList()
    @Volatile var radioHits: List<YoutubeHit> = emptyList()
    @Volatile var likedHits: List<YoutubeHit> = emptyList()
    @Volatile private var visitorId: String? = null
    @Volatile private var identityChecked = false

    private data class CachedResolve(
        val track: YoutubeResolvedTrack,
        val cachedAtMs: Long,
    )

    init {
        ensureInitialized(downloader)
    }

    fun loadTrendingMusic(limit: Int = 30): List<YoutubeHit> {
        applySession()
        val extractor = youtube().kioskList.getExtractorById("trending_music", null)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() && it.kind == YoutubeHit.Kind.TRACK }
            .take(limit)
            .toList()
    }

    fun search(
        query: String,
        filter: YoutubeSearchFilter,
        limit: Int = 30,
    ): List<YoutubeHit> {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query is empty" }
        applySession()
        val contentFilter = when (filter) {
            YoutubeSearchFilter.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            YoutubeSearchFilter.ALBUMS -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            YoutubeSearchFilter.PLAYLISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        }
        val handler = youtube().searchQHFactory.fromQuery(trimmed, listOf(contentFilter), "")
        val extractor = youtube().getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    /**
     * One page of Liked Music, newest like first (YouTube Music's own order).
     * Pass [YoutubePage.continuation] back in to get the next page.
     */
    fun loadLikedPage(continuation: String? = null, pageSize: Int = LIKED_PAGE_SIZE): YoutubePage {
        applySession()
        if (!session.isSignedIn()) return YoutubePage(emptyList(), null)
        val hits = linkedSetOf<YoutubeHit>()
        var token = continuation
        var loggedIn: Boolean? = null
        var requests = 0
        do {
            val before = hits.size
            val root = musicBrowse(
                browseId = if (token.isNullOrBlank()) LIKED_MUSIC_BROWSE_ID else null,
                continuation = token,
            )
            if (loggedIn == null) loggedIn = loggedInFlag(root)
            collectLikedShelf(root, hits, Int.MAX_VALUE)
            token = findPlaylistContinuation(root)
            requests++
            // Empty continuation pages mean we reached the end of Liked Music.
            if (hits.size == before) {
                token = null
                break
            }
        } while (!token.isNullOrBlank() && hits.size < pageSize && requests < MAX_REQUESTS_PER_PAGE)
        if (hits.isEmpty() && continuation.isNullOrBlank()) {
            throw IllegalStateException(
                if (loggedIn == false) "YouTube Music did not accept this session. Sign in again from Account."
                else "YouTube Music returned no liked songs.",
            )
        }
        return YoutubePage(hits.toList(), token?.takeIf { it.isNotBlank() })
    }

    fun loadLikedMusic(limit: Int = 24): List<YoutubeHit> {
        if (!session.isSignedIn()) return emptyList()
        return runCatching { loadLikedPage(pageSize = limit).hits }
            .getOrDefault(emptyList())
            .take(limit)
    }

    /**
     * Personalized YouTube Music home (Quick picks, Listen again, Mixed for you, ...).
     * Requires a signed-in session; anonymous sessions only get regional charts.
     */
    fun loadHome(maxShelves: Int = 8): List<YoutubeShelf> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val shelves = mutableListOf<YoutubeShelf>()
        var root = musicBrowse(browseId = "FEmusic_home")
        var sectionList = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
        var page = 0
        while (sectionList != null && shelves.size < maxShelves && page < 3) {
            collectCarouselShelves(sectionList.optJSONArray("contents"), shelves)
            val continuation = findContinuation(sectionList.opt("continuations")) ?: break
            if (shelves.size >= maxShelves) break
            root = musicBrowse(continuation = continuation)
            sectionList = root.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")
            page++
        }
        return shelves.take(maxShelves)
    }

    /** Playlists saved in the user's YouTube Music library (FEmusic_liked_playlists). */
    fun loadLibraryPlaylists(limit: Int = 60): List<YoutubeHit> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val hits = linkedSetOf<YoutubeHit>()
        var root = musicBrowse(browseId = "FEmusic_liked_playlists")
        var grid = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents ->
                (0 until contents.length()).firstNotNullOfOrNull { index ->
                    val section = contents.optJSONObject(index) ?: return@firstNotNullOfOrNull null
                    section.optJSONObject("gridRenderer") ?: section.optJSONObject("musicShelfRenderer")
                }
            }
        var page = 0
        while (grid != null && hits.size < limit && page < 4) {
            collectShelfItems(grid.optJSONArray("items") ?: grid.optJSONArray("contents"), hits, limit)
            val continuation = findContinuation(grid.opt("continuations")) ?: break
            root = musicBrowse(continuation = continuation)
            grid = root.optJSONObject("continuationContents")?.let { cont ->
                cont.optJSONObject("gridContinuation") ?: cont.optJSONObject("musicShelfContinuation")
            }
            page++
        }
        return hits.filter { it.kind == YoutubeHit.Kind.COLLECTION && !it.url.contains("list=LM") }
            .take(limit)
    }

    /** Verifies the session against InnerTube and returns the signed-in account name. */
    fun loadAccountName(): String {
        applySession()
        if (!session.isSignedIn()) return ""
        val root = musicPost("account/account_menu", """{"context":${musicContext()}}""")
        val header = root.optJSONArray("actions")
            ?.optJSONObject(0)
            ?.optJSONObject("openPopupAction")
            ?.optJSONObject("popup")
            ?.optJSONObject("multiPageMenuRenderer")
            ?.optJSONObject("header")
            ?.optJSONObject("activeAccountHeaderRenderer")
            ?: throw IllegalStateException("YouTube Music did not recognise this session.")
        return jsonText(header.optJSONObject("accountName"))
    }

    /**
     * Makes sure visitorData/dataSyncId for the signed-in account are known.
     * Mirrors KuroMusic: visitorData from sw.js_data (with cookies), dataSyncId from the page config.
     */
    fun resetIdentity() {
        identityChecked = false
        visitorId = null
    }

    fun refreshIdentity() {
        applySession()
        val account = session.account.value
        if (!account.isSignedIn) return
        identityChecked = true
        var visitor = account.visitorData
        var dataSync = account.dataSyncId
        if (visitor.isBlank()) visitor = fetchVisitorFromServiceWorker()
        if (visitor.isBlank() || dataSync.isBlank()) {
            val html = runCatching {
                downloader.getText("https://music.youtube.com/", MUSIC_USER_AGENT, withCookies = true)
            }.getOrNull().orEmpty()
            if (visitor.isBlank()) visitor = configValue(html, "VISITOR_DATA")
            if (dataSync.isBlank()) dataSync = configValue(html, "DATASYNC_ID")
        }
        session.updateIdentity(visitorData = visitor, dataSyncId = dataSync)
    }

    private fun fetchVisitorFromServiceWorker(): String {
        val body = runCatching {
            downloader.getText("https://music.youtube.com/sw.js_data", MUSIC_USER_AGENT, withCookies = true)
        }.getOrNull().orEmpty()
        if (body.length < 6) return ""
        return runCatching {
            val candidates = JSONArray(body.substring(5)).optJSONArray(0)?.optJSONArray(2) ?: return ""
            (0 until candidates.length()).asSequence()
                .mapNotNull { candidates.opt(it) as? String }
                .firstOrNull { VISITOR_REGEX.containsMatchIn(it) }
                .orEmpty()
        }.getOrDefault("")
    }

    private fun configValue(html: String, key: String): String {
        val raw = Regex(""""$key"\s*:\s*"([^"]*)"""").find(html)?.groupValues?.getOrNull(1).orEmpty()
        return raw.replace("\\/", "/").replace("\\u003d", "=").replace("\\u0026", "&")
    }

    /**
     * Artist and album for one video, from YouTube Music's watch-next panel.
     * Shelf rows often only carry the artist, so playback and stats call this
     * when the album is still missing.
     */
    fun creditsFor(videoId: String): YoutubeCredits {
        val id = videoId.trim()
        if (id.length != 11) return YoutubeCredits("", "")
        applySession()
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .put("videoId", id)
            .put("playlistId", "RDAMVM$id")
        val root = musicPost("next", body.toString())
        val panels = mutableListOf<JSONObject>()
        collectCreditRenderers(root, panels)
        val match = panels.firstOrNull { renderer ->
            renderer.optString("videoId") == id || firstVideoId(renderer) == id
        } ?: panels.firstOrNull() ?: return YoutubeCredits("", "")
        val (artist, album) = creditsFrom(match)
        return YoutubeCredits(artist, album)
    }

    fun fillPlayedCredits(store: YoutubeMusicStore, playedSongIds: Set<String>, limit: Int = 40) {
        if (playedSongIds.isEmpty()) return
        store.creditGaps(playedSongIds, limit).forEach { gap ->
            if (!store.claimCreditLookup(gap.videoId)) return@forEach
            val credits = runCatching { creditsFor(gap.videoId) }.getOrNull()
            if (credits == null) {
                store.releaseCreditLookup(gap.videoId)
                return@forEach
            }
            val artist = credits.artist.ifBlank { gap.artist }
            val album = credits.album.ifBlank { gap.album }
            if (artist == gap.artist && album == gap.album) return@forEach
            store.rememberPlayback(
                songId = gap.songId,
                title = gap.title,
                artist = artist,
                album = album,
                artworkUrl = gap.artworkUrl,
                videoId = gap.videoId,
            )
        }
    }

    private fun collectCreditRenderers(node: Any?, found: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("playlistPanelVideoRenderer")?.let { found += it }
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { found += it }
                val keys = node.keys()
                while (keys.hasNext()) collectCreditRenderers(node.opt(keys.next()), found)
            }
            is JSONArray -> {
                for (index in 0 until node.length()) collectCreditRenderers(node.opt(index), found)
            }
        }
    }

    private fun creditsFrom(renderer: JSONObject): Pair<String, String> {
        val runs = buildList {
            addAll(textRuns(renderer.optJSONObject("longBylineText")))
            addAll(textRuns(renderer.optJSONObject("shortBylineText")))
            addAll(textRuns(renderer.optJSONObject("subtitle")))
            addAll(textRuns(renderer.optJSONObject("secondSubtitle")))
            val flex = renderer.optJSONArray("flexColumns") ?: return@buildList
            for (index in 1 until flex.length()) {
                val text = flex.optJSONObject(index)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                val parsed = textRuns(text)
                if (parsed.isNotEmpty()) addAll(parsed) else addAll(splitCreditRuns(jsonText(text)))
            }
        }
        return pickCredits(runs)
    }

    private fun splitCredits(value: String): Pair<String, String> = pickCredits(splitCreditRuns(value))

    private fun splitCreditRuns(value: String): List<Pair<String, String>> =
        value.split('•', '·')
            .map { it.trim() to "" }
            .filter { it.first.isNotBlank() }

    private fun textRuns(node: JSONObject?): List<Pair<String, String>> {
        if (node == null) return emptyList()
        val runs = node.optJSONArray("runs")
        if (runs == null) return splitCreditRuns(node.optString("simpleText"))
        return buildList {
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                val text = run.optString("text").trim()
                if (text.isBlank() || text == "•" || text == "·") continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    .orEmpty()
                if (browseId.isEmpty() && (text.contains('•') || text.contains('·'))) {
                    addAll(splitCreditRuns(text))
                } else {
                    add(text to browseId)
                }
            }
        }
    }

    private fun pickCredits(runs: List<Pair<String, String>>): Pair<String, String> {
        val useful = runs.filter { (text, _) -> isCreditLabel(text) }
        val artist = useful.firstOrNull { it.second.startsWith("UC") }?.first
            ?: useful.firstOrNull()?.first
            .orEmpty()
            .removeSuffix(" - Topic")
            .trim()
        val album = useful.firstOrNull { it.second.startsWith("MPRE") }?.first
            ?: useful.firstOrNull { (text, id) ->
                !text.equals(artist, ignoreCase = true) && !id.startsWith("UC")
            }?.first
            .orEmpty()
        return artist to album
    }

    private fun isCreditLabel(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.equals("YouTube Music", ignoreCase = true)) return false
        if (trimmed.equals("Song", ignoreCase = true)) return false
        if (trimmed.equals("Single", ignoreCase = true)) return false
        if (trimmed.equals("EP", ignoreCase = true)) return false
        if (trimmed.contains("view", ignoreCase = true)) return false
        if (CREDIT_DURATION.matches(trimmed) || CREDIT_YEAR.matches(trimmed)) return false
        return true
    }

    private fun musicBrowse(
        browseId: String? = null,
        continuation: String? = null,
        params: String? = null,
    ): JSONObject {
        val account = session.account.value
        if (account.isSignedIn && account.visitorData.isBlank() && !identityChecked) {
            identityChecked = true
            runCatching { refreshIdentity() }
        }
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .apply {
                if (!browseId.isNullOrBlank()) put("browseId", browseId)
                if (!params.isNullOrBlank()) put("params", params)
                if (!continuation.isNullOrBlank()) put("continuation", continuation)
            }
        val query = if (continuation.isNullOrBlank()) "" else {
            val token = java.net.URLEncoder.encode(continuation, Charsets.UTF_8.name())
            "&continuation=$token&ctoken=$token&type=next"
        }
        return musicPost("browse", body.toString(), query)
    }

    private fun musicPost(endpoint: String, body: String, query: String = ""): JSONObject {
        val json = downloader.postJson(
            url = "https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false&key=$MUSIC_API_KEY$query",
            json = body,
            origin = "https://music.youtube.com",
            clientName = MUSIC_CLIENT_ID,
            clientVersion = MUSIC_CLIENT_VERSION,
            userAgent = MUSIC_USER_AGENT,
            visitorId = currentVisitor(),
        )
        return JSONObject(json)
    }

    private fun currentVisitor(): String {
        val account = session.account.value
        if (account.isSignedIn && account.visitorData.isNotBlank()) return account.visitorData
        return musicVisitorId().ifBlank { INNER_TUNE_VISITOR }
    }

    private fun musicContext(): String {
        val account = session.account.value
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", MUSIC_CLIENT_VERSION)
            .put("gl", "US")
            .put("hl", "en")
            .put("visitorData", currentVisitor())
        val context = JSONObject().put("client", client)
        val user = JSONObject().put("lockedSafetyMode", false)
        if (account.isSignedIn && account.dataSyncId.isNotBlank()) {
            user.put("onBehalfOfUser", account.dataSyncId)
        }
        context.put("user", user)
        return context.toString()
    }

    private fun loggedInFlag(root: JSONObject): Boolean? {
        val services = root.optJSONObject("responseContext")?.optJSONArray("serviceTrackingParams") ?: return null
        for (index in 0 until services.length()) {
            val params = services.optJSONObject(index)?.optJSONArray("params") ?: continue
            for (p in 0 until params.length()) {
                val param = params.optJSONObject(p) ?: continue
                if (param.optString("key") == "logged_in") return param.optString("value") == "1"
            }
        }
        return null
    }

    private fun collectCarouselShelves(sections: JSONArray?, shelves: MutableList<YoutubeShelf>) {
        if (sections == null) return
        for (index in 0 until sections.length()) {
            val carousel = sections.optJSONObject(index)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val title = jsonText(
                carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title"),
            ).ifBlank { "For you" }
            val hits = linkedSetOf<YoutubeHit>()
            collectShelfItems(carousel.optJSONArray("contents"), hits, 30)
            if (hits.isEmpty()) continue
            val id = "home_${index}_${title.hashCode().toUInt()}"
            if (shelves.any { it.id == id }) continue
            shelves += YoutubeShelf(id = id, title = title, items = hits.toList())
        }
    }

    private fun collectShelfItems(items: JSONArray?, hits: MutableSet<YoutubeHit>, limit: Int) {
        if (items == null) return
        for (index in 0 until items.length()) {
            if (hits.size >= limit) return
            val item = items.optJSONObject(index) ?: continue
            item.optJSONObject("musicTwoRowItemRenderer")?.let { twoRowHit(it) }?.let { hits += it }
            item.optJSONObject("musicResponsiveListItemRenderer")?.let { listItemHit(it) }?.let { hits += it }
        }
    }

    private fun twoRowHit(renderer: JSONObject): YoutubeHit? {
        val title = jsonText(renderer.optJSONObject("title")).trim()
        if (title.isBlank()) return null
        val subtitle = jsonText(renderer.optJSONObject("subtitle")).trim()
        val navigation = renderer.optJSONObject("navigationEndpoint")
        val thumbnail = lastThumbnail(
            renderer.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail"),
        )
        navigation?.optJSONObject("watchEndpoint")?.let { watch ->
            val videoId = watch.optString("videoId")
            val playlistId = watch.optString("playlistId")
            if (isCatalogPlaylist(playlistId)) {
                return YoutubeHit(
                    url = "https://music.youtube.com/playlist?list=$playlistId",
                    title = title,
                    artist = subtitle,
                    durationSec = 0,
                    thumbnailUrl = thumbnail,
                    kind = YoutubeHit.Kind.COLLECTION,
                )
            }
            if (videoId.length == 11) {
                val isMix = isRadioMix(playlistId)
                val (artist, album) = if (isMix) subtitle to "" else splitCredits(subtitle)
                return YoutubeHit(
                    url = if (isMix) "https://www.youtube.com/watch?v=$videoId&list=$playlistId"
                    else "https://music.youtube.com/watch?v=$videoId",
                    title = title,
                    artist = artist,
                    durationSec = 0,
                    thumbnailUrl = if (isMix) thumbnail else fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                    kind = if (isMix) YoutubeHit.Kind.COLLECTION else YoutubeHit.Kind.TRACK,
                    album = album,
                )
            }
        }
        navigation?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }
            ?.let { playlistId ->
                return YoutubeHit(
                    url = "https://music.youtube.com/playlist?list=$playlistId",
                    title = title,
                    artist = subtitle,
                    durationSec = 0,
                    thumbnailUrl = thumbnail,
                    kind = YoutubeHit.Kind.COLLECTION,
                )
            }
        val browseId = navigation?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        val playlistId = renderer.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.let { play ->
                play.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                    ?.takeIf { it.isNotBlank() }
                    ?: play.optJSONObject("watchEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }
            }
            ?: browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
        if (playlistId.isNullOrBlank()) return null
        if (browseId.startsWith("UC") || browseId.startsWith("MPLA")) return null // artists
        return YoutubeHit(
            url = "https://music.youtube.com/playlist?list=$playlistId",
            title = title,
            artist = subtitle,
            durationSec = 0,
            thumbnailUrl = thumbnail,
            kind = YoutubeHit.Kind.COLLECTION,
        )
    }

    private fun listItemHit(renderer: JSONObject): YoutubeHit? {
        val title = flexColumnText(renderer, 0).trim()
        if (title.isBlank()) return null
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifBlank { firstVideoId(renderer.optJSONObject("overlay")) }
            .ifBlank {
                renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
            }
        if (videoId.length == 11) {
            val (artist, album) = creditsFrom(renderer)
            return YoutubeHit(
                url = "https://music.youtube.com/watch?v=$videoId",
                title = title,
                artist = artist,
                durationSec = 0,
                thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                kind = YoutubeHit.Kind.TRACK,
                album = album,
            )
        }
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            .orEmpty()
        val playlistId = browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?: firstPlaylistId(renderer.optJSONObject("overlay"))
        if (playlistId.isBlank()) return null
        return YoutubeHit(
            url = "https://music.youtube.com/playlist?list=$playlistId",
            title = title,
            artist = flexColumnText(renderer, 1),
            durationSec = 0,
            thumbnailUrl = lastThumbnail(
                renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"),
            ),
            kind = YoutubeHit.Kind.COLLECTION,
        )
    }

    private fun firstPlaylistId(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("playlistId").takeIf { it.isNotBlank() }?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val child = node.opt(keys.next())) {
                is JSONObject -> firstPlaylistId(child).takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> {
                    for (index in 0 until child.length()) {
                        (child.opt(index) as? JSONObject)?.let { item ->
                            firstPlaylistId(item).takeIf { it.isNotBlank() }?.let { return it }
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun lastThumbnail(thumbnail: JSONObject?): String? {
        val list = thumbnail?.optJSONArray("thumbnails") ?: return null
        var best: String? = null
        var bestSize = -1
        for (index in 0 until list.length()) {
            val entry = list.optJSONObject(index) ?: continue
            val size = entry.optInt("width", 0)
            val url = entry.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (size >= bestSize) {
                bestSize = size
                best = url
            }
        }
        return best?.let { url ->
            // Ask Google's image CDN for a larger square when the URL is a googleusercontent one.
            if (url.contains("googleusercontent.com") && url.contains("=w")) {
                url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
            } else fullBleedArtwork(url)
        }
    }

    private fun collectLikedShelf(root: JSONObject, hits: MutableSet<YoutubeHit>, limit: Int) {
        val rows = findLikedRows(root)
        if (rows == null) {
            collectVideoHits(root, hits, limit)
            return
        }
        addResponsiveListHits(rows, hits, limit)
    }

    private fun collectPlaylistShelf(root: JSONObject, hits: MutableSet<YoutubeHit>, limit: Int) {
        val rows = findPlaylistRows(root)
        if (rows == null) {
            collectVideoHits(root, hits, limit)
            return
        }
        addResponsiveListHits(rows, hits, limit)
    }

    private fun addResponsiveListHits(rows: JSONArray, hits: MutableSet<YoutubeHit>, limit: Int) {
        for (index in 0 until rows.length()) {
            if (hits.size >= limit) return
            val item = rows.optJSONObject(index) ?: continue
            if (item.optJSONObject("continuationItemRenderer") != null) continue
            val renderer = item.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            val title = flexColumnText(renderer, 0)
            if (videoId.length != 11 || title.isBlank()) continue
            val (artist, album) = creditsFrom(renderer)
            hits += YoutubeHit(
                url = "https://music.youtube.com/watch?v=$videoId",
                title = title,
                artist = artist,
                durationSec = 0,
                thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                kind = YoutubeHit.Kind.TRACK,
                album = album,
            )
        }
    }

    /** Playlist pages only: the playlist shelf, not related mixes below it. */
    private fun findPlaylistRows(root: JSONObject): JSONArray? {
        root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents -> firstPlaylistShelfContents(contents) }
            ?.let { return it }

        root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents -> firstPlaylistShelfContents(contents) }
            ?.let { return it }

        return root.optJSONObject("continuationContents")
            ?.optJSONObject("musicPlaylistShelfContinuation")
            ?.optJSONArray("contents")
    }

    private fun firstPlaylistShelfContents(sections: JSONArray): JSONArray? {
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            section.optJSONObject("musicPlaylistShelfRenderer")?.optJSONArray("contents")?.let { return it }
        }
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")?.let { return it }
        }
        return null
    }

    /** Desktop two-column Liked Music, mobile single-column, or a continuation page. */
    private fun findLikedRows(root: JSONObject): JSONArray? {
        root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents -> firstShelfContents(contents) }
            ?.let { return it }

        root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents -> firstShelfContents(contents) }
            ?.let { return it }

        val continuation = root.optJSONObject("continuationContents") ?: return null
        return continuation.optJSONObject("musicPlaylistShelfContinuation")?.optJSONArray("contents")
            ?: continuation.optJSONObject("musicShelfContinuation")?.optJSONArray("contents")
    }

    private fun firstShelfContents(sections: JSONArray): JSONArray? {
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            section.optJSONObject("musicPlaylistShelfRenderer")?.optJSONArray("contents")?.let { return it }
            section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")?.let { return it }
        }
        return null
    }

    private fun musicVisitorId(): String {
        visitorId?.takeIf { it.isNotBlank() }?.let { return it }
        val html = runCatching {
            downloader.getText("https://music.youtube.com", MUSIC_USER_AGENT)
        }.getOrNull().orEmpty()
        val found = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (found.isNotBlank()) visitorId = found
        return found
    }

    fun loadSubscriptionFeed(limit: Int = 20): List<YoutubeHit> {
        applySession()
        val extractor = youtube().getFeedExtractor("https://www.youtube.com/feed/subscriptions")
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    fun loadRadio(watchUrl: String, limit: Int = 25): List<YoutubeHit> {
        applySession()
        val videoId = watchUrl.substringAfter("v=", "")
            .substringBefore('&')
            .substringBefore('?')
            .trim()
        require(videoId.length >= 6) { "This track can't start a radio" }
        return loadCollectionTracks("https://www.youtube.com/watch?v=$videoId&list=RD$videoId", limit)
    }

    fun loadCollectionTracks(url: String, limit: Int = 200): List<YoutubeHit> {
        applySession()
        val playlistId = playlistIdFromUrl(url)
        if (playlistId == "LM") return loadLikedPage(pageSize = limit).hits.take(limit)
        if (playlistId.isNotBlank()) {
            val viaMusic = runCatching { loadPlaylistViaInnerTube(playlistId, limit) }
                .getOrDefault(emptyList())
            if (viaMusic.isNotEmpty()) return viaMusic
        }
        val info = PlaylistInfo.getInfo(youtube(), url.trim())
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() && it.kind == YoutubeHit.Kind.TRACK }
            .take(limit)
            .toList()
    }

    /**
     * Same path KuroMusic/InnerTune use: browse `VL` + playlist id on music.youtube.com.
     * Recap / official playlists (RDCLAK…) fail in NewPipe but work here.
     */
    private fun loadPlaylistViaInnerTube(playlistId: String, limit: Int): List<YoutubeHit> {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val hits = linkedSetOf<YoutubeHit>()
        var token: String? = null
        var first = true
        var requests = 0
        do {
            val before = hits.size
            val root = musicBrowse(
                browseId = if (first) browseId else null,
                continuation = token,
            )
            first = false
            collectPlaylistShelf(root, hits, limit)
            token = findPlaylistContinuation(root)
            requests++
            if (hits.size == before) break
        } while (!token.isNullOrBlank() && hits.size < limit && requests < 8)
        return hits.take(limit)
    }

    /** InnerTube like/like and like/removelike — this is what actually updates Liked Music. */
    fun likeVideo(videoId: String, like: Boolean) {
        applySession()
        require(session.isSignedIn()) { "Sign in to like songs on YouTube Music." }
        require(videoId.length == 11) { "Missing video id." }
        val endpoint = if (like) "like/like" else "like/removelike"
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .put("target", JSONObject().put("videoId", videoId))
        musicPost(endpoint, body.toString())
    }

    /** Browse/edit_playlist ACTION_ADD_VIDEO, same payload KuroMusic sends. */
    fun addVideosToPlaylist(playlistId: String, videoIds: List<String>) {
        applySession()
        require(session.isSignedIn()) { "Sign in to add songs to a playlist." }
        val ids = videoIds.map { it.trim() }.filter { it.length == 11 }.distinct()
        require(ids.isNotEmpty()) { "No songs to add." }
        val actions = JSONArray()
        ids.forEach { videoId ->
            actions.put(
                JSONObject()
                    .put("action", "ACTION_ADD_VIDEO")
                    .put("addedVideoId", videoId),
            )
        }
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .put("playlistId", playlistId.removePrefix("VL"))
            .put("actions", actions)
        musicPost("browse/edit_playlist", body.toString())
    }

    fun createPlaylist(title: String, videoIds: List<String> = emptyList()): String {
        applySession()
        require(session.isSignedIn()) { "Sign in to create a playlist." }
        val name = title.trim()
        require(name.isNotEmpty()) { "Give the playlist a name." }
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .put("title", name)
            .put("privacyStatus", "PRIVATE")
        val ids = videoIds.map { it.trim() }.filter { it.length == 11 }
        if (ids.isNotEmpty()) body.put("videoIds", JSONArray(ids))
        val root = musicPost("playlist/create", body.toString())
        val created = root.optString("playlistId").ifBlank {
            root.optJSONObject("playlistId")?.optString("playlistId").orEmpty()
        }
        require(created.isNotBlank()) { "YouTube Music did not create the playlist." }
        return created
    }

    /**
     * Resolves a stream ahead of a possible tap so playback can start immediately.
     * Errors are swallowed: this is best effort, and a real tap will surface them.
     */
    fun prefetchTrack(watchUrl: String) {
        val key = watchUrl.trim()
        if (key.isBlank() || cachedTrack(key) != null) return
        if (!inFlightResolves.add(key)) return
        try {
            resolveTrack(key)
        } catch (error: Exception) {
            Timber.d(error, "YouTube: prefetch failed for %s", key)
        } finally {
            inFlightResolves.remove(key)
        }
    }

    fun resolveTrack(watchUrl: String, avoidStreamUrl: String? = null): YoutubeResolvedTrack {
        applySession()
        val key = watchUrl.trim()
        val avoided = avoidStreamUrl?.trim().orEmpty()
        cachedTrack(key)?.takeIf { it.streamUrl != avoided }?.let { return it }

        val extracted = runCatching { extractResolved(key) }.getOrNull()
            ?.takeIf { it.streamUrl.isNotBlank() && it.streamUrl != avoided }
        if (extracted != null) {
            resolveCache[key] = CachedResolve(extracted, System.currentTimeMillis())
            return extracted
        }

        val videoId = videoIdFromUrl(key)
        val fallback = if (videoId.length == 11) {
            runCatching { audioFromPlayerClients(videoId, avoided) }.getOrNull()
        } else {
            null
        }
        if (fallback != null) {
            resolveCache[key] = CachedResolve(fallback, System.currentTimeMillis())
            return fallback
        }
        throw IllegalStateException("No playable audio stream for this track.")
    }

    private fun extractResolved(key: String): YoutubeResolvedTrack {
        val info = StreamInfo.getInfo(youtube(), key)
        val stream = pickBestAudioStream(info.audioStreams)
            ?: throw IllegalStateException("No playable audio stream for this track.")
        val videoId = videoIdFromUrl(info.url.orEmpty()).ifBlank { videoIdFromUrl(key) }
        val credits = if (videoId.length == 11) {
            runCatching { creditsFor(videoId) }.getOrDefault(YoutubeCredits("", ""))
        } else {
            YoutubeCredits("", "")
        }
        return YoutubeResolvedTrack(
            watchUrl = info.url.orEmpty().ifBlank { key },
            title = info.name.orEmpty().ifBlank { "Unknown title" },
            artist = credits.artist.ifBlank {
                info.uploaderName.orEmpty().removeSuffix(" - Topic").trim()
            },
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            streamUrl = stream.content,
            mimeType = stream.format?.mimeType,
            artworkUrl = fullBleedArtwork(info.thumbnails.maxByOrNull { it.height }?.url),
            album = credits.album,
        )
    }

    /**
     * NewPipe's web player has no audio file for some YouTube Music tracks
     * (the link is SABR-only, or the catalog video is blocked on that client).
     * Those are the songs a playlist skips. A music or embedded player response
     * still carries a direct audio URL for them.
     */
    private fun audioFromPlayerClients(videoId: String, avoidUrl: String): YoutubeResolvedTrack? {
        val responses = listOf(
            runCatching { musicPlayer(videoId) },
            runCatching { embeddedPlayer(videoId) },
            runCatching { androidVrPlayer(videoId) },
        )
        for (response in responses) {
            val root = response.getOrNull() ?: continue
            val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (status.isNotEmpty() && status != "OK") continue
            val picked = pickPlayableAudio(audioCandidates(root), avoidUrl.ifBlank { null }) ?: continue
            val details = root.optJSONObject("videoDetails")
            val title = details?.optString("title").orEmpty().ifBlank { "Unknown title" }
            val author = details?.optString("author").orEmpty().removeSuffix(" - Topic").trim()
            val length = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
            val thumb = details?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs ->
                    (0 until thumbs.length())
                        .mapNotNull { thumbs.optJSONObject(it)?.optString("url") }
                        .lastOrNull { it.isNotBlank() }
                }
            return YoutubeResolvedTrack(
                watchUrl = "https://music.youtube.com/watch?v=$videoId",
                title = title,
                artist = author,
                durationMs = length.coerceAtLeast(0L) * 1000L,
                streamUrl = picked.url,
                mimeType = picked.mimeType.substringBefore(';').trim().ifBlank { null },
                artworkUrl = fullBleedArtwork(thumb),
            )
        }
        return null
    }

    private fun musicPlayer(videoId: String): JSONObject {
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        return musicPost("player", body.toString())
    }

    private fun embeddedPlayer(videoId: String): JSONObject = innertubePlayer(
        videoId = videoId,
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        clientVersion = "2.0",
        clientId = "85",
        userAgent = MUSIC_USER_AGENT,
    )

    private fun androidVrPlayer(videoId: String): JSONObject = innertubePlayer(
        videoId = videoId,
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip",
        extraClient = JSONObject()
            .put("deviceMake", "Oculus")
            .put("deviceModel", "Quest 3")
            .put("androidSdkVersion", 32)
            .put("osName", "Android")
            .put("osVersion", "12"),
    )

    private fun innertubePlayer(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientId: String,
        userAgent: String,
        extraClient: JSONObject? = null,
    ): JSONObject {
        val client = JSONObject()
            .put("clientName", clientName)
            .put("clientVersion", clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        extraClient?.keys()?.let { keys ->
            while (keys.hasNext()) {
                val name = keys.next()
                client.put(name, extraClient.get(name))
            }
        }
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val json = downloader.postJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            json = body.toString(),
            origin = "https://www.youtube.com",
            clientName = clientId,
            clientVersion = clientVersion,
            userAgent = userAgent,
        )
        return JSONObject(json)
    }

    private fun audioCandidates(root: JSONObject): List<YoutubeAudioCandidate> {
        val streaming = root.optJSONObject("streamingData") ?: return emptyList()
        return buildList {
            addAll(candidatesFrom(streaming.optJSONArray("adaptiveFormats")))
            addAll(candidatesFrom(streaming.optJSONArray("formats")))
        }
    }

    private fun candidatesFrom(formats: JSONArray?): List<YoutubeAudioCandidate> {
        if (formats == null) return emptyList()
        return buildList {
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                val url = format.optString("url")
                if (!url.startsWith("http")) continue
                add(
                    YoutubeAudioCandidate(
                        url = url,
                        mimeType = format.optString("mimeType"),
                        bitrate = format.optInt("bitrate"),
                    ),
                )
            }
        }
    }

    private fun cachedTrack(key: String): YoutubeResolvedTrack? = resolveCache[key]
        ?.takeIf { System.currentTimeMillis() - it.cachedAtMs < RESOLVE_CACHE_TTL_MS }
        ?.track

    fun toSong(track: YoutubeResolvedTrack, watchUrl: String = track.watchUrl): Song {
        val permalink = watchUrl.ifBlank { track.watchUrl }.trim()
        val id = songIdForUrl(permalink)
        if (permalink.startsWith("http")) rememberWatchUrl(id, permalink)
        return Song(
            id = id,
            title = track.title,
            artist = track.artist,
            artistId = -1L,
            artists = emptyList(),
            album = track.album,
            albumId = -1L,
            albumArtist = track.artist,
            path = track.streamUrl,
            contentUriString = track.streamUrl,
            albumArtUriString = track.artworkUrl,
            duration = track.durationMs,
            genre = null,
            mimeType = track.mimeType,
            bitrate = null,
            sampleRate = null,
        )
    }

    fun listSong(hit: YoutubeHit): Song {
        val id = songIdForUrl(hit.url)
        rememberWatchUrl(id, hit.url)
        return Song(
            id = id,
            title = hit.title,
            artist = hit.artist,
            artistId = -1L,
            artists = emptyList(),
            album = hit.album,
            albumId = -1L,
            albumArtist = hit.artist,
            path = hit.url,
            contentUriString = hit.url,
            albumArtUriString = hit.thumbnailUrl,
            duration = hit.durationSec.coerceAtLeast(0L) * 1000L,
            genre = null,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )
    }

    private fun youtube(): YoutubeService = ServiceList.YouTube

    private fun applySession() {
        val cookies = session.cookieHeader().takeIf { it.isNotBlank() }
        downloader.cookieHeader = cookies
        injectCookie(cookies)
    }

    private fun injectCookie(cookie: String?) {
        val helper = YoutubeParsingHelper::class.java
        val field = helper.declaredFields.firstOrNull { candidate ->
            java.lang.reflect.Modifier.isStatic(candidate.modifiers) &&
                candidate.type == String::class.java &&
                candidate.name.contains("cookie", ignoreCase = true)
        } ?: return
        field.isAccessible = true
        field.set(null, cookie)
    }

    private fun collectVideoHits(node: Any?, hits: MutableSet<YoutubeHit>, limit: Int) {
        if (hits.size >= limit || node == null) return
        when (node) {
            is JSONObject -> {
                val videoId = node.optString("videoId").ifBlank {
                    node.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                }.ifBlank {
                    node.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        .orEmpty()
                }.ifBlank {
                    if (node.has("flexColumns")) firstVideoId(node.optJSONObject("overlay")) else ""
                }
                if (videoId.length == 11 && looksLikeTrack(node)) {
                    val title = videoTitle(node)
                    if (title.isNotBlank()) {
                        val (artist, album) = creditsFrom(node)
                        hits += YoutubeHit(
                            url = "https://music.youtube.com/watch?v=$videoId",
                            title = title,
                            artist = artist,
                            durationSec = 0,
                            thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                            kind = YoutubeHit.Kind.TRACK,
                            album = album,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext() && hits.size < limit) {
                    collectVideoHits(node.opt(keys.next()), hits, limit)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    if (hits.size >= limit) return
                    collectVideoHits(node.opt(index), hits, limit)
                }
            }
        }
    }

    private fun looksLikeTrack(node: JSONObject): Boolean {
        return node.has("playlistItemData") ||
            node.has("flexColumns") ||
            node.has("longBylineText") ||
            node.has("shortBylineText") ||
            node.has("lengthText") ||
            node.optJSONObject("title") != null
    }

    private fun firstVideoId(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("videoId").takeIf { it.length == 11 }?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val child = node.opt(keys.next())) {
                is JSONObject -> firstVideoId(child).takeIf { it.length == 11 }?.let { return it }
                is JSONArray -> {
                    for (index in 0 until child.length()) {
                        val item = child.opt(index)
                        if (item is JSONObject) {
                            firstVideoId(item).takeIf { it.length == 11 }?.let { return it }
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun findPlaylistContinuation(node: JSONObject): String? {
        val shelf = findNamedObject(node, "musicPlaylistShelfRenderer")
            ?: findNamedObject(node, "musicPlaylistShelfContinuation")
            ?: findNamedObject(node, "musicShelfRenderer")
            ?: findNamedObject(node, "musicShelfContinuation")
        if (shelf != null) {
            findContinuation(shelf.opt("continuations"))?.let { return it }
            tokenFromContinuationItems(shelf.optJSONArray("contents"))?.let { return it }
        }
        return null
    }

    /** Next-page tokens sit on continuationItemRenderer, usually the last row of the shelf. */
    private fun tokenFromContinuationItems(contents: JSONArray?): String? {
        if (contents == null) return null
        for (index in contents.length() - 1 downTo 0) {
            val renderer = contents.optJSONObject(index)?.optJSONObject("continuationItemRenderer")
                ?: continue
            findContinuation(renderer)?.let { return it }
        }
        return null
    }

    private fun findNamedObject(node: Any?, name: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(name)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findNamedObject(node.opt(keys.next()), name)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    findNamedObject(node.opt(index), name)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findContinuation(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                node.optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findContinuation(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    findContinuation(node.opt(index))?.let { return it }
                }
            }
        }
        return null
    }

    private fun videoTitle(node: JSONObject): String {
        jsonText(node.optJSONObject("title")).takeIf { it.isNotBlank() }?.let { return it }
        node.optString("title").takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }?.let { return it }
        flexColumnText(node, 0).takeIf { it.isNotBlank() }?.let { return it }
        val label = node.optJSONObject("accessibility")
            ?.optJSONObject("accessibilityData")
            ?.optString("label")
            .orEmpty()
        return label.substringBefore(" by ").substringBefore(',').trim()
    }

    private fun flexColumnText(node: JSONObject, column: Int): String {
        val flex = node.optJSONArray("flexColumns") ?: return ""
        val text = flex.optJSONObject(column)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        return jsonText(text)
    }

    private fun jsonText(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
    }

    private fun InfoItem.toHitOrNull(): YoutubeHit? = when (this) {
        is StreamInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Unknown title" },
            artist = uploaderName.orEmpty().removeSuffix(" - Topic").trim(),
            durationSec = duration.coerceAtLeast(0L),
            thumbnailUrl = fullBleedArtwork(thumbnails.maxByOrNull { it.height }?.url),
            kind = YoutubeHit.Kind.TRACK,
        )
        is PlaylistInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Playlist" },
            artist = uploaderName.orEmpty().removeSuffix(" - Topic").trim(),
            durationSec = 0L,
            thumbnailUrl = fullBleedArtwork(thumbnails.maxByOrNull { it.height }?.url),
            kind = YoutubeHit.Kind.COLLECTION,
        )
        else -> null
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val picked = pickPlayableAudio(
            streams.map { stream ->
                val mime = stream.format?.mimeType
                    ?: if (stream.deliveryMethod == DeliveryMethod.HLS) "application/vnd.apple.mpegurl" else ""
                YoutubeAudioCandidate(
                    url = stream.content.orEmpty(),
                    mimeType = mime,
                    bitrate = stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate,
                )
            },
        ) ?: return null
        return streams.firstOrNull { it.content == picked.url }
    }

    companion object {
        const val EXTRA_WATCH_URL = "com.theveloper.pixelplay.youtube.WATCH_URL"
        private val watchUrlBySongId = ConcurrentHashMap<String, String>()
        private val initialized = AtomicBoolean(false)
        private const val RESOLVE_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val MUSIC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val MUSIC_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val MUSIC_CLIENT_ID = "67"
        private const val MUSIC_CLIENT_VERSION = "1.20260804.16.00"
        private const val INNER_TUNE_VISITOR = "CgtsZG1ySnZiQWtSbyiMjuGSBg=="
        private const val LIKED_MUSIC_BROWSE_ID = "VLLM"
        // One InnerTube response per scroll page so older likes arrive as you go down.
        const val LIKED_PAGE_SIZE = 40
        private const val MAX_REQUESTS_PER_PAGE = 1
        private val VISITOR_REGEX = Regex("^Cg[ts]")
        private val CREDIT_DURATION = Regex("""^\d+:\d{2}(?::\d{2})?$""")
        private val CREDIT_YEAR = Regex("""^\d{4}$""")

        fun rememberWatchUrl(songId: String, watchUrl: String) {
            val id = songId.trim()
            val url = watchUrl.trim()
            if (id.isNotEmpty() && url.startsWith("http")) watchUrlBySongId[id] = url
        }

        fun watchUrlForSongId(songId: String): String? = watchUrlBySongId[songId]

        fun songIdForUrl(url: String): String = "yt_${url.trim().hashCode().toUInt()}"

        fun videoIdFromUrl(url: String): String {
            val fromQuery = url.substringAfter("v=", "")
                .substringBefore('&')
                .substringBefore('?')
                .trim()
            return if (fromQuery.length == 11) fromQuery else ""
        }

        fun playlistIdFromUrl(url: String): String {
            val fromList = url.substringAfter("list=", "")
                .substringBefore('&')
                .substringBefore('?')
                .trim()
            if (fromList.isNotBlank()) return fromList.removePrefix("VL")
            return Regex("""browse/(VL)?([^/?&]+)""")
                .find(url)
                ?.groupValues
                ?.getOrNull(2)
                .orEmpty()
                .removePrefix("VL")
        }

        fun isCatalogPlaylist(playlistId: String): Boolean {
            if (playlistId.isBlank()) return false
            return playlistId.startsWith("PL") ||
                playlistId.startsWith("OLAK") ||
                playlistId.startsWith("RDCLAK") ||
                playlistId.startsWith("RDTMA") ||
                playlistId == "LM" ||
                playlistId == "WL" ||
                playlistId.startsWith("VL")
        }

        fun isRadioMix(playlistId: String): Boolean {
            if (!playlistId.startsWith("RD")) return false
            return !isCatalogPlaylist(playlistId) && !playlistId.startsWith("RDAMVM")
        }

        fun fullBleedArtwork(url: String?): String? {
            val raw = url?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val videoId = Regex("/vi/([A-Za-z0-9_-]{6,})/").find(raw)?.groupValues?.getOrNull(1)
                ?: return raw
            return "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        }

        private fun ensureInitialized(downloader: YoutubeDownloader) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
                Timber.i("YouTube: NewPipe Extractor initialized")
            }
        }
    }
}
