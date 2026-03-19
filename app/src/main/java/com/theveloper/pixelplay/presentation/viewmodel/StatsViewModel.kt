package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val musicRepository: MusicRepository
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

    @Volatile
    private var cachedSongs: List<Song>? = null

    init {
        observeStatsRefreshFlow()
        refreshRange(
            range = StatsTimeRange.WEEK,
            showLoading = true,
            updateWeeklyOverview = true
        )
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

    private suspend fun loadSongs(): List<Song> {
        cachedSongs?.let { existing ->
            if (existing.isNotEmpty()) return existing
        }
        val songs = musicRepository.getAudioFiles().first()
        cachedSongs = songs
        return songs
    }
}
