package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R

@Composable
fun ImportProgressDialog(progress: Int) {
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.importing_model)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.progress_percent, progress))
            }
        },
        confirmButton = {}
    )
}