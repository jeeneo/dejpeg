/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.view

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.PreparingShareDialog
import com.je.dejpeg.ui.components.horizontalSegmentedShapes
import com.je.dejpeg.ui.processing.ImageItem
import com.je.dejpeg.ui.processing.ProcessingDialogActions
import com.je.dejpeg.ui.processing.ProcessingDialogHost
import com.je.dejpeg.ui.processing.ProcessingViewModel
import com.je.dejpeg.utils.ImageActions
import kotlinx.coroutines.launch

sealed interface ImageMode {
    val imageId: String

    data class Single(override val imageId: String) : ImageMode
    data class Compare(override val imageId: String, val compareImageId: String) : ImageMode
}

fun ImageItem.viewerMode(): ImageMode =
    if (outputBitmap != null) ImageMode.Compare(id, id) else ImageMode.Single(id)

@Composable
fun ImageScreen(
    viewModel: ProcessingViewModel,
    imageRepository: ImageRepository,
    mode: ImageMode,
    onBack: () -> Unit = {},
) {
    val images by imageRepository.images.collectAsState()
    val image = images.firstOrNull { it.id == mode.imageId }
    val compareImage =
        (mode as? ImageMode.Compare)?.compareImageId?.let { id -> images.firstOrNull { it.id == id } }
    if (image == null || (mode is ImageMode.Compare && compareImage == null)) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val appPreferences = remember { AppPreferences() }
    val glassSlider = remember { appPreferences.loadGlassSlider() }

    when (mode) {
        is ImageMode.Single -> SingleImage(
            image = image,
            onBack = onBack,
        )

        is ImageMode.Compare -> CompareImages(
            image = image,
            compareImage = compareImage ?: return,
            viewModel = viewModel,
            glassSlider = glassSlider,
            onBack = onBack,
        )
    }
}

