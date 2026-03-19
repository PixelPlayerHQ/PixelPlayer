package com.theveloper.pixelplay.presentation.viewmodel

import android.util.Log
import android.os.Trace
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ViewModelScoped
class LibraryTabsStateHolder @Inject constructor() {
    fun showSortingSheet(isSortingSheetVisible: MutableStateFlow<Boolean>) {
        isSortingSheetVisible.value = true
    }

    fun hideSortingSheet(isSortingSheetVisible: MutableStateFlow<Boolean>) {
        isSortingSheetVisible.value = false
    }

    fun onLibraryTabSelected(
        tabIndex: Int,
        libraryTabs: List<String>,
        loadedTabs: MutableStateFlow<Set<String>>,
        currentLibraryTabId: MutableStateFlow<LibraryTabId>,
        saveLastTabIndex: suspend (Int) -> Unit,
        scope: CoroutineScope,
        loadSongs: () -> Unit,
        loadAlbums: () -> Unit,
        loadArtists: () -> Unit,
        loadFolders: () -> Unit
    ) {
        Trace.beginSection("PlayerViewModel.onLibraryTabSelected")
        scope.launch { saveLastTabIndex(tabIndex) }

        val tabIdentifier = libraryTabs.getOrNull(tabIndex) ?: run {
            Trace.endSection()
            return
        }
        val tabId = tabIdentifier.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
        currentLibraryTabId.value = tabId

        if (loadedTabs.value.contains(tabIdentifier)) {
            Log.d("PlayerViewModel", "Tab '$tabIdentifier' already loaded. Skipping data load.")
            Trace.endSection()
            return
        }

        Log.d("PlayerViewModel", "Tab '$tabIdentifier' selected. Attempting to load data.")
        scope.launch {
            Trace.beginSection("PlayerViewModel.onLibraryTabSelected_coroutine_load")
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> loadSongs()
                    LibraryTabId.ALBUMS -> loadAlbums()
                    LibraryTabId.ARTISTS -> loadArtists()
                    LibraryTabId.FOLDERS -> loadFolders()
                    else -> Unit
                }
                loadedTabs.update { currentTabs -> currentTabs + tabIdentifier }
                Log.d("PlayerViewModel", "Tab '$tabIdentifier' marked as loaded. Current loaded tabs: ${loadedTabs.value}")
            } finally {
                Trace.endSection()
            }
        }
        Trace.endSection()
    }
}
