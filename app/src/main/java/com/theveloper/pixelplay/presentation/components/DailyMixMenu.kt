package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMixMenu(
    onDismiss: () -> Unit,
    onApplyPrompt: (String) -> Unit,
    isLoading: Boolean
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState()
    var prompt by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_e_daily_mix_how_title),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.presentation_batch_e_daily_mix_how_body),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.presentation_batch_e_daily_mix_prompt_label)) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.presentation_batch_e_daily_mix_cost_hint)) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onApplyPrompt(prompt)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.isNotBlank() && !isLoading
            ) {
                Text(
                    if (isLoading) {
                        stringResource(R.string.presentation_batch_e_daily_mix_updating)
                    } else {
                        stringResource(R.string.presentation_batch_e_daily_mix_update)
                    }
                )
            }
        }
    }
}
