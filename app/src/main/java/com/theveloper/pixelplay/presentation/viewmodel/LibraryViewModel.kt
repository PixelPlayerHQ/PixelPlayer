package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {

    /**
     * Scroll positions for the Albums and Folders tabs (PixelPlayer #2814).
     * Held here, rather than in composition, so leaving for an album detail
     * page or drilling into a folder does not lose the position: the
     * ViewModel outlives both transitions and is only cleared when the
     * Library destination itself is popped.
     */
    val libraryScrollPositions = LibraryScrollPositionStore()

    val songsPagingFlow = libraryStateHolder.songsPagingFlow.cachedIn(viewModelScope)

    val albumsPagingFlow = libraryStateHolder.albumsPagingFlow.cachedIn(viewModelScope)

    val artistsPagingFlow = libraryStateHolder.artistsPagingFlow.cachedIn(viewModelScope)

    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow.cachedIn(viewModelScope)

    val favoriteSongCountFlow = libraryStateHolder.favoriteSongCountFlow

    val isLoadingLibrary = libraryStateHolder.isLoadingLibrary
}
