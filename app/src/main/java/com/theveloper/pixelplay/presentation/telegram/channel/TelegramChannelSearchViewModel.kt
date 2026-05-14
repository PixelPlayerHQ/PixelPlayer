package com.theveloper.pixelplay.presentation.telegram.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
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

    private val _foundChat = MutableStateFlow<TdApi.Chat?>(null)
    val foundChat = _foundChat.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _playbackRequest = kotlinx.coroutines.flow.MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val playbackRequest = _playbackRequest.asSharedFlow()

    // null = not yet loaded; empty list = loaded but no channels found
    private val _myChannels = MutableStateFlow<List<TdApi.Chat>?>(null)
    val myChannels = _myChannels.asStateFlow()

    private val _isLoadingMyChannels = MutableStateFlow(false)
    val isLoadingMyChannels = _isLoadingMyChannels.asStateFlow()

    init {
        loadMyChannels()
    }

    fun loadMyChannels() {
        if (_isLoadingMyChannels.value) return
        viewModelScope.launch {
            _isLoadingMyChannels.value = true
            _myChannels.value = telegramRepository.getUserChannels()
            _isLoadingMyChannels.value = false
        }
    }

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun searchChannel() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        _isLoading.value = true
        _statusMessage.value = null
        _foundChat.value = null
        _songs.value = emptyList()

        viewModelScope.launch {
            when (val input = parseInput(query)) {
                is ChannelInput.InviteLink -> {
                    val result = telegramRepository.resolveInviteLink(input.link)
                    _isLoading.value = false
                    if (result.isSuccess) {
                        val chat = result.getOrThrow()
                        _foundChat.value = chat
                        fetchSongs(chat)
                    } else {
                        _statusMessage.value = "Could not resolve invite link: ${result.exceptionOrNull()?.message}"
                    }
                }
                is ChannelInput.Username -> {
                    val chat = telegramRepository.searchPublicChat(input.username)
                    _isLoading.value = false
                    if (chat != null) {
                        _foundChat.value = chat
                        fetchSongs(chat)
                    } else {
                        _statusMessage.value = "Channel not found"
                    }
                }
            }
        }
    }

    fun addChannelFromMyList(chat: TdApi.Chat) {
        if (_isLoading.value) return
        _foundChat.value = chat
        _statusMessage.value = null
        fetchSongs(chat)
    }

    private fun fetchSongs(chat: TdApi.Chat) {
        _isLoading.value = true
        _statusMessage.value = "Syncing songs from channel…"

        viewModelScope.launch {
            try {
                val isForum = telegramRepository.isForum(chat.id)

                val allSongs = telegramRepository.getAudioMessages(chat.id)
                musicRepository.replaceTelegramSongsForChannel(chat.id, allSongs)

                var localPhotoPath: String? = null
                val photoFileId = chat.photo?.small?.id
                if (photoFileId != null) {
                    localPhotoPath = telegramRepository.downloadFileAwait(photoFileId)
                }

                val baseEntity = TelegramChannelEntity(
                    chatId = chat.id,
                    title = chat.title,
                    username = null, // username resolved on demand from supergroup info
                    songCount = allSongs.size,
                    lastSyncTime = System.currentTimeMillis(),
                    photoPath = localPhotoPath
                )

                musicRepository.saveTelegramChannel(baseEntity)

                if (isForum) {
                    val topics = telegramRepository.getForumTopics(chat.id)
                    if (topics.isNotEmpty()) {
                        musicRepository.replaceTopicsForChannel(chat.id, topics)
                        var totalSongs = 0
                        topics.forEach { topic ->
                            val topicSongs = telegramRepository.getAudioMessagesByTopic(chat.id, topic.threadId)
                            totalSongs += topicSongs.size
                            musicRepository.replaceTelegramSongsForTopic(
                                chatId = chat.id,
                                threadId = topic.threadId,
                                topicName = topic.name,
                                songs = topicSongs
                            )
                            musicRepository.saveTelegramTopics(chat.id, listOf(
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
                runCatching { musicRepository.requestTelegramUnifiedSync() }
                _songs.value = emptyList()
                _isLoading.value = false
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
                val playableSong = song.copy(path = localPath, contentUriString = localPath)
                musicRepository.saveTelegramSongs(listOf(playableSong))
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
        _statusMessage.value = null
    }

    private sealed class ChannelInput {
        data class Username(val username: String) : ChannelInput()
        data class InviteLink(val link: String) : ChannelInput()
    }

    private fun parseInput(input: String): ChannelInput {
        val trimmed = input.trim()
        // Normalise to a full URL so we can check the path uniformly
        val url = when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.contains("t.me/") -> "https://$trimmed"
            else -> trimmed
        }

        return when {
            // Private invite link patterns: t.me/+ (modern) or t.me/joinchat/ (legacy)
            url.contains("t.me/+") || url.contains("t.me/joinchat/") -> {
                // Strip surrounding whitespace/newlines and ensure https:// prefix
                val link = if (url.startsWith("http")) url else "https://t.me/${url.substringAfterLast("t.me/")}"
                ChannelInput.InviteLink(link)
            }
            url.contains("t.me/") -> {
                val slug = url.substringAfterLast("t.me/")
                    .substringBefore("?")
                    .substringBefore("/")
                    .removePrefix("@")
                ChannelInput.Username("@$slug")
            }
            trimmed.startsWith("@") -> ChannelInput.Username(trimmed)
            else -> ChannelInput.Username("@$trimmed")
        }
    }
}
