package com.theveloper.pixelplay.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetaInfoBottomSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val issuesUrl = "https://github.com/theovilardo/PixelPlayer/issues"
    val reportUrl = "https://github.com/theovilardo/PixelPlayer/issues/new/choose"

    val fabCornerRadius = 18.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Beta 0.6.0",
                        fontFamily = GoogleSansRounded,
                        style = ExpTitleTypography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(horizontal = 8.dp),
                        animate = true,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        alpha = 0.95f,
                        strokeWidth = 4.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                }
            }

            item(key = "welcome") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    //tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "β",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Welcome to PixelPlayer 0.6.0-beta",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "This beta is now focused on stability, performance, and cross-device playback while shipping major new integrations.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "what-to-expect") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "What to expect",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Faster daily use: smoother startup, navigation, and player interactions across the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Broader device support: Android Auto, Wear OS upgrades, and stronger Cast reliability.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Expanded cloud ecosystem: Telegram playlists, NetEase sync, QQ Music, and Google Drive streaming updates.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Large reliability pass: queue/shuffle logic, background playback behavior, and many UI fixes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "report-issue") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Report an issue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Share steps to reproduce, expected result, actual result, and your device/OS details. A short screen recording is very helpful.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { launchUrl(context, issuesUrl) },
                                // Eliminamos height fija y usamos contentPadding
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = fabCornerRadius,
                                    cornerRadiusTL = fabCornerRadius,
                                    cornerRadiusBL = fabCornerRadius,
                                    cornerRadiusBR = fabCornerRadius,
                                    smoothnessAsPercentTR = 60,
                                    smoothnessAsPercentTL = 60,
                                    smoothnessAsPercentBL = 60,
                                    smoothnessAsPercentBR = 60
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.github),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Open GitHub issues")
                            }
                            FilledTonalButton(
                                onClick = { launchUrl(context, reportUrl) },
                                // Eliminamos height fija y usamos contentPadding
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = fabCornerRadius,
                                    cornerRadiusTL = fabCornerRadius,
                                    cornerRadiusBL = fabCornerRadius,
                                    cornerRadiusBR = fabCornerRadius,
                                    smoothnessAsPercentTR = 60,
                                    smoothnessAsPercentTL = 60,
                                    smoothnessAsPercentBL = 60,
                                    smoothnessAsPercentBR = 60
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Report a bug")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(38.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {

        }
    }
}

private fun launchUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Swallow the error; caller does not need to handle it here.
    }
}
