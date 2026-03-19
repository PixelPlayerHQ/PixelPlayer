package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    // Use dagger.Lazy to defer construction (and TDLib native library loading) off the main thread.
    @Inject
    lateinit var telegramStreamProxy: dagger.Lazy<com.theveloper.pixelplay.data.telegram.TelegramStreamProxy>

    @Inject
    lateinit var neteaseStreamProxy: com.theveloper.pixelplay.data.netease.NeteaseStreamProxy

    @Inject
    lateinit var qqMusicStreamProxy: com.theveloper.pixelplay.data.qqmusic.QqMusicStreamProxy

    @Inject
    lateinit var navidromeStreamProxy: com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy

    @Inject
    lateinit var telegramCacheManager: dagger.Lazy<com.theveloper.pixelplay.data.telegram.TelegramCacheManager>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.TelegramCoilFetcher.Factory>

    @Inject
    lateinit var navidromeCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.NavidromeCoilFetcher.Factory>

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
            Timber.plant(ReleaseTree())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelPlayer Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Start Netease proxy immediately (no heavy native deps)
        neteaseStreamProxy.start()

        // Start QQ Music proxy immediately (no heavy native deps)
        qqMusicStreamProxy.start()

        // Start Navidrome proxy immediately (no heavy native deps)
        navidromeStreamProxy.start()

        // Start Telegram proxy and schedule cache cleanup on IO thread to avoid blocking
        // Application.onCreate() with TDLib native library loading (System.loadLibrary("tdjni")).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // First .get() call constructs TelegramStreamProxy (and transitively TelegramClientManager),
            // loading the tdjni native library here on the IO thread instead of the main thread.
            telegramStreamProxy.get().start()

            try {
                // Wait a bit for TDLib to initialize before cleaning up
                kotlinx.coroutines.delay(5000)
                Timber.d("Performing startup Telegram cache cleanup...")
                telegramCacheManager.get().clearTdLibCache()
                telegramCacheManager.get().trimEmbeddedArtCache()
            } catch (e: Exception) {
                Timber.e(e, "Error during startup cache cleanup")
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(telegramCoilFetcherFactory.get())
                add(navidromeCoilFetcherFactory.get())
            }
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            MediaMetadataRetrieverPool.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
