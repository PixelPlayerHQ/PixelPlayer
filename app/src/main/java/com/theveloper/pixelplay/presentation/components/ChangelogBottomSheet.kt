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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

val LocalRequestAppUpdateCheck = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChangelogModalSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val fill = if (sheetState.targetValue == SheetValue.Expanded) 1f else 0.55f
        ChangelogBottomSheet(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fill),
            onDismiss = onDismiss,
        )
    }
}

private const val CHANGELOG_RAW_URL =
    "https://raw.githubusercontent.com/kibetmasi/PixelPlayer/master/CHANGELOG.md"
private const val CHANGELOG_PAGE_URL =
    "https://github.com/kibetmasi/PixelPlayer/blob/master/CHANGELOG.md"

data class ChangelogSection(
    val title: String,
    val items: List<String>,
)

data class ChangelogVersion(
    val version: String,
    val date: String,
    val sections: List<ChangelogSection>,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogBottomSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val requestUpdateCheck = LocalRequestAppUpdateCheck.current
    var changelog by remember { mutableStateOf<List<ChangelogVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val fabCornerRadius = 16.dp

    LaunchedEffect(Unit) {
        changelog = withContext(Dispatchers.IO) { fetchRepoChangelog() }
        loading = false
    }

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.changelog_title),
                fontFamily = GoogleSansRounded,
                style = ExpTitleTypography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
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
                phase = 0f,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "check_for_updates") {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            requestUpdateCheck()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.changelog_check_updates))
                    }
                }
                if (loading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (changelog.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.changelog_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(changelog, key = { it.version + it.date }) { version ->
                        ChangelogVersionItem(version = version)
                    }
                }
            }
        }

        MediumExtendedFloatingActionButton(
            onClick = { openUrl(context, CHANGELOG_PAGE_URL) },
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBR = fabCornerRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = fabCornerRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusTR = fabCornerRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = fabCornerRadius,
                smoothnessAsPercentTL = 60,
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = null,
                )
            },
            text = { Text(text = stringResource(R.string.changelog_view_github)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 24.dp, vertical = 24.dp),
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
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                ),
        )
    }
}

@Composable
fun ChangelogVersionItem(version: ChangelogVersion) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionBadge(versionNumber = version.version)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = version.date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            section.items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
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
                                    styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                                ),
                            ) {
                                append(match.value)
                            }
                            lastIndex = match.range.last + 1
                        }
                        if (lastIndex < item.length) append(item.substring(lastIndex))
                    }
                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (index != section.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
fun VersionBadge(versionNumber: String) {
    Box(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ),
    ) {
        Text(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            text = versionNumber,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun fetchRepoChangelog(): List<ChangelogVersion> {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(CHANGELOG_RAW_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "PixelPlayer")
            setRequestProperty("Accept", "text/plain")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
        connection.inputStream.bufferedReader().use { parseChangelog(it.readText()) }
    } catch (_: Exception) {
        emptyList()
    } finally {
        connection?.disconnect()
    }
}

internal fun parseChangelog(markdown: String): List<ChangelogVersion> {
    val versions = mutableListOf<ChangelogVersion>()
    var version: String? = null
    var date = ""
    val sections = mutableListOf<ChangelogSection>()
    var sectionTitle: String? = null
    val items = mutableListOf<String>()
    val heading = Regex("""^## \[(.+)](?:\s*-\s*(.*))?$""")

    fun flushSection() {
        val title = sectionTitle ?: return
        if (items.isNotEmpty()) sections += ChangelogSection(title, items.toList())
        items.clear()
        sectionTitle = null
    }

    fun flushVersion() {
        flushSection()
        val name = version ?: return
        if (sections.isNotEmpty()) versions += ChangelogVersion(name, date, sections.toList())
        sections.clear()
        version = null
        date = ""
    }

    markdown.lineSequence().forEach { raw ->
        val line = raw.trim()
        val match = heading.matchEntire(line)
        when {
            match != null -> {
                flushVersion()
                val name = match.groupValues[1].trim()
                if (!name.equals("Unreleased", ignoreCase = true)) {
                    version = name
                    date = match.groupValues.getOrNull(2)?.trim().orEmpty()
                }
            }
            version != null && line.startsWith("### ") -> {
                flushSection()
                sectionTitle = line.removePrefix("### ").trim()
            }
            version != null && sectionTitle != null && line.startsWith("- ") -> {
                items += line.removePrefix("- ").trim()
            }
        }
    }
    flushVersion()
    return versions
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
