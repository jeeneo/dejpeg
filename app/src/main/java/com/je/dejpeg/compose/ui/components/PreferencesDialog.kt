package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.HapticFeedback
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.data.AppPreferences
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

@Composable
fun PreferencesDialog(
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val appPreferences = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    val hapticFeedbackEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val haptic = rememberHapticFeedback()
    val view = LocalView.current

    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.preferences)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                PreferenceSection(title = stringResource(R.string.haptic_feedback)) {
                    MaterialSwitchPreference(
                        title = stringResource(R.string.vibration_on_touch),
                        summary = stringResource(R.string.haptic_feedback_desc),
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                HapticFeedback.light(view, isEnabled = true)
                            }
                            scope.launch { appPreferences.setHapticFeedbackEnabled(enabled) }
                        }
                    )
                }
                PreferenceDivider()
                PreferenceSection(title = stringResource(R.string.save_dialog)) {
                    ActionPreference(
                        currentValue = stringResource(
                            R.string.currently,
                            stringResource(if (skipSaveDialog) R.string.hidden else R.string.shown)
                        ).lowercase(),
                        buttonText = stringResource(R.string.show_save_dialog),
                        onButtonClick = {
                            scope.launch { appPreferences.setSkipSaveDialog(false) }
                        },
                        showButton = skipSaveDialog,
                        infoText = stringResource(R.string.save_dialog_shown)
                    )
                }
                PreferenceDivider()
                PreferenceSection(title = stringResource(R.string.default_image_source)) {
                    ActionPreference(
                        currentValue = stringResource(
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
                        ).lowercase(),
                        buttonText = stringResource(R.string.clear_default_source),
                        onButtonClick = {
                            scope.launch { appPreferences.clearDefaultImageSource() }
                        },
                        showButton = defaultImageSource != null,
                        infoText = stringResource(R.string.no_default_source)
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
