package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.je.dejpeg.R

@Composable
fun PreferencesDialog(
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val prefs = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
    var skipSaveDialog by remember { mutableStateOf(prefs.getBoolean("skipSaveDialog", false)) }
    var defaultImageSource by remember { mutableStateOf(prefs.getString("defaultImageSource", null)) }
    var hapticFeedbackEnabled by remember { mutableStateOf(prefs.getBoolean("hapticFeedbackEnabled", true)) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.preferences)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.haptic_feedback),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vibration_on_touch),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.haptic_feedback_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("hapticFeedbackEnabled", enabled) }
                            hapticFeedbackEnabled = enabled
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.save_dialog),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.currently,
                            stringResource(if (skipSaveDialog) R.string.hidden else R.string.shown)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (skipSaveDialog) {
                    Button(
                        onClick = {
                            haptic.medium()
                            prefs.edit { putBoolean("skipSaveDialog", false) }
                            skipSaveDialog = false
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.show_save_dialog))
                    }
                } else {
                    Text(
                        stringResource(R.string.save_dialog_shown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.default_image_source),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.currently,
                            defaultImageSource?.let {
                                when (it) {
                                    "gallery" -> stringResource(R.string.gallery)
                                    "internal" -> stringResource(R.string.photos)
                                    "documents" -> stringResource(R.string.documents)
                                    "camera" -> stringResource(R.string.camera)
                                    else -> stringResource(R.string.none)
                                }
                            } ?: stringResource(R.string.none)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (defaultImageSource != null) {
                    Button(
                        onClick = {
                            haptic.medium()
                            prefs.edit { remove("defaultImageSource") }
                            defaultImageSource = null
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.clear_default_source))
                    }
                } else {
                    Text(
                        stringResource(R.string.no_default_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
