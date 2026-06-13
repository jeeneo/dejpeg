/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.je.dejpeg.AppPreferences
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.ImageRepository
import com.je.dejpeg.R
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ImageLoadingHelper
import com.je.dejpeg.utils.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        shape = RoundedCornerShape(16.dp),
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
fun ErrorAlertDialog(
    title: String,
    errorMessage: String,
    onDismiss: () -> Unit,
    context: Context,
    confirmButtonText: String? = null
) {
    val clipboardManager =
        remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(errorMessage) },
        dismissButton = {
            val scope = rememberCoroutineScope()
            TextButton(onClick = {
                HapticFeedbacks.light()
                clipboardManager?.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.error), errorMessage)
                )
                scope.launch {
                    SnackySnackbarController.pushEvent(
                        SnackySnackbarEvents.MessageEvent(
                            message = context.getString(R.string.error_copied),
                            duration = SnackbarDuration.Short
                        )
                    )
                }
            }) { Text(stringResource(R.string.copy)) }
        },
        confirmButton = {
            MorphButton(
                label = confirmButtonText ?: stringResource(R.string.ok),
                onClick = { HapticFeedbacks.light(); onDismiss() })
        })
}

@Composable
fun SimpleAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmButtonText: String? = null,
    onConfirm: () -> Unit = onDismiss,
    dismissButtonText: String = "",
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null
) {
    val resolvedText = confirmButtonText ?: stringResource(R.string.ok)
    StyledAlertDialog(
        onDismissRequest = { HapticFeedbacks.light(); onDismiss() },
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = {
            TextButton(
                onClick = { onDismiss(); HapticFeedbacks.light() },
            ) {
                Text(dismissButtonText)
            }
        },
        confirmButton = {
            DialogPrimaryButton(resolvedText, { onConfirm() }, { HapticFeedbacks.light() })
        })
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
    var textState by remember(defaultFilename) { mutableStateOf(fileExt) }
    var saveAll by remember(initialSaveAll) { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { HapticFeedbacks.light(); saveAll = !saveAll }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = saveAll,
                                onCheckedChange = { HapticFeedbacks.light(); saveAll = it },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.save_all),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { HapticFeedbacks.light(); skipNext = !skipNext }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = skipNext,
                            onCheckedChange = { HapticFeedbacks.light(); skipNext = it },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.dont_show_dialog),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }, dismissButton = {
        TextButton(
            onClick = { onDismissRequest(); HapticFeedbacks.light() },
        ) {
            Text(stringResource(R.string.nope))
        }
    }, confirmButton = {
        DialogPrimaryButton(stringResource(R.string.save), {
            onSave(sanitizeFilename(textState), saveAll, skipNext)
            onDismissRequest()
        }, { HapticFeedbacks.light() })
    })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreparingShareDialog(
    progressText: String? = null,
    progress: Float? = null,
    title: String? = null,
) {
    val thickStrokeWidth = with(LocalDensity.current) { 8.dp.toPx() }
    val thickStroke = remember(thickStrokeWidth) {
        Stroke(width = thickStrokeWidth, cap = StrokeCap.Round)
    }
    BasicAlertDialog(
        onDismissRequest = {}, properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.wrapContentSize()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator(modifier = Modifier.size(80.dp))
                    if (progress != null) {
                        CircularWavyProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(88.dp),
                            stroke = thickStroke,
                            trackStroke = thickStroke,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(88.dp),
                            stroke = thickStroke,
                            trackStroke = thickStroke,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                progressText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.remove_image_title)) },
        text = { Text(stringResource(R.string.remove_image_question, imageFilename)) },
        dismissButton = {
            TextButton(
                onClick = { onDismissRequest(); HapticFeedbacks.light() },
            ) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // color red
                TextButton(
                    onClick = {
                        onDismissRequest()
                        CacheManager.deleteRecoveryPair(
                            context, imageId, deleteProcessed = true, deleteUnprocessed = true
                        )
                        onRemove()
                        HapticFeedbacks.light()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
                if (hasOutput) {
                    MorphButton(
                        label = stringResource(R.string.save),
                        onClick = { HapticFeedbacks.medium(); onSaveAndRemove(); onDismissRequest() })

                }
            }
        })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CancelProcessingDialog(
    imageFilename: String? = null, onDismissRequest: () -> Unit, onConfirm: () -> Unit
) {
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
            TextButton(
                onClick = { onDismissRequest(); HapticFeedbacks.light() },
            ) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            MorphButton(
                label = stringResource(R.string.yes_stop),
                onClick = { HapticFeedbacks.heavy(); onConfirm(); onDismissRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            )
        })
}

