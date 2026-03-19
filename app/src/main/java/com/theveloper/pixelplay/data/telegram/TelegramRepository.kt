package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.database.TelegramSongEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

import timber.log.Timber

@Singleton
class TelegramRepository @Inject constructor(
    private val clientManager: TelegramClientManager,
    private val dao: TelegramDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {
    private companion object {
        private const val AUTH_REQUEST_TIMEOUT_MS = 20_000L
        private const val TELEGRAM_PLAYLIST_PREFIX = "telegram_channel:"
    }

    val authorizationState: Flow<TdApi.AuthorizationState?> = clientManager.authorizationState
    val authErrors: SharedFlow<TdApi.Error> = clientManager.errors
            
    /**
     * Clear memory caches in the repository.
     * For full cache clearing including files, use TelegramCacheManager.
     */
    fun clearMemoryCache() {
        resolvedPathCache.clear()
        Timber.d("TelegramRepository: Memory cache cleared")
    }

    /**
     * Quick check if TDLib is ready to process requests.
     */
    fun isReady(): Boolean = clientManager.isReady()

    /**
     * Suspends until the TDLib client is ready.
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean = 
        clientManager.awaitReady(timeoutMs)

    fun sendPhoneNumber(phoneNumber: String) {
        clientManager.sendPhoneNumber(phoneNumber)
    }

    suspend fun sendPhoneNumberAwait(
        phoneNumber: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        clientManager.sendRequest<TdApi.Ok>(
            TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings)
        )
    }

    fun checkAuthenticationCode(code: String) {
        clientManager.checkAuthenticationCode(code)
    }

    suspend fun checkAuthenticationCodeAwait(
        code: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        clientManager.sendRequest<TdApi.Ok>(TdApi.CheckAuthenticationCode(code))
    }
    
    fun checkAuthenticationPassword(password: String) {
        clientManager.checkAuthenticationPassword(password)
    }

    suspend fun checkAuthenticationPasswordAwait(
        password: String,
        timeoutMs: Long = AUTH_REQUEST_TIMEOUT_MS
    ): Result<Unit> = runAuthRequest(timeoutMs) {
        clientManager.sendRequest<TdApi.Ok>(TdApi.CheckAuthenticationPassword(password))
    }

    fun logout() {
        clientManager.logout()
    }

    private suspend fun runAuthRequest(
        timeoutMs: Long,
        block: suspend () -> TdApi.Object
    ): Result<Unit> {
        return try {
            withTimeout(timeoutMs) {
                block()
            }
            Result.success(Unit)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(
                IllegalStateException(
                    "Telegram did not respond in ${timeoutMs / 1000}s.",
                    timeout
                )
            )
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun searchPublicChat(username: String): TdApi.Chat? {
        return try {
            clientManager.sendRequest(TdApi.SearchPublicChat(username))
        } catch (e: Exception) {
            Timber.e(e, "Error searching public chat: $username")
            null
        }
    }

    suspend fun getAudioMessages(chatId: Long): List<Song> {
        Timber.d("Fetching chat history for chat: $chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100 // TdApi limit

        try {
            while (true) {
                // Use SearchChatMessages to find audio files specifically
                val request = TdApi.SearchChatMessages()
                request.chatId = chatId
                request.query = ""
                request.senderId = null // Use null for any sender
                request.fromMessageId = nextFromMessageId
                request.offset = 0
                request.limit = batchSize
                request.filter = TdApi.SearchMessagesFilterAudio()
                
                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)
                // Timber.d("SearchChatMessages batch (fromId $nextFromMessageId): found ${response.messages.size} / total ${response.totalCount}")
                
                if (response.messages.isEmpty()) {
                    break
                }
                
                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }
                
                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) {
                    break // No more results
                }
            }

            Timber.d("Total mapped audio songs: ${allSongs.size}")
            return allSongs
        } catch (e: Exception) {
            Timber.e(e, "Error fetching chat history for chat $chatId")
            return allSongs // Return partial results if we crash mid-way
        }
    }
    
    private suspend fun mapMessageToSong(message: TdApi.Message): Song? {
        val content = message.content
        
        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                // Timber.d("Mapping MessageAudio: ${audio.fileName} (${audio.title} - ${audio.performer})")
                
                var albumArtPath: String? = null
                // Priority 1: Main album cover thumbnail (embedded)
                var thumbnail = audio.albumCoverThumbnail
                
                // Priority 2: External album covers (e.g., from Spotify/Apple Music metadata)
                // These have size=0 initially but TDLib CAN download them - just needs time to resolve
                if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                    thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
                }

                if (thumbnail != null) {
                     // Use custom URI scheme for Coil Fetcher with ChatID/MessageID for robust lookup
                     albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                     // OPTIMIZATION: Populate cache if already downloaded
                     if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                         resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                     }
                }
                
                var title = audio.title.takeIf { it.isNotEmpty() }
                var artist = audio.performer.takeIf { it.isNotEmpty() }

                Song(
                    id = "${message.chatId}_${message.id}", // Unique ID
                    title = title ?: audio.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                    artist = artist ?: "Unknown Artist",
                    artistId = -1,
                album = "Telegram Stream",
                albumId = -1,
                path = "", // Will be filled when downloaded
                contentUriString = "telegram://${message.chatId}/${message.id}", // Persistent URI scheme
                albumArtUriString = albumArtPath,
                duration = audio.duration * 1000L,
                telegramFileId = audio.audio.id,
                telegramChatId = message.chatId,
                    mimeType = audio.mimeType,
                    bitrate = 0,
                    sampleRate = 0,
                    year = 0,
                    trackNumber = 0,
                    dateAdded = message.date.toLong(),
                    isFavorite = false
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                // Timber.d("Checking MessageDocument: ${document.fileName}, Mime: ${document.mimeType}")
                
                val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
                val isAudioExtension = document.fileName.lowercase().run {
                    endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") || endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
                }
                
                if (isAudioMime || isAudioExtension) {
                    var title = document.fileName.substringBeforeLast('.').ifEmpty { "Unknown Track" }
                    var artist = "Telegram Audio"
                    
                    var albumArtPath: String? = null
                    val thumbnail = document.thumbnail
                    if (thumbnail != null) {
                         albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                         // OPTIMIZATION: Populate cache if already downloaded
                         if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                             resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                         }
                    }

                    Song(
                    id = "${message.chatId}_${message.id}",
                    title = title,
                    artist = artist,
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
                    albumArtUriString = albumArtPath,
                    duration = 0L,
                    telegramFileId = document.document.id,
                    telegramChatId = message.chatId,
                        mimeType = document.mimeType,
                        bitrate = 0,
                        sampleRate = 0,
                        year = 0,
                        trackNumber = 0,
                        dateAdded = message.date.toLong(),
                        isFavorite = false
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating DownloadFile for fileId: $fileId")
            null
        }
    }

    suspend fun getFile(fileId: Int): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.GetFile(fileId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error fetching message: $chatId / $messageId")
            null
        }
    }

    suspend fun isFileCached(fileId: Int): Boolean {
        // 1. Check memory cache
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return true
            resolvedPathCache.remove(fileId)
        }

        // 2. Check TDLib
        val file = getFile(fileId)
        return file?.local?.isDownloadingCompleted == true && 
               file.local.path.isNotEmpty() && 
               java.io.File(file.local.path).exists()
    }

    suspend fun resolveTelegramUri(uriString: String): Pair<Int, Long>? {
        // 1. Check Memory Cache
        uriResolutionCache[uriString]?.let {
             return it
        }

        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme != "telegram") return null
        
        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()
        
        if (chatId == null || messageId == null) return null
        
        // Fetch fresh message to get valid fileId for this session
        // we use getMessage which internally calls TdApi.GetMessage
        val message = getMessage(chatId, messageId) ?: return null
        
        val result = when (val content = message.content) {
            is TdApi.MessageAudio -> Pair(content.audio.audio.id, content.audio.audio.size)
            is TdApi.MessageDocument -> Pair(content.document.document.id, content.document.document.size)
            else -> null
        }
        
        // 2. Populate Cache
        if (result != null) {
            uriResolutionCache[uriString] = result
        }
        
        return result
    }

    /**
     * Non-blocking (or fire-and-forget) method to populate cache for a URI.
     * Useful for pre-fetching next/prev songs.
     */
    fun preResolveTelegramUri(uriString: String) {
        if (uriResolutionCache.containsKey(uriString)) return
        
        repositoryScope.launch {
            try {
                // This calls the suspending resolve which populates the cache
                resolveTelegramUri(uriString)
                // Timber.d("Pre-resolved $uriString")
            } catch (e: Exception) {
                // Ignore pre-fetch errors
            }
        }
    }

    /**
     * Forces a refresh of the message from the server using GetChatHistory.
     * This handles stale file references/access hashes.
     */
    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            // Using GetChatHistory with limit=1 mostly fetches from server if not cached fresh.
            // There is no explicit "Force Network" flag in TDLib for messages, but this is the standard workaround.
            val history = clientManager.sendRequest<TdApi.Messages>(
                TdApi.GetChatHistory(chatId, messageId, 0, 1, false)
            )
            history.messages.firstOrNull { it.id == messageId }
                ?: clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing message: $messageId")
            null
        }
    }

    // Cache for resolved paths to avoid repeated IPC calls
    private val resolvedPathCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    
    // Cache for resolved FileId+Size to avoid getMessage calls
    private val uriResolutionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()
    
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Deferred<String?>>()
    
    // Limit concurrent downloads to prevent TDLib overwhelm and reduce GC pressure
    // Reduced from 12 to 4 for thumbnails - higher values cause timeouts and frame drops
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(4)

    private val _downloadCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val downloadCompleted: SharedFlow<Int> = _downloadCompleted.asSharedFlow()

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? {
        // 1. Check Memory Cache first
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return path
            resolvedPathCache.remove(fileId)
        }

        // Dedup: If already downloading, join that job
        val existingJob = activeDownloads[fileId]
        if (existingJob != null && existingJob.isActive) {
            return existingJob.await()
        }

        val newJob = repositoryScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                // Use withPermit to limit concurrent heavyweight downloads
                downloadSemaphore.withPermit {
                    // Double check status after acquiring permit
                    val currentFile = getFile(fileId)
                    if (currentFile?.local?.isDownloadingCompleted == true) {
                        currentFile.local.path.takeIf { it.isNotEmpty() }?.let {
                             resolvedPathCache[fileId] = it
                             _downloadCompleted.tryEmit(fileId) // Notify
                             return@withPermit it
                        }
                    }

                    val initialFile = getFile(fileId)
                    // Use synchronous download for thumbnails (size=0 or small < 1MB)
                    // This forces TDLib to resolve the file immediately, fixing size=0 issues
                    val isSmallFile = initialFile?.size == 0L || (initialFile?.size ?: 0) < 1024 * 1024
                    
                    if (isSmallFile) {
                        Timber.v("Sync download starting for fileId: $fileId")
                        return@withPermit try {
                            // 15 seconds timeout for sync download
                            val resultFile = withTimeout(15_000L) {
                                clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, true))
                            }
                            
                            if (resultFile.local.isDownloadingCompleted && resultFile.local.path.isNotEmpty()) {
                                resolvedPathCache[fileId] = resultFile.local.path
                                _downloadCompleted.tryEmit(fileId) // Notify
                                resultFile.local.path
                            } else {
                                null
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Normal during fast scrolling - don't log
                            throw e
                        } catch (e: Exception) {
                            // Only log unexpected errors (not cancellations or file-not-found)
                            if (e.message?.contains("canceled") != true && e.message?.contains("has failed") != true) {
                                Timber.w("Sync download failed for $fileId: ${e.message}")
                            }
                            null
                        }
                    }

                    // Fallback to Async for larger files (if needed in future)
                    Timber.v("Async download starting for fileId: $fileId")
                    try {
                        clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
                    } catch(e: Exception) {
                        Timber.w("Async download request failed for $fileId: ${e.message}")
                        return@withPermit null
                    }
                    
                    // Wait for updateFile events
                    val completedPath = withTimeoutOrNull(60_000L) {
                        clientManager.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .filter { it.file.id == fileId }
                            .first { update ->
                                val file = update.file
                                when {
                                    file.local.isDownloadingCompleted && file.local.path.isNotEmpty() -> true
                                    !file.local.canBeDownloaded -> throw Exception("File cannot be downloaded")
                                    else -> false
                                }
                            }
                            .file.local.path
                    }
                    
                    if (completedPath != null) {
                        resolvedPathCache[fileId] = completedPath
                        _downloadCompleted.tryEmit(fileId) // Notify
                        return@withPermit completedPath
                    }
                    
                    // Final check
                    val finalFile = getFile(fileId)
                    return@withPermit if (finalFile?.local?.isDownloadingCompleted == true && finalFile.local.path.isNotEmpty()) {
                        _downloadCompleted.tryEmit(fileId) // Notify
                        finalFile.local.path
                    } else {
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal during fast scrolling - propagate without logging
                throw e
            } catch (e: Exception) {
                // Only log truly unexpected errors
                Timber.w("downloadFileAwait error for $fileId: ${e.message}")
                throw e
            } finally {
                activeDownloads.remove(fileId)
            }
        }

        activeDownloads[fileId] = newJob
        
        // Correctly handle cancellation propagation
        try {
            newJob.start()
            return newJob.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            newJob.cancel(e) // Cancel the background work if the caller cancels
            throw e
        }
    }

    // ─── App Playlist Management ────────────────────────────────────────

    private suspend fun getAppPlaylistIdForTelegram(chatId: Long): String {
        return "$TELEGRAM_PLAYLIST_PREFIX$chatId"
    }

    private fun toUnifiedTelegramSongId(telegramSongId: String): Long {
        val songId = -(telegramSongId.hashCode().toLong().absoluteValue)
        return if (songId == 0L) -1L else songId
    }

    suspend fun updateAppPlaylistForTelegramChannel(
        chatId: Long,
        channelTitle: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            // Convert Telegram song entities to unified song IDs (SyncWorker-compatible)
            val unifiedSongIds = telegramEntities.map { entity ->
                toUnifiedTelegramSongId(entity.id).toString()
            }

            val appPlaylistId = getAppPlaylistIdForTelegram(chatId)
            
            // Get all current app playlists
            val allPlaylists = playlistPreferencesRepository.userPlaylistsFlow
            val existingPlaylist = withContext(Dispatchers.IO) {
                allPlaylists.map { playlists ->
                    playlists.find { it.id == appPlaylistId }
                }.first()
            }

            if (existingPlaylist != null) {
                // Update the existing playlist
                playlistPreferencesRepository.updatePlaylist(
                    existingPlaylist.copy(
                        name = channelTitle,
                        songIds = unifiedSongIds,
                        lastModified = System.currentTimeMillis(),
                        source = "TELEGRAM" // Mark as Telegram source
                    )
                )
                Timber.d("Updated app playlist for Telegram channel $chatId: $channelTitle")
            } else {
                // Create a new playlist
                playlistPreferencesRepository.createPlaylist(
                    name = channelTitle,
                    songIds = unifiedSongIds,
                    customId = appPlaylistId,
                    source = "TELEGRAM"
                )
                Timber.d("Created new app playlist for Telegram channel $chatId: $channelTitle")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update/create app playlist for Telegram channel $chatId")
        }
    }

    suspend fun deleteAppPlaylistForTelegramChannel(chatId: Long) {
        try {
            val appPlaylistId = getAppPlaylistIdForTelegram(chatId)
            playlistPreferencesRepository.deletePlaylist(appPlaylistId)
            Timber.d("Deleted app playlist for Telegram channel $chatId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Telegram channel $chatId")
        }
    }
}
