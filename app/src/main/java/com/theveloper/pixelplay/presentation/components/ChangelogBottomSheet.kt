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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

data class ChangelogSection(
    val title: String,
    val items: List<String>
)

// Data class for a single changelog version
data class ChangelogVersion(
    val version: String,
    val date: String,
    val sections: List<ChangelogSection>
)

// The changelog data
val changelog = listOf(
    ChangelogVersion(
        version = "0.6.0-beta",
        date = "2026-03-05",
        sections = listOf(
            ChangelogSection(
                title = "What's New",
                items = listOf(
                    "Android Auto support is now available for in-car playback.",
                    "Wear OS support is live, including better watch-to-phone playback controls.",
                    "Cloud integrations expanded with Telegram, NetEase, QQ Music, and Google Drive improvements.",
                    "Recently Played and persistent queue restoration keep your listening session ready.",
                    "Backup & Restore v3 and account management tools are now included.",
                    "Lyrics got smarter with manual search fallback and storage improvements."
                )
            ),
            ChangelogSection(
                title = "Improvements",
                items = listOf(
                    "Big performance pass across startup, library, queue, and player interactions.",
                    "Player, Cast, Lyrics, Artist, and Genre surfaces were redesigned for smoother use.",
                    "Navigation and search flows are more reliable, with safer route handling.",
                    "Audio playback compatibility improved for more devices and formats.",
                    "Multi-selection workflows were expanded across songs, albums, and playlists."
                )
            ),
            ChangelogSection(
                title = "Fixes",
                items = listOf(
                    "Queue and shuffle behavior is now more stable and predictable.",
                    "Several background playback and casting edge cases were fixed.",
                    "Sleep Timer, Files tab navigation, and album artist crash issues were fixed.",
                    "Widget loading and service stability were improved to reduce overheating/memory issues.",
                    "General bug fixes and UI polish across the app."
                )
            )
        )
    ),
    ChangelogVersion(
        version = "0.5.0-beta",
        date = "2026-01-14",
        sections = listOf(
            ChangelogSection(
                title = "Highlights",
                items = listOf(
                    "Material 3 Expressive UI Update",
                    "10-band Equalizer & Effects",
                    "New Library Sync Flow",
                    "AI Integration (Gemini Models)",
                    "M3U Playlist Import/Export",
                    "Deezer Artist Artwork Integration",
                    "Custom Playlist Covers"
                )
            ),
            ChangelogSection(
                title = "Improvements",
                items = listOf(
                    "Settings Architecture Refactor",
                    "Queue & Player Animations",
                    "Baseline Profiles & Performance",
                    "Better Lyrics System with Sync Offset"
                )
            ),
            ChangelogSection(
                title = "Fixes",
                items = listOf(
                    "Casting Stability Improvements",
                    "Player Sheet Stability",
                    "General Bug Fixes & Cleanup"
                )
            )
        )
    ),
    ChangelogVersion(
        version = "0.4.0-beta",
        date = "2025-12-15",
        sections = listOf(
            ChangelogSection(
                title = "Highlights",
                items = listOf(
                    "Major navigation redesign",
                    "New file explorer for choosing source directories",
                    "New Connectivity and casting functionalities",
                    "Seamless continuity between remote devices",
                    "Gapless transition between songs",
                    "Crossfade control",
                    "New Custom Transitions feature (only for playlists)",
                    "Keep playing after closed the app",
                    "UI Optimizations",
                    "Improved stats feature",
                    "Redesigned Queue control with more features",
                    "Improved different filetypes support for playing and metadata editing",
                    "Improved permission controller",
                    "Minor bug fixes"
                )
            )
        )
    ),
    ChangelogVersion(
        version = "0.3.0-beta",
        date = "2025-10-28",
        sections = listOf(
            ChangelogSection(
                title = "What's new",
                items = listOf(
                    "Introduced a richer listening stats hub with deeper insights into your sessions.",
                    "Launched a floating quick player to instantly open and preview local files.",
                    "Added a folders tab with a tree-style navigator and playlist-ready view."
                )
            ),
            ChangelogSection(
                title = "Improvements",
                items = listOf(
                    "Refined the overall Material 3 UI for a cleaner and more cohesive experience.",
                    "Metadata editing now supports cover art change.",
                    "Smoothed out animations and transitions across the app for more fluid navigation.",
                    "Enhanced the artist screen layout with richer details and polish.",
                    "Upgraded DailyMix and YourMix generation with smarter, more diverse selections.",
                    "Strengthened the AI playlist generation.",
                    "Improved search relevance and presentation for faster discovery.",
                    "Expanded support for a broader range of audio file formats."
                )
            ),
            ChangelogSection(
                title = "Fixes",
                items = listOf(
                    "Resolved metadata quirks so song details stay accurate everywhere.",
                    "Restored notification shortcuts so they reliably jump back into playback."
                )
            )
        )
    ),
    ChangelogVersion(
        version = "0.2.0-beta",
        date = "2024-09-15",
        sections = listOf(
            ChangelogSection(
                title = "Added",
                items = listOf(
                    "Chromecast support for casting audio from your device.",
                    "In-app changelog to keep you updated on the latest features.",
                    "Support for .LRC files, both embedded and external.",
                    "Offline lyrics support.",
                    "Synchronized lyrics (synced with the song).",
                    "New screen to view the full queue.",
                    "Reorder and remove songs from the queue.",
                    "Mini-player gestures (swipe down to close).",
                    "Added more material animations.",
                    "New settings to customize the look and feel.",
                    "New settings to clear the cache."
                )
            ),
            ChangelogSection(
                title = "Changed",
                items = listOf(
                    "Complete redesign of the user interface.",
                    "Complete redesign of the player.",
                    "Performance improvements in the library.",
                    "Improved application startup speed.",
                    "The AI now provides better results."
                )
            ),
            ChangelogSection(
                title = "Fixed",
                items = listOf(
                    "Fixed various bugs in the tag editor.",
                    "Fixed a bug where the playback notification was not clearing.",
                    "Fixed several bugs that caused the app to crash."
                )
            )
        )
    )
)


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogBottomSheet(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val changelogUrl = "https://github.com/theovilardo/PixelPlayer/blob/master/CHANGELOG.md"

    val fabCornerRadius = 16.dp

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Changelog",
                fontFamily = GoogleSansRounded,
                style = ExpTitleTypography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(changelog, key = { it.version }) { version ->
                    ChangelogVersionItem(version = version)
                }
            }
        }

        MediumExtendedFloatingActionButton(
            onClick = { openUrl(context, changelogUrl) },
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBR = fabCornerRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = fabCornerRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusTR = fabCornerRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = fabCornerRadius,
                smoothnessAsPercentTL = 60
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = null
                )
            },
            text = { Text(text = "View on GitHub") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )

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

