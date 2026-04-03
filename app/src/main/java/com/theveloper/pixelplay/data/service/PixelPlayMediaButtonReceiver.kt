package com.theveloper.pixelplay.data.service

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.util.UnstableApi
import timber.log.Timber

class PixelPlayMediaButtonReceiver : MediaButtonReceiver() {

    @OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
            super.onReceive(context, intent)
            return
        }

        MusicService.markPendingMediaButtonForegroundStart()
        try {
            super.onReceive(context, intent)
        } catch (throwable: Throwable) {
            MusicService.unmarkPendingMediaButtonForegroundStart()
            Timber.tag(TAG).w(throwable, "Media button dispatch failed before MusicService start")
            throw throwable
        }
    }

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }
}
