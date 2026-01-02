package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { 
            Text(
                stringResource(R.string.preferences),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    MaterialSwitchPreference(
                        title = stringResource(R.string.show_save_dialog),
                        summary = stringResource(R.string.show_save_dialog_desc),
                        checked = !skipSaveDialog,
                        onCheckedChange = { show ->
                            haptic.light()
                            scope.launch { appPreferences.setSkipSaveDialog(!show) }
                        }
                    )
                }
                CompactActionPreference(
                    icon = Icons.Filled.Image,
                    title = stringResource(R.string.default_image_source),
                    subtitle = defaultImageSource?.let {
                        when (it) {
                            "gallery" -> stringResource(R.string.gallery)
                            "internal" -> stringResource(R.string.photos)
                            "documents" -> stringResource(R.string.documents)
                            "camera" -> stringResource(R.string.camera)
                            else -> stringResource(R.string.none)
                        }
                    } ?: stringResource(R.string.no_default_source),
                    hasValue = defaultImageSource != null,
                    onClear = {
                        haptic.light()
                        scope.launch { appPreferences.clearDefaultImageSource() }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                haptic.light()
                onDismiss() 
            }) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun CompactActionPreference(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    hasValue: Boolean,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasValue) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasValue) {
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear_default_source),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
