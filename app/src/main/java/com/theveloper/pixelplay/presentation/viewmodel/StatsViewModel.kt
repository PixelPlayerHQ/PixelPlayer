package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.data.stats.mergeStatsSongs
import com.theveloper.pixelplay.youtube.YoutubeClient
import com.theveloper.pixelplay.youtube.YoutubeMusicStore
import com.theveloper.pixelplay.youtube.YoutubeStatsCreditAi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository,
    private val youtubeMusicStore: YoutubeMusicStore,
    private val youtubeClient: YoutubeClient,
    private val youtubeStatsCreditAi: YoutubeStatsCreditAi,
) : ViewModel() {

    data class StatsUiState(
        val selectedRange: StatsTimeRange = StatsTimeRange.WEEK,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val summary: PlaybackStatsSummary? = null,
        val availableRanges: List<StatsTimeRange> = StatsTimeRange.entries
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _weeklyOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val weeklyOverview: StateFlow<PlaybackStatsSummary?> = _weeklyOverview.asStateFlow()

    private val _homeOverview = MutableStateFlow<PlaybackStatsSummary?>(null)
    val homeOverview: StateFlow<PlaybackStatsSummary?> = _homeOverview.asStateFlow()

    @Volatile
    private var cachedSongs: List<Song>? = null

    init {
        observeYoutubeCatalog()
        observeStatsRefreshFlow()
        enrichYoutubeCredits()
        refreshRange(
            range = StatsTimeRange.WEEK,
            showLoading = true,
            updateWeeklyOverview = true
        )
        refreshHomeOverview()
    }

    fun onRangeSelected(range: StatsTimeRange) {
        if (range == _uiState.value.selectedRange && !_uiState.value.isLoading) {
            return
        }
        refreshRange(
            range = range,
            showLoading = true,
            updateWeeklyOverview = range == StatsTimeRange.WEEK
        )
    }

    fun refreshWeeklyOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(StatsTimeRange.WEEK, songs)
                }
            }.onSuccess { summary ->
                _weeklyOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load weekly stats overview")
                _weeklyOverview.value = null
            }
        }
    }

    fun refreshHomeOverview() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    for (range in HomeOverviewRanges) {
                        val summary = playbackStatsRepository.loadSummary(range, songs)
                        if (summary.hasListeningActivity()) {
                            return@withContext summary
                        }
                    }
                    null
                }
            }.onSuccess { summary ->
                _homeOverview.value = summary
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load home stats overview")
                _homeOverview.value = null
            }
        }
    }

    private fun refreshRange(
        range: StatsTimeRange,
        showLoading: Boolean = true,
        updateWeeklyOverview: Boolean = false
    ) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, isRefreshing = false, selectedRange = range) }
            } else {
                _uiState.update { it.copy(isRefreshing = true, selectedRange = range) }
            }
            val summary = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    playbackStatsRepository.loadSummary(range, songs)
                }
            }
            summary.getOrNull()?.let { loaded ->
                if (updateWeeklyOverview) {
                    _weeklyOverview.value = loaded
                }
            }
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    summary = summary.getOrNull(),
                    selectedRange = range
                )
            }
            summary.exceptionOrNull()?.let { Timber.e(it, "Failed to load stats for range %s", range) }
        }
    }

    private fun observeStatsRefreshFlow() {
        viewModelScope.launch {
            playbackStatsRepository.refreshFlow
                .drop(1)
                .collectLatest {
                    val selectedRange = _uiState.value.selectedRange
                    refreshRange(
                        range = selectedRange,
                        showLoading = false,
                        updateWeeklyOverview = selectedRange == StatsTimeRange.WEEK
                    )
                    if (selectedRange != StatsTimeRange.WEEK) {
                        refreshWeeklyOverview()
                    }
                    refreshHomeOverview()
                }
        }
    }

    private fun enrichYoutubeCredits() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val played = playbackStatsRepository.exportEventsForBackup()
                    .asSequence()
                    .map { it.songId }
                    .filter { it.startsWith("yt_") }
                    .toSet()
                youtubeClient.fillPlayedCredits(youtubeMusicStore, played)
                youtubeStatsCreditAi.fill(youtubeMusicStore, played)
            }.onFailure { throwable ->
                Timber.e(throwable, "Failed to load YouTube artist and album names")
            }
        }
    }

    fun requestStatsRefresh() {
        playbackStatsRepository.requestRefresh()
    }

    fun forceRegenerateStats() {
        cachedSongs = null
        playbackStatsRepository.requestRefresh()
    }

    private fun observeYoutubeCatalog() {
        viewModelScope.launch {
            youtubeMusicStore.catalogVersion
                .drop(1)
                .collectLatest {
                    enrichYoutubeCredits()
                    val selectedRange = _uiState.value.selectedRange
                    refreshRange(
                        range = selectedRange,
                        showLoading = false,
                        updateWeeklyOverview = selectedRange == StatsTimeRange.WEEK
                    )
                    if (selectedRange != StatsTimeRange.WEEK) {
                        refreshWeeklyOverview()
                    }
                    refreshHomeOverview()
                }
        }
    }

    private suspend fun loadSongs(): List<Song> {
        val library = cachedSongs?.takeIf { it.isNotEmpty() } ?: musicRepository.getAllSongsOnce().also { loaded ->
            if (loaded.isNotEmpty()) cachedSongs = loaded
        }
        return mergeStatsSongs(library, youtubeMusicStore.songsForStats())
    }

    private fun PlaybackStatsSummary.hasListeningActivity(): Boolean {
        return totalDurationMs > 0L ||
            totalPlayCount > 0 ||
            uniqueSongs > 0 ||
            activeDays > 0 ||
            totalSessions > 0
    }

    private companion object {
        val HomeOverviewRanges = listOf(
            StatsTimeRange.WEEK,
            StatsTimeRange.MONTH,
            StatsTimeRange.YEAR,
            StatsTimeRange.ALL
        )
    }
}
