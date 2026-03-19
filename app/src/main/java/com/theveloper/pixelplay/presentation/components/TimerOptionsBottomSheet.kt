package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

val predefinedTimes = listOf(0, 5, 10, 15, 20, 30, 45, 60) // 0 represents 'Off'

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerOptionsBottomSheet(
    onPlayCounter: (count: Int) -> Unit,
    activeTimerValueDisplay: String?, // e.g., "15 minutes", "End of Track"
    playCount: Float,
    isEndOfTrackTimerActive: Boolean,
    onDismiss: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit
) {
    var showCustomTimePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var timerSliderPosition by remember { mutableStateOf(0f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSwitchEnabled = isEndOfTrackTimerActive

    var counterSliderPosition by remember { mutableStateOf(1f) }
    var isTimerMode by remember { mutableStateOf(true) } // true = timer mode, false = counter mode

    // Animate background color
    val boxBackgroundColor by animateColorAsState(
        targetValue = if (isSwitchEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "boxBackgroundColorAnimation"
    )

    // Animate corner radius
    val boxCornerRadius by animateDpAsState(
        targetValue = if (isSwitchEnabled) 18.dp else 50.dp,
        label = "boxCornerRadiusAnimation"
    )

    LaunchedEffect(activeTimerValueDisplay, playCount) {
        timerSliderPosition = when {
            activeTimerValueDisplay == null -> 0f // Off
            activeTimerValueDisplay == "End of Track" -> 0f // Slider shows 'Off' as EOT is a separate control
            activeTimerValueDisplay.startsWith("Custom:") -> 0f // Slider shows 'Off' if custom time is active via a different mechanism
            else -> {
                val minutesString = activeTimerValueDisplay.removeSuffix(" minutes")
                val minutesInt = minutesString.toIntOrNull()
                val index = minutesInt?.let { predefinedTimes.indexOf(it) } ?: -1
                if (index != -1) index.toFloat() else 0f
            }
        }
        counterSliderPosition = playCount
        // Restore counter mode if play count was previously set
        if (playCount > 1f) {
            isTimerMode = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = "Sleep Timer",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Predefined times replaced by Slider
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val currentIndex =
                    timerSliderPosition.roundToInt().coerceIn(0, predefinedTimes.size - 1)
                val currentMinutes = predefinedTimes[currentIndex]
                val timerDisplayText = if (currentMinutes == 0) "Timer" else "$currentMinutes minutes"
                Text(
                    text = timerDisplayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                topEnd = 18.dp,
                                topStart = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 18.dp
                            )
                        )
                ) {
                    Slider(
                        value = timerSliderPosition,
                        onValueChange = {
                            timerSliderPosition = it
                            isTimerMode = true
                        },
                        valueRange = 0f..(predefinedTimes.size - 1).toFloat(),
                        steps = predefinedTimes.size - 2, // Number of discrete intervals
                        enabled = isTimerMode || counterSliderPosition == 1f,
                        onValueChangeFinished = {
                            val selectedIndexOnFinish = timerSliderPosition.roundToInt()
                                .coerceIn(0, predefinedTimes.size - 1)
                            val selectedMinutesOnFinish = predefinedTimes[selectedIndexOnFinish]
                            if (selectedMinutesOnFinish == 0) {
                                if (activeTimerValueDisplay != null && activeTimerValueDisplay != "End of Track") { // Only cancel if a duration timer was set
                                    onCancelTimer()
                                }
                            } else {
                                onSetPredefinedTimer(selectedMinutesOnFinish)
                            }
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier
                                    .heightIn(min = 32.dp),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                bottomEnd = 18.dp,
                                bottomStart = 18.dp,
                                topEnd = 6.dp,
                                topStart = 6.dp
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {


                val currentPlayCount = counterSliderPosition.toInt()
                val counterDisplayText = "Play Count: " + when (currentPlayCount) {
                    1 -> "1 time"
                    else -> "$currentPlayCount times"
                }
                Text(
                    text = counterDisplayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Slider(
                        value = counterSliderPosition,
                        onValueChange = {
                            counterSliderPosition = it
                            isTimerMode = false
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = !isTimerMode || timerSliderPosition == 0f,
                        onValueChangeFinished = {
                            onCancelTimer()
                            onSetEndOfTrackTimer(false)
                            onPlayCounter(counterSliderPosition.toInt())
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier
                                    .heightIn(min = 32.dp),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End of track option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusBL = boxCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = boxCornerRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = boxCornerRadius,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = boxCornerRadius,
                            smoothnessAsPercentTR = 60
                        )
                    ) // Apply animated corner radius for clipping
                    .background(color = boxBackgroundColor)   // Apply animated background color
                    .clickable(
                        enabled = isTimerMode || counterSliderPosition == 1f,
                        onClick = {
                            onSetEndOfTrackTimer(!isSwitchEnabled)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "End of current track",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        color = if (isSwitchEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface // Adjust text color for contrast
                    )
                    Switch(
                        checked = isSwitchEnabled,
                        enabled = isTimerMode || counterSliderPosition == 1f,
                        onCheckedChange = {
                            onSetEndOfTrackTimer(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        thumbContent = if (isSwitchEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Switch is on",
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            val buttonHeight = 68.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = counterSliderPosition == 1f,
                    onClick = {
                        showCustomTimePicker = true
                    },
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        bottomStart = 50.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    ),
                    modifier = Modifier
                        .weight(1f) // Give buttons equal space if desired
                        .height(buttonHeight)
                ) {
                    Text("Custom Time")
                }
                Button(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                        onCancelCountedPlay()
                    },
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    enabled = activeTimerValueDisplay != null || counterSliderPosition != 1f,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight)
                ) {
                    Text("Cancel Timer")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCustomTimePicker) {
        val initialHour = 0    // Default to 0 hours for a duration
        val initialMinute = 15 // Default to 15 minutes for a duration

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true // Consistent with your previous setting (24-hour format)
        )

        AlertDialog(
            onDismissRequest = {
                showCustomTimePicker = false // Dismiss the M3 dialog
                // No need to call onDismiss() for the bottom sheet here,
                // as that's handled by the confirm button or if the user specifically dismisses the bottom sheet.
            },
            title = { Text("Set Custom Duration") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hour = timePickerState.hour
                        val minute = timePickerState.minute
                        val totalMinutes = hour * 60 + minute

                        if (totalMinutes > 0) { // Ensure some time is set
                            onSetPredefinedTimer(totalMinutes) // Your existing callback
                        }
                        showCustomTimePicker = false // Dismiss the M3 dialog
                        onDismiss() // Dismiss the bottom sheet after setting time, as per original logic
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomTimePicker = false // Dismiss the M3 dialog
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
