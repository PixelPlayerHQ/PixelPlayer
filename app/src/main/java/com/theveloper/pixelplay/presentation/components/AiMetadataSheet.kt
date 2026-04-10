package com.theveloper.pixelplay.presentation.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiMetadataSheet(
    onDismiss: () -> Unit,
    onApply: (SongMetadata) -> Unit,
    initialMetadata: SongMetadata?,
    isGenerating: Boolean,
    isSuccess: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    var title by remember(initialMetadata) { mutableStateOf(initialMetadata?.title ?: "") }
    var artist by remember(initialMetadata) { mutableStateOf(initialMetadata?.artist ?: "") }
    var album by remember(initialMetadata) { mutableStateOf(initialMetadata?.album ?: "") }
    var genre by remember(initialMetadata) { mutableStateOf(initialMetadata?.genre ?: "") }

    val smoothCornerShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentTR = 60
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ai_meta_animation")
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isGenerating) Modifier.rotate(iconRotation).scale(iconScale)
                            else Modifier
                        ),
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    color = when {
                        isGenerating -> colors.primaryContainer
                        isSuccess -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        error != null -> colors.errorContainer
                        else -> colors.secondaryContainer
                    },
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isSuccess -> Icons.Rounded.Check
                                error != null -> Icons.Rounded.Close
                                else -> Icons.Rounded.AutoAwesome
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = when {
                                isGenerating -> colors.onPrimaryContainer
                                isSuccess -> Color(0xFF4CAF50)
                                error != null -> colors.onErrorContainer
                                else -> colors.onSecondaryContainer
                            }
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isSuccess) "Perfectly Tagged!" else "AI Metadata",
                        style = ExpTitleTypography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.onSurface
                    )
                    Text(
                        text = if (isGenerating) "Consulting the sonic oracle..." else "Review and refine generated details",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(AbsoluteSmoothCornerShape(3.dp, 60)),
                        color = colors.primary,
                        trackColor = colors.primaryContainer.copy(alpha = 0.3f)
                    )
                }
            }

            // Editable Fields
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataField("Title", title) { title = it }
                MetadataField("Artist", artist) { artist = it }
                MetadataField("Album", album) { album = it }
                MetadataField("Genre", genre) { genre = it }
            }

            // Error Display & Retry
            AnimatedVisibility(visible = error != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.errorContainer,
                        shape = smoothCornerShape
                    ) {
                        Text(
                            text = error ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = smoothCornerShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                    ) {
                        Text("Try Again")
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = smoothCornerShape
                ) {
                    Icon(Icons.Rounded.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onApply(SongMetadata(title, artist, album, genre))
                    },
                    modifier = Modifier.weight(1.5f).height(56.dp),
                    shape = smoothCornerShape,
                    enabled = !isGenerating && (initialMetadata != null || isSuccess)
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply Changes")
                }
            }
        }
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainer,
            unfocusedContainerColor = colors.surfaceContainer,
            focusedIndicatorColor = colors.primary.copy(alpha = 0.5f),
            unfocusedIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}
