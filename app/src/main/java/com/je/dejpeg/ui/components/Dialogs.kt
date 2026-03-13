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

package com.je.dejpeg.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import com.je.dejpeg.ModelManager
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.PreferenceKeys
import com.je.dejpeg.data.dataStore
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.HapticFeedback
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

object DialogDefaults {
    val Shape = RoundedCornerShape(16.dp)
}

@Composable
fun StyledAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = icon?.let {
            {
                Icon(
                    it, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary
                )
            }
        },
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

@Composable
fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissButton: (() -> Unit)? = null,
    isError: Boolean = false,
    context: Context? = null,
    showCopyButton: Boolean = isError,
    icon: ImageVector? = null,
    customButtons: (@Composable () -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val clipboardManager = remember(context) { context?.getClipboard() }
    val textToCopy = message ?: ""

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = when {
            customButtons != null -> null
            dismissButtonText != null && onDismissButton != null -> {
                {
                    DialogTextButton(dismissButtonText, { onDismissButton() }, { haptic.light() })
                }
            }

            showCopyButton && isError && clipboardManager != null -> {
                {
                    DialogTextButton(stringResource(R.string.copy), {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                context?.getString(R.string.error) ?: "error", textToCopy
                            )
                        )
                        context?.let { it.toast(it.getString(R.string.error_copied)) }
                    }, { haptic.light() })
                }
            }

            else -> null
        },
        confirmButton = {
            if (customButtons != null) customButtons()
            else DialogPrimaryButton(confirmButtonText, { onConfirm() }, { haptic.light() })
        })
}

@Composable
fun ErrorAlertDialog(
    title: String,
    errorMessage: String,
    onDismiss: () -> Unit,
    context: Context,
    confirmButtonText: String? = null
) {
    BaseDialog(
        title = title,
        message = errorMessage,
        onDismiss = onDismiss,
        confirmButtonText = confirmButtonText ?: stringResource(R.string.ok),
        onConfirm = onDismiss,
        isError = true,
        context = context,
        showCopyButton = true
    )
}

@Composable
fun SimpleAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmButtonText: String? = null,
    onConfirm: () -> Unit = onDismiss,
    dismissButtonText: String? = null,
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val resolvedText = confirmButtonText ?: stringResource(R.string.ok)
    StyledAlertDialog(
        onDismissRequest = { haptic.light(); onDismiss() },
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = dismissButtonText?.let {
            { DialogTextButton(it, { onDismiss() }, { haptic.light() }) }
        },
        confirmButton = {
            DialogPrimaryButton(resolvedText, { onConfirm() }, { haptic.light() })
        })
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }
    var spawnTrigger by remember { mutableLongStateOf(0L) }

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
                                        interactionSource = remember { MutableInteractionSource() }) {
                                        haptic.light(); spawnTrigger = System.currentTimeMillis()
                                    })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${stringResource(R.string.app_name)} v$versionName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Column {
                        Text(stringResource(R.string.open_source_app_description))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                    ) {
                        DialogTextButton(
                            stringResource(R.string.source_code),
                            { context.openUrl("https://codeberg.org/dryerlint/dejpeg") },
                            { haptic.light() })
                        DialogPrimaryButton(
                            stringResource(R.string.ok),
                            { onDismiss() },
                            { haptic.light() })
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
                LinearProgressIndicator(
                    progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.progress_percent, progress))
            }
        },
        confirmButton = {})
}

