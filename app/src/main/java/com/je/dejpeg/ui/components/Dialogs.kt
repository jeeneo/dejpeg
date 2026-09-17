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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.ImageLoadingHelper
import com.je.dejpeg.utils.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun StyledAlertDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(ScreenHorizontalPadding),
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
                clipboardManager?.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.error), errorMessage)
                )
                scope.launch {
                    SnackbarController.pushEvent(
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
                onClick = { onDismiss() })
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
        onDismissRequest = { onDismiss() },
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
            ) {
                Text(dismissButtonText)
            }
        },
        confirmButton = {
            MorphButton(
                label = resolvedText, onClick = {
                    onConfirm()
                })
        })
}

@Composable
fun SaveImageDialog(
    defaultFilename: String,
    showSaveAllOption: Boolean = false,
    initialSaveAll: Boolean = false,
    overwriteMode: Boolean = false,
    onDismissRequest: () -> Unit,
    onSave: (String, Boolean, Boolean) -> Unit
) {
    val fileExt = defaultFilename.substringBeforeLast('.')
    var textState by remember(defaultFilename) { mutableStateOf(fileExt) }
    var saveAll by remember(initialSaveAll) { mutableStateOf(initialSaveAll) }
    var skipNext by remember { mutableStateOf(false) }
    StyledAlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(stringResource(if (overwriteMode) R.string.overwrite_image else R.string.save_image))
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.filename)) },
                isError = overwriteMode,
                supportingText = if (overwriteMode) {
                    { Text(stringResource(R.string.already_exists)) }
                } else null)
            if (!overwriteMode) {
                Column(Modifier.padding(top = 4.dp)) {
                    if (showSaveAllOption) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveAll = !saveAll }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = saveAll,
                                onCheckedChange = { saveAll = it },
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
                            .clickable { skipNext = !skipNext }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = skipNext,
                            onCheckedChange = { skipNext = it },
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
            onClick = { onDismissRequest() },
        ) {
            Text(stringResource(R.string.nope))
        }
    }, confirmButton = {
        MorphButton(
            label = stringResource(R.string.save), onClick = {
                onSave(sanitizeFilename(textState), saveAll, skipNext)
            })
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
                verticalArrangement = Arrangement.spacedBy(ScreenHorizontalPadding)
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
    imageFilename: String?,
    count: Int = 1,
    onDismissRequest: () -> Unit,
    onRemove: () -> Unit,
    onSaveAndRemove: () -> Unit
) {
    StyledAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                if (count > 1) stringResource(R.string.remove_images_title)
                else stringResource(R.string.remove_image_title)
            )
        },
        text = {
            Text(
                if (count > 1) pluralStringResource(R.plurals.remove_images_question, count, count)
                else stringResource(R.string.remove_image_question, imageFilename.orEmpty())
            )
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissRequest() },
            ) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onRemove()
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(R.string.remove))
                }
                MorphButton(
                    label = stringResource(R.string.save),
                    onClick = { onSaveAndRemove() })
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
                onClick = { onDismissRequest() },
            ) {
                Text(stringResource(R.string.nope))
            }
        },
        confirmButton = {
            MorphButton(
                label = stringResource(R.string.yes_stop),
                onClick = { onConfirm(); onDismissRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            )
        })
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalGridApi::class
)
@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit,
    viewModel: ProcessingViewModel,
) {
    val appPreferences = remember { AppPreferences() }
    var setAsDefault by remember { mutableStateOf(false) }
    val handleSelection: suspend (String, () -> Unit) -> Unit = { key, action ->
        if (setAsDefault) appPreferences.saveDefaultImageSource(key)
        action()
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    LaunchedEffect(sheetState) {
        viewModel.imagePickedEvent.collect {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)
        ) {
            Text(
                text = stringResource(R.string.select_image_source),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)
            ) {
                item {
                    SegmentedListItem(
                        modifier = Modifier.height(100.dp),
                        shapes = CornerRole(topStart = true).toListItemShapes(),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_gallery),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(stringResource(R.string.gallery))
                            }
                        },
                        onClick = {
                            scope.launch { handleSelection("gallery") { viewModel.launchGalleryPicker() } }
                        })
                }
                item {
                    SegmentedListItem(
                        modifier = Modifier.height(100.dp),
                        shapes = CornerRole(topEnd = true).toListItemShapes(),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(stringResource(R.string.media_picker))
                            }
                        },
                        onClick = {
                            scope.launch { handleSelection("internal") { viewModel.launchInternalPhotoPicker() } }
                        })
                }
                item {
                    SegmentedListItem(
                        modifier = Modifier.height(100.dp),
                        shapes = CornerRole().toListItemShapes(),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(stringResource(R.string.documents))
                            }
                        },
                        onClick = {
                            scope.launch { handleSelection("documents") { viewModel.launchDocumentsPicker() } }
                        })

                }
                item {
                    SegmentedListItem(
                        modifier = Modifier.height(100.dp),
                        shapes = CornerRole().toListItemShapes(),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        content = {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(stringResource(R.string.camera))
                            }
                        },
                        onClick = {
                            scope.launch { handleSelection("camera") { viewModel.launchCamera() } }
                        })
                }
            }
            SegmentedListItem(
                checked = setAsDefault, onCheckedChange = {
                    setAsDefault = it
                }, shapes = segmentedShapes(2, 2), colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ), content = {
                    Text(
                        stringResource(R.string.set_as_default),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                })
        }
    }
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
                                ImageLoadingHelper.loadBitmap(
                                    ImageSource.FromFile(
                                        unprocessedFile
                                    )
                                )
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

        StyledAlertDialog(onDismissRequest = {}, title = {
            Text(
                pluralStringResource(
                    R.plurals.recover_images_title, count, count
                )
            )
        }, text = {
            Column {
                Text(pluralStringResource(R.plurals.recover_images_message, count, count))
                if (count <= 3) {
                    Spacer(modifier = Modifier.height(ScreenHorizontalPadding))
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
                    Spacer(modifier = Modifier.height(ScreenHorizontalPadding))
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
        }, dismissButton = {
            TextButton(onClick = {
                clearCache()
            }) { Text(discardButtonText) }
        }, confirmButton = {
            MorphButton(
                label = recoverButtonText, onClick = {
                    Log.d("RecoveryDialog", "User chose to keep recovered images")
                    recoveryImages.value.forEach { img ->
                        val processed = img.processedBitmap
                        val unprocessedFile = CacheManager.getUnprocessedImage(context, img.imageId)
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
                                thumbnailBitmap = ImageLoadingHelper.generateThumbnail(
                                    processed
                                ),
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

@Composable
fun Heading(title: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun PowerSlider(
    label: String? = null,
    value: Int? = null,
    powers: List<Int>,
    maxAllowed: Int = Int.MAX_VALUE,
    onChange: (Int) -> Unit,
    hideValue: Boolean = false,
) {
    val effectivePowers = remember(powers, maxAllowed) {
        powers.filter { it <= maxAllowed }.ifEmpty { listOf(powers.first()) }
    }
    val clampedValue = value?.coerceAtMost(effectivePowers.last())
    var index by remember(clampedValue, effectivePowers) {
        mutableIntStateOf(maxOf(effectivePowers.indexOf(clampedValue), 0))
    }
    val sliderState = rememberSliderState(
        value = index.toFloat(),
        steps = (effectivePowers.size - 2).coerceAtLeast(0),
        trackRange = 0f..(effectivePowers.lastIndex.toFloat().coerceAtLeast(0f)),
    )
    LaunchedEffect(index) { sliderState.value = index.toFloat() }
    LaunchedEffect(maxAllowed) {
        if (value != null && value >= maxAllowed && effectivePowers.isNotEmpty()) {
            onChange(effectivePowers.last())
        }
    }
    Column {
        Row {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!hideValue) Text(
                " • ${effectivePowers[index]}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Slider(
                state = sliderState, onValueChange = {
                    val newIdx = it.roundToInt().coerceIn(effectivePowers.indices)
                    if (newIdx != index) {
                        index = newIdx
                        // ha
                        onChange(effectivePowers[newIdx])
                    }
                }, enabled = effectivePowers.size > 1
            )
        }
    }
}


@Composable
fun LabeledSwitch(
    title: String,
    desc: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc.isNotEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
            onCheckedChange = {
                onCheckedChange(!checked)
            },
            modifier = Modifier
                .padding(end = GroupedListSpacing)
                .height(24.dp)
                .aspectRatio(2f)
                .wrapContentSize(Alignment.Center),
        )
    }
}

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    title: String,
    subtitle: String? = "",
    icon: Any,
    iconTint: Color? = null,
    expanded: Boolean = false,
    expandedContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    index: Int = 1,
    count: Int = 1,
) {
    val colors =
        ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val pill = index == count
    Column(modifier = modifier.fillMaxWidth()) {
        SegmentedListItem(
            colors = colors,
            shapes = CornerRole(
                bottomStart = !expanded, bottomEnd = !expanded, topStart = pill, topEnd = pill
            ).toListItemShapes(),
            onClick = { onClick() },
            leadingContent = {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint ?: Color.Unspecified,
                        modifier = Modifier.size(21.dp)
                    )

                    is Painter -> Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = iconTint ?: Color.Unspecified,
                        modifier = Modifier.size(21.dp)
                    )
                }
            },
            content = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AnimatedVisibility(
                        visible = !subtitle.isNullOrEmpty(),
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Spacer(modifier = Modifier.height(3.dp))
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            trailingContent = {
                if (trailing != null) {
                    trailing()
                } else {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 90f else 0f, label = "chevron"
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(34.dp)
                            .rotate(chevronRotation)
                    )
                }
            })
        Spacer(modifier = Modifier.height(GroupedListSpacing))
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SegmentedListItem(
                    shapes = segmentedShapes(2, 2), colors = colors
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GroupedListSpacing),
                    ) {
                        expandedContent?.invoke()
                    }
                }
            }
        }
    }
}
