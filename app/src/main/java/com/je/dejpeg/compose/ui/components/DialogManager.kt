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

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.BuildConfig

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) { "Unknown" }
    var spawnTrigger by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = DialogDefaults.Shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(128.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.dejpeg_logo_rounded),
                                contentDescription = stringResource(R.string.app_icon),
                                modifier = Modifier
                                    .size(128.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { haptic.light(); spawnTrigger = System.currentTimeMillis() }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${stringResource(R.string.app_name)} v$versionName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (BuildConfig.OIDN_ENABLED) {
                            Text(stringResource(R.string.oidn_description))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column {
                        Text(stringResource(R.string.open_source_app_description))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            haptic.light()
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://codeberg.org/dryerlint/dejpeg".toUri())
                                )
                            } catch (_: Exception) { }
                        }) {
                            Text(stringResource(R.string.source_code))
                        }
                        Button(onClick = { haptic.light(); onDismiss() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
                val painter = painterResource(R.drawable.dejpeg_logo_rounded)
                val density = LocalDensity.current
                val logoSizePx = with(density) { 128.dp.roundToPx() }
                RainingLogoEffect(
                    painter = painter,
                    logoSize = IntSize(logoSizePx, logoSizePx),
                    modifier = Modifier.matchParentSize(),
                    particleCount = 15,
                    onTap = { haptic.light() },
                    spawnTrigger = spawnTrigger
                )
            }
        }
    }
}

@Composable
fun ImportProgressDialog(progress: Int) {
    StyledAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.importing_model)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.progress_percent, progress))
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LoadingDialog(
    title: String,
    message: String? = null,
    progress: Float? = null,
    progressText: String? = null
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = DialogDefaults.Shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
                progressText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SaveImageDialog(
    defaultFilename: String,
    showSaveAllOption: Boolean = false,
    initialSaveAll: Boolean = false,
    hideOptions: Boolean = false,
    onDismissRequest: () -> Unit,
    onSave: (String, Boolean, Boolean) -> Unit
) {
    val fileExt = defaultFilename.substringBeforeLast('.', defaultFilename)
    var textState by remember { mutableStateOf(TextFieldValue(fileExt, TextRange(0, fileExt.length))) }
    var saveAll by remember { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(if (hideOptions) R.string.overwrite_image else R.string.save_image)) },
        text = {
            Column {
                if (hideOptions) Text(stringResource(R.string.already_exists), Modifier.padding(bottom = 12.dp))
                OutlinedTextField(textState, { textState = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.filename)) })
                if (!hideOptions) {
                    Spacer(Modifier.height(12.dp))
                    if (showSaveAllOption) {
                        MaterialSwitchRow(stringResource(R.string.save_all), saveAll, { haptic.light(); saveAll = it }, Modifier.fillMaxWidth())
                    }
                    MaterialSwitchRow(stringResource(R.string.dont_show_dialog), skipNext, { haptic.light(); skipNext = it }, Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismissRequest() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(onClick = {
                haptic.light()
                val sanitizedFilename = textState.text.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").takeIf { it.isNotBlank() } ?: "image"
                onSave(sanitizedFilename, saveAll, skipNext)
                onDismissRequest()
            }) {
                Text(stringResource(R.string.save))
            }
        }
    )
}

@Composable
fun RemoveImageDialog(
    imageFilename: String,
    hasOutput: Boolean,
    imageId: String,
    context: android.content.Context,
    onDismissRequest: () -> Unit,
    onRemove: () -> Unit,
    onSaveAndRemove: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove_image_title)) },
        text = { Text(stringResource(R.string.remove_image_question, imageFilename)) },
        dismissButton = {
            TextButton({ haptic.light(); onDismissRequest() }) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ 
                    haptic.heavy()
                    CacheManager.deleteRecoveryPair(context, imageId, deleteProcessed = true, deleteUnprocessed = true)
                    onRemove()
                    onDismissRequest()
                }) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (hasOutput) {
                    Button({ haptic.medium(); onSaveAndRemove(); onDismissRequest() }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    )
}

@Composable
fun CancelProcessingDialog(imageFilename: String? = null, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.stop_processing_title)) },
        text = {
            Text(
                if (imageFilename != null) {
                    stringResource(R.string.stop_processing_question, imageFilename)
                } else {
                    stringResource(R.string.stop_processing_all_question)
                }
            )
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismissRequest() }) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            Button(
                onClick = { haptic.heavy(); onConfirm(); onDismissRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.yes_stop))
            }
        }
    )
}

@Composable
fun DeprecatedModelWarningDialog(
    modelName: String,
    warning: com.je.dejpeg.compose.ModelManager.ModelWarning,
    onContinue: () -> Unit,
    onGoToSettings: () -> Unit
) {
    BaseDialog(
        title = stringResource(warning.titleResId),
        content = {
            Column {
                Text(
                    "Active model: $modelName",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(warning.messageResId))
            }
        },
        onDismiss = onContinue,
        confirmButtonText = stringResource(R.string.ok),
        onConfirm = onContinue,
        dismissButtonText = stringResource(R.string.go_to_settings),
        onDismissButton = onGoToSettings
    )
}

@Composable
fun StarterModelDialog(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.starter_model_title)) },
        text = {
            Column {
                Text(stringResource(R.string.starter_model_message))
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

@Composable
fun ModelInfoDialog(modelName: String, infoText: String, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        text = {
            Text(infoText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Button(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
