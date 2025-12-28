package com.je.dejpeg.compose.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                "${stringResource(R.string.app_name)} v$versionName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.non_destructive_restoration))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.open_source_app_description))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.license_text),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.medium()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/dryerlint/dejpeg"))
                    )
                } catch (_: Exception) {
                }
            }) {
                Text(stringResource(R.string.github))
            }
        }
    )
}
