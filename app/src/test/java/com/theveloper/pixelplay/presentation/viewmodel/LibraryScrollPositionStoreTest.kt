package com.theveloper.pixelplay.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryScrollPositionStoreTest {

    @Test
    fun `restore returns the saved position unchanged when it is in range`() {
        val store = LibraryScrollPositionStore()
        store.save("albums-grid", 42, 120)

        assertEquals(
            LibraryScrollPosition(firstVisibleItemIndex = 42, firstVisibleItemScrollOffset = 120),
            store.restore("albums-grid", totalItems = 200),
        )
    }

    @Test
    fun `restore returns null when nothing was saved or the list is empty`() {
        val store = LibraryScrollPositionStore()

        assertNull(store.restore("albums-grid", totalItems = 200))
        store.save("albums-grid", 5, 0)
        assertNull(store.restore("albums-grid", totalItems = 0))
    }

    @Test
    fun `restore clamps to the last item and drops the offset when items were removed`() {
        val store = LibraryScrollPositionStore()
        store.save("folders:/music", 90, 120)

        assertEquals(
            LibraryScrollPosition(firstVisibleItemIndex = 49, firstVisibleItemScrollOffset = 0),
            store.restore("folders:/music", totalItems = 50),
        )
    }

    @Test
    fun `positions are kept independently per key`() {
        val store = LibraryScrollPositionStore()
        store.save("albums-list", 10, 0)
        store.save("albums-grid", 20, 30)
        store.save("folders:__folder_root__", 3, 0)

        assertEquals(10, store.restore("albums-list", totalItems = 100)?.firstVisibleItemIndex)
        assertEquals(20, store.restore("albums-grid", totalItems = 100)?.firstVisibleItemIndex)
        assertEquals(3, store.restore("folders:__folder_root__", totalItems = 100)?.firstVisibleItemIndex)
        assertNull(store.restore("albums-unknown", totalItems = 100))
    }

    @Test
    fun `save ignores a negative index and clear removes the position`() {
        val store = LibraryScrollPositionStore()
        store.save("albums-grid", -1, 0)
        assertNull(store.restore("albums-grid", totalItems = 100))

        store.save("albums-grid", 7, 0)
        store.clear("albums-grid")
        assertNull(store.restore("albums-grid", totalItems = 100))
    }
}
