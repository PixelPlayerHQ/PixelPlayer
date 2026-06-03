package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Shelf
import com.theveloper.pixelplay.data.repository.ExtensionRepository
import com.theveloper.pixelplay.data.model.ExtensionCapabilities

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository
) : ViewModel() {

    val allExtensions = repository.allExtensions
    val installedMusicExtensions = repository.installedMusicExtensions
    val searchQuery: StateFlow<String> = repository.searchQuery
    val storeItems = repository.storeItems

    fun updateSearchQuery(query: String) = repository.updateSearchQuery(query)

    val currentMusicExtension: StateFlow<MusicExtension?> = repository.currentMusicExtension
    val homeFeed: StateFlow<Feed<Shelf>?> = repository.homeFeed
    val shelves: StateFlow<List<Shelf>> = repository.shelves
    val isLoadingFeed: StateFlow<Boolean> = repository.isLoadingFeed
    val yourMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = repository.yourMixSongsFromExtension
    val dailyMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = repository.dailyMixSongsFromExtension
    val libraryFeed: StateFlow<Feed<Shelf>?> = repository.libraryFeed
    val libraryShelves: StateFlow<List<Shelf>> = repository.libraryShelves
    val isLoadingLibraryFeed: StateFlow<Boolean> = repository.isLoadingLibraryFeed
    val isLoadingStore: StateFlow<Boolean> = repository.isLoadingStore
    val messages = repository.messages
    val errors = repository.errors
    val extensionCapabilities = repository.extensionCapabilities

    fun fetchStoreExtensions() = repository.fetchStoreExtensions()
    fun installExtension(item: com.theveloper.pixelplay.extensions.core.ExtensionStoreItem) = repository.installExtension(item)
    fun selectMusicExtension(extension: MusicExtension) = repository.selectMusicExtension(extension)
    fun loadHomeFeed() = repository.loadHomeFeed()
    fun loadLibraryFeed() = repository.loadLibraryFeed()
    
    fun filterRecentlyPlayedByExtension(allRecentlyPlayed: List<com.theveloper.pixelplay.data.model.Song>): List<com.theveloper.pixelplay.data.model.Song> {
        val extensionId = currentMusicExtension.value?.metadata?.id ?: return allRecentlyPlayed
        val extensionPrefix = "extension:$extensionId:"
        
        return allRecentlyPlayed.filter { song ->
            song.id.startsWith(extensionPrefix)
        }
    }

    fun login(extension: MusicExtension) { /* TODO */ }
    fun logout(extension: MusicExtension) { /* TODO */ }
    fun refresh() = repository.fetchStoreExtensions()
}
