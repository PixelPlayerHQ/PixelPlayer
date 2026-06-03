package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity
import com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.Extension

@Composable
fun SourceSelectionBottomSheet(
    musicExtensions: List<MusicExtension>,
    currentMusicExtension: MusicExtension?,
    onMusicExtensionSelected: (MusicExtension) -> Unit,
    lyricsExtensions: List<Extension<*>>,
    onNavigateToStore: () -> Unit,
    isNeteaseLoggedIn: Boolean = false,
    onNeteaseClick: () -> Unit = {},
    isQqMusicLoggedIn: Boolean = false,
    onQqMusicClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val itemShape = RoundedCornerShape(8.dp)
    val containerShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Music Source",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Select your active online content provider",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = containerShape,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .clip(containerShape),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Extensions
                musicExtensions.forEach { extension ->
                    val isSelected = extension == currentMusicExtension
                    SourceRow(
                        title = extension.metadata.name,
                        subtitle = "v${extension.metadata.version}",
                        iconVector = Icons.Rounded.MusicNote,
                        iconTint = MaterialTheme.colorScheme.primary,
                        isSelected = isSelected,
                        onClick = { onMusicExtensionSelected(extension) },
                        shape = itemShape
                    )
                }

                // Cloud Providers
                SourceRow(
                    title = "Telegram",
                    subtitle = "Channels & Chats",
                    iconPainter = painterResource(R.drawable.telegram),
                    iconTint = Color(0xFF2AABEE),
                    onClick = { 
                        context.startActivity(Intent(context, TelegramLoginActivity::class.java))
                    },
                    shape = itemShape
                )

                SourceRow(
                    title = "Netease Music",
                    subtitle = if (isNeteaseLoggedIn) "Connected" else "Sign in to stream",
                    iconPainter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                    iconTint = Color(0xFFE85959),
                    isConnected = isNeteaseLoggedIn,
                    onClick = onNeteaseClick,
                    shape = itemShape
                )

                SourceRow(
                    title = "QQ Music",
                    subtitle = if (isQqMusicLoggedIn) "Connected" else "Sign in to stream",
                    iconPainter = painterResource(R.drawable.qq_music),
                    iconTint = Color(0xFF31C27C),
                    isConnected = isQqMusicLoggedIn,
                    onClick = onQqMusicClick,
                    shape = itemShape
                )

                // Management shortcut
                SourceRow(
                    title = "Manage Extensions",
                    subtitle = "Add or update sources",
                    iconVector = Icons.Rounded.Extension,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onNavigateToStore,
                    shape = itemShape
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    iconVector: ImageVector? = null,
    iconPainter: Painter? = null,
    iconTint: Color,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    isConnected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = when {
        isSelected || isConnected -> MaterialTheme.colorScheme.surfaceContainerHighest
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val subtitleColor = when {
        isSelected || isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = containerColor
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = iconTint
                        )
                    } else if (iconPainter != null) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = iconTint
                        )
                    }
                }
            },
            trailingContent = {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceBright
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        )
    }
}
