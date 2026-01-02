package com.je.dejpeg.compose.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.SaveImageDialog
import com.je.dejpeg.compose.ui.components.LoadingDialog
import com.je.dejpeg.compose.utils.HapticFeedbackPerformer
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import androidx.core.graphics.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeforeAfterScreen(
    viewModel: ProcessingViewModel,
    imageId: String,
    onBack: () -> Unit = {},
    showAfter: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val skipSaveDialog by appPreferences.skipSaveDialog.collectAsState(initial = false)

    val images by viewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var showSaveDialog by remember { mutableStateOf(false) }
    var overwriteDialogFilename by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val isSavingImages by viewModel.isSavingImages.collectAsState()
    val savingImagesProgress by viewModel.savingImagesProgress.collectAsState()

    BackHandler(onBack = onBack)

    if (image == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val beforeBitmap = image.inputBitmap
    val afterBitmap = if (showAfter) image.outputBitmap else null
    val filename = image.filename
    val showSaveAllOption = images.any { it.outputBitmap != null }

    val performSave = { bitmap: Bitmap, name: String ->
        ImageActions.saveImage(
            context = context,
            bitmap = bitmap,
            filename = name,
            imageId = imageId,
            onSuccess = {
                viewModel.markImageAsSaved(imageId)
            },
            onError = { errorMsg ->
                saveErrorMessage = errorMsg
            }
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text(filename, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = { haptic.light(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (afterBitmap != null) SliderView(beforeBitmap, afterBitmap, haptic)
            else SingleImageView(beforeBitmap)
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp)
                    .navigationBarsPadding(),
                Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {
                    haptic.light()
                    ImageActions.shareImage(context, afterBitmap ?: beforeBitmap)
                }) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = {
                    haptic.medium()
                    if (skipSaveDialog) {
                        if (ImageActions.checkFileExists(context, filename)) {
                            overwriteDialogFilename = filename
                        } else {
                            viewModel.saveImage(
                                context = context,
                                imageId = imageId,
                                onSuccess = {},
                                onError = { errorMsg -> saveErrorMessage = errorMsg }
                            )
                        }
                    } else {
                        showSaveDialog = true
                    }
                }) {
                    Icon(Icons.Filled.Save, stringResource(id = R.string.save), modifier = Modifier.size(32.dp))
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveImageDialog(filename, showSaveAllOption, false,
            hideOptions = false,
            onDismissRequest = { showSaveDialog = false }) { name, all, skip ->
            if (skip) {
                scope.launch { appPreferences.setSkipSaveDialog(true) }
            }
            if (all) {
                viewModel.saveAllImages(
                    context = context,
                    onComplete = {},
                    onError = { errorMsg -> saveErrorMessage = errorMsg }
                )
            } else {
                if (ImageActions.checkFileExists(context, name)) {
                    overwriteDialogFilename = name
                } else {
                    performSave(afterBitmap ?: beforeBitmap, name)
                }
            }
        }
    }

    overwriteDialogFilename?.let { fname ->
        SaveImageDialog(fname,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogFilename = null }) { name, _, _ ->
            performSave(afterBitmap ?: beforeBitmap, name)
        }
    }

    saveErrorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text(stringResource(R.string.error_saving_image_title)) },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { saveErrorMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (isSavingImages) {
        val progress = savingImagesProgress
        LoadingDialog(
            title = stringResource(R.string.saving_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let { stringResource(R.string.saving_image_progress, it.first, it.second) }
        )
    }
}

@Composable
private fun zoomStateEnabled() = run {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val isHapticEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    zoomState(isHapticEnabled)
}

@Composable
private fun SliderView(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    haptic: HapticFeedbackPerformer
) {
    val zoomableState = zoomStateEnabled()
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val (sliderColor, iconColor) = remember(beforeBitmap) {
        val luminances = mutableListOf<Int>()
        val cx = beforeBitmap.width / 2
        val cy = beforeBitmap.height / 2
        val sample = 10
        for (dy in -sample..sample) {
            for (dx in -sample..sample) {
                val x = (cx + dx).coerceIn(0, beforeBitmap.width - 1)
                val y = (cy + dy).coerceIn(0, beforeBitmap.height - 1)
                val pixel = beforeBitmap[x, y]
                val luminance = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                luminances.add(luminance)
            }
        }
        val median = luminances.sorted()[luminances.size / 2]
        val inverted = 255 - median
        val sliderCol = if (kotlin.math.abs(inverted - median) < 30) {
            if (median > 127) Color.Black else Color.White
        } else {
            Color(inverted, inverted, inverted)
        }
        val sliderLuminance = (sliderCol.red + sliderCol.green + sliderCol.blue) / 3f
        val iconCol = if (sliderLuminance < 0.7f) Color.White else Color.Black
        Pair(sliderCol, iconCol)
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }, Alignment.Center) {
        listOf(
            Triple(beforeBitmap, 0f to sliderPosition, stringResource(R.string.before) to Alignment.TopStart),
            Triple(afterBitmap, sliderPosition to 1f, stringResource(R.string.after) to Alignment.TopEnd)
        ).forEach { (bitmap, range, labelInfo) ->
            val (label, alignment) = labelInfo
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    clipRect(size.width * range.first, 0f, size.width * range.second, size.height) { this@drawWithContent.drawContent() }
                }
            ) {
                Box(Modifier.fillMaxSize().zoomable(zoomableState), Alignment.Center) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                }
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = alignment) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp), modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp))) {
                        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(3.dp).offset(x = with(density) { sliderX.toDp() - 1.5.dp }).background(sliderColor))
                Box(
                    Modifier.fillMaxHeight().width(64.dp).offset(x = with(density) { sliderX.toDp() - 32.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { haptic.gestureStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    sliderPosition = (sliderPosition + dragAmount.x / containerSize.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {}
                            )
                        }
                ) {
                    Box(Modifier.size(48.dp).align(Alignment.Center).shadow(8.dp, CircleShape).clip(CircleShape).background(sliderColor), Alignment.Center) {
                        Icon(Icons.Filled.SwapHoriz, "Drag to compare", tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleImageView(bitmap: Bitmap) {
    val zoomableState = zoomStateEnabled()
    Box(Modifier.fillMaxSize().zoomable(zoomableState), Alignment.Center) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
    }
}

@Composable
private fun zoomState(isHapticEnabled: Boolean) =
    rememberZoomableState(
        ZoomSpec(
            maximum = ZoomLimit(factor = 20f, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled),
            minimum = ZoomLimit(factor = 1f, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled)
        )
    )