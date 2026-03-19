package com.theveloper.pixelplay.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.theveloper.pixelplay.data.local.LocalSongDao
import com.theveloper.pixelplay.data.local.LocalSongEntity
import com.theveloper.pixelplay.shared.WearLibraryItem
import com.theveloper.pixelplay.shared.WearThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local player state mirroring WearPlayerState structure for unified UI.
 */
data class WearLocalPlayerState(
    val songId: String = "",
    val songTitle: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val canToggleFavorite: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val isEmpty: Boolean get() = songId.isEmpty()
}

data class WearQueueSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
)

/**
 * Repository managing ExoPlayer for standalone local playback on the watch.
 * Plays audio files that have been transferred from the phone and stored locally.
 *
 * Uses a lightweight local ExoPlayer plus MediaSession so Bluetooth headset media buttons
 * and other system transport controls route to watch playback correctly.
 */
@Singleton
class WearLocalPlayerRepository @Inject constructor(
    private val application: Application,
    private val localSongDao: LocalSongDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val _localPlayerState = MutableStateFlow(WearLocalPlayerState())
    val localPlayerState: StateFlow<WearLocalPlayerState> = _localPlayerState.asStateFlow()

    private val _isLocalPlaybackActive = MutableStateFlow(false)
    val isLocalPlaybackActive: StateFlow<Boolean> = _isLocalPlaybackActive.asStateFlow()

    private val _localPaletteSeedArgb = MutableStateFlow<Int?>(null)
    val localPaletteSeedArgb: StateFlow<Int?> = _localPaletteSeedArgb.asStateFlow()

    private val _localThemePalette = MutableStateFlow<WearThemePalette?>(null)
    val localThemePalette: StateFlow<WearThemePalette?> = _localThemePalette.asStateFlow()

    private val _localAlbumArt = MutableStateFlow<Bitmap?>(null)
    val localAlbumArt: StateFlow<Bitmap?> = _localAlbumArt.asStateFlow()

    private val _localQueueState = MutableStateFlow(WearLocalQueueState())
    val localQueueState: StateFlow<WearLocalQueueState> = _localQueueState.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentQueueSongIds: List<String> = emptyList()
    private var currentQueueSongsById: Map<String, LocalSongEntity> = emptyMap()
    private var currentQueueItemsById: Map<String, WearQueueSong> = emptyMap()
    private var lastPaletteSongId: String = ""
    private var lastArtworkSongId: String = ""

    companion object {
        private const val TAG = "WearLocalPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
        private const val MEDIA_SESSION_ID = "wear-local-playback"
    }

    init {
        scope.launch {
            localSongDao.getAllSongs().collect { songs ->
                if (songs.isNotEmpty()) {
                    val latestSongsById = songs.associateBy { it.songId }
                    if (currentQueueSongsById.isNotEmpty()) {
                        currentQueueSongsById = currentQueueSongsById.mapValues { (songId, song) ->
                            latestSongsById[songId] ?: song
                        }
                    }
                }

                if (!localPlayerState.value.isEmpty) {
                    updateState()
                }
            }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(application).build().also { player ->
            exoPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            player.setHandleAudioBecomingNoisy(true)
            ensureMediaSession(player)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    if (playbackState == Player.STATE_ENDED) {
                        stopPositionUpdates()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }
            })
            Timber.tag(TAG).d("ExoPlayer created")
        }
    }

    private fun ensureMediaSession(player: ExoPlayer) {
        if (mediaSession != null) return

        mediaSession = MediaSession.Builder(application, player)
            .setId(MEDIA_SESSION_ID)
            .build()
    }

    /**
     * Start local playback with the given songs, beginning at [startIndex].
     */
    fun playLocalSongs(songs: List<LocalSongEntity>, startIndex: Int = 0) {
        scope.launch {
            val playableSongs = songs.filter { song ->
                val file = File(song.localPath)
                file.isFile && file.length() > 0L
            }
            if (playableSongs.isEmpty()) {
                Timber.tag(TAG).w("No playable local files available")
                return@launch
            }

            val queueSongs = playableSongs.map { song ->
                WearQueueSong(
                    songId = song.songId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    uri = Uri.fromFile(File(song.localPath)),
                )
            }
            startPlayback(
                queueSongs = queueSongs,
                queueSongIdToLocal = playableSongs.associateBy { it.songId },
                startIndex = startIndex,
            )
        }
    }

    /**
     * Start local playback from watch MediaStore songs.
     */
    fun playUriSongs(songs: List<WearQueueSong>, startIndex: Int = 0) {
        scope.launch {
            if (songs.isEmpty()) {
                Timber.tag(TAG).w("No watch library songs available")
                return@launch
            }
            startPlayback(
                queueSongs = songs,
                queueSongIdToLocal = emptyMap(),
                startIndex = startIndex,
            )
        }
    }

    private suspend fun startPlayback(
        queueSongs: List<WearQueueSong>,
        queueSongIdToLocal: Map<String, LocalSongEntity>,
        startIndex: Int,
    ) {
        withContext(Dispatchers.Main) {
            val player = getOrCreatePlayer()
            currentQueueSongIds = queueSongs.map { it.songId }
            val latestSongsById = queueSongIdToLocal.keys.mapNotNull { songId ->
                localSongDao.getSongById(songId)
            }.associateBy { it.songId }
            currentQueueSongsById = queueSongIdToLocal.mapValues { (songId, song) ->
                latestSongsById[songId] ?: song
            }
            currentQueueItemsById = queueSongs.associateBy { it.songId }
            lastPaletteSongId = ""
            lastArtworkSongId = ""
            _localThemePalette.value = null
            _localPaletteSeedArgb.value = null
            _localAlbumArt.value = null

            val mediaItems = queueSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.songId)
                    .setUri(song.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            val startIndexSafe = startIndex.coerceIn(0, mediaItems.lastIndex)
            player.setMediaItems(mediaItems, startIndexSafe, 0L)
            player.prepare()
            player.play()
            _isLocalPlaybackActive.value = true
            updateQueueState(currentIndex = startIndexSafe)
            updateState()
            Timber.tag(TAG).d(
                "Playing locally: ${queueSongs.getOrNull(startIndexSafe)?.title}, queue=${queueSongs.size}"
            )
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun next() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun previous() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                updateState()
            }
        }
    }

    fun cycleRepeat() {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                updateState()
            }
        }
    }

    fun playQueueIndex(index: Int) {
        scope.launch {
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                if (index !in 0 until player.mediaItemCount) return@withContext

                player.seekToDefaultPosition(index)
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.play()
                updateState()
            }
        }
    }

    suspend fun removeSongFromActiveQueue(songId: String) {
        withContext(Dispatchers.Main) {
            val queueIndex = currentQueueSongIds.indexOf(songId)
            if (queueIndex == -1) return@withContext

            val player = exoPlayer
            if (player == null || currentQueueSongIds.size <= 1) {
                release()
                return@withContext
            }

            player.removeMediaItem(queueIndex)
            currentQueueSongIds = currentQueueSongIds.toMutableList().apply {
                removeAt(queueIndex)
            }
            currentQueueSongsById = currentQueueSongsById.toMutableMap().apply {
                remove(songId)
            }
            currentQueueItemsById = currentQueueItemsById.toMutableMap().apply {
                remove(songId)
            }
            if (lastPaletteSongId == songId) lastPaletteSongId = ""
            if (lastArtworkSongId == songId) lastArtworkSongId = ""
            updateQueueState()
            updateState()
        }
    }

    /**
     * Stop local playback and release the player.
     */
    fun release() {
        stopPositionUpdates()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        _isLocalPlaybackActive.value = false
        _localPlayerState.value = WearLocalPlayerState()
        _localThemePalette.value = null
        _localPaletteSeedArgb.value = null
        _localAlbumArt.value = null
        _localQueueState.value = WearLocalQueueState()
        currentQueueSongIds = emptyList()
        currentQueueSongsById = emptyMap()
        currentQueueItemsById = emptyMap()
        lastPaletteSongId = ""
        lastArtworkSongId = ""
        Timber.tag(TAG).d("ExoPlayer released")
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        val currentLocalSong = currentItem?.mediaId?.let(currentQueueSongsById::get)
        _localPlayerState.value = WearLocalPlayerState(
            songId = currentItem?.mediaId ?: "",
            songTitle = currentItem?.mediaMetadata?.title?.toString() ?: "",
            artistName = currentItem?.mediaMetadata?.artist?.toString() ?: "",
            albumName = currentItem?.mediaMetadata?.albumTitle?.toString() ?: "",
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            totalDurationMs = player.duration.coerceAtLeast(0L),
            isFavorite = currentLocalSong?.isFavorite == true,
            canToggleFavorite = currentLocalSong != null,
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        updateQueueState(currentIndex = player.currentMediaItemIndex)
        updatePaletteForSong(currentItem?.mediaId.orEmpty())
        updateArtworkForSong(currentItem?.mediaId.orEmpty())
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                updateState()
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateQueueState(currentIndex: Int? = null) {
        val player = exoPlayer
        val rawCurrentIndex = currentIndex ?: exoPlayer?.currentMediaItemIndex ?: -1
        val visibleQueueIndices = when {
            player == null -> {
                if (rawCurrentIndex in currentQueueSongIds.indices) {
                    (rawCurrentIndex until currentQueueSongIds.size).toList()
                } else {
                    currentQueueSongIds.indices.toList()
                }
            }

            rawCurrentIndex !in 0 until player.mediaItemCount -> {
                (0 until player.mediaItemCount).toList()
            }

            else -> buildVisibleQueueIndices(player, rawCurrentIndex)
        }

        val queueItems = visibleQueueIndices.mapNotNull { index ->
            val mediaItem = player?.getMediaItemAt(index)
            val songId = mediaItem?.mediaId ?: currentQueueSongIds.getOrNull(index) ?: return@mapNotNull null
            val queueItem = currentQueueItemsById[songId]
            val title = queueItem?.title
                ?: mediaItem?.mediaMetadata?.title?.toString()
                ?: return@mapNotNull null
            val artist = queueItem?.artist ?: mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
            val album = queueItem?.album ?: mediaItem?.mediaMetadata?.albumTitle?.toString().orEmpty()
            val subtitle = when {
                index == rawCurrentIndex -> {
                    val supportingText = artist.ifBlank { album }
                    if (supportingText.isBlank()) {
                        "Playing on watch"
                    } else {
                        "Playing · $supportingText"
                    }
                }

                artist.isNotBlank() -> artist
                else -> album
            }

            WearLibraryItem(
                id = index.toString(),
                title = title,
                subtitle = subtitle,
                type = WearLibraryItem.TYPE_SONG,
            )
        }

        val resolvedCurrentIndex = if (rawCurrentIndex in currentQueueSongIds.indices && queueItems.isNotEmpty()) {
            0
        } else {
            -1
        }

        _localQueueState.value = WearLocalQueueState(
            items = queueItems,
            currentIndex = resolvedCurrentIndex,
        )
    }

    private fun buildVisibleQueueIndices(player: ExoPlayer, currentIndex: Int): List<Int> {
        if (currentIndex !in 0 until player.mediaItemCount) {
            return (0 until player.mediaItemCount).toList()
        }

        val visibleIndices = mutableListOf(currentIndex)
        val visited = hashSetOf(currentIndex)
        val timeline = player.currentTimeline
        var nextIndex = timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            player.shuffleModeEnabled,
        )

        while (nextIndex != C.INDEX_UNSET && visited.add(nextIndex)) {
            visibleIndices += nextIndex
            nextIndex = timeline.getNextWindowIndex(
                nextIndex,
                Player.REPEAT_MODE_OFF,
                player.shuffleModeEnabled,
            )
        }

        return visibleIndices
    }

    private fun updatePaletteForSong(songId: String) {
        if (songId.isBlank()) {
            lastPaletteSongId = ""
            _localThemePalette.value = null
            _localPaletteSeedArgb.value = null
            return
        }
        if (songId == lastPaletteSongId) return
        lastPaletteSongId = songId

        val queueSong = currentQueueSongsById[songId]
        val cachedThemePalette = queueSong?.themePaletteJson
            ?.takeIf { it.isNotBlank() }
            ?.let { encodedPalette ->
                runCatching { json.decodeFromString<WearThemePalette>(encodedPalette) }
                    .onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to decode persisted Wear palette")
                    }
                    .getOrNull()
            }
        val cachedSeed = queueSong?.paletteSeedArgb
        _localThemePalette.value = cachedThemePalette
        if (cachedThemePalette != null || cachedSeed != null) {
            _localPaletteSeedArgb.value = cachedSeed
            return
        }

        _localThemePalette.value = null
        _localPaletteSeedArgb.value = null
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val extractedSeed = extractSeedFromLocalSong(queueSong)
                if (extractedSeed != null) {
                    runCatching { localSongDao.updatePaletteSeed(queueSong.songId, extractedSeed) }
                        .onFailure { error ->
                            Timber.tag(TAG).w(error, "Failed to persist local palette seed")
                        }
                }

                withContext(Dispatchers.Main) {
                    if (lastPaletteSongId != queueSong.songId) return@withContext
                    if (extractedSeed != null) {
                        currentQueueSongsById = currentQueueSongsById.toMutableMap().apply {
                            put(queueSong.songId, queueSong.copy(paletteSeedArgb = extractedSeed))
                        }
                    }
                    _localPaletteSeedArgb.value = extractedSeed
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId] ?: return
        scope.launch(Dispatchers.IO) {
            val extractedSeed = extractSeedFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastPaletteSongId != queueItem.songId) return@withContext
                _localThemePalette.value = null
                _localPaletteSeedArgb.value = extractedSeed
            }
        }
    }

    private fun updateArtworkForSong(songId: String) {
        if (songId.isBlank()) {
            lastArtworkSongId = ""
            _localAlbumArt.value = null
            return
        }
        if (songId == lastArtworkSongId) return
        lastArtworkSongId = songId

        val queueSong = currentQueueSongsById[songId]
        if (queueSong != null) {
            scope.launch(Dispatchers.IO) {
                val bitmap = loadLocalAlbumArtBitmap(queueSong)
                withContext(Dispatchers.Main) {
                    if (lastArtworkSongId != queueSong.songId) return@withContext
                    _localAlbumArt.value = bitmap
                }
            }
            return
        }

        val queueItem = currentQueueItemsById[songId]
        if (queueItem == null) {
            _localAlbumArt.value = null
            return
        }
        scope.launch(Dispatchers.IO) {
            val bitmap = loadArtworkBitmapFromUri(queueItem.uri, queueItem.songId)
            withContext(Dispatchers.Main) {
                if (lastArtworkSongId != queueItem.songId) return@withContext
                _localAlbumArt.value = bitmap
            }
        }
    }

    private fun loadLocalAlbumArtBitmap(song: LocalSongEntity): Bitmap? {
        val fromStoredArtwork = song.artworkPath
            ?.takeIf { it.isNotBlank() }
            ?.let { artworkPath ->
                decodeBoundedBitmapFromFile(artworkPath, maxDimension = 1024)
            }
        if (fromStoredArtwork != null) return fromStoredArtwork

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load local artwork for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromFile(path: String, maxDimension: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun decodeBoundedBitmapFromBytes(data: ByteArray, maxDimension: Int): Bitmap? {
        if (data.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun extractSeedFromLocalSong(song: LocalSongEntity): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(song.localPath)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract local artwork seed for songId=${song.songId}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedFromUri(uri: Uri, songId: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            ) ?: return null

            try {
                extractSeedColorArgb(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract artwork seed from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadArtworkBitmapFromUri(uri: Uri, songId: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(application, uri)
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmapFromBytes(embedded, maxDimension = 1024)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load artwork from URI for songId=$songId")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val step = (minOf(bitmap.width, bitmap.height) / 24).coerceAtLeast(1)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 28) {
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    if (red + green + blue > 36) {
                        redSum += red
                        greenSum += green
                        blueSum += blue
                        count++
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return null
        return Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    }
}
