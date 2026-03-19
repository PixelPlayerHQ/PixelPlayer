package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * OPT #6 — Cached instances of frequently-used AbsoluteSmoothCornerShape.
 *
 * AbsoluteSmoothCornerShape is significantly more expensive than RoundedCornerShape because it
 * computes cubic Bézier curves analytically. In LazyColumn items (song cards, album cards, etc.)
 * each item pays this cost on first composition. By reusing singleton instances we avoid
 * repeated Path construction for the most common radii.
 *
 * Usage:
 *   Modifier.clip(ShapeCache.smooth12)
 *   Modifier.background(color, ShapeCache.smooth16)
 */
object ShapeCache {
    /** 8dp smooth corners — compact chips, small surfaces */
    val smooth8 = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)

    /** 10dp smooth corners */
    val smooth10 = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)

    /** 12dp smooth corners — song list items, small cards */
    val smooth12 = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)

    /** 14dp smooth corners */
    val smooth14 = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)

    /** 16dp smooth corners — album cards, playlist items */
    val smooth16 = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)

    /** 20dp smooth corners — larger cards */
    val smooth20 = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)

    /** 24dp smooth corners — dialog surfaces */
    val smooth24 = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)

    /** 28dp smooth corners */
    val smooth28 = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)

    /** 32dp smooth corners — bottom sheets, floating panels */
    val smooth32 = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)

    /** Fully smooth (pill) — 50dp, used for buttons and chips */
    val smoothPill = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)
}
