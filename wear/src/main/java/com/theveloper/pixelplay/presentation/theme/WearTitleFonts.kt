package com.theveloper.pixelplay.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.theveloper.pixelplay.R

@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberBrowseSubscreenTitleFont(): FontFamily {
    return remember {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.width(134f),
                    FontVariation.Setting("ROND", 74f),
                    FontVariation.Setting("XTRA", 540f),
                    FontVariation.Setting("YOPQ", 92f),
                    FontVariation.Setting("YTLC", 510f),
                ),
            ),
        )
    }
}
