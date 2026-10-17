package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import com.theveloper.pixelplay.MainCoroutineExtension
import com.theveloper.pixelplay.data.model.PlaybackQueueItemSnapshot
import com.theveloper.pixelplay.data.model.PlaybackQueueSnapshot
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import androidx.media3.session.MediaController

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaybackStateHolderTest {

    private val dualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val castStateHolder: CastStateHolder = mockk(relaxed = true)
    private val queueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val powerManager: PowerManager = mockk(relaxed = true)

    private fun createHolder() = PlaybackStateHolder(
        dualPlayerEngine = dualPlayerEngine,
        userPreferencesRepository = userPreferencesRepository,
        castStateHolder = castStateHolder,
        queueStateHolder = queueStateHolder,
        appContext = appContext
    )

    private fun snapshot(
        mediaId: String = "duplicate-song",
        positionMs: Long = 48_000L
    ) = PlaybackQueueSnapshot(
        items = listOf(
            PlaybackQueueItemSnapshot(
                mediaId = mediaId,
                uri = "file:///music/$mediaId.mp3"
            )
        ),
        currentMediaId = mediaId,
        currentIndex = 0,
        currentPositionMs = positionMs
    )

    init {
        every { appContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isInteractive } returns true
    }

    @Test
    fun `paused override does not bleed into later occurrence with same media id`() {
        val holder = createHolder()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.rememberPausedPositionOverride("duplicate-song", 91_000L)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `clearing latest media controller restores previous activity controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(mainController)
        holder.setMediaController(externalController)

        assertSame(externalController, holder.mediaController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `clearing stale media controller keeps active controller`() {
        val holder = createHolder()
        val mainController = mockk<MediaController>(relaxed = true)
        val externalController = mockk<MediaController>(relaxed = true)

        holder.setMediaController(externalController)
        holder.setMediaController(mainController)

        holder.clearMediaController(externalController)

        assertSame(mainController, holder.mediaController)
    }

    @Test
    fun `next song resumes playback when paused and another item is available`() {
        val holder = createHolder()
        val controller = mockk<MediaController>(relaxed = true)
        every { castStateHolder.castSession } returns MutableStateFlow(null)
        every { controller.isConnected } returns true
        every { controller.playWhenReady } returns false
        every { controller.isPlaying } returns false
        every { controller.hasNextMediaItem() } returns true
        holder.setMediaController(controller)

        holder.nextSong()

        verifyOrder {
            controller.seekToNext()
            controller.play()
        }
    }

    @Test
    fun `next song keeps paused playback when there is no next item`() {
        val holder = createHolder()
        val controller = mockk<MediaController>(relaxed = true)
        every { castStateHolder.castSession } returns MutableStateFlow(null)
        every { controller.isConnected } returns true
        every { controller.playWhenReady } returns false
        every { controller.isPlaying } returns false
        every { controller.hasNextMediaItem() } returns false
        holder.setMediaController(controller)

        holder.nextSong()

        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `previous song resumes playback when paused and another item is available`() {
        val holder = createHolder()
        val controller = mockk<MediaController>(relaxed = true)
        every { castStateHolder.castSession } returns MutableStateFlow(null)
        every { controller.isConnected } returns true
        every { controller.currentPosition } returns 0L
        every { controller.playWhenReady } returns false
        every { controller.isPlaying } returns false
        every { controller.hasPreviousMediaItem() } returns true
        holder.setMediaController(controller)

        holder.previousSong()

        verifyOrder {
            controller.seekToPrevious()
            controller.play()
        }
    }

    @Test
    fun `previous song keeps paused playback when restarting current item`() {
        val holder = createHolder()
        val controller = mockk<MediaController>(relaxed = true)
        every { castStateHolder.castSession } returns MutableStateFlow(null)
        every { controller.isConnected } returns true
        every { controller.currentPosition } returns 11_000L
        every { controller.playWhenReady } returns false
        every { controller.isPlaying } returns false
        every { controller.hasPreviousMediaItem() } returns true
        holder.setMediaController(controller)

        holder.previousSong()

        verify(exactly = 1) { controller.seekTo(0) }
        verify(exactly = 0) { controller.play() }
    }

    @Test
    fun `cold start snapshot only applies to the first matching occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(48_000L, holder.currentPosition.value)

        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot binds to the already active first occurrence`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)
        assertEquals(0L, holder.currentPosition.value)

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(48_000L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after playback occurrence advances`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition("another-song")
        holder.onPlaybackOccurrenceTransition("duplicate-song")

        advanceUntilIdle()

        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }

    @Test
    fun `late cold start snapshot is discarded after first occurrence already ended`() = runTest {
        coEvery { userPreferencesRepository.getPlaybackQueueSnapshotOnce() } returns snapshot()

        val holder = createHolder()
        holder.initialize(this)
        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.onPlaybackOccurrenceTransition(null)

        advanceUntilIdle()

        holder.ensureCurrentPlaybackOccurrence("duplicate-song")
        holder.syncCurrentPositionFromPlayer("duplicate-song", 0L)

        assertEquals(0L, holder.currentPosition.value)
    }
}
