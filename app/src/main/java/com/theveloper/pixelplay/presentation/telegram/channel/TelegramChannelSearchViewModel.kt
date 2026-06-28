package com.theveloper.pixelplay.presentation.telegram.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import com.theveloper.pixelplay.data.telegram.TelegramSyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

import com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity

@HiltViewModel
class TelegramChannelSearchViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    connectivityStateHolder: ConnectivityStateHolder
) : ViewModel() {

    val isOnline = connectivityStateHolder.isOnline

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _resolvedUsername = MutableStateFlow<String?>(null)

    private val _foundChat = MutableStateFlow<TdApi.Chat?>(null)
    val foundChat = _foundChat.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // See TelegramSyncProgress. Null when no fetch is in progress.
    private val _syncProgress = MutableStateFlow<TelegramSyncProgress?>(null)
    val syncProgress = _syncProgress.asStateFlow()

    // Status message for errors or "Not Found"
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _playbackRequest = kotlinx.coroutines.flow.MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val playbackRequest = _playbackRequest.asSharedFlow()

    private fun extractUsername(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.contains("t.me/") -> "@" + trimmed
                .substringAfterLast("t.me/")
                .substringBefore("?")
                .substringBefore("/")
                .removePrefix("@")
            trimmed.startsWith("@") -> trimmed
            else -> "@$trimmed"
        }
    }
    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun searchChannel() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            _isLoading.value = true
            _statusMessage.value = null
            _foundChat.value = null
            _songs.value = emptyList()

            val resolvedUsername = extractUsername(query)
            _resolvedUsername.value = resolvedUsername

            viewModelScope.launch {
                val chat = telegramRepository.searchPublicChat(resolvedUsername)
                _isLoading.value = false

                if (chat != null) {
                    _foundChat.value = chat
                    fetchSongs(chat.id)
                } else {
                    _statusMessage.value = "Channel not found"
                }
            }
        }
    }

    private fun fetchSongs(chatId: Long) {
        _isLoading.value = true
        _statusMessage.value = "Syncing songs from channel..."

        viewModelScope.launch {
            try {
                val isForum = telegramRepository.isForum(chatId)
                val chat = _foundChat.value ?: return@launch

                val allSongs = telegramRepository.getAudioMessages(chatId) { current, approxTotal ->
                    _syncProgress.value = TelegramSyncProgress(current, approxTotal)
                }
                musicRepository.replaceTelegramSongsForChannel(chatId, allSongs)

                var localPhotoPath: String? = null
                val photoFileId = chat.photo?.small?.id
                if (photoFileId != null) {
                    localPhotoPath = telegramRepository.downloadFileAwait(photoFileId)
                }

                val baseEntity = TelegramChannelEntity(
                    chatId = chat.id,
                    title = chat.title,
                    username = _resolvedUsername.value,
                    songCount = allSongs.size,
                    lastSyncTime = System.currentTimeMillis(),
                    photoPath = localPhotoPath
                )

                // Always save base entity first for channel playlist
                musicRepository.saveTelegramChannel(baseEntity)

                if (isForum) {
                    val topics = telegramRepository.getForumTopics(chatId)
                    if (topics.isNotEmpty()) {
                        musicRepository.replaceTopicsForChannel(chatId, topics)
                        var totalSongs = 0
                        topics.forEach { topic ->
                            _statusMessage.value = "Syncing topic \"${topic.name}\"..."
                            val topicSongs = telegramRepository.getAudioMessagesByTopic(chatId, topic.threadId) { current ->
                                // approxTotal = -1: no honest per-topic total available, see
                                // the equivalent comment in TelegramDashboardViewModel.
                                _syncProgress.value = TelegramSyncProgress(current, -1)
                            }
                            totalSongs += topicSongs.size
                            musicRepository.replaceTelegramSongsForTopic(
                                chatId = chatId,
                                threadId = topic.threadId,
                                topicName = topic.name,
                                songs = topicSongs
                            )
                            musicRepository.saveTelegramTopics(chatId, listOf(
                                topic.copy(
                                    songCount = topicSongs.size,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            ))
                        }
                        musicRepository.saveTelegramChannel(baseEntity.copy(songCount = totalSongs))
                        _statusMessage.value = "Success! $totalSongs songs across ${topics.size} topics added. You can close this window."
                    } else {
                        _statusMessage.value = "Success! ${allSongs.size} songs added to library. You can close this window."
                    }
                } else {
                    _statusMessage.value = "Success! ${allSongs.size} songs added to library. You can close this window."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync failed: ${e.message}"
            } finally {
                // Guarantee the unified-table sync runs even if forum ingestion threw
                // partway through the topic loop. Without this, topic songs persisted
                // to telegram_songs before the failure would stay invisible until the
                // next unrelated sync. KEEP policy means this is a no-op while another
                // sync is still in flight.
                runCatching { musicRepository.requestTelegramUnifiedSync() }
                _songs.value = emptyList()
                _isLoading.value = false
                _syncProgress.value = null
            }
        }
    }

    fun downloadAndPlay(song: Song) {
        if (song.telegramFileId == null) return

        _isLoading.value = true
        _statusMessage.value = "Downloading ${song.title}..."

        viewModelScope.launch {
            val localPath = telegramRepository.downloadFileAwait(song.telegramFileId)
            _isLoading.value = false

            if (localPath != null) {
                // Create a new Song with the local path
                val playableSong = song.copy(path = localPath, contentUriString = localPath)
                musicRepository.saveTelegramSongs(listOf(playableSong)) // Update DB with path
                _playbackRequest.tryEmit(playableSong)
                _statusMessage.value = "Playing..."
            } else {
                _statusMessage.value = "Failed to download song"
            }
        }
    }

    fun resetState() {
        _searchQuery.value = ""
        _foundChat.value = null
        _songs.value = emptyList()
        _isLoading.value = false
        _syncProgress.value = null
        _statusMessage.value = null
        _resolvedUsername.value = null
    }
}
