package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@Composable
fun StarterModelDialog(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { 
            Text(
                stringResource(R.string.starter_model_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.starter_model_message))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.starter_model_hint))
            }
        },
        confirmButton = {
            TextButton(
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
