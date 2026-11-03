package com.theveloper.pixelplay.presentation.viewmodel

/**
 * Pure, UI-framework-free store for library scroll positions.
 *
 * Why this exists (PixelPlayer #2814): the Albums and Folders tabs create their
 * lazy list/grid states with plain `remember...State()`, so the state object
 * dies with the composition. That happens in two places:
 * - Albums: leaving the Library destination for an AlbumDetail destination
 *   disposes LibraryScreen, and returning creates a fresh state at item 0.
 * - Folders: drilling into a folder swaps the AnimatedContent destination,
 *   disposing the previous destination's list state, so going back to the
 *   parent folder also starts at item 0.
 *
 * The store lives in [LibraryViewModel], which outlives both of those
 * transitions (it is only cleared when the Library destination itself is
 * popped), so a tab can save its position on dispose and restore it once
 * when it next enters composition. It deliberately does not restore on
 * every recomposition: sort changes already scroll to item 0 explicitly,
 * and a restore flag in the UI ensures a sort (or a view-mode toggle) after
 * the restore is not undone by re-running the restore.
 */
data class LibraryScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

class LibraryScrollPositionStore {
    private val positions = mutableMapOf<String, LibraryScrollPosition>()

    fun save(key: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        if (firstVisibleItemIndex < 0) return
        positions[key] = LibraryScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
    }

    fun saved(key: String): LibraryScrollPosition? = positions[key]

    /**
     * Returns the saved position clamped to [totalItems], or null when
     * nothing was saved or the list is still empty. Clamping the index down
     * also drops the pixel offset, since it belonged to a different item.
     */
    fun restore(key: String, totalItems: Int): LibraryScrollPosition? {
        val saved = positions[key] ?: return null
        if (totalItems <= 0) return null
        val index = saved.firstVisibleItemIndex.coerceIn(0, totalItems - 1)
        return if (index == saved.firstVisibleItemIndex) {
            saved
        } else {
            LibraryScrollPosition(firstVisibleItemIndex = index, firstVisibleItemScrollOffset = 0)
        }
    }

    fun clear(key: String) {
        positions.remove(key)
    }
}
