package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

data class PlayStoreAnnouncementUiModel(
    val enabled: Boolean,
    val title: String,
    val body: String,
    val playStoreUrl: String?,
    val primaryActionLabel: String,
    val dismissActionLabel: String,
    val linkPendingMessage: String,
)

object PlayStoreAnnouncementDefaults {
    const val LOCAL_PREVIEW_ENABLED = false

    fun localizedTemplate(context: Context): PlayStoreAnnouncementUiModel =
        PlayStoreAnnouncementUiModel(
            enabled = false,
            title = context.getString(R.string.playstore_dialog_title),
            body = context.getString(R.string.playstore_dialog_body),
            playStoreUrl = null,
            primaryActionLabel = context.getString(R.string.playstore_dialog_action_open),
            dismissActionLabel = context.getString(R.string.playstore_dialog_action_continue_beta),
            linkPendingMessage = context.getString(R.string.playstore_dialog_link_pending),
        )

    fun hardcodedPreview(context: Context): PlayStoreAnnouncementUiModel =
        localizedTemplate(context).copy(enabled = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayStoreAnnouncementDialog(
    announcement: PlayStoreAnnouncementUiModel,
    onDismiss: () -> Unit,
    onOpenPlayStore: (String) -> Unit,
    primaryBusy: Boolean = false,
) {
    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 30.dp,
        cornerRadiusTR = 30.dp,
        cornerRadiusBL = 30.dp,
        cornerRadiusBR = 30.dp,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentBR = 60,
    )
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)
    val hasPlayStoreLink = !announcement.playStoreUrl.isNullOrBlank()

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.pixelplay_base_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(26.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.playstore_dialog_app_name),
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.playstore_dialog_release_announcement),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasPlayStoreLink) {
                    Text(
                        text = announcement.linkPendingMessage,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = actionShape,
                        //modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = announcement.dismissActionLabel,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Button(
                        onClick = {
                            if (!primaryBusy) {
                                announcement.playStoreUrl?.let(onOpenPlayStore)
                            }
                        },
                        enabled = hasPlayStoreLink && !primaryBusy,
                        shape = actionShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (primaryBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_arrow_forward_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                primaryBusy -> stringResource(R.string.update_dialog_downloading)
                                hasPlayStoreLink -> announcement.primaryActionLabel
                                else -> stringResource(R.string.playstore_dialog_coming_soon)
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
