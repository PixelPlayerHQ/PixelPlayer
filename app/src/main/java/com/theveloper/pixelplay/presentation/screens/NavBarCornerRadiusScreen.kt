package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.iconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.resolveNavBarSurfaceHeight

const val DEFAULT_NAV_BAR_CORNER_RADIUS = 28f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavBarCornerRadiusScreen(
    navController: NavController, settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isFullWidth = uiState.navBarStyle == NavBarStyle.FULL_WIDTH
    
    NavBarCornerRadiusContent(
        initialRadius = uiState.navBarCornerRadius.toFloat(),
        onRadiusChange = { settingsViewModel.setNavBarCornerRadius(it) },
        onDone = { navController.popBackStack() },
        onBack = { navController.popBackStack() },
        isFullWidth = isFullWidth,
        isCompactMode = uiState.navBarCompactMode
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavBarCornerRadiusContent(
    initialRadius: Float,
    onRadiusChange: (Int) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    isFullWidth: Boolean,
    isCompactMode: Boolean = false
) {
    var sliderValue by remember { mutableFloatStateOf(initialRadius) }
    var hasBeenAdjusted by remember { mutableStateOf(sliderValue != DEFAULT_NAV_BAR_CORNER_RADIUS) }

    val haptic = LocalHapticFeedback.current

    // Sync if initial value changes externally (though unlikely in this flow, good practice)
    LaunchedEffect(initialRadius) {
        sliderValue = initialRadius
    }
    
    // Update hasBeenAdjusted when sliderValue changes relative to default
    LaunchedEffect(sliderValue) {
        hasBeenAdjusted = sliderValue != DEFAULT_NAV_BAR_CORNER_RADIUS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    FilledIconButton(
                        modifier =
                            Modifier
                                .padding(start = 12.dp, top = 4.dp), 
                        onClick = onBack,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                    ) {
                        Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onRadiusChange(sliderValue.toInt())
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
                .padding(bottom = if (isFullWidth) 0.dp else paddingValues.calculateBottomPadding())
        ) {
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Adjust Corner Radius",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Match the navbar shape's corners with your device's physical corners for a seamless look.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Controls Area
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                // Controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Corner Radius",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (hasBeenAdjusted) {
                                FilledTonalButton(
                                    onClick = {
                                        sliderValue = DEFAULT_NAV_BAR_CORNER_RADIUS
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = "Reset",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RoundedCorner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Slider
                            Box(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        if (sliderValue != it) {
                                            if (it.toInt() != sliderValue.toInt()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            sliderValue = it
                                        }
                                    },
                                    valueRange = 0f..60f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            trackCornerSize = 14.dp,
                                            modifier = Modifier.height(36.dp)
                                        )
                                    }
                                )
                            }
                            
                            Text(
                                modifier = Modifier.width(46.dp),
                                text = "${sliderValue.toInt()} dp",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Placeholder
                val bottomPadding = paddingValues.calculateBottomPadding()
                val previewHeight = resolveNavBarSurfaceHeight(
                    navBarStyle = if (isFullWidth) NavBarStyle.FULL_WIDTH else NavBarStyle.DEFAULT,
                    systemNavBarInset = bottomPadding,
                    compactMode = isCompactMode
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .padding(horizontal = if (isFullWidth) 0.dp else bottomPadding), // Full Width: No horizontal padding
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = if (isFullWidth) {
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = sliderValue.dp, // Customize TOP
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = sliderValue.dp, // Customize TOP
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp, // Fixed BOTTOM
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp, // Fixed BOTTOM
                            smoothnessAsPercentBR = 60
                        )
                    } else {
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 10.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusTR = 10.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = sliderValue.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = sliderValue.dp,
                            smoothnessAsPercentTR = 60
                        )
                    }
                ) {

                }
            }
        }
    }
}
