package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.download.DownloadBackendApi
import com.theveloper.pixelplay.data.download.GobackendDownloadBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {
    @Binds @Singleton
    abstract fun bindDownloadBackend(impl: GobackendDownloadBackend): DownloadBackendApi
}
