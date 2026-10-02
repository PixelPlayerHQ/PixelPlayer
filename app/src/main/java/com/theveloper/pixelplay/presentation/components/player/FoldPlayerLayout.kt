package com.theveloper.pixelplay.presentation.components.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * How the expanded player should sit on the current window.
 * [Phone] is every window that does not cross a hinge: a normal phone in
 * either orientation, and the Pixel Fold cover display.
 */
internal enum class FoldPlayerLayout {
    Phone,
    Tabletop,
    Book,
    FlatWide,
}

internal data class FoldSignal(
    val halfOpened: Boolean,
    val flat: Boolean,
    val horizontal: Boolean,
    val vertical: Boolean,
)

internal data class FoldObservation(
    val layout: FoldPlayerLayout,
    val hingeThickness: Dp,
)

internal fun foldPlayerLayout(signal: FoldSignal?, isLandscape: Boolean): FoldPlayerLayout {
    if (signal == null) return FoldPlayerLayout.Phone
    return when {
        signal.halfOpened && signal.horizontal -> FoldPlayerLayout.Tabletop
        signal.halfOpened && signal.vertical -> FoldPlayerLayout.Book
        signal.flat && isLandscape -> FoldPlayerLayout.FlatWide
        else -> FoldPlayerLayout.Phone
    }
}

@Composable
internal fun rememberFoldObservation(isLandscape: Boolean): FoldObservation {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    if (activity == null) {
        return FoldObservation(FoldPlayerLayout.Phone, 0.dp)
    }
    val layoutInfo = WindowInfoTracker.getOrCreate(activity)
        .windowLayoutInfo(activity)
        .collectAsStateWithLifecycle(initialValue = WindowLayoutInfo(emptyList()))
        .value
    val fold = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    val signal = fold?.let {
        FoldSignal(
            halfOpened = it.state == FoldingFeature.State.HALF_OPENED,
            flat = it.state == FoldingFeature.State.FLAT,
            horizontal = it.orientation == FoldingFeature.Orientation.HORIZONTAL,
            vertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
        )
    }
    val thickness = foldThickness(fold)
    return FoldObservation(foldPlayerLayout(signal, isLandscape), thickness)
}

@Composable
private fun foldThickness(fold: FoldingFeature?): Dp {
    if (fold == null) return 0.dp
    val px = if (fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
        fold.bounds.height()
    } else {
        fold.bounds.width()
    }
    return with(LocalDensity.current) { px.toDp() }.coerceAtLeast(0.dp)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
