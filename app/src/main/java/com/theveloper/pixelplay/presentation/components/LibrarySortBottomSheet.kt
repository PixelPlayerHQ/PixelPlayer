package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SortDirection
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortBottomSheet(
    title: String,
    options: List<SortOption>,
    selectedOption: SortOption?,
    onDismiss: () -> Unit,
    onOptionSelected: (SortOption) -> Unit,
    onDirectionToggle: (SortOption) -> Unit = onOptionSelected,
    showViewToggle: Boolean = false,
    viewSectionTitle: String = "View",
    viewToggleLabel: String = "Playlist View",
    viewToggleChecked: Boolean = false,
    onViewToggleChange: (Boolean) -> Unit = {},
    viewToggleContent: (@Composable () -> Unit)? = null,
    sourceToggleContent: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Animate background color
    val boxBackgroundColor by animateColorAsState(
        targetValue = if (viewToggleChecked) MaterialTheme.colorScheme.tertiary else unselectedColor,
        label = "boxBackgroundColorAnimation"
    )

    // Animate corner radius
    val boxCornerRadius by animateDpAsState(
        targetValue = if (viewToggleChecked) 18.dp else 50.dp,
        label = "boxCornerRadiusAnimation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 16.dp)
            )

            // Cast to nullable list to handle potential runtime nulls, then filter
            @Suppress("UNCHECKED_CAST")
            val safeOptions = remember(options) {
                (options as List<SortOption?>).filterNotNull()
            }
            val resolvedSelectedOption = remember(safeOptions, selectedOption) {
                safeOptions.firstOrNull { option ->
                    option.storageKey == selectedOption?.storageKey
                } ?: selectedOption ?: safeOptions.firstOrNull()
            }
            val methodOptions = remember(safeOptions) {
                safeOptions
                    .map { it.methodOption() }
                    .distinctBy { it.methodKey }
            }
            val selectedDirection = resolvedSelectedOption?.direction
            val shouldShowDirectionToggle = remember(methodOptions) {
                methodOptions.any { it.canFlipDirection }
            }

            if (shouldShowDirectionToggle && resolvedSelectedOption != null) {
                val directionLabel = when (selectedDirection) {
                    SortDirection.Descending -> "Descending"
                    SortDirection.Ascending -> "Ascending"
                    null -> "Original Order"
                }
                val directionHint = when {
                    resolvedSelectedOption.canFlipDirection && selectedDirection == SortDirection.Descending ->
                        "Tap to switch to ascending"
                    resolvedSelectedOption.canFlipDirection && selectedDirection == SortDirection.Ascending ->
                        "Tap to switch to descending"
                    else -> "This sort keeps its original order"
                }
                val isDescending = selectedDirection == SortDirection.Descending

                LibrarySheetSortDirectionCard(
                    modifier = Modifier.padding(bottom = 12.dp),
                    label = directionLabel,
                    supportingText = directionHint,
                    isDescending = isDescending,
                    enabled = resolvedSelectedOption.canFlipDirection,
                    onClick = {
                        onDirectionToggle(resolvedSelectedOption.flipDirection())
                    }
                )
            }

            Column(
                modifier = Modifier
                    .clip(
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                methodOptions.forEach { option ->
                    val isSelected = resolvedSelectedOption?.methodKey == option.methodKey
                    val containerColor = remember(isSelected) {
                        if (isSelected) selectedColor else unselectedColor
                    }

                    Surface(
                        //shape = MaterialTheme.shapes.extraLarge,
                        color = containerColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.dp)
                            .clip(
                                shape = RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 8.dp,
                                    bottomStart = 8.dp,
                                    bottomEnd = 8.dp
                                )
                            )
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    onOptionSelected(option.resolveForDirection(selectedDirection))
                                },
                                role = Role.RadioButton
                            )
                            .semantics { this.selected = isSelected }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.methodLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }
            }

            if (showViewToggle || viewToggleContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = viewSectionTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                )

                if (viewToggleContent != null) {
                    viewToggleContent()
                } else {
                    LibrarySheetToggleCard(
                        label = viewToggleLabel,
                        checked = viewToggleChecked,
                        boxBackgroundColor = boxBackgroundColor,
                        boxCornerRadius = boxCornerRadius,
                        onCheckedChange = onViewToggleChange
                    )
                }
            }

            if (sourceToggleContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Source",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                )
                sourceToggleContent()
            }

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                extraContent()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LibrarySheetSortDirectionCard(
    modifier: Modifier = Modifier,
    label: String,
    supportingText: String,
    isDescending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val transition = updateTransition(
        targetState = isDescending,
        label = "sortDirectionTransition"
    )
    val containerColor by transition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionContainerColor"
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
            descending -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    }
    val contentColor by transition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionContentColor"
    ) { descending ->
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            descending -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    val iconContainerColor by animateColorAsState(
        targetValue = contentColor.copy(alpha = if (enabled) 0.16f else 0.1f),
        label = "sortDirectionIconContainerColor"
    )
    val iconRotation by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "sortDirectionIconRotation"
    ) { descending ->
        if (descending) 0f else 180f
    }
    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "sortDirectionIconScale"
    ) { descending ->
        if (descending) 1f else 1.08f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.72f)
            .clip(AbsoluteSmoothCornerShape(18.dp, 60))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(iconRotation)
                        .scale(iconScale)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Order",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.82f)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
//                Text(
//                    text = supportingText,
//                    style = MaterialTheme.typography.bodySmall,
//                    color = contentColor.copy(alpha = 0.78f)
//                )
            }
        }
    }
}

@Composable
internal fun LibrarySheetToggleCard(
    label: String,
    checked: Boolean,
    boxBackgroundColor: Color,
    boxCornerRadius: androidx.compose.ui.unit.Dp,
    onCheckedChange: (Boolean) -> Unit
) {
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
            )
            .background(color = boxBackgroundColor)
            .clickable(onClick = { onCheckedChange(!checked) })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                    checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                thumbContent = if (checked) {
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
}
