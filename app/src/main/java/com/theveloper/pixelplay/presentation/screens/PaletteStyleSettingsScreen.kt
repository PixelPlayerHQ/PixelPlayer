package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteStyleSettingsScreen(
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val albumSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()

    val baseScheme = MaterialTheme.colorScheme
    val albumScheme = remember(albumSchemePair, isDarkTheme, baseScheme) {
        albumSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light } ?: baseScheme
    }

    val currentSong = stablePlayerState.currentSong
    val isMiniPlayerVisible = currentSong != null
    val seedKey = currentSong?.albumArtUriString ?: currentSong?.id?.toString() ?: "system"
    var seedColor by remember(seedKey) { mutableStateOf<Color?>(null) }

    LaunchedEffect(seedKey, albumSchemePair, baseScheme.primary) {
        if (currentSong == null) {
            seedColor = baseScheme.primary
        } else if (seedColor == null && albumSchemePair != null) {
            seedColor = albumScheme.primary
        }
    }

    val resolvedSeed = seedColor ?: if (currentSong != null) albumScheme.primary else baseScheme.primary

    val styleSchemes = remember(resolvedSeed, isDarkTheme) {
        AlbumArtPaletteStyle.entries.associateWith { style ->
            val pair = generateColorSchemeFromSeed(
                seedColor = resolvedSeed,
                paletteStyle = style
            )
            if (isDarkTheme) pair.dark else pair.light
        }
    }

    var pendingStyle by remember { mutableStateOf(uiState.albumArtPaletteStyle) }
    var pendingAccuracy by remember { mutableStateOf(uiState.albumArtColorAccuracy) }
    LaunchedEffect(uiState.albumArtPaletteStyle) {
        pendingStyle = uiState.albumArtPaletteStyle
    }
    LaunchedEffect(uiState.albumArtColorAccuracy) {
        pendingAccuracy = uiState.albumArtColorAccuracy
    }

    var previewSchemePair by remember(currentSong?.albumArtUriString) {
        mutableStateOf<ColorSchemePair?>(albumSchemePair)
    }
    LaunchedEffect(
        currentSong?.albumArtUriString,
        pendingStyle,
        pendingAccuracy,
        uiState.albumArtPaletteStyle,
        uiState.albumArtColorAccuracy,
        albumSchemePair
    ) {
        val artUriString = currentSong?.albumArtUriString
        if (artUriString.isNullOrBlank()) {
            previewSchemePair = albumSchemePair
            return@LaunchedEffect
        }

        val matchesApplied =
            pendingStyle == uiState.albumArtPaletteStyle &&
                pendingAccuracy == uiState.albumArtColorAccuracy
        previewSchemePair = if (matchesApplied && albumSchemePair != null) {
            albumSchemePair
        } else {
            settingsViewModel.getAlbumArtPalettePreview(
                uriString = artUriString,
                style = pendingStyle,
                accuracyLevel = pendingAccuracy
            ) ?: albumSchemePair
        }
    }

    val hasPendingChanges =
        pendingStyle != uiState.albumArtPaletteStyle ||
            pendingAccuracy != uiState.albumArtColorAccuracy
    val previewScheme = previewSchemePair?.let { pair ->
        if (isDarkTheme) pair.dark else pair.light
    } ?: styleSchemes[pendingStyle] ?: albumScheme
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = bottomInset + if (isMiniPlayerVisible) MiniPlayerHeight + 12.dp else 16.dp

    Scaffold(
        containerColor = previewScheme.background,
        topBar = {
            PaletteStyleHeader(
                scheme = previewScheme,
                onBackClick = onBackClick,
                onApplyClick = {
                    settingsViewModel.setAlbumArtPaletteSettings(
                        style = pendingStyle,
                        accuracyLevel = pendingAccuracy
                    )
                },
                applyEnabled = hasPendingChanges
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniFullPlayerSkeletonPreview(
                scheme = previewScheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )

            Surface(
                color = previewScheme.surfaceContainer,
                shape = RoundedCornerShape(34.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Palette style",
                        style = MaterialTheme.typography.titleLarge,
                        color = previewScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose the album colors for the player UI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = previewScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AlbumArtPaletteStyle.entries.forEach { style ->
                            PaletteSwatchSquare(
                                scheme = styleSchemes[style] ?: previewScheme,
                                selected = style == pendingStyle,
                                onClick = { pendingStyle = style },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pendingStyle.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = previewScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = previewScheme.tertiaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(8.dp)
                                .padding(start = 4.dp),
                            text = pendingStyle.description(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = previewScheme.onTertiaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    PaletteAccuracySlider(
                        scheme = previewScheme,
                        value = pendingAccuracy,
                        hapticsEnabled = uiState.hapticsEnabled,
                        onValueChange = { pendingAccuracy = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(contentBottomPadding))
        }
    }
}

@Composable
private fun PaletteStyleHeader(
    scheme: ColorScheme,
    onBackClick: () -> Unit,
    onApplyClick: () -> Unit,
    applyEnabled: Boolean
) {
    Surface(color = scheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = scheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close"
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Colors",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = onApplyClick,
                enabled = applyEnabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    disabledContainerColor = scheme.surfaceContainerHigh,
                    disabledContentColor = scheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun MiniFullPlayerSkeletonPreview(
    scheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val sizeFactor = 0.7f
    fun scaled(dp: Dp): Dp = (dp.value * sizeFactor).dp
    val cardWidthFraction = 0.72f * sizeFactor

    val placeholderColor = scheme.onPrimaryContainer.copy(alpha = 0.1f)
    val placeholderOnColor = scheme.onPrimaryContainer.copy(alpha = 0.2f)
    val chipColor = scheme.onPrimary.copy(alpha = 0.8f)
    val topBarButtonColor = scheme.onPrimary.copy(alpha = 0.7f)
    val inactiveTrackColor = scheme.onPrimaryContainer.copy(alpha = 0.2f)
    val toggleRowColor = scheme.surfaceContainerLowest.copy(alpha = 0.7f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            color = scheme.primaryContainer,
            shape = RoundedCornerShape(scaled(34.dp)),
            modifier = Modifier.fillMaxWidth(cardWidthFraction)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(scaled(14.dp)),
                verticalArrangement = Arrangement.spacedBy(scaled(10.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(scaled(42.dp))
                            .clip(CircleShape)
                            .background(topBarButtonColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(scaled(92.dp))
                            .height(scaled(42.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(topBarButtonColor)
                    )
                }

//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth(0.5f)
//                        .height(scaled(12.dp))
//                        .clip(RoundedCornerShape(scaled(6.dp)))
//                        .background(placeholderOnColor)
//                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(190.dp))
                        .clip(RoundedCornerShape(scaled(22.dp)))
                        .background(placeholderColor)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(scaled(8.dp)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .height(scaled(18.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .height(scaled(12.dp))
                                .clip(RoundedCornerShape(scaled(6.dp)))
                                .background(placeholderOnColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(scaled(12.dp)))
                    Box(
                        modifier = Modifier
                            .size(scaled(40.dp))
                            .clip(CircleShape)
                            .background(chipColor)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(6.dp))
                        .clip(RoundedCornerShape(scaled(99.dp)))
                        .background(inactiveTrackColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.12f)
                            .height(scaled(6.dp))
                            .clip(RoundedCornerShape(scaled(99.dp)))
                            .background(scheme.primary.copy(alpha = 0.35f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.onPrimary)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .height(scaled(50.dp))
                            .width(scaled(58.dp))
                            .clip(CircleShape)
                            .background(scheme.onPrimary)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(48.dp))
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaled(48.dp))
                            .clip(RoundedCornerShape(scaled(50.dp)))
                            .background(toggleRowColor)
                            .padding(
                                horizontal = scaled(6.dp),
                                vertical = scaled(6.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(scaled(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.secondary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(scaled(40.dp))
                                    .clip(RoundedCornerShape(scaled(18.dp)))
                                    .background(scheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val circleRadius = maxWidth / 2
        val innerCorner by animateDpAsState(
            targetValue = if (selected) 12.dp else circleRadius,
            label = "paletteInnerCorner"
        )
        val outerCorner by animateDpAsState(
            targetValue = if (selected) 16.dp else circleRadius,
            label = "paletteOuterCorner"
        )
        val outlinePadding by animateDpAsState(
            targetValue = if (selected) 4.dp else 0.dp,
            label = "paletteOutlinePadding"
        )
        val borderWidth by animateDpAsState(
            targetValue = if (selected) 2.dp else 0.dp,
            label = "paletteBorderWidth"
        )

        Surface(
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(outerCorner),
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, scheme.primary) else null,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outlinePadding)
            ) {
                Surface(
                    color = scheme.surface,
                    shape = RoundedCornerShape(innerCorner),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.secondary)
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.tertiary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteAccuracySlider(
    scheme: ColorScheme,
    value: Int,
    hapticsEnabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val clampedValue = AlbumArtColorAccuracy.clamp(value)
    val hapticFeedback = LocalHapticFeedback.current
    var lastDispatchedValue by remember { mutableIntStateOf(clampedValue) }

    LaunchedEffect(clampedValue) {
        lastDispatchedValue = clampedValue
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Color accuracy",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "0 keeps the current tuning. Higher values stay closer to the album art's dominant hue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = scheme.primaryContainer,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = clampedValue.accuracySummary(),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Slider(
            value = clampedValue.toFloat(),
            onValueChange = {
                val nextValue = AlbumArtColorAccuracy.clamp(it.roundToInt())
                if (nextValue == lastDispatchedValue) return@Slider

                if (hapticsEnabled) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastDispatchedValue = nextValue
                onValueChange(nextValue)
            },
            valueRange = AlbumArtColorAccuracy.MIN.toFloat()..AlbumArtColorAccuracy.MAX.toFloat(),
            steps = AlbumArtColorAccuracy.STEPS,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.surfaceContainerHighest,
                activeTickColor = scheme.primary,
                inactiveTickColor = scheme.onSurfaceVariant.copy(alpha = 0.22f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Text(
                text = "More accurate",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

private fun AlbumArtPaletteStyle.description(): String {
    return when (this) {
        AlbumArtPaletteStyle.TONAL_SPOT -> "Balanced and calm."
        AlbumArtPaletteStyle.VIBRANT -> "High saturation accents."
        AlbumArtPaletteStyle.EXPRESSIVE -> "Bold hue shifts and contrast."
        AlbumArtPaletteStyle.FRUIT_SALAD -> "Playful rotated accents."
    }
}

private fun Int.accuracySummary(): String {
    return when (AlbumArtColorAccuracy.clamp(this)) {
        0 -> "0 • Current"
        in 1..3 -> "$this • Subtle"
        in 4..7 -> "$this • Balanced"
        else -> "$this • Precise"
    }
}
