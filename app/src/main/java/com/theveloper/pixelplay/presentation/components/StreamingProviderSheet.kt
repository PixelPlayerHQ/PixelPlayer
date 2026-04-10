package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.jellyfin.auth.JellyfinLoginActivity
import com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity
import com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity
import com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Bottom sheet that lets the user choose between streaming providers.
 * Uses Material 3 standard surface colors and compact ListItem-style rows
 * inside a single outlined card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingProviderSheet(
    onDismissRequest: () -> Unit,
    isNeteaseLoggedIn: Boolean = false,
    onNavigateToNeteaseDashboard: () -> Unit = {},
    isQqMusicLoggedIn: Boolean = false,
    onNavigateToQqMusicDashboard: () -> Unit = {},
    isNavidromeLoggedIn: Boolean = false,
    onNavigateToNavidromeDashboard: () -> Unit = {},
    isJellyfinLoggedIn: Boolean = false,
    onNavigateToJellyfinDashboard: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cloud Streaming",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Stream music from your cloud accounts",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Single grouped card for all providers
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Column {
                    // Telegram
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.telegram),
                        iconTint = Color(0xFF2AABEE),
                        title = "Telegram",
                        subtitle = "Stream from channels & chats",
                        onClick = {
                            context.startActivity(Intent(context, TelegramLoginActivity::class.java))
                            onDismissRequest()
                        }
                    )

                    ProviderDivider()

                    // Google Drive (coming soon)
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.rounded_drive_export_24),
                        iconTint = Color(0xFF4285F4),
                        title = "Google Drive",
                        subtitle = "Coming soon",
                        enabled = false,
                        onClick = { }
                    )

                    ProviderDivider()

                    // Subsonic / Navidrome
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_navidrome_md3),
                        iconTint = Color(0xFFE8A54B),
                        title = "Subsonic",
                        subtitle = if (isNavidromeLoggedIn) "Connected · Navidrome/Airsonic" else "Connect Navidrome & others",
                        isConnected = isNavidromeLoggedIn,
                        onClick = {
                            if (isNavidromeLoggedIn) {
                                onNavigateToNavidromeDashboard()
                            } else {
                                context.startActivity(Intent(context, NavidromeLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderDivider()

                    // Jellyfin
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_jellyfin),
                        iconTint = Color(0xFF00A4DC),
                        title = "Jellyfin",
                        subtitle = if (isJellyfinLoggedIn) "Connected" else "Connect your Jellyfin server",
                        isConnected = isJellyfinLoggedIn,
                        onClick = {
                            if (isJellyfinLoggedIn) {
                                onNavigateToJellyfinDashboard()
                            } else {
                                context.startActivity(Intent(context, JellyfinLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderDivider()

                    // Netease
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                        iconTint = Color(0xFFE85959),
                        title = "Netease Cloud Music",
                        subtitle = if (isNeteaseLoggedIn) "Connected" else "Sign in to stream",
                        isConnected = isNeteaseLoggedIn,
                        onClick = {
                            if (isNeteaseLoggedIn) {
                                onNavigateToNeteaseDashboard()
                            } else {
                                context.startActivity(Intent(context, NeteaseLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderDivider()

                    // QQ Music
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.qq_music),
                        iconTint = Color(0xFF31C27C),
                        title = "QQ Music",
                        subtitle = if (isQqMusicLoggedIn) "Connected" else "Sign in to stream",
                        isConnected = isQqMusicLoggedIn,
                        onClick = {
                            if (isQqMusicLoggedIn) {
                                onNavigateToQqMusicDashboard()
                            } else {
                                context.startActivity(Intent(context, QqMusicLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun ProviderRow(
    iconPainter: Painter,
    iconTint: Color,
    title: String,
    subtitle: String,
    isConnected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon in a tinted circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
