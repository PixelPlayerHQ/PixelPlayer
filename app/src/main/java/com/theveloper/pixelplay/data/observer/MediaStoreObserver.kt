package com.theveloper.pixelplay.data.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())), DefaultLifecycleObserver {

    private val _mediaStoreChanges = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val mediaStoreChanges: SharedFlow<Unit> = _mediaStoreChanges.asSharedFlow()

    @Volatile
    private var isRegistered: Boolean = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun register() {
        if (isRegistered) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        context.contentResolver.unregisterContentObserver(this)
        isRegistered = false
    }

    override fun onStart(owner: LifecycleOwner) {
        register()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregister()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unregister()
        owner.lifecycle.removeObserver(this)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        _mediaStoreChanges.tryEmit(Unit)
    }

    fun forceRescan() {
        _mediaStoreChanges.tryEmit(Unit)
    }
}
