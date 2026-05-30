package com.theveloper.pixelplay.data.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWorkerRequestTest {

    @Test
    fun `startup sync request runs maintenance`() {
        val request = SyncWorker.startUpSyncWork()

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }

    @Test
    fun `default incremental sync request runs maintenance`() {
        val request = SyncWorker.incrementalSyncWork()

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }

    @Test
    fun `incremental sync request can skip maintenance`() {
        val request = SyncWorker.incrementalSyncWork(runMaintenance = false)

        assertEquals(SyncMode.INCREMENTAL.name, request.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertFalse(request.workSpec.input.getBoolean(SyncWorker.INPUT_FORCE_METADATA, false))
        assertFalse(request.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, true))
    }

    @Test
    fun `full sync and rebuild requests run maintenance`() {
        val fullRequest = SyncWorker.fullSyncWork()
        val rebuildRequest = SyncWorker.rebuildDatabaseWork()

        assertEquals(SyncMode.FULL.name, fullRequest.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(fullRequest.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))

        assertEquals(SyncMode.REBUILD.name, rebuildRequest.workSpec.input.getString(SyncWorker.INPUT_SYNC_MODE))
        assertTrue(rebuildRequest.workSpec.input.getBoolean(SyncWorker.INPUT_RUN_MAINTENANCE, false))
    }
}
