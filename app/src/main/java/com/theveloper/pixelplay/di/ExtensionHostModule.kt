package com.theveloper.pixelplay.di

import android.content.Context
import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtensionHostModule {

    @Provides
    @Singleton
    fun provideExtensionHost(@ApplicationContext context: Context): PixelPlayExtensionHost =
        PixelPlayExtensionHost(context as android.app.Application)

    @Provides
    @Singleton
    fun provideExtensionLoader(
        host: PixelPlayExtensionHost,
        webViewManager: com.theveloper.pixelplay.extensions.webview.ExtensionWebViewManager
    ): ExtensionLoader =
        ExtensionLoader(
            host = host,
            webViewClientFactory = { metadata -> 
                object : dev.brahmkshatriya.echo.common.helpers.WebViewClient {
                    override suspend fun await(
                        showWebView: Boolean, reason: String, request: dev.brahmkshatriya.echo.common.helpers.WebViewRequest<String>
                    ): Result<String?> {
                        return webViewManager.await(request, reason)
                    }
                }
            }
        )

}

