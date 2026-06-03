package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

@Composable
fun HomeOptionsBottomSheet(
    onNavigateToMashup: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(bottom = 32.dp)) { // Padding for gesture bar
        ListItem(
            headlineContent = { Text(stringResource(R.string.home_option_dj_mashup)) },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                    contentDescription = stringResource(R.string.home_option_dj_mashup)
                )
            },
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onNavigateToMashup)
        )

        ListItem(
            headlineContent = { Text("Extension Store") },
            leadingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_audio_file_24),
                    contentDescription = "Extension Store"
                )
            },
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onNavigateToExtensions)
        )
    }
}