@Composable
private fun SingleImage(
    image: ImageItem,
    onBack: () -> Unit,
) {
    ViewerScaffold(
        title = { Text(image.filename, style = MaterialTheme.typography.titleMedium) },
        onBack = onBack,
    ) {
        BeforeAfterSlider(
            beforeBitmap = image.inputBitmap, modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CompareImages(
    image: ImageItem,
    compareImage: ImageItem,
    viewModel: ProcessingViewModel,
    glassSlider: Boolean,
    onBack: () -> Unit,
) {
    Log.d("tag", "CompareImages")
    val isSelf = image.id == compareImage.id
    val output = image.outputBitmap
    var leftShowsOutput by remember { mutableStateOf(!isSelf && output != null) }
    var rightShowsOutput by remember { mutableStateOf(isSelf && output != null) }

    val context = LocalContext.current
    var isPreparingShare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val dialogActions = remember(viewModel) {
        ProcessingDialogActions(
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            onPickSource = viewModel::onSourcePicked,
            onConfirmSave = viewModel::confirmSave,
            onConfirmOverwrite = viewModel::confirmOverwrite,
            onConfirmRemoval = viewModel::confirmRemoval,
        )
    }

    val beforeBitmap = if (leftShowsOutput) output ?: image.inputBitmap
    else image.inputBitmap
    val afterBitmap = if (rightShowsOutput) compareImage.outputBitmap ?: compareImage.inputBitmap
    else compareImage.inputBitmap

    ViewerScaffold(
        title = {
            if (isSelf) {
                Text(image.filename, style = MaterialTheme.typography.titleMedium)
            } else {
                TopBarItems(
                    image = image,
                    compareImage = compareImage,
                    leftShowsOutput = leftShowsOutput,
                    rightShowsOutput = rightShowsOutput,
                    onLeftSourceChange = { leftShowsOutput = it },
                    onRightSourceChange = { rightShowsOutput = it },
                )
            }
        },
        onBack = onBack,
    ) {
        BeforeAfterSlider(
            beforeBitmap = beforeBitmap,
            afterBitmap = afterBitmap,
            glassSlider = glassSlider,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelf && output != null) {
            ViewerActions(modifier = Modifier.align(Alignment.BottomCenter), onShare = {
                HapticPatterns.tap()
                isPreparingShare = true
                scope.launch {
                    try {
                        ImageActions.shareImageAwait(context, output)
                    } finally {
                        isPreparingShare = false
                    }
                }
            }, onSave = {
                HapticPatterns.tap()
                viewModel.requestSave(listOf(image.id), false)
            })
        }
        if (isPreparingShare) PreparingShareDialog()
    }
    ProcessingDialogHost(
        dialog = dialog,
        saveState = saveState,
        actions = dialogActions,
    )
}

@Composable
private fun ViewerScaffold(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = title,
            navigationIcon = {
                IconButton(onClick = { HapticPatterns.tap(); onBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Box {
            content()
        }
    }
}

@Composable
private fun ViewerActions(
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 28.dp)
            .zIndex(1f)
            .width(IntrinsicSize.Min)
    ) {
        SegmentedListItem(
            modifier = Modifier.weight(1f),
            shapes = horizontalSegmentedShapes(1, 2),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = {
                Icon(
                    Icons.Rounded.Share, contentDescription = "Share image"
                )
            },
            content = {
                Text(
                    stringResource(id = R.string.share_image),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            onClick = onShare
        )
        SegmentedListItem(
            modifier = Modifier.weight(1f),
            shapes = horizontalSegmentedShapes(2, 2),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = {
                Icon(
                    Icons.Rounded.Save, contentDescription = "Save image"
                )
            },
            content = {
                Text(
                    stringResource(id = R.string.save), style = MaterialTheme.typography.bodyMedium
                )
            },
            onClick = onSave
        )
    }
}

@Composable
fun TopBarItems(
    image: ImageItem,
    compareImage: ImageItem,
    leftShowsOutput: Boolean,
    rightShowsOutput: Boolean,
    onLeftSourceChange: (Boolean) -> Unit,
    onRightSourceChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart
        ) {
            SourceDropdown(
                title = image.filename.withOutputSuffix(leftShowsOutput),
                hasOutput = image.outputBitmap != null,
                onSelect = onLeftSourceChange
            )
        }
        Box(
            modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd
        ) {
            SourceDropdown(
                title = compareImage.filename.withOutputSuffix(rightShowsOutput),
                hasOutput = compareImage.outputBitmap != null,
                onSelect = onRightSourceChange
            )
        }
    }
}

private fun String.withOutputSuffix(showsOutput: Boolean) =
    if (showsOutput) "$this (output)" else this

@Composable
private fun SourceDropdown(
    title: String,
    hasOutput: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = {
            HapticPatterns.tap()
            expanded = true
        }) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Input") }, onClick = {
                HapticPatterns.tap()
                onSelect(false)
                expanded = false
            })
            if (hasOutput) {
                DropdownMenuItem(text = { Text("Output") }, onClick = {
                    HapticPatterns.tap()
                    onSelect(true)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun rememberCheckerShader(cellSize: Dp = 8.dp): ShaderBrush {
    val isDark = isSystemInDarkTheme()
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }.toInt()
    val colorA = if (isDark) Color(0xFF262626) else Color(0xFFCCCCCC)
    val colorB = if (isDark) Color(0xFF343434) else Color(0xFFFFFFFF)
    return remember(isDark, cellPx) {
        val size = cellPx * 2
        val tile = createBitmap(size, size)
        val canvas = Canvas(tile)
        val paintA = Paint().apply { color = colorA.toArgb() }
        val paintB = Paint().apply { color = colorB.toArgb() }
        canvas.drawRect(0f, 0f, cellPx.toFloat(), cellPx.toFloat(), paintA)
        canvas.drawRect(cellPx.toFloat(), cellPx.toFloat(), size.toFloat(), size.toFloat(), paintA)
        canvas.drawRect(cellPx.toFloat(), 0f, size.toFloat(), cellPx.toFloat(), paintB)
        canvas.drawRect(0f, cellPx.toFloat(), cellPx.toFloat(), size.toFloat(), paintB)
        ShaderBrush(
            BitmapShader(
                tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
            )
        )
    }
}
