package com.theveloper.pixelplay.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.ambient.AmbientModeSupport
import com.theveloper.pixelplay.presentation.theme.WearPixelPlayTheme
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    companion object {
        @Volatile
        var isForeground: Boolean = false
            private set
    }

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val ambientCallback = object : AmbientModeSupport.AmbientCallback() {}

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = ambientCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ambientController = AmbientModeSupport.attach(this).also {
            it.setAutoResumeEnabled(true)
        }
        setContent {
            val playerViewModel: WearPlayerViewModel = hiltViewModel()
            val albumArt by playerViewModel.albumArt.collectAsState()
            val paletteSeedArgb by playerViewModel.paletteSeedArgb.collectAsState()
            val themePalette by playerViewModel.themePalette.collectAsState()

            WearPixelPlayTheme(
                albumArt = albumArt,
                seedColorArgb = paletteSeedArgb,
                themePalette = themePalette,
            ) {
                WearNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isForeground = true
    }

    override fun onStop() {
        isForeground = false
        super.onStop()
    }
}