@Composable
fun LoadingDialog(
    title: String, message: String? = null, progress: Float? = null, progressText: String? = null
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
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
                progressText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    val fileExt = defaultFilename.substringBeforeLast('.')
    var textState by remember { mutableStateOf(fileExt) }
    var saveAll by remember { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    StyledAlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(stringResource(if (hideOptions) R.string.overwrite_image else R.string.save_image))
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.filename)) },
                isError = hideOptions,
                supportingText = if (hideOptions) {
                    { Text(stringResource(R.string.already_exists)) }
                } else null)
            if (!hideOptions) {
                Column(Modifier.padding(top = 4.dp)) {
                    if (showSaveAllOption) {
                        CompactOptionRow(
                            label = stringResource(R.string.save_all),
                            checked = saveAll,
                            onCheckedChange = { haptic.light(); saveAll = it })
                    }
                    CompactOptionRow(
                        label = stringResource(R.string.dont_show_dialog),
                        checked = skipNext,
                        onCheckedChange = { haptic.light(); skipNext = it })
                }
            }
        }
    }, dismissButton = {
        DialogTextButton(stringResource(R.string.cancel), onDismissRequest, { haptic.light() })
    }, confirmButton = {
        DialogPrimaryButton(label = stringResource(R.string.save), onClick = {
            onSave(sanitizeFilename(textState), saveAll, skipNext)
            onDismissRequest()
        }, hapticAction = { haptic.light() })
    })
}

@Composable
private fun CompactOptionRow(
    label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, onCheckedChange = null, modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RemoveImageDialog(
    imageFilename: String,
    hasOutput: Boolean,
    imageId: String,
    context: Context,
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
            DialogTextButton(
                stringResource(R.string.nope),
                { onDismissRequest() },
                { haptic.light() })
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogTextButton(
                    stringResource(R.string.remove), {
                    CacheManager.deleteRecoveryPair(
                        context, imageId, deleteProcessed = true, deleteUnprocessed = true
                    )
                    onRemove()
                    onDismissRequest()
                }, { haptic.heavy() }, MaterialTheme.colorScheme.error
                )
                if (hasOutput) {
                    Button({ haptic.medium(); onSaveAndRemove(); onDismissRequest() }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        })
}

@Composable
fun CancelProcessingDialog(
    imageFilename: String? = null, onDismissRequest: () -> Unit, onConfirm: () -> Unit
) {
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
            DialogTextButton(
                stringResource(R.string.nope),
                { onDismissRequest() },
                { haptic.light() })
        },
        confirmButton = {
            Button(
                onClick = { haptic.heavy(); onConfirm(); onDismissRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.yes_stop))
            }
        })
}

@Composable
fun DeprecatedModelWarningDialog(
    modelName: String,
    warning: ModelManager.ModelWarning,
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
                }) {
                Text(stringResource(R.string.got_it))
            }
        })
}

