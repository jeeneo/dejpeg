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
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.je.dejpeg.ModelManager
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.launch
import kotlin.random.Random

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) { "Unknown" }
    var spawnTrigger by remember { mutableLongStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
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
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.open_source_app_description))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogTextButton(
                        stringResource(R.string.source_code),
                        { context.openUrl("https://codeberg.org/dryerlint/dejpeg") },
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
            y < canvasSize.height + scaledHeight * 2f
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
                        startY = -logoSize.height * scale,
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
                    val startY = -(logoSize.height * (Random.nextFloat() * 0.25f + 0.15f))
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
            shape = RoundedCornerShape(16.dp),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { haptic.light(); saveAll = !saveAll }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = saveAll,
                                onCheckedChange = null,
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
                            .clickable { haptic.light(); skipNext = !skipNext }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = skipNext,
                            onCheckedChange = null,
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
        DialogTextButton(stringResource(R.string.cancel), onDismissRequest, { haptic.light() })
    }, confirmButton = {
        DialogPrimaryButton(label = stringResource(R.string.save), onClick = {
            onSave(sanitizeFilename(textState), saveAll, skipNext)
            onDismissRequest()
        }, hapticAction = { haptic.light() })
    })
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

data class FAQSectionData(
    val title: String, val content: String?, val subSections: List<Pair<String, String>>?
)

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

private sealed interface HelpTarget {
    data object Gallery : HelpTarget
    data object Internal : HelpTarget
    data object Documents : HelpTarget
    data object Camera : HelpTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val haptic = rememberHapticFeedback()
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceTile(
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_gallery),
                    label = stringResource(R.string.gallery),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onHelpClick = { haptic.light(); helpTarget = HelpTarget.Gallery },
                    onClick = {
                        haptic.medium(); scope.launch {
                        handleSelection(
                            "gallery", onGallerySelected
                        )
                    }
                    })
                SourceTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Photo,
                    label = stringResource(R.string.internal_picker),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onHelpClick = { haptic.light(); helpTarget = HelpTarget.Internal },
                    onClick = {
                        haptic.medium(); scope.launch {
                        handleSelection(
                            "internal", onInternalSelected
                        )
                    }
                    })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Folder,
                    label = stringResource(R.string.documents),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onHelpClick = { haptic.light(); helpTarget = HelpTarget.Documents },
                    onClick = {
                        haptic.medium(); scope.launch {
                        handleSelection(
                            "documents", onDocumentsSelected
                        )
                    }
                    })
                SourceTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CameraAlt,
                    label = stringResource(R.string.camera),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onHelpClick = { haptic.light(); helpTarget = HelpTarget.Camera },
                    onClick = {
                        haptic.medium(); scope.launch {
                        handleSelection(
                            "camera", onCameraSelected
                        )
                    }
                    })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { haptic.light(); setAsDefault = !setAsDefault }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = setAsDefault,
                    onCheckedChange = { haptic.light(); setAsDefault = it },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceTile(
    modifier: Modifier = Modifier,
    icon: Any,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedCorner by animateFloatAsState(
        targetValue = if (isPressed) 28f else 20f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "tile_corner"
    )
    val animatedIconSize by animateFloatAsState(
        targetValue = if (isPressed) 42f else 36f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ), label = "tile_icon_size"
    )

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(animatedCorner.dp),
            color = containerColor,
            interactionSource = interactionSource
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
                            icon, null, Modifier.size(animatedIconSize.dp), contentColor
                        )

                        is Painter -> Icon(
                            icon, null, Modifier.size(animatedIconSize.dp), contentColor
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
                IconButton(
                    onClick = onHelpClick, modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        stringResource(R.string.help),
                        Modifier.size(24.dp),
                        contentColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
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
    textColor: Color? = null
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
