package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.database.TelegramSongEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val TELEGRAM_TOPIC_PLAYLIST_PREFIX = "telegram_topic:"
    }

    val authorizationState: Flow<TdApi.AuthorizationState?> = clientManager.authorizationState
    val authErrors: SharedFlow<TdApi.Error> = clientManager.errors

    fun clearMemoryCache() {
        resolvedPathCache.clear()
        Timber.d("TelegramRepository: Memory cache cleared")
    }

    fun isReady(): Boolean = clientManager.isReady()

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
            withTimeout(timeoutMs) { block() }
            Result.success(Unit)
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Telegram did not respond in ${timeoutMs / 1000}s.", timeout))
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

    // ─── Forum Topic Support ──────────────────────────────────────────────────

    /**
     * Returns true if [chatId] is a supergroup with Forum mode enabled.
     * Regular broadcast channels always return false.
     */
    suspend fun isForum(chatId: Long): Boolean {
        return try {
            val chat = clientManager.sendRequest<TdApi.Chat>(TdApi.GetChat(chatId))
            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup) return false
            val supergroup = clientManager.sendRequest<TdApi.Supergroup>(
                TdApi.GetSupergroup(type.supergroupId)
            )
            supergroup.isForum
        } catch (e: Exception) {
            Timber.w(e, "isForum check failed for chatId=$chatId")
            false
        }
    }

    /**
     * Fetches all forum topics for a supergroup.
     * Returns empty list for non-forum chats.
     */

    suspend fun getForumTopics(chatId: Long): List<TelegramTopicEntity> {
        val topics = mutableListOf<TelegramTopicEntity>()
        try {
            var offsetDate = 0
            var offsetMessageId = 0L          // int53 → Long ✓
            var offsetForumTopicId = 0        // int32 → Int ✓

            while (true) {
                val request = TdApi.GetForumTopics().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.offsetDate = offsetDate              // Int ✓
                    this.offsetMessageId = offsetMessageId   // Long ✓
                    this.offsetForumTopicId = offsetForumTopicId  // Int ✓
                    this.limit = 100
                }
                val result = clientManager.sendRequest<TdApi.ForumTopics>(request)

                if (result.topics.isEmpty()) break

                for (topic in result.topics) {
                    val info = topic.info
                    val emojiId = info.icon.customEmojiId

                    // Resolve the thread ID from ForumTopicInfo via reflection.
                    // We only accept Long/Int fields with a non-zero value whose name
                    // looks like a thread/message identifier. We skip fields named
                    // exactly "id" because in many TDLib Java builds that field is a
                    // String composite key, not the numeric thread ID.
                    val threadId: Long = run {
                        // Log all fields once so we can confirm the correct name in Logcat
                        val allFields = info.javaClass.declaredFields
                        Timber.d("ForumTopicInfo fields: ${allFields.map { "${it.name}:${it.type.simpleName}" }}")

                        var resolved = 0L
                        // Prefer the most specific name first, skip bare "id" (likely String)
                        val preferredNames = listOf(
                            "messageThreadId", "message_thread_id",
                            "threadId", "thread_id",
                            "topicId", "topic_id",
                            "forumTopicId", "forum_topic_id"
                        )
                        for (name in preferredNames) {
                            try {
                                val f = info.javaClass.getDeclaredField(name)
                                f.isAccessible = true
                                val v = f.get(info)
                                val candidate = when (v) {
                                    is Long -> v
                                    is Int  -> v.toLong()
                                    else    -> 0L
                                }
                                if (candidate != 0L) {
                                    Timber.d("ForumTopicInfo: resolved threadId via field '$name' = $candidate")
                                    resolved = candidate
                                    break
                                }
                            } catch (_: NoSuchFieldException) { }
                        }

                        // Last resort: scan ALL Long/Int fields for the first non-zero value
                        // that isn't a known non-thread field
                        if (resolved == 0L) {
                            val skipNames = setOf("chatId", "chat_id", "creatorUserId",
                                "creator_user_id", "customEmojiId", "custom_emoji_id",
                                "editDate", "edit_date", "date")
                            for (f in allFields) {
                                if (f.name in skipNames) continue
                                if (f.type != Long::class.java && f.type != Int::class.java) continue
                                try {
                                    f.isAccessible = true
                                    val candidate = when (val v = f.get(info)) {
                                        is Long -> v
                                        is Int  -> v.toLong()
                                        else    -> 0L
                                    }
                                    if (candidate != 0L) {
                                        Timber.w("ForumTopicInfo: fallback threadId via field '${f.name}' = $candidate")
                                        resolved = candidate
                                        break
                                    }
                                } catch (_: Exception) { }
                            }
                        }

                        if (resolved == 0L) {
                            Timber.e("ForumTopicInfo: could not resolve threadId for topic '${info.name}'")
                        }
                        resolved
                    }

                    // Only add topics where we successfully resolved a thread ID
                    if (threadId != 0L) {
                        topics.add(
                            TelegramTopicEntity(
                                id = "${chatId}_${threadId}",
                                chatId = chatId,
                                threadId = threadId,
                                name = info.name,
                                iconEmoji = if (emojiId != 0L) emojiId.toString() else null
                            )
                        )
                    }
                }

                if (result.nextOffsetDate == 0) break

                offsetDate         = result.nextOffsetDate           // Int ✓
                offsetMessageId    = result.nextOffsetMessageId      // Long ✓
                offsetForumTopicId = result.nextOffsetForumTopicId   // Int ✓
            }

            Timber.d("Fetched ${topics.size} forum topics for chat $chatId")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching forum topics for chat $chatId")
        }
        return topics
    }

    suspend fun getAudioMessagesByTopic(chatId: Long, threadId: Long): List<Song> {
        Timber.d("Fetching audio for topic threadId=$threadId in chat=$chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()

                    // Set the topic/thread filter via reflection to handle different TDLib builds.
                    // In newer builds the field is 'topicId' (MessageTopic object).
                    // In older builds it was 'messageThreadId' (Long).
                    val scFields = this.javaClass.declaredFields
                    Timber.d("SearchChatMessages fields: ${scFields.map { "${it.name}:${it.type.simpleName}" }}")

                    var topicSet = false

                    // Try 'topicId' field (newer TDLib — expects a MessageTopic object)
                    try {
                        val f = this.javaClass.getDeclaredField("topicId")
                        f.isAccessible = true
                        // MessageTopicForum wraps the thread ID as Int
                        f.set(this, TdApi.MessageTopicForum(threadId.toInt()))
                        Timber.d("SearchChatMessages: set topicId = MessageTopicForum($threadId)")
                        topicSet = true
                    } catch (_: NoSuchFieldException) { }

                    // Fallback: try 'messageThreadId' field (older TDLib — Long)
                    if (!topicSet) {
                        try {
                            val f = this.javaClass.getDeclaredField("messageThreadId")
                            f.isAccessible = true
                            f.set(this, threadId)
                            Timber.d("SearchChatMessages: set messageThreadId = $threadId")
                            topicSet = true
                        } catch (_: NoSuchFieldException) { }
                    }

                    if (!topicSet) {
                        Timber.e("SearchChatMessages: could not set topic filter — results will be unfiltered")
                    }
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            Timber.d("Topic $threadId: fetched ${allSongs.size} songs")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching audio for topic $threadId in chat $chatId")
        }
        return allSongs
    }


    // ─── Full-channel fetch

    suspend fun getAudioMessages(chatId: Long): List<Song> {
        Timber.d("Fetching chat history for chat: $chatId")
        try {
            clientManager.sendRequest<TdApi.Ok>(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100

        try {
            while (true) {
                val request = TdApi.SearchChatMessages().apply {
                    this.chatId = chatId
                    this.query = ""
                    this.senderId = null
                    this.fromMessageId = nextFromMessageId
                    this.offset = 0
                    this.limit = batchSize
                    this.filter = TdApi.SearchMessagesFilterAudio()
                }

                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)

                if (response.messages.isEmpty()) break

                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }

                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) break
            }
            Timber.d("Total mapped audio songs: ${allSongs.size}")
            return allSongs
        } catch (e: Exception) {
            Timber.e(e, "Error fetching chat history for chat $chatId")
            return allSongs
        }
    }

    private suspend fun mapMessageToSong(message: TdApi.Message): Song? {
        val content = message.content

        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio

                var albumArtPath: String? = null
                var thumbnail = audio.albumCoverThumbnail

                if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                    thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
                }

                if (thumbnail != null) {
                    albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                    if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                        resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                    }
                }

                Song(
                    id = "${message.chatId}_${message.id}",
                    title = audio.title.takeIf { it.isNotEmpty() }
                        ?: audio.fileName.substringBeforeLast('.').ifEmpty { "Unknown Title" },
                    artist = audio.performer.takeIf { it.isNotEmpty() } ?: "Unknown Artist",
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
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

                val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
                val isAudioExtension = document.fileName.lowercase().run {
                    endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") ||
                            endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
                }

                if (isAudioMime || isAudioExtension) {
                    var albumArtPath: String? = null
                    val thumbnail = document.thumbnail
                    if (thumbnail != null) {
                        albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                        if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                            resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                        }
                    }

                    Song(
                        id = "${message.chatId}_${message.id}",
                        title = document.fileName.substringBeforeLast('.').ifEmpty { "Unknown Track" },
                        artist = "Telegram Audio",
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
                } else null
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
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return true
            resolvedPathCache.remove(fileId)
        }
        val file = getFile(fileId)
        return file?.local?.isDownloadingCompleted == true &&
                file.local.path.isNotEmpty() &&
                java.io.File(file.local.path).exists()
    }

    suspend fun resolveTelegramUri(uriString: String): Pair<Int, Long>? {
        uriResolutionCache[uriString]?.let { return it }

        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme != "telegram") return null

        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()
        if (chatId == null || messageId == null) return null

        val message = getMessage(chatId, messageId) ?: return null

        val result = when (val content = message.content) {
            is TdApi.MessageAudio -> Pair(content.audio.audio.id, content.audio.audio.size)
            is TdApi.MessageDocument -> Pair(content.document.document.id, content.document.document.size)
            else -> null
        }

        if (result != null) uriResolutionCache[uriString] = result
        return result
    }

    fun preResolveTelegramUri(uriString: String) {
        if (uriResolutionCache.containsKey(uriString)) return
        repositoryScope.launch {
            try { resolveTelegramUri(uriString) } catch (e: Exception) { /* ignore */ }
        }
    }

    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
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

    private val resolvedPathCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val uriResolutionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Deferred<String?>>()
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(4)

    private val _downloadCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val downloadCompleted: SharedFlow<Int> = _downloadCompleted.asSharedFlow()
    private val _songFileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val songFileUpdated: SharedFlow<String> = _songFileUpdated.asSharedFlow()

    fun warmUpArtworkForSongs(
        songs: List<TelegramSongEntity>,
        maxSongs: Int = 24
    ) {
        val targets = songs.asSequence()
            .map { it.chatId to it.messageId }
            .distinct()
            .take(maxSongs)
            .toList()

        if (targets.isEmpty()) return

        repositoryScope.launch {
            targets.forEach { (chatId, messageId) ->
                try {
                    warmUpArtwork(chatId, messageId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.v(e, "Artwork warm-up failed for $chatId/$messageId")
                }
            }
        }
    }

    private suspend fun warmUpArtwork(chatId: Long, messageId: Long) {
        val message = getMessage(chatId, messageId) ?: return
        val fileId = extractArtworkFileId(message.content) ?: return
        val existingFile = getFile(fileId)
        if (existingFile?.local?.isDownloadingCompleted == true && existingFile.local.path.isNotEmpty()) {
            resolvedPathCache[fileId] = existingFile.local.path
            return
        }
        downloadFileAwait(fileId, priority = 1)
    }

    private fun extractArtworkFileId(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> {
                val thumbnail = content.audio.albumCoverThumbnail
                    ?: content.audio.externalAlbumCovers?.maxByOrNull { it.width * it.height }
                thumbnail?.file?.id
            }
            is TdApi.MessageDocument -> content.document.thumbnail?.file?.id
            else -> null
        }
    }

    private suspend fun persistSongFilePathIfNeeded(fileId: Int, path: String?) {
        if (path.isNullOrBlank()) return

        val existingSong = dao.getSongByFileId(fileId) ?: return
        if (existingSong.filePath == path) return

        dao.insertSongs(listOf(existingSong.copy(filePath = path)))
        _songFileUpdated.tryEmit(existingSong.id)
    }

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? {
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return path
            resolvedPathCache.remove(fileId)
        }

        val existingJob = activeDownloads[fileId]
        if (existingJob != null && existingJob.isActive) return existingJob.await()

        val newJob = repositoryScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                downloadSemaphore.withPermit {
                    val currentFile = getFile(fileId)
                    if (currentFile?.local?.isDownloadingCompleted == true) {
                        currentFile.local.path.takeIf { it.isNotEmpty() }?.let {
                            resolvedPathCache[fileId] = it
                            persistSongFilePathIfNeeded(fileId, it)
                            _downloadCompleted.tryEmit(fileId)
                            return@withPermit it
                        }
                    }

                    val initialFile = getFile(fileId)
                    val isSmallFile = initialFile?.size == 0L || (initialFile?.size ?: 0) < 1024 * 1024

                    if (isSmallFile) {
                        return@withPermit try {
                            val resultFile = withTimeout(15_000L) {
                                clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, true))
                            }
                            if (resultFile.local.isDownloadingCompleted && resultFile.local.path.isNotEmpty()) {
                                resolvedPathCache[fileId] = resultFile.local.path
                                persistSongFilePathIfNeeded(fileId, resultFile.local.path)
                                _downloadCompleted.tryEmit(fileId)
                                resultFile.local.path
                            } else null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e.message?.contains("canceled") != true && e.message?.contains("has failed") != true) {
                                Timber.w("Sync download failed for $fileId: ${e.message}")
                            }
                            null
                        }
                    }

                    try {
                        clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
                    } catch (e: Exception) {
                        Timber.w("Async download request failed for $fileId: ${e.message}")
                        return@withPermit null
                    }

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
                        persistSongFilePathIfNeeded(fileId, completedPath)
                        _downloadCompleted.tryEmit(fileId)
                        return@withPermit completedPath
                    }

                    val finalFile = getFile(fileId)
                    return@withPermit if (finalFile?.local?.isDownloadingCompleted == true && finalFile.local.path.isNotEmpty()) {
                        persistSongFilePathIfNeeded(fileId, finalFile.local.path)
                        _downloadCompleted.tryEmit(fileId)
                        finalFile.local.path
                    } else null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("downloadFileAwait error for $fileId: ${e.message}")
                throw e
            } finally {
                activeDownloads.remove(fileId)
            }
        }

        activeDownloads[fileId] = newJob
        return try {
            newJob.start()
            newJob.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            newJob.cancel(e)
            throw e
        }
    }

    // ─── App Playlist Management ──────────────────────────────────────────────

    private fun getAppPlaylistIdForChannel(chatId: Long) = "$TELEGRAM_PLAYLIST_PREFIX$chatId"
    private fun getAppPlaylistIdForTopic(chatId: Long, threadId: Long) =
        "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_$threadId"

    private fun toUnifiedTelegramSongId(telegramSongId: String): Long {
        val songId = -(telegramSongId.hashCode().toLong().absoluteValue)
        return if (songId == 0L) -1L else songId
    }

    /** Creates/updates the whole-channel playlist (used for non-forum channels). */
    suspend fun updateAppPlaylistForTelegramChannel(
        chatId: Long,
        channelTitle: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(getAppPlaylistIdForChannel(chatId), channelTitle, unifiedSongIds, "TELEGRAM")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for Telegram channel $chatId")
        }
    }

    /** Creates/updates a per-topic playlist. */
    suspend fun updateAppPlaylistForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        telegramEntities: List<TelegramSongEntity>
    ) {
        try {
            val unifiedSongIds = telegramEntities.map { toUnifiedTelegramSongId(it.id).toString() }
            upsertPlaylist(
                getAppPlaylistIdForTopic(chatId, threadId),
                topicName,
                unifiedSongIds,
                "TELEGRAM_TOPIC"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update app playlist for topic $threadId in chat $chatId")
        }
    }

    private suspend fun upsertPlaylist(
        playlistId: String,
        name: String,
        songIds: List<String>,
        source: String
    ) {
        val existing = withContext(Dispatchers.IO) {
            playlistPreferencesRepository.userPlaylistsFlow
                .map { it.find { p -> p.id == playlistId } }
                .first()
        }

        if (existing != null) {
            playlistPreferencesRepository.updatePlaylist(
                existing.copy(
                    name = name,
                    songIds = songIds,
                    lastModified = System.currentTimeMillis(),
                    source = source
                )
            )
        } else {
            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = songIds,
                customId = playlistId,
                source = source
            )
        }
    }

    suspend fun deleteAppPlaylistForTelegramChannel(chatId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForChannel(chatId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for Telegram channel $chatId")
        }
    }

    suspend fun deleteAppPlaylistForTopic(chatId: Long, threadId: Long) {
        try {
            playlistPreferencesRepository.deletePlaylist(getAppPlaylistIdForTopic(chatId, threadId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete app playlist for topic $threadId in chat $chatId")
        }
    }

    /** Deletes all topic playlists for a given channel (used when removing a channel). */
    suspend fun deleteAllTopicPlaylistsForChannel(chatId: Long) {
        try {
            val all = withContext(Dispatchers.IO) {
                playlistPreferencesRepository.userPlaylistsFlow.first()
            }
            val prefix = "$TELEGRAM_TOPIC_PLAYLIST_PREFIX${chatId}_"
            all.filter { it.id.startsWith(prefix) }.forEach {
                playlistPreferencesRepository.deletePlaylist(it.id)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete topic playlists for channel $chatId")
        }
    }
}
