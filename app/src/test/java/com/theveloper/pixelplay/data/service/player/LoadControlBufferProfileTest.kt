package com.theveloper.pixelplay.data.service.player

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LoadControlBufferProfileTest {

    @Test
    fun normalDevice_usesFullPrefetchProfile() {
        val profile = loadControlBufferProfileFor(isLowRamDevice = false)

        assertThat(profile.minBufferMs).isEqualTo(30_000)
        assertThat(profile.maxBufferMs).isEqualTo(60_000)
        assertThat(profile.bufferForPlaybackMs).isEqualTo(1_000)
        assertThat(profile.bufferForPlaybackAfterRebufferMs).isEqualTo(5_000)
    }

    @Test
    fun lowRamDevice_cutsPrefetchWindow() {
        val normal = loadControlBufferProfileFor(isLowRamDevice = false)
        val lowRam = loadControlBufferProfileFor(isLowRamDevice = true)

        assertThat(lowRam.maxBufferMs).isLessThan(normal.maxBufferMs)
        assertThat(lowRam.minBufferMs).isLessThan(normal.minBufferMs)
    }

    @Test
    fun lowRamDevice_keepsStartLatencyIdenticalToNormal() {
        // The whole point: capping RAM must not regress the cross-format start normalization.
        val normal = loadControlBufferProfileFor(isLowRamDevice = false)
        val lowRam = loadControlBufferProfileFor(isLowRamDevice = true)

        assertThat(lowRam.bufferForPlaybackMs).isEqualTo(normal.bufferForPlaybackMs)
        assertThat(lowRam.bufferForPlaybackAfterRebufferMs)
            .isEqualTo(normal.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun bothProfiles_satisfyDefaultLoadControlConstraints() {
        for (isLowRam in listOf(false, true)) {
            val profile = loadControlBufferProfileFor(isLowRam)

            // DefaultLoadControl.Builder.build() asserts these; violating them crashes at runtime.
            assertThat(profile.minBufferMs).isAtLeast(profile.bufferForPlaybackMs)
            assertThat(profile.minBufferMs).isAtLeast(profile.bufferForPlaybackAfterRebufferMs)
            assertThat(profile.maxBufferMs).isAtLeast(profile.minBufferMs)
        }
    }
}
