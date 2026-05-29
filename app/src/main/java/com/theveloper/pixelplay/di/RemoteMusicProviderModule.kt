package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.cloud.JellyfinRepository
import com.theveloper.pixelplay.data.cloud.NavidromeRepository
import com.theveloper.pixelplay.data.cloud.NeteaseRepository
import com.theveloper.pixelplay.data.cloud.RemoteMusicProvider
import com.theveloper.pixelplay.data.cloud.QqMusicRepository
import com.theveloper.pixelplay.data.cloud.TelegramRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Contributes each remote music repository into the [RemoteMusicProvider] set
 * consumed by `RemoteMusicProviderRegistry`.
 *
 * Each repository keeps its existing `@Singleton @Inject` constructor, so these
 * bindings reuse the same singleton instances — they only expose them under the
 * shared interface. Adding a new provider is a single `@Binds @IntoSet` line.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteMusicProviderModule {

    @Binds
    @IntoSet
    abstract fun bindJellyfinProvider(impl: JellyfinRepository): RemoteMusicProvider

    @Binds
    @IntoSet
    abstract fun bindNavidromeProvider(impl: NavidromeRepository): RemoteMusicProvider

    @Binds
    @IntoSet
    abstract fun bindNeteaseProvider(impl: NeteaseRepository): RemoteMusicProvider

    @Binds
    @IntoSet
    abstract fun bindQqMusicProvider(impl: QqMusicRepository): RemoteMusicProvider

    @Binds
    @IntoSet
    abstract fun bindTelegramProvider(impl: TelegramRepository): RemoteMusicProvider
}