private sealed interface HelpTarget {
    data object Gallery : HelpTarget
    data object Internal : HelpTarget
    data object Documents : HelpTarget
    data object Camera : HelpTarget
}

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit,
    onGallerySelected: () -> Unit,
    onInternalSelected: () -> Unit,
    onDocumentsSelected: () -> Unit,
    onCameraSelected: () -> Unit
) {
    val appPreferences = remember { AppPreferences() }
    val scope = rememberCoroutineScope()
    var setAsDefault by remember { mutableStateOf(false) }
    var helpTarget by remember { mutableStateOf<HelpTarget?>(null) }

    val handleSelection: suspend (String, () -> Unit) -> Unit = { key, action ->
        if (setAsDefault) appPreferences.setDefaultImageSource(key)
        onDismiss()
        action()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_image_source),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    GroupedSourceTile(
                        modifier = Modifier.weight(1f),
                        icon = painterResource(R.drawable.ic_gallery),
                        label = stringResource(R.string.gallery),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        topStart = 28f,
                        topEnd = 16f,
                        bottomStart = 16f,
                        bottomEnd = 16f,
                        onHelpClick = { HapticFeedbacks.light(); helpTarget = HelpTarget.Gallery },
                        onClick = {
                            HapticFeedbacks.medium()
                            scope.launch { handleSelection("gallery", onGallerySelected) }
                        })
                    GroupedSourceTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Photo,
                        label = stringResource(R.string.internal_picker),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        topStart = 16f,
                        topEnd = 28f,
                        bottomStart = 16f,
                        bottomEnd = 16f,
                        onHelpClick = { HapticFeedbacks.light(); helpTarget = HelpTarget.Internal },
                        onClick = {
                            HapticFeedbacks.medium()
                            scope.launch { handleSelection("internal", onInternalSelected) }
                        })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    GroupedSourceTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Folder,
                        label = stringResource(R.string.documents),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        topStart = 16f,
                        topEnd = 16f,
                        bottomStart = 28f,
                        bottomEnd = 16f,
                        onHelpClick = {
                            HapticFeedbacks.light(); helpTarget = HelpTarget.Documents
                        },
                        onClick = {
                            HapticFeedbacks.medium()
                            scope.launch { handleSelection("documents", onDocumentsSelected) }
                        })
                    GroupedSourceTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.CameraAlt,
                        label = stringResource(R.string.camera),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        topStart = 16f,
                        topEnd = 16f,
                        bottomStart = 16f,
                        bottomEnd = 28f,
                        onHelpClick = { HapticFeedbacks.light(); helpTarget = HelpTarget.Camera },
                        onClick = {
                            HapticFeedbacks.medium()
                            scope.launch { handleSelection("camera", onCameraSelected) }
                        })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { HapticFeedbacks.light(); setAsDefault = !setAsDefault }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = setAsDefault,
                    onCheckedChange = { HapticFeedbacks.light(); setAsDefault = it },
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    stringResource(R.string.set_as_default),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    helpTarget?.let { target ->
        val (titleRes, descRes) = when (target) {
            HelpTarget.Gallery -> R.string.gallery_picker_title to R.string.gallery_picker_desc
            HelpTarget.Internal -> R.string.internal_picker_title to R.string.internal_picker_desc
            HelpTarget.Documents -> R.string.documents_picker_title to R.string.documents_picker_desc
            HelpTarget.Camera -> R.string.camera_title to R.string.camera_desc
        }
        HelpDialog(
            title = stringResource(titleRes),
            text = stringResource(descRes),
            onDismiss = { helpTarget = null })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupedSourceTile(
    modifier: Modifier = Modifier,
    icon: Any,
    label: String,
    containerColor: Color,
    contentColor: Color,
    topStart: Float,
    topEnd: Float,
    bottomStart: Float,
    bottomEnd: Float,
    onClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press by rememberMaterialPressState(interactionSource)

    val targetTopStart = if (topStart == 16f) 28f else topStart
    val targetTopEnd = if (topEnd == 16f) 28f else topEnd
    val targetBottomStart = if (bottomStart == 16f) 28f else bottomStart
    val targetBottomEnd = if (bottomEnd == 16f) 28f else bottomEnd

    val animTopStart = lerp(topStart, targetTopStart, press)
    val animTopEnd = lerp(topEnd, targetTopEnd, press)
    val animBottomStart = lerp(bottomStart, targetBottomStart, press)
    val animBottomEnd = lerp(bottomEnd, targetBottomEnd, press)

    Box(
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onHelpClick
                ), shape = RoundedCornerShape(
                topStart = animTopStart.dp,
                topEnd = animTopEnd.dp,
                bottomStart = animBottomStart.dp,
                bottomEnd = animBottomEnd.dp
            ), color = containerColor
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (icon) {
                        is ImageVector -> Icon(
                            icon, null, Modifier.size(34.dp), contentColor
                        )

                        is Painter -> Icon(
                            icon, null, Modifier.size(34.dp), contentColor
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(title: String, text: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.padding(top = 48.dp),
        containerColor = MaterialTheme.colorScheme.inverseOnSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogPrimaryButton(
    label: String, onClick: () -> Unit, hapticAction: () -> Unit, enabled: Boolean = true
) {
    MorphButton(
        label = label, onClick = {
            hapticAction()
            onClick()
        }, enabled = enabled
    )
}

@Composable
fun MorphButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressProgress by rememberMaterialPressState(interactionSource)
    val cornerRadius = lerp(50f, 6f, pressProgress)

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = colors,
        interactionSource = interactionSource
    ) { Text(label) }
}

private fun sanitizeFilename(input: String, fallback: String = "image"): String {
    return input.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").takeIf { it.isNotBlank() }
        ?: fallback
}

@Composable
fun RecoveryDialog(
    imageRepository: ImageRepository
) {
    data class RecoveryImage(
        val imageId: String,
        val label: String,
        val processedBitmap: Bitmap,
        val unprocessedBitmap: Bitmap?
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recoveryImages = remember { mutableStateOf<List<RecoveryImage>>(emptyList()) }
    val showDialog = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CacheManager.clearChunks(context)
        CacheManager.clearAbandonedImages(context)
        val cachedRecoveryImages = CacheManager.getRecoveryImages(context)
        if (cachedRecoveryImages.isNotEmpty()) {
            val existingIds = imageRepository.images.value.map { it.id }.toSet()
            val newRecoveryImages = cachedRecoveryImages.filter { (id, _) -> id !in existingIds }
            if (newRecoveryImages.isNotEmpty()) {
                val loadedImages = mutableListOf<RecoveryImage>()
                for ((imageId, file) in newRecoveryImages) {
                    val processedBitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (processedBitmap != null) {
                        val unprocessedFile = CacheManager.getUnprocessedImage(context, imageId)
                        val unprocessedBitmap = if (unprocessedFile != null) {
                            withContext(Dispatchers.IO) {
                                ImageLoadingHelper.loadBitmap(ImageSource.FromFile(unprocessedFile))
                            }
                        } else null
                        loadedImages.add(
                            RecoveryImage(
                                imageId,
                                "Recovered_${imageId.take(8)}",
                                processedBitmap,
                                unprocessedBitmap
                            )
                        )
                    }
                }
                recoveryImages.value = loadedImages
                showDialog.value = loadedImages.isNotEmpty()
            }
        }
    }

    if (showDialog.value && recoveryImages.value.isNotEmpty()) {
        val count = recoveryImages.value.size
        val recoveredImagePrefix = stringResource(R.string.recovered_image_prefix)
        val recoverButtonText = stringResource(R.string.recover)
        val discardButtonText = stringResource(R.string.discard)

        fun clearCache() {
            Log.d("RecoveryDialog", "User chose to discard recovery images")
            showDialog.value = false
            scope.launch(Dispatchers.IO) {
                CacheManager.getRecoveryImages(context).forEach { (id, _) ->
                    CacheManager.deleteRecoveryPair(
                        context, id, deleteProcessed = true, deleteUnprocessed = true
                    )
                }
            }
        }

        StyledAlertDialog(
            onDismissRequest = {},
            title = { Text(pluralStringResource(R.plurals.recover_images_title, count, count)) },
            text = {
                Column {
                    Text(pluralStringResource(R.plurals.recover_images_message, count, count))
                    if (count <= 3) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recoveryImages.value.take(3).forEach { img ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Image(
                                        bitmap = img.processedBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.image),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recoveryImages.value.take(3).forEach { img ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Image(
                                        bitmap = img.processedBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.image),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${count - 3}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    HapticFeedbacks.light()
                    clearCache()
                }) { Text(discardButtonText) }
            },
            confirmButton = {
                MorphButton(
                    label = recoverButtonText, onClick = {
                        Log.d("RecoveryDialog", "User chose to keep recovered images")
                        HapticFeedbacks.medium()
                        recoveryImages.value.forEach { img ->
                            val processed = img.processedBitmap
                            val unprocessedFile =
                                CacheManager.getUnprocessedImage(context, img.imageId)
                            val uri = unprocessedFile?.let {
                                FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", it
                                )
                            }
                            imageRepository.addImage(
                                ImageItem(
                                    id = img.imageId,
                                    uri = uri,
                                    filename = recoveredImagePrefix,
                                    inputBitmap = img.unprocessedBitmap ?: processed,
                                    outputBitmap = processed,
                                    thumbnailBitmap = ImageLoadingHelper.generateThumbnail(processed),
                                    size = "${processed.width}x${processed.height}",
                                    hasBeenSaved = false
                                )
                            )
                        }
                        showDialog.value = false
                    })
            })
    }
}