@Composable
fun ModelInfoDialog(modelName: String, infoText: String, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()

    StyledAlertDialog(onDismissRequest = onDismiss, title = {
        Text(
            modelName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }, text = {
        Text(
            infoText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }, confirmButton = {
        DialogPrimaryButton(stringResource(R.string.ok), { onDismiss() }, { haptic.light() })
    })
}


@Composable
fun ChunkDialog(
    chunk: Int,
    overlap: Int,
    threads: Int,
    onChunkChange: (Int) -> Unit,
    onOverlapChange: (Int) -> Unit,
    onThreadsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var chunkSize by remember { mutableIntStateOf(chunk) }
    var overlapSize by remember { mutableIntStateOf(overlap) }
    val maxThreads = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
    var threadCount by remember { mutableIntStateOf(threads.coerceIn(0, maxThreads)) }
    val haptic = rememberHapticFeedback()
    val chunkPowers = generateSequence(512) { it * 2 }.takeWhile { it <= 2048 }.toList()
    val overlapPowers = generateSequence(16) { it * 2 }.takeWhile { it <= 128 }.toList()

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chunk_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PowerSlider(
                    label = stringResource(R.string.chunk_size),
                    value = chunkSize,
                    powers = chunkPowers,
                    onChange = { chunkSize = it },
                    hapticAction = { haptic.light() })
                if (chunkSize >= 2048) {
                    Text(
                        stringResource(R.string.large_chunk_warning, chunkSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                PowerSlider(
                    label = stringResource(R.string.overlap_size),
                    value = overlapSize,
                    powers = overlapPowers,
                    maxAllowed = chunkSize,
                    onChange = { overlapSize = it },
                    hapticAction = { haptic.light() })
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.oidn_num_threads),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${stringResource(R.string.processing_threads_desc)} ${
                        stringResource(
                            R.string.processing_threads_max, maxThreads
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = threadCount.toFloat(),
                    onValueChange = {
                        val updated = it.roundToInt().coerceIn(0, maxThreads)
                        if (updated != threadCount) {
                            threadCount = updated
                            haptic.light()
                        }
                    },
                    valueRange = 0f..maxThreads.toFloat(),
                    steps = (maxThreads - 1).coerceAtLeast(0)
                )
                Row {
                    Text(
                        if (threadCount == 0) stringResource(
                            R.string.thread_value_auto, maxThreads
                        ) else "$threadCount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.note_chunk_info, chunkSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        dismissButton = {
            DialogTextButton(stringResource(R.string.cancel), { onDismiss() }, { haptic.light() })
        },
        confirmButton = {
            DialogPrimaryButton(stringResource(R.string.save), {
                haptic.medium()
                onChunkChange(chunkSize)
                onOverlapChange(overlapSize)
                onThreadsChange(threadCount)
                onDismiss()
            }, { haptic.medium() })
        })
}

data class FAQSectionData(
    val title: String, val content: String?, val subSections: List<Pair<String, String>>?
)

@Composable
fun FAQDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val faqSections = remember { loadFAQSections(context) }
    SimpleAlertDialog(
        title = stringResource(R.string.faqs),
        onDismiss = onDismiss,
        confirmButtonText = stringResource(R.string.close),
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(faqSections.size) {
                    FAQSection(
                        faqSections[it].title, faqSections[it].content, faqSections[it].subSections
                    )
                }
            }
        })
}

@Composable
fun FAQSection(title: String, content: String?, subSections: List<Pair<String, String>>? = null) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.light(); expanded = !expanded }
            .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(
                    R.string.expand
                ),
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                content?.let {
                    MarkdownText(
                        it,
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                subSections?.forEach { (subTitle, subContent) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        subTitle,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarkdownText(
                        subContent,
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun MarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color
) {
    Text(text = text, style = style.copy(color = color))
}

fun loadFAQSections(context: Context): List<FAQSectionData> {
    val sections = mutableListOf<FAQSectionData>()

    sections.add(
        FAQSectionData(
            title = context.getString(R.string.faq_what_is_this_title),
            content = context.getString(R.string.faq_what_is_this_content),
            subSections = null
        )
    )

    sections.add(
        FAQSectionData(
            title = context.getString(R.string.faq_which_models_title),
            content = null,
            subSections = listOf(
                Pair(
                    context.getString(R.string.faq_fbcnn_title),
                    context.getString(R.string.faq_fbcnn_content)
                ), Pair(
                    context.getString(R.string.faq_scunet_title),
                    context.getString(R.string.faq_scunet_content)
                )
            )
        )
    )

    return sections
}


private const val HELP_TYPE_GALLERY = 0
private const val HELP_TYPE_INTERNAL = 1
private const val HELP_TYPE_DOCUMENTS = 2
private const val HELP_TYPE_CAMERA = 3

@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit,
    onGallerySelected: () -> Unit,
    onInternalSelected: () -> Unit,
    onDocumentsSelected: () -> Unit,
    onCameraSelected: () -> Unit
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var helpInfo by remember { mutableIntStateOf(-1) }
    var setAsDefault by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    val handleSelection: suspend (String, () -> Unit) -> Unit = { key, action ->
        if (setAsDefault) {
            appPreferences.setDefaultImageSource(key)
        }
        onDismiss()
        action()
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 8.dp),
            shape = DialogDefaults.Shape,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_image_source),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                ImageSource(
                    icon = painterResource(id = R.drawable.ic_gallery),
                    title = stringResource(R.string.gallery),
                    description = stringResource(R.string.gallery_picker_title),
                    onClick = {
                        haptic.medium()
                        scope.launch { handleSelection("gallery", onGallerySelected) }
                    },
                    onHelpClick = { haptic.light(); helpInfo = HELP_TYPE_GALLERY })
                val sources = listOf(
                    ImageSourceSpec(
                        key = "internal",
                        icon = Icons.Outlined.Photo,
                        titleRes = R.string.internal_picker,
                        descRes = R.string.internal_picker_title,
                        onSelect = {
                            haptic.medium(); scope.launch {
                            handleSelection(
                                "internal", onInternalSelected
                            )
                        }
                        },
                        onHelp = { haptic.light(); helpInfo = HELP_TYPE_INTERNAL }),
                    ImageSourceSpec(
                        key = "documents",
                        icon = Icons.Outlined.Folder,
                        titleRes = R.string.documents,
                        descRes = R.string.documents_picker_title,
                        onSelect = {
                            haptic.medium(); scope.launch {
                            handleSelection(
                                "documents", onDocumentsSelected
                            )
                        }
                        },
                        onHelp = { haptic.light(); helpInfo = HELP_TYPE_DOCUMENTS }),
                    ImageSourceSpec(
                        key = "camera",
                        icon = Icons.Outlined.CameraAlt,
                        titleRes = R.string.camera,
                        descRes = R.string.camera_title,
                        onSelect = {
                            haptic.medium(); scope.launch {
                            handleSelection(
                                "camera", onCameraSelected
                            )
                        }
                        },
                        onHelp = { haptic.light(); helpInfo = HELP_TYPE_CAMERA })
                )
                ImageSourceList(sources)
                Spacer(Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    MaterialSwitchPreference(
                        title = stringResource(R.string.set_as_default),
                        checked = setAsDefault,
                        onCheckedChange = { haptic.light(); setAsDefault = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    DialogTextButton(
                        stringResource(R.string.cancel),
                        { onDismiss() },
                        { haptic.light() })
                }
            }
        }
    }
    if (helpInfo >= 0) {
        val (titleRes, descRes) = when (helpInfo) {
            HELP_TYPE_GALLERY -> R.string.gallery_picker_title to R.string.gallery_picker_desc
            HELP_TYPE_INTERNAL -> R.string.internal_picker_title to R.string.internal_picker_desc
            HELP_TYPE_DOCUMENTS -> R.string.documents_picker_title to R.string.documents_picker_desc
            HELP_TYPE_CAMERA -> R.string.camera_title to R.string.camera_desc
            else -> R.string.gallery_picker_title to R.string.gallery_picker_desc
        }
        HelpDialog(
            title = stringResource(titleRes), text = stringResource(descRes)
        ) {
            @Suppress("AssignedValueIsNeverRead")
            helpInfo = -1
        }
    }
}

@Composable
private fun ImageSource(
    icon: Any, title: String, description: String, onClick: () -> Unit, onHelpClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(1.dp, 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (icon) {
                            is ImageVector -> Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            is Painter -> Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onHelpClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.help),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HelpDialog(title: String, text: String, onDismiss: () -> Unit) {
    SimpleAlertDialog(
        title = title, message = text, onDismiss = onDismiss, icon = Icons.Default.Info
    )
}

@Composable
fun PreferencesDialog(
    context: Context,
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

    StyledAlertDialog(onDismissRequest = onDismiss, title = {
        Text(
            stringResource(R.string.preferences),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSwitchRow(
                title = stringResource(R.string.vibration_on_touch),
                checked = hapticFeedbackEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) HapticFeedback.light(view, isEnabled = true)
                    onHapticFeedbackChange(enabled)
                },
                showDivider = true
            )
            CompactSwitchRow(
                title = stringResource(R.string.show_save_dialog),
                checked = !skipSaveDialog,
                onCheckedChange = { show ->
                    haptic.light()
                    onSkipSaveDialogChange(!show)
                },
                showDivider = true
            )
            CompactSwitchRow(
                title = stringResource(R.string.swap_swipe_actions),
                checked = swapSwipeActions,
                onCheckedChange = { swap ->
                    haptic.light()
                    onSwapSwipeActionsChange(swap)
                },
                showDivider = false
            )
            CompactActionItem(
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
                onClear = if (defaultImageSource != null) {
                    { haptic.light(); onDefaultImageSourceChange(null) }
                } else null,
                showDivider = true)
            CompactActionItem(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.starter_model_title),
                subtitle = stringResource(R.string.extract_starter_model),
                onClick = {
                    haptic.light()
                    scope.launch {
                        context.dataStore.edit { prefs -> prefs.remove(PreferenceKeys.STARTER_MODEL_EXTRACTED) }
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        modelManager.extractStarterModel(setAsActive = false, onSuccess = {
                            Handler(Looper.getMainLooper()).post {
                                context.toast("Starter models extracted")
                                onModelExtracted?.invoke()
                                onDismiss()
                            }
                        }, onError = { error ->
                            Handler(Looper.getMainLooper()).post {
                                context.toast("Failed to extract starter models: $error")
                            }
                        })
                    }
                })
        }
    }, confirmButton = {
        DialogPrimaryButton(stringResource(R.string.close), { onDismiss() }, { haptic.light() })
    })
}

