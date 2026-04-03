package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    iconId: Int,
    contentDesc: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDesc: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else inactiveContentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.primary,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    text: String
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = if (active) activeContentColor else inactiveContentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    text: String,
    imageVector: ImageVector
) {
    ToggleSegmentButtonContainer(
        modifier = modifier,
        active = active,
        enabled = enabled,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        activeCornerRadius = activeCornerRadius,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (active) activeContentColor else inactiveContentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (active) activeContentColor else inactiveContentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}


@Composable
private fun ToggleSegmentButtonContainer(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    activeCornerRadius: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val targetBgColor = if (active) activeColor else inactiveColor
    val bgColor by animateColorAsState(
        targetValue = if (enabled) targetBgColor else targetBgColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 250),
        label = ""
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = ""
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.graphicsLayer(alpha = if (enabled) 1f else 0.38f)) {
            content()
        }
    }
}
