package com.theveloper.pixelplay.data.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Wire models for the gobackend JSON API. Field names MUST match the Go
 * structs in SpotiFLAC-Mobile's go_backend (exports_download.go,
 * extension_provider_types.go, progress.go, extension_manager.go).
 */
@Serializable
data class DownloadRequest(
    /** Download extension id; Go locks the download to it (strict mode, use_fallback=false). */
    val service: String,
    /** Extension the track was found with; Go uses it for metadata enrichment. */
    val source: String = "",
    @SerialName("track_name") val trackName: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String,
    @SerialName("album_artist") val albumArtist: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    /** Go builds "<dir>/<filename_format><ext>" here; the extension picks the real extension. */
    @SerialName("output_dir") val outputDir: String,
    @SerialName("output_ext") val outputExt: String = "",
    @SerialName("filename_format") val filenameFormat: String = "{artist} - {title}",
    val quality: String,
    @SerialName("deezer_id") val deezerId: String = "",
    @SerialName("tidal_id") val tidalId: String = "",
    @SerialName("qobuz_id") val qobuzId: String = "",
    val genre: String = "",
    val label: String = "",
    val copyright: String = "",
    val composer: String = "",
    val explicit: Boolean = false,
    @SerialName("album_type") val albumType: String = "",
    val upc: String = "",
    @SerialName("embed_metadata") val embedMetadata: Boolean = true,
    @SerialName("item_id") val itemId: String,
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("track_number") val trackNumber: Int = 0,
    @SerialName("disc_number") val discNumber: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("total_discs") val totalDiscs: Int = 0,
    @SerialName("release_date") val releaseDate: String = "",
    val isrc: String = "",
    @SerialName("spotify_id") val spotifyId: String = "",
    @SerialName("provider_track_id") val providerTrackId: String = "",
    @SerialName("download_provider") val downloadProvider: String = "",
    // Spec: built-in providers are retired; extension mode is mandatory.
    @SerialName("use_extensions") val useExtensions: Boolean = true,
    @SerialName("use_fallback") val useFallback: Boolean = false,
)

@Serializable
data class DownloadResponse(
    val success: Boolean = false,
    val message: String = "",
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("resolved_file_name") val resolvedFileName: String? = null,
    val error: String? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int = 0,
    @SerialName("already_exists") val alreadyExists: Boolean = false,
    @SerialName("actual_extension") val actualExtension: String? = null,
    @SerialName("audio_codec") val audioCodec: String? = null,
    @SerialName("actual_bit_depth") val actualBitDepth: Int = 0,
    @SerialName("actual_sample_rate") val actualSampleRate: Int = 0,
    val service: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("track_number") val trackNumber: Int = 0,
    @SerialName("disc_number") val discNumber: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("total_discs") val totalDiscs: Int = 0,
    val isrc: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val genre: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val composer: String? = null,
    @SerialName("lyrics_lrc") val lyricsLrc: String? = null,
    /** Legacy flat key; newer extensions use [decryption]. */
    @SerialName("decryption_key") val decryptionKey: String? = null,
    val decryption: DecryptionInfo? = null,
)

/** Go extension_provider_types.go DownloadDecryptionInfo: the host must decrypt file_path itself. */
@Serializable
data class DecryptionInfo(
    val strategy: String = "",
    val key: String = "",
    val iv: String = "",
    @SerialName("input_format") val inputFormat: String = "",
    @SerialName("output_extension") val outputExtension: String = "",
)

@Serializable
data class ItemProgress(
    @SerialName("item_id") val itemId: String,
    @SerialName("bytes_total") val bytesTotal: Long = 0,
    @SerialName("bytes_received") val bytesReceived: Long = 0,
    val progress: Float = 0f,
    @SerialName("speed_mbps") val speedMbps: Float = 0f,
    @SerialName("is_downloading") val isDownloading: Boolean = false,
    val status: String = "",
    val stage: String? = null,
)

@Serializable
data class ProgressDelta(
    val seq: Long,
    val reset: Boolean = false,
    val items: Map<String, ItemProgress> = emptyMap(),
    val removed: List<String> = emptyList(),
)

@Serializable
data class ExtTrack(
    val id: String,
    val name: String,
    val artists: String = "",
    @SerialName("album_name") val albumName: String = "",
    @SerialName("album_artist") val albumArtist: String = "",
    @SerialName("duration_ms") val durationMs: Int = 0,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("provider_id") val providerId: String = "",
    val isrc: String? = null,
    @SerialName("spotify_id") val spotifyId: String? = null,
    @SerialName("provider_track_id") val providerTrackId: String? = null,
    @SerialName("track_number") val trackNumber: Int = 0,
    @SerialName("disc_number") val discNumber: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("total_discs") val totalDiscs: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    val genre: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    val explicit: Boolean = false,
    @SerialName("deezer_id") val deezerId: String? = null,
    @SerialName("tidal_id") val tidalId: String? = null,
    @SerialName("qobuz_id") val qobuzId: String? = null,
    val upc: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val composer: String? = null,
    /** "track" (or empty), "album", "artist" or "playlist" in custom search results. */
    @SerialName("item_type") val itemType: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
) {
    /** Album/artist/playlist result: opens a detail page instead of downloading. */
    val collectionType: String? get() = collectionTypeOf(itemType)
}