@Composable
fun ChangelogVersionItem(version: ChangelogVersion) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionBadge(versionNumber = version.version)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = version.date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            version.sections.forEach { section ->
                ChangelogCategory(section = section)
            }
        }
    }
}

@Composable
fun ChangelogCategory(section: ChangelogSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            section.items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    val linkColor = MaterialTheme.colorScheme.primary
                    val annotatedText = buildAnnotatedString {
                        val mentionRegex = Regex("@(\\w+)")
                        var lastIndex = 0
                        mentionRegex.findAll(item).forEach { match ->
                            append(item.substring(lastIndex, match.range.first))
                            val username = match.groupValues[1]
                            withLink(
                                LinkAnnotation.Url(
                                    url = "https://github.com/$username",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = linkColor)
                                    )
                                )
                            ) {
                                append(match.value)
                            }
                            lastIndex = match.range.last + 1
                        }
                        if (lastIndex < item.length) {
                            append(item.substring(lastIndex))
                        }
                    }
                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (index != section.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

@Composable
fun VersionBadge(
    versionNumber: String
){
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
    ) {
        Text(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            text = versionNumber,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try { url.toUri() } catch (_: Throwable) { url.toUri() }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // As a last resort, do nothing; you could show a toast/snackbar from the caller if needed.
    }
}
