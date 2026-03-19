package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity
import com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity
import com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Bottom sheet that lets the user choose between streaming providers
 * (Telegram, Google Drive, Netease Cloud Music).
 *
 * For Netease: if already logged in, navigates to dashboard.
 * If not logged in, launches WebView login activity.
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
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
) {
    val context = LocalContext.current
    val isDark = com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme.current

    // Brand colors for each provider
    val telegramContainerColor = if (isDark) Color(0xFF1B3A4B) else Color(0xFFD6EEFB)
    val telegramContentColor = if (isDark) Color(0xFF59B3E8) else Color(0xFF1B6FA0)

    val googleDriveContainerColor = if (isDark) Color(0xFF2A3A2A) else Color(0xFFE0F0D8)
    val googleDriveContentColor = if (isDark) Color(0xFF7BC67B) else Color(0xFF2E7D32)

    val subsonicContainerColor = if (isDark) Color(0xFF3B2A1A) else Color(0xFFFDE8D0)
    val subsonicContentColor = if (isDark) Color(0xFFE8A54B) else Color(0xFFB06A1A)

    val neteaseContainerColor = if (isDark) Color(0xFF3B1A1A) else Color(0xFFFDD8D8)
    val neteaseContentColor = if (isDark) Color(0xFFE85959) else Color(0xFFBF1B1B)

    val qqMusicContainerColor = if (isDark) Color(0xFF1A3B2A) else Color(0xFFD0F5E0)
    val qqMusicContentColor = if (isDark) Color(0xFF4BCB7B) else Color(0xFF1A8A40)

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    val neteaseCardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 20.dp, cornerRadiusBL = 20.dp,
        cornerRadiusTR = 6.dp, cornerRadiusBR = 6.dp, // 中间一侧圆角变小
        smoothnessAsPercentTL = 60, smoothnessAsPercentBL = 60,
        smoothnessAsPercentTR = 10, smoothnessAsPercentBR = 10
    )

    val qqCardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusBR = 20.dp,
        cornerRadiusTL = 6.dp, cornerRadiusBL = 6.dp, // 中间一侧圆角变小
        smoothnessAsPercentTR = 60, smoothnessAsPercentBR = 60,
        smoothnessAsPercentTL = 10, smoothnessAsPercentBL = 10
    )

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cloud Streaming",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Stream music from your cloud accounts",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Telegram Provider
            ProviderCard(
                iconPainter = painterResource(R.drawable.telegram),
                icon = Icons.Rounded.Cloud,
                title = "Telegram",
                subtitle = "Stream from channels & chats",
                containerColor = telegramContainerColor,
                contentColor = telegramContentColor,
                iconColor = telegramContentColor,
                shape = cardShape,
                onClick = {
                    context.startActivity(Intent(context, TelegramLoginActivity::class.java))
                    onDismissRequest()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Google Drive Provider (coming soon)
            ProviderCard(
                icon = Icons.Rounded.CloudQueue,
                iconPainter = painterResource(R.drawable.rounded_drive_export_24),
                title = "Google Drive",
                subtitle = "Coming soon",
                containerColor = googleDriveContainerColor,
                contentColor = googleDriveContentColor,
                iconColor = googleDriveContentColor,
                shape = cardShape,
                enabled = false,
                onClick = { }
            )

            Spacer(Modifier.height(12.dp))

            // Subsonic Provider
            ProviderCard(
                icon = null,
                iconPainter = painterResource(R.drawable.ic_navidrome_md3),
                title = "Subsonic",
                subtitle = if (isNavidromeLoggedIn)
                    "✓ Connected (Navidrome/Airsonic)"
                else
                    "Connect Navidrome & others",
                containerColor = subsonicContainerColor,
                contentColor = subsonicContentColor,
                iconColor = subsonicContentColor,
                shape = cardShape,
                onClick = {
                    if (isNavidromeLoggedIn) {
                        onNavigateToNavidromeDashboard()
                    } else {
                        context.startActivity(Intent(context, NavidromeLoginActivity::class.java))
                    }
                    onDismissRequest()
                }
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Netease Cloud Music Provider
                ProviderCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.MusicNote,
                    iconPainter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                    title = "Netease",
                    subtitle = if (isNeteaseLoggedIn)
                        "✓ Connected"
                    else
                        "Sign in",
                    containerColor = neteaseContainerColor,
                    contentColor = neteaseContentColor,
                    iconColor = neteaseContentColor,
                    shape = neteaseCardShape,
                    onClick = {
                        if (isNeteaseLoggedIn) {
                            onNavigateToNeteaseDashboard()
                        } else {
                            context.startActivity(Intent(context, NeteaseLoginActivity::class.java))
                        }
                        onDismissRequest()
                    }
                )

                // QQ Music Provider
                 ProviderCard(
                    modifier = Modifier.weight(1f),
                    iconPainter = painterResource(R.drawable.qq_music),
                    title = "QQ",
                    subtitle = if (isQqMusicLoggedIn)
                        "✓ Connected"
                    else
                        "Sign in",
                    containerColor = qqMusicContainerColor,
                    contentColor = qqMusicContentColor,
                    iconColor = qqMusicContentColor,
                    shape = qqCardShape,
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

@Composable
private fun ProviderCard(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    customIconContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    shape: AbsoluteSmoothCornerShape,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .clip(shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customIconContent != null) {
                customIconContent()
            } else if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = iconColor
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    fontFamily = GoogleSansRounded,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontFamily = GoogleSansRounded,
                    lineHeight = 14.sp,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