/** Normalizes extension type/filter ids ("albums", "Artist", "songs") to album/artist/playlist, or null for tracks. */
fun collectionTypeOf(raw: String?): String? = when (raw?.lowercase()?.removeSuffix("s")) {
    "album" -> "album"
    "artist" -> "artist"
    "playlist" -> "playlist"
    else -> null
}

@Serializable
data class SearchFilterOption(val id: String, val label: String = "", val icon: String = "")

/** Manifest searchBehavior: the extension supports customSearch with these filters. */
@Serializable
data class SearchBehavior(
    val enabled: Boolean = false,
    val placeholder: String = "",
    val primary: Boolean = false,
    val filters: List<SearchFilterOption> = emptyList(),
)

/** Album/playlist/artist header from GetProviderMetadataJSON (album_info, artist_info, albums[]...). */
@Serializable
data class CollectionInfo(
    val id: String = "",
    val name: String = "",
    val artists: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    @SerialName("album_type") val albumType: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
)

/** GetProviderMetadataJSON(provider, "album"|"playlist"|"artist", id). */
@Serializable
data class ProviderMetadata(
    @SerialName("album_info") val albumInfo: CollectionInfo? = null,
    @SerialName("playlist_info") val playlistInfo: CollectionInfo? = null,
    @SerialName("artist_info") val artistInfo: CollectionInfo? = null,
    @SerialName("track_list") val trackList: List<ExtTrack> = emptyList(),
    val albums: List<CollectionInfo> = emptyList(),
    val releases: List<CollectionInfo> = emptyList(),
    @SerialName("top_tracks") val topTracks: List<ExtTrack> = emptyList(),
)

@Serializable
data class QualityOption(
    val id: String,
    val kind: String = "",
    val label: String = "",
    val description: String = "",
)

/** One entry of GetInstalledExtensions (Go extension_manager.go ExtensionInfo, a flat object). */
@Serializable
data class InstalledExtension(
    val id: String,
    val name: String = "",
    @SerialName("display_name") val displayNameRaw: String = "",
    val version: String = "",
    val description: String? = null,
    val types: List<String> = emptyList(),
    val enabled: Boolean = false,
    val status: String = "",
    @SerialName("error_message") val error: String? = null,
    @SerialName("quality_options") val qualityOptions: List<QualityOption> = emptyList(),
    @SerialName("has_metadata_provider") val hasMetadataProvider: Boolean = false,
    @SerialName("has_download_provider") val hasDownloadProvider: Boolean = false,
    val capabilities: JsonObject? = null,
    @SerialName("search_behavior") val searchBehavior: SearchBehavior? = null,
) {
    val hasCustomSearch: Boolean get() = searchBehavior?.enabled == true
    val displayName: String get() = displayNameRaw.ifEmpty { name.ifEmpty { id } }
    val isDownloadProvider: Boolean get() = hasDownloadProvider || "download_provider" in types
    val hasHomeFeed: Boolean
        get() = (capabilities?.get("homeFeed") as? JsonPrimitive)?.booleanOrNull == true
}

/** Extension getHomeFeed result (same shape SpotiFLAC's explore_provider parses). */
@Serializable
data class HomeFeed(
    val success: Boolean = false,
    val error: String? = null,
    val sections: List<FeedSection> = emptyList(),
)

@Serializable
data class FeedSection(
    val uri: String = "",
    val title: String = "",
    val items: List<FeedItem> = emptyList(),
)

@Serializable
data class FeedItem(
    val id: String = "",
    val uri: String = "",
    val type: String = "track",
    val name: String = "",
    val artists: String = "",
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("duration_ms") val durationMs: Double = 0.0,
) {
    fun toTrack(defaultProvider: String) = ExtTrack(
        id = id,
        name = name,
        artists = artists,
        albumName = albumName.orEmpty(),
        durationMs = durationMs.toInt(),
        coverUrl = coverUrl,
        providerId = providerId?.takeIf { it.isNotBlank() } ?: defaultProvider,
    )
}

/**
 * Entry from the extension registry. The Go repoExtension struct emits BOTH
 * snake_case and camelCase keys (alt fields), so ignoreUnknownKeys +
 * snake_case SerialNames is the correct pairing. Defaults everywhere so a
 * partial registry entry never breaks parsing.
 */
@Serializable
data class RepoExtension(
    val id: String = "",
    val name: String = "",
    @SerialName("display_name") val displayName: String = "",
    val version: String = "",
    val description: String? = null,
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    val category: String = "",
    val sha256: String = "",
    @SerialName("min_app_version") val minAppVersion: String = "",
)

// isLenient: extension JSON sometimes sends numbers where we expect strings.
val DownloadJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true; coerceInputValues = true }
