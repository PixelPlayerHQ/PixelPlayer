package com.theveloper.pixelplay.presentation.telegram.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.database.toTelegramEntityWithThread
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.cloud.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.theveloper.pixelplay.presentation.viewmodel.ConnectivityStateHolder

@HiltViewModel
class TelegramDashboardViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    private val connectivityStateHolder: ConnectivityStateHolder
) : ViewModel() {

    val isOnline = connectivityStateHolder.isOnline

    val channels = musicRepository.getAllTelegramChannels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow<Long?>(null)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    // Topics per channel: channelChatId -> list of topics
    val topicsMap = musicRepository.getAllTelegramTopics()
        .map { topics -> topics.groupBy { it.chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Track which channels are expanded to show topics
    private val _expandedChannels = MutableStateFlow<Set<Long>>(emptySet())
    val expandedChannels = _expandedChannels.asStateFlow()

    fun toggleChannelExpanded(chatId: Long) {
        val current = _expandedChannels.value
        _expandedChannels.value = if (chatId in current) current - chatId else current + chatId
    }

    fun refreshChannel(channel: TelegramChannelEntity) {
        if (_isRefreshing.value != null) return

        viewModelScope.launch {
            _isRefreshing.value = channel.chatId
            _statusMessage.value = "Syncing ${channel.title}..."

            try {
                val isForum = telegramRepository.isForum(channel.chatId)

                if (isForum) {
                    syncForumChannel(channel)
                } else {
                    syncFlatChannel(channel)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync failed: ${e.message}"
            } finally {
                // Guarantee the unified-table sync runs even if a forum topic loop
                // threw partway. Without this, topic songs already committed to
                // telegram_songs would stay invisible until an unrelated later sync.
                // KEEP policy means this is a no-op while another sync is still in
                // flight (in particular, never disturbs a full/rebuild).
                runCatching { musicRepository.requestTelegramUnifiedSync() }
                _isRefreshing.value = null
            }
        }
    }

    private suspend fun syncFlatChannel(channel: TelegramChannelEntity) {
        val songs = telegramRepository.getAudioMessages(channel.chatId)
        musicRepository.replaceTelegramSongsForChannel(channel.chatId, songs)

        val updatedChannel = channel.copy(
            songCount = songs.size,
            lastSyncTime = System.currentTimeMillis()
        )
        musicRepository.saveTelegramChannel(updatedChannel)

        _statusMessage.value = if (songs.isNotEmpty())
            "Synced ${songs.size} songs from ${channel.title}"
        else
            "No songs found in ${channel.title}"
    }

    private suspend fun syncForumChannel(channel: TelegramChannelEntity) {
        val topics = telegramRepository.getForumTopics(channel.chatId)

        if (topics.isEmpty()) {
            _statusMessage.value = "No topics found in ${channel.title}"
            return
        }

        // Replace topics in DB — this deletes any topics removed from Telegram
        // and inserts the fresh list. The reactive topicsMap flow will detect this.
        musicRepository.replaceTopicsForChannel(channel.chatId, topics)
        if (channel.chatId !in _expandedChannels.value) {
            _expandedChannels.value = _expandedChannels.value + channel.chatId
        }

        var totalSongs = 0
        topics.forEach { topic ->
            val topicSongs = telegramRepository.getAudioMessagesByTopic(channel.chatId, topic.threadId)
            totalSongs += topicSongs.size

            // Replace songs for this topic (with thread_id stamped)
            musicRepository.replaceTelegramSongsForTopic(
                chatId = channel.chatId,
                threadId = topic.threadId,
                topicName = topic.name,
                songs = topicSongs
            )

            // Track updated topic with real song count
            val updatedTopic = topic.copy(
                songCount = topicSongs.size,
                lastSyncTime = System.currentTimeMillis()
            )
            musicRepository.saveTelegramTopics(channel.chatId, listOf(updatedTopic))
        }

        // Update channel metadata (total across all topics)
        val updatedChannel = channel.copy(
            songCount = totalSongs,
            lastSyncTime = System.currentTimeMillis()
        )
        musicRepository.saveTelegramChannel(updatedChannel)

        _statusMessage.value = "Synced $totalSongs songs across ${topics.size} topics in ${channel.title}"
    }

    fun removeChannel(chatId: Long) {
        viewModelScope.launch {
            musicRepository.deleteTelegramChannel(chatId)
            _statusMessage.value = "Channel removed"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun refreshChannels() {
        connectivityStateHolder.refreshLocalConnectionInfo()
    }
}