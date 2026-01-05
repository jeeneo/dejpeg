package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@Composable
fun StarterModelDialog(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.starter_model_title)) },
        text = {
            Column {
                Text(stringResource(R.string.starter_model_message))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.starter_model_hint))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.light()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.got_it))
            }
        }
    )
}
