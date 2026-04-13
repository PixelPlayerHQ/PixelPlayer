package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.content.ComponentCallbacks2
import android.os.Trace
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.clearExtractedColorCache
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ThemeStateHolder @Inject constructor(
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val themePreferencesRepository: ThemePreferencesRepository
) {

    private var scope: CoroutineScope? = null
    @Volatile
    private var currentPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default
    @Volatile
    private var currentPaletteAccuracy: Int = AlbumArtColorAccuracy.DEFAULT

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()
    private val _currentAlbumArtUri = MutableStateFlow<String?>(null)
    val currentAlbumArtUri: StateFlow<String?> = _currentAlbumArtUri.asStateFlow()

    private val _lavaLampColors = MutableStateFlow<ImmutableList<Color>>(persistentListOf())
    val lavaLampColors: StateFlow<ImmutableList<Color>> = _lavaLampColors.asStateFlow()

    private val playerThemePreference = themePreferencesRepository.playerThemePreferenceFlow

    private val _activePlayerColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = _activePlayerColorSchemePair.asStateFlow()

    fun initialize(scope: CoroutineScope) {
        this.scope = scope

        // Drive activePlayerColorSchemePair from the proper lifecycle-scoped coroutine
        // instead of the orphaned placeholder CoroutineScope used during field initialisation.
        scope.launch {
            combine(playerThemePreference, _currentAlbumArtColorSchemePair) { playerPref, albumScheme ->
                when (playerPref) {
                    com.theveloper.pixelplay.data.preferences.ThemePreference.ALBUM_ART -> albumScheme
                    else -> null
                }
            }.collect { _activePlayerColorSchemePair.value = it }
        }

        scope.launch {
            combine(
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow
            ) { style, accuracy -> style to accuracy }
                .collect { (style, accuracy) ->
                    val paletteChanged =
                        currentPaletteStyle != style || currentPaletteAccuracy != accuracy
                    currentPaletteStyle = style
                    currentPaletteAccuracy = accuracy

                    if (!paletteChanged) return@collect

                    val uri = _currentAlbumArtUri.value ?: return@collect
                    val refreshedScheme = colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = uri,
                        paletteStyle = style,
                        colorAccuracyLevel = accuracy
                    )
                    _currentAlbumArtColorSchemePair.value = refreshedScheme
                    individualAlbumColorSchemes[uri]?.value = refreshedScheme
                }
        }

        scope.launch {
            activePlayerColorSchemePair.collect { schemePair ->
                 updateLavaLampColors(schemePair)
            }
        }
    }

    suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, currentSongUriString: String?, isPreload: Boolean = false) {
        Trace.beginSection("ThemeStateHolder.extractAndGenerateColorScheme")
        try {
            if (albumArtUriAsUri == null) {
                if (!isPreload && currentSongUriString == null) {
                    _currentAlbumArtColorSchemePair.value = null
                    _currentAlbumArtUri.value = null
                }
                return
            }

            val uriString = albumArtUriAsUri.toString()
            // Use the optimized ColorSchemeProcessor with LRU cache
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(
                albumArtUri = uriString,
                paletteStyle = currentPaletteStyle,
                colorAccuracyLevel = currentPaletteAccuracy
            )

            if (!isPreload && currentSongUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                _currentAlbumArtUri.value = uriString
            }
        } catch (e: Exception) {
            if (!isPreload && albumArtUriAsUri != null && currentSongUriString == albumArtUriAsUri.toString()) {
                _currentAlbumArtColorSchemePair.value = null
                _currentAlbumArtUri.value = null
            }
        } finally {
            Trace.endSection()
        }
    }

    private fun updateLavaLampColors(schemePair: ColorSchemePair?) {
        val schemeForLava = schemePair?.dark ?: DarkColorScheme
        _lavaLampColors.update {
            listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList()
        }
    }

    // LRU Cache for individual album schemes
    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        32, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 30
        }
    }

    private val pendingAlbumColorSchemeRequests = ConcurrentHashMap.newKeySet<String>()

    private fun requestAlbumColorSchemeGeneration(
        uriString: String,
        targetFlow: MutableStateFlow<ColorSchemePair?>
    ) {
        if (!pendingAlbumColorSchemeRequests.add(uriString)) return

        scope?.launch(Dispatchers.IO) {
            try {
                val scheme = colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uriString,
                    paletteStyle = currentPaletteStyle,
                    colorAccuracyLevel = currentPaletteAccuracy
                )
                targetFlow.value = scheme
            } catch (_: Exception) {
                // Ignore or log
            } finally {
                pendingAlbumColorSchemeRequests.remove(uriString)
            }
        }
    }

    fun getAlbumColorSchemeFlow(
        uriString: String,
        eager: Boolean = true
    ): StateFlow<ColorSchemePair?> {
        val existingFlow = individualAlbumColorSchemes[uriString]
        if (existingFlow != null) {
            if (eager && existingFlow.value == null) {
                requestAlbumColorSchemeGeneration(uriString, existingFlow)
            }
            return existingFlow.asStateFlow()
        }

        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        if (eager) {
            requestAlbumColorSchemeGeneration(uriString, newFlow)
        }

        return newFlow.asStateFlow()
    }

    fun ensureAlbumColorScheme(uriString: String) {
        val targetFlow = individualAlbumColorSchemes[uriString]
            ?: MutableStateFlow<ColorSchemePair?>(null).also { individualAlbumColorSchemes[uriString] = it }

        if (targetFlow.value != null) return
        requestAlbumColorSchemeGeneration(uriString, targetFlow)
    }
    
    suspend fun getOrGenerateColorScheme(uriString: String): ColorSchemePair? {
         return colorSchemeProcessor.getOrGenerateColorScheme(
             albumArtUri = uriString,
             paletteStyle = currentPaletteStyle,
             colorAccuracyLevel = currentPaletteAccuracy
         )
    }

    suspend fun forceRegenerateColorScheme(
        uriString: String?,
        regenerateAllStyles: Boolean = false
    ) {
         if (uriString == null) {
             _currentAlbumArtColorSchemePair.value = null
             _currentAlbumArtUri.value = null
             return
         }

         android.util.Log.d("ThemeStateHolder", "forceRegenerateColorScheme called for: $uriString")
         android.util.Log.d("ThemeStateHolder", "Current tracked global URI: ${_currentAlbumArtUri.value}")
         
         colorSchemeProcessor.invalidateScheme(uriString)

         val newScheme = if (regenerateAllStyles) {
             var selectedStyleScheme: ColorSchemePair? = null
             AlbumArtPaletteStyle.entries.forEach { style ->
                 val generated = colorSchemeProcessor.getOrGenerateColorScheme(
                     albumArtUri = uriString,
                     paletteStyle = style,
                     colorAccuracyLevel = currentPaletteAccuracy,
                     forceRefresh = true
                 )
                 if (style == currentPaletteStyle) {
                     selectedStyleScheme = generated
                 }
             }
             selectedStyleScheme
         } else {
             colorSchemeProcessor.getOrGenerateColorScheme(
                 albumArtUri = uriString,
                 paletteStyle = currentPaletteStyle,
                 colorAccuracyLevel = currentPaletteAccuracy,
                 forceRefresh = true
             )
         }

         // Iterate if there is an active flow for this URI and update it
         val activeFlow = individualAlbumColorSchemes[uriString]
         if (activeFlow != null) {
             activeFlow.value = newScheme
         }
         
         // Also update the main current album art scheme if it matches the one we are tracking
         // We use equality check. If they are the same string object or equal content.
         if (_currentAlbumArtUri.value == uriString) {
             android.util.Log.d("ThemeStateHolder", "Updating global color scheme flow directly.")
             _currentAlbumArtColorSchemePair.value = newScheme
         } else {
             android.util.Log.d("ThemeStateHolder", "Global URI did not match. Skipping global update.")
         }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        colorSchemeProcessor.clearMemoryCache()
        clearExtractedColorCache()

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            individualAlbumColorSchemes.clear()
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            pendingAlbumColorSchemeRequests.clear()
        }
    }

}