@Composable
private fun CompactSwitchRow(
    title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, showDivider: Boolean
) {
    Column {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked, onCheckedChange = null, modifier = Modifier.scale(0.85f)
            )
        }
        if (showDivider) HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun CompactActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    showDivider: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClear != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (showDivider) HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

data class FallingLogo(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val scale: Float,
    val startTime: Long
)

private fun hapticAnd(action: () -> Unit, hapticAction: () -> Unit) {
    hapticAction()
    action()
}

@Composable
private fun DialogTextButton(
    label: String,
    onClick: () -> Unit,
    hapticAction: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color? = null
) {
    TextButton(onClick = { hapticAnd(onClick, hapticAction) }) {
        if (textColor != null) Text(label, color = textColor) else Text(label)
    }
}

@Composable
private fun DialogPrimaryButton(
    label: String, onClick: () -> Unit, hapticAction: () -> Unit, enabled: Boolean = true
) {
    Button(onClick = { hapticAnd(onClick, hapticAction) }, enabled = enabled) { Text(label) }
}

private fun sanitizeFilename(input: String, fallback: String = "image"): String {
    return input.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").takeIf { it.isNotBlank() }
        ?: fallback
}

@Composable
private fun PowerSlider(
    label: String,
    value: Int,
    powers: List<Int>,
    maxAllowed: Int = Int.MAX_VALUE,
    onChange: (Int) -> Unit,
    hapticAction: () -> Unit
) {
    val availablePowers = remember(powers, maxAllowed) { powers.filter { it < maxAllowed } }
    val effectivePowers = availablePowers.ifEmpty { listOf(powers.first()) }
    val clampedValue = value.coerceAtMost(effectivePowers.last())
    var index by remember(
        clampedValue, effectivePowers
    ) { mutableIntStateOf(effectivePowers.indexOf(clampedValue).coerceAtLeast(0)) }
    LaunchedEffect(maxAllowed) {
        if (value >= maxAllowed && effectivePowers.isNotEmpty()) {
            onChange(effectivePowers.last())
        }
    }

    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val newIdx = it.roundToInt().coerceIn(effectivePowers.indices)
                if (newIdx != index) {
                    index = newIdx
                    hapticAction()
                    onChange(effectivePowers[newIdx])
                }
            },
            valueRange = 0f..(effectivePowers.lastIndex.toFloat().coerceAtLeast(0f)),
            steps = (effectivePowers.size - 2).coerceAtLeast(0),
            enabled = effectivePowers.size > 1
        )
        Row {
            Text(
                "${effectivePowers[index]}px",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class ImageSourceSpec(
    val key: String,
    val icon: Any,
    val titleRes: Int,
    val descRes: Int,
    val onSelect: () -> Unit,
    val onHelp: () -> Unit
)

@Composable
private fun ImageSourceList(sources: List<ImageSourceSpec>) {
    sources.forEach { s ->
        ImageSource(
            icon = s.icon,
            title = stringResource(s.titleRes),
            description = stringResource(s.descRes),
            onClick = s.onSelect,
            onHelpClick = s.onHelp
        )
    }
}

private fun Context.getClipboard(): ClipboardManager? =
    getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

private fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
    }
}

