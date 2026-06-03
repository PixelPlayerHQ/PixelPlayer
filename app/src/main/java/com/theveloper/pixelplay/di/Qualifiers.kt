package com.theveloper.pixelplay.di

import javax.inject.Qualifier

/**
 * Custom Dagger/Hilt qualifiers used throughout the application.
 */

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupGson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExtensionHost

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeteaseGson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class QqMusicGson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NavidromeGson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JellyfinGson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleDriveGson
