package com.theveloper.pixelplay.soundcloud

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SoundCloud access via NewPipe Extractor (web api-v2).
 *
 * Likes / Tracks / Playlists use the user's **public** profile URL
 * (`soundcloud.com/{username}/…`) — not OAuth. Private likes won't show.
 */
@Singleton
class SoundCloudClient @Inject constructor(
    private val sessionApi: SoundCloudSessionApi,
) {
    private val downloader = SoundCloudDownloader()
    private val runtimeClientId = AtomicReference<String?>(null)

    init {
        ensureInitialized(downloader)
    }

    fun setClientIdOverride(clientId: String?) {
        val trimmed = clientId?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            runtimeClientId.set(null)
            return
        }
        runtimeClientId.set(trimmed)
        injectClientId(trimmed)
        sessionApi.updateSession(
            token = downloader.oauthToken,
            cookies = downloader.cookieHeader,
            clientIdValue = trimmed,
        )
    }

    fun setSession(oauthToken: String?, cookieHeader: String?) {
        downloader.oauthToken = oauthToken?.trim()?.takeIf { it.isNotEmpty() }
        downloader.cookieHeader = cookieHeader?.trim()?.takeIf { it.isNotEmpty() }
        sessionApi.updateSession(
            token = downloader.oauthToken,
            cookies = downloader.cookieHeader,
            clientIdValue = runtimeClientId.get(),
        )
    }

    val hasSession: Boolean get() = sessionApi.hasSession

    @Throws(Exception::class)
    fun loadFeed(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadFeed(limit)

    @Throws(Exception::class)
    fun loadMyLikes(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyLikes(limit)

    @Throws(Exception::class)
    fun loadMyTracks(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyTracks(limit)

    @Throws(Exception::class)
    fun loadMyPlaylists(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyPlaylists(limit)

    @Throws(Exception::class)
    fun loadDiscover(limit: Int = 30): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val extractor = soundCloud().kioskList.defaultKioskExtractor
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        return info.relatedItems
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .map { it.toHit() }
            .filter { it.url.isNotBlank() }
            .toList()
            .let { filterOutDrmTracks(it, limit) }
    }

    @Throws(Exception::class)
    fun search(query: String, limit: Int = 30): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query is empty" }
        val extractor = soundCloud().getSearchExtractor(trimmed)
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .toList()
            .let { filterOutDrmTracks(it, limit) }
    }

    @Throws(Exception::class)
    fun loadUserLikes(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.LIKES, limit)

    @Throws(Exception::class)
    fun loadUserTracks(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.TRACKS, limit)

    @Throws(Exception::class)
    fun loadUserPlaylists(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.PLAYLISTS, limit)

    @Throws(Exception::class)
    fun loadPlaylistTracks(playlistUrl: String, limit: Int = 100): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val info = PlaylistInfo.getInfo(soundCloud(), playlistUrl.trim())
        return info.relatedItems
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .map { it.toHit() }
            .filter { it.url.isNotBlank() }
            .toList()
            .let { filterOutDrmTracks(it, limit) }
    }

    @Throws(Exception::class)
    fun resolveTrack(url: String): SoundCloudResolvedTrack {
        ensureClientIdReady()
        val trimmedUrl = url.trim()
        val info = try {
            StreamInfo.getInfo(soundCloud(), trimmedUrl)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "No playable stream (often DRM encrypted-hls). Try another track. ${t.message}",
                t,
            )
        }
        val stream = pickBestAudioStream(info.audioStreams)
            ?: throw IllegalStateException(
                "No progressive/plain stream available. DRM-protected tracks can't be played or downloaded.",
            )
        return SoundCloudResolvedTrack(
            url = info.url.orEmpty().ifBlank { trimmedUrl },
            title = info.name.orEmpty().ifBlank { "Unknown title" },
            artist = info.uploaderName.orEmpty().ifBlank { "Unknown artist" },
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            streamUrl = stream.content,
            mimeType = stream.format?.mimeType,
            artworkUrl = info.thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    fun toSong(track: SoundCloudResolvedTrack): Song {
        val id = "sc_${track.url.hashCode().toUInt()}"
        return Song(
            id = id,
            title = track.title,
            artist = track.artist,
            artistId = -1L,
            artists = emptyList(),
            album = "SoundCloud",
            albumId = -1L,
            albumArtist = track.artist,
            path = track.streamUrl,
            contentUriString = track.streamUrl,
            albumArtUriString = track.artworkUrl,
            duration = track.durationMs,
            genre = "SoundCloud",
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            discNumber = null,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            mimeType = track.mimeType,
            bitrate = null,
            sampleRate = null,
            telegramFileId = null,
            telegramChatId = null,
            neteaseId = null,
            gdriveFileId = null,
            qqMusicMid = null,
            navidromeId = null,
            jellyfinId = null,
        )
    }

    private fun loadChannelTab(username: String, tab: String, limit: Int): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val user = username.trim().removePrefix("@").removePrefix("https://soundcloud.com/")
            .substringBefore('/')
        require(user.isNotEmpty()) { "Set your SoundCloud username in Settings → Experimental" }

        val url = "https://soundcloud.com/$user" + when (tab) {
            ChannelTabs.LIKES -> "/likes"
            ChannelTabs.TRACKS -> "/tracks"
            ChannelTabs.PLAYLISTS -> "/sets"
            ChannelTabs.ALBUMS -> "/albums"
            else -> ""
        }
        val link = soundCloud().channelTabLHFactory.fromUrl(url)
        val info = ChannelTabInfo.getInfo(soundCloud(), link)
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .toList()
            .let { filterOutDrmTracks(it, limit) }
    }

    /**
     * Drop DRM-only tracks (encrypted-hls with no progressive/plain stream).
     * Playlists are kept; unknown resolve failures keep the row (avoid over-hiding).
     */
    private fun filterOutDrmTracks(
        hits: List<SoundCloudSearchHit>,
        limit: Int,
    ): List<SoundCloudSearchHit> {
        if (hits.isEmpty()) return hits
        val out = ArrayList<SoundCloudSearchHit>(minOf(hits.size, limit))
        for (hit in hits) {
            if (out.size >= limit) break
            if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                out.add(hit)
                continue
            }
            if (!sessionApi.isDrmOnlyUrl(hit.url)) {
                out.add(hit)
            }
        }
        return out
    }

    private fun InfoItem.toHitOrNull(): SoundCloudSearchHit? = when (this) {
        is StreamInfoItem -> toHit()
        is PlaylistInfoItem -> SoundCloudSearchHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Playlist" },
            artist = uploaderName.orEmpty().ifBlank { "SoundCloud" },
            durationSec = 0,
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
            kind = SoundCloudSearchHit.Kind.PLAYLIST,
            streamCount = streamCount.coerceAtLeast(0L),
        )
        else -> null
    }

    private fun StreamInfoItem.toHit() = SoundCloudSearchHit(
        url = url.orEmpty(),
        title = name.orEmpty().ifBlank { "Unknown title" },
        artist = uploaderName.orEmpty().ifBlank { "Unknown artist" },
        durationSec = duration.coerceAtLeast(0L),
        thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
        kind = SoundCloudSearchHit.Kind.TRACK,
    )

    private fun ensureClientIdReady() {
        val id = runtimeClientId.get()?.trim().orEmpty()
        if (id.isEmpty()) {
            throw IllegalStateException("Set SoundCloud client_id in Settings → Experimental")
        }
        injectClientId(id)
    }

    private fun injectClientId(clientId: String) {
        try {
            val field = SoundcloudParsingHelper::class.java.getDeclaredField("clientId")
            field.isAccessible = true
            synchronized(SoundcloudParsingHelper::class.java) {
                field.set(null, clientId)
            }
        } catch (t: Throwable) {
            throw IllegalStateException("Could not inject SoundCloud client_id: ${t.message}", t)
        }
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val progressive = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = progressive.ifEmpty {
            streams.filter { it.deliveryMethod != DeliveryMethod.HLS || !it.content.contains("encrypted") }
                .ifEmpty { streams }
        }
        return pool.maxWithOrNull(
            compareBy<AudioStream> { it.averageBitrate }.thenBy { it.bitrate },
        )
    }

    private fun soundCloud(): SoundcloudService = ServiceList.SoundCloud

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun ensureInitialized(downloader: SoundCloudDownloader) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
                Timber.i("SoundCloud: NewPipe Extractor initialized")
            }
        }
    }
}
