package com.theveloper.pixelplay.data.download

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRepositoryTest {

    private fun repo() = DownloadRepository()

    @Test
    fun `enqueue adds queued job`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        val jobs = r.jobs.value
        assertEquals(1, jobs.size)
        assertEquals(JobStatus.QUEUED, jobs[0].status)
        assertEquals("Song", jobs[0].title)
    }

    @Test
    fun `applyDelta updates progress and completion`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        val itemId = r.jobs.value[0].itemId
        r.applyDelta(
            ProgressDelta(seq = 1, items = mapOf(itemId to ItemProgress(itemId, bytesTotal = 100, bytesReceived = 40, progress = 0.4f, isDownloading = true, status = "downloading"))),
        )
        assertEquals(JobStatus.DOWNLOADING, r.jobs.value[0].status)
        assertEquals(0.4f, r.jobs.value[0].progress)
        r.applyDelta(
            ProgressDelta(seq = 2, items = mapOf(itemId to ItemProgress(itemId, progress = 1f, isDownloading = false, status = "completed"))),
        )
        assertEquals(JobStatus.COMPLETED, r.jobs.value[0].status)
    }

    @Test
    fun `applyDelta removes and marks failed`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        val itemId = r.jobs.value[0].itemId
        r.markFailed(itemId, error = "boom", errorType = "http_403")
        assertEquals(JobStatus.FAILED, r.jobs.value[0].status)
        assertEquals("boom", r.jobs.value[0].error)
        // removed item disappears from active list but stays in history
        r.applyDelta(ProgressDelta(seq = 3, removed = listOf(itemId)))
        assertEquals(1, r.jobs.value.size) // history kept
        assertNull(r.claimNextQueued())   // failed not re-claimed
    }

    @Test
    fun `claimNextQueued returns oldest queued`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "S1", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        r.enqueue(ExtTrack(id = "t2", name = "S2", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        assertEquals("S1", r.claimNextQueued()?.title)
        assertEquals("S2", r.claimNextQueued()?.title)
        assertNull(r.claimNextQueued())
    }

    @Test
    fun `retry resets failed job`() = runTest {
        val r = repo()
        r.enqueue(ExtTrack(id = "t1", name = "Song", artists = "A"), quality = "flac", downloadProvider = "qobuz")
        val id = r.jobs.value[0].itemId
        r.markFailed(id, "err", "x")
        r.retry(id)
        assertEquals(JobStatus.QUEUED, r.jobs.value[0].status)
        assertNull(r.jobs.value[0].error)
    }
}