@Composable
fun RainingLogoEffect(
    painter: Painter,
    logoSize: IntSize,
    modifier: Modifier = Modifier,
    particleCount: Int = 12,
    onTap: () -> Unit = {},
    spawnTrigger: Long = 0
) {
    var logos by remember { mutableStateOf<List<FallingLogo>>(emptyList()) }
    var nextId by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameTime = nanos / 1_000_000L
            }
        }
    }

    LaunchedEffect(frameTime) {
        logos = logos.filter { logo ->
            val elapsed = (frameTime - logo.startTime).toFloat()
            val elapsedSeconds = elapsed / 1000f
            val y = logo.startY + logo.velocityY * elapsedSeconds
            val scaledHeight = logoSize.height * logo.scale
            y < canvasSize.height + scaledHeight
        }
    }

    LaunchedEffect(spawnTrigger) {
        if (spawnTrigger > 0) {
            onTap()
            val newLogos = if (logos.size > 50) {
                val currentHugeCount = logos.count { it.scale >= 1.0f }
                val scale = 1.0f + (currentHugeCount * 0.2f)
                listOf(
                    FallingLogo(
                        id = nextId++,
                        startX = Random.nextFloat() * canvasSize.width,
                        startY = 0f,
                        velocityX = (Random.nextFloat() - 0.5f) * 2f,
                        velocityY = 100f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 180f - 90f,
                        scale = scale,
                        startTime = frameTime
                    )
                )
            } else {
                (0 until particleCount).map {
                    val startX = Random.nextFloat() * canvasSize.width
                    val startY = 0f
                    val velocityX = (Random.nextFloat() - 0.5f) * 2f
                    val velocityY = Random.nextFloat() * 100f + 150f

                    FallingLogo(
                        id = nextId++,
                        startX = startX,
                        startY = startY,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 180f - 90f,
                        scale = Random.nextFloat() * 0.25f + 0.15f,
                        startTime = frameTime
                    )
                }
            }
            logos = logos + newLogos
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (canvasSize.width == 0 || canvasSize.height == 0) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
            }
            logos.forEach { logo ->
                val elapsed = (frameTime - logo.startTime).toFloat()
                val elapsedSeconds = elapsed / 1000f
                val x = logo.startX + logo.velocityX * elapsedSeconds
                val y = logo.startY + logo.velocityY * elapsedSeconds
                val rotation = logo.rotation + logo.rotationSpeed * elapsedSeconds
                val alpha = 1f
                val scaledWidth = logoSize.width * logo.scale
                val scaledHeight = logoSize.height * logo.scale
                translate(x - scaledWidth / 2f, y - scaledHeight / 2f) {
                    rotate(
                        degrees = rotation, pivot = Offset(scaledWidth / 2f, scaledHeight / 2f)
                    ) {
                        with(painter) {
                            draw(
                                size = androidx.compose.ui.geometry.Size(scaledWidth, scaledHeight),
                                alpha = alpha
                            )
                        }
                    }
                }
            }
        }
    }
}
