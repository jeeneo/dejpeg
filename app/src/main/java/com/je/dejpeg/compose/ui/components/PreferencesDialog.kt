/**
* Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.components

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.je.dejpeg.R
import com.je.dejpeg.compose.ModelManager
import com.je.dejpeg.compose.utils.HapticFeedback
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.data.PreferenceKeys
import com.je.dejpeg.data.dataStore
import kotlinx.coroutines.launch

@Composable
fun PreferencesDialog(
    context: android.content.Context,
    skipSaveDialog: Boolean,
    defaultImageSource: String?,
    hapticFeedbackEnabled: Boolean,
    swapSwipeActions: Boolean,
    onSkipSaveDialogChange: (Boolean) -> Unit,
    onDefaultImageSourceChange: (String?) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onSwapSwipeActionsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onModelExtracted: (() -> Unit)? = null
) {
    val modelManager = remember { ModelManager(context) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val view = LocalView.current
    
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preferences), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
                            onHapticFeedbackChange(enabled)
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
                            onSkipSaveDialogChange(!show)
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
                        title = stringResource(R.string.swap_swipe_actions),
                        summary = stringResource(
                            if (swapSwipeActions) R.string.swap_swipe_actions_desc_swapped
                            else R.string.swap_swipe_actions_desc_default
                        ),
                        checked = swapSwipeActions,
                        onCheckedChange = { swap ->
                            haptic.light()
                            onSwapSwipeActionsChange(swap)
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
                        onDefaultImageSourceChange(null)
                    }
                )
                CompactActionPreference(
                    icon = Icons.Filled.Download,
                    title = stringResource(R.string.starter_model_title),
                    subtitle = stringResource(R.string.extract_starter_model),
                    hasValue = true,
                    onAction = {
                        haptic.light()
                        scope.launch {
                            context.dataStore.edit { it.remove(PreferenceKeys.STARTER_MODEL_EXTRACTED) }
                        }
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            modelManager.extractStarterModel(
                                setAsActive = false,
                                onSuccess = {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            "Starter models extracted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onModelExtracted?.invoke()
                                        onDismiss()
                                    }
                                },
                                onError = { error ->
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(
                                            context,
                                            "Failed to extract starter models: $error",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
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
    onClear: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        onClick = onAction ?: {},
        enabled = onAction != null,
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
            if (hasValue && onClear != null) {
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