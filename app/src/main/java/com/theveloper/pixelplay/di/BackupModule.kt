package com.theveloper.pixelplay.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.backup.format.BackupFormatDetector
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.module.ArtistImagesModuleHandler
import com.theveloper.pixelplay.data.backup.module.BackupModuleHandler
import com.theveloper.pixelplay.data.backup.module.EngagementStatsModuleHandler
import com.theveloper.pixelplay.data.backup.module.EqualizerModuleHandler
import com.theveloper.pixelplay.data.backup.module.FavoritesModuleHandler
import com.theveloper.pixelplay.data.backup.module.GlobalSettingsModuleHandler
import com.theveloper.pixelplay.data.backup.module.LyricsModuleHandler
import com.theveloper.pixelplay.data.backup.module.PlaybackHistoryModuleHandler
import com.theveloper.pixelplay.data.backup.module.PlaylistsModuleHandler
import com.theveloper.pixelplay.data.backup.module.QuickFillModuleHandler
import com.theveloper.pixelplay.data.backup.module.SearchHistoryModuleHandler
import com.theveloper.pixelplay.data.backup.module.TransitionsModuleHandler
import com.theveloper.pixelplay.data.backup.module.AiUsageBackupHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    @BackupGson
    fun provideBackupGson(): Gson {
        return GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create()
    }

    @Provides
    @Singleton
    fun provideBackupFormatDetector(): BackupFormatDetector {
        return BackupFormatDetector()
    }

    @Provides
    @Singleton
    fun provideModuleHandlers(
        playlistsHandler: PlaylistsModuleHandler,
        globalSettingsHandler: GlobalSettingsModuleHandler,
        favoritesHandler: FavoritesModuleHandler,
        lyricsHandler: LyricsModuleHandler,
        searchHistoryHandler: SearchHistoryModuleHandler,
        transitionsHandler: TransitionsModuleHandler,
        engagementStatsHandler: EngagementStatsModuleHandler,
        playbackHistoryHandler: PlaybackHistoryModuleHandler,
        quickFillHandler: QuickFillModuleHandler,
        artistImagesHandler: ArtistImagesModuleHandler,
        equalizerHandler: EqualizerModuleHandler,
        aiUsageHandler: AiUsageBackupHandler
    ): Map<BackupSection, BackupModuleHandler> {
        return mapOf(
            BackupSection.PLAYLISTS to playlistsHandler,
            BackupSection.GLOBAL_SETTINGS to globalSettingsHandler,
            BackupSection.FAVORITES to favoritesHandler,
            BackupSection.LYRICS to lyricsHandler,
            BackupSection.SEARCH_HISTORY to searchHistoryHandler,
            BackupSection.TRANSITIONS to transitionsHandler,
            BackupSection.ENGAGEMENT_STATS to engagementStatsHandler,
            BackupSection.PLAYBACK_HISTORY to playbackHistoryHandler,
            BackupSection.QUICK_FILL to quickFillHandler,
            BackupSection.ARTIST_IMAGES to artistImagesHandler,
            BackupSection.EQUALIZER to equalizerHandler,
            BackupSection.AI_USAGE_LOGS to aiUsageHandler
        )
    }
}
