package com.je.dejpeg.compose.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.SaveImageDialog
import com.je.dejpeg.compose.ui.components.LoadingDialog
import com.je.dejpeg.compose.utils.HapticFeedbackPerformer
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeforeAfterScreen(viewModel: ProcessingViewModel, imageId: String, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val images by viewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var showSaveDialog by remember { mutableStateOf(false) }
    var overwriteDialogFilename by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val isDarkTheme = isSystemInDarkTheme()
    val isSavingImages by viewModel.isSavingImages.collectAsState()
    val savingImagesProgress by viewModel.savingImagesProgress.collectAsState()
    
    DisposableEffect(isDarkTheme) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) 
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose { }
    }
    
    BackHandler(onBack = { navController.popBackStack() })
    
    if (image == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    
    val beforeBitmap = image.inputBitmap
    val afterBitmap = image.outputBitmap
    val filename = image.filename
    val showSaveAllOption = images.any { it.outputBitmap != null }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(filename, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {IconButton(onClick = { haptic.light(); navController.popBackStack() }) {Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")}},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                if (afterBitmap != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 8.dp + with(LocalDensity.current) { 22f.toDp() },
                                    bottom = 16.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                                .navigationBarsPadding(),
                            Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = { haptic.light(); ImageActions.shareImage(context, afterBitmap) }) { Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(32.dp))}
                            
                            IconButton(onClick = {
                                haptic.medium()
                                val skip = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getBoolean("skipSaveDialog", false)
                                if (skip) {
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
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.surface)) {
            if (afterBitmap != null) SliderView(beforeBitmap, afterBitmap, haptic)
            else AsyncImage(beforeBitmap, "Image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
    
    if (showSaveDialog) {
        SaveImageDialog(filename, showSaveAllOption, false, false, { showSaveDialog = false }) { name, all, skip ->
            if (skip) context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putBoolean("skipSaveDialog", true).apply()
            if (all) {
                viewModel.saveAllImages(
                    context = context,
                    onComplete = {},
                    onError = { errorMsg -> saveErrorMessage = errorMsg }
                )
            } else {
                if (ImageActions.checkFileExists(context, name)) {
                    showSaveDialog = false
                    overwriteDialogFilename = name
                } else {
                    afterBitmap?.let { bitmap ->
                        ImageActions.saveImage(
                            context = context,
                            bitmap = bitmap,
                            filename = name,
                            onSuccess = {
                                viewModel.markImageAsSaved(imageId)
                            },
                            onError = { errorMsg ->
                                saveErrorMessage = errorMsg
                            }
                        )
                    }
                    showSaveDialog = false
                }
            }
        }
    }
    
    overwriteDialogFilename?.let { fname ->
        SaveImageDialog(fname, false, false, true, { overwriteDialogFilename = null }) { name, _, _ ->
            afterBitmap?.let { bitmap ->
                ImageActions.saveImage(
                    context = context,
                    bitmap = bitmap,
                    filename = name,
                    onSuccess = {
                        viewModel.markImageAsSaved(imageId)
                    },
                    onError = { errorMsg ->
                        saveErrorMessage = errorMsg
                    }
                )
            }
            overwriteDialogFilename = null
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
private fun SliderView(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    haptic: HapticFeedbackPerformer
) {
    val zoomableState = rememberZoomableState(ZoomSpec(maxZoomFactor = 20f))
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var hasDraggedToCenter by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }, Alignment.Center) {
        listOf(Pair(beforeBitmap, 0f to sliderPosition), Pair(afterBitmap, sliderPosition to 1f)).forEach { (bitmap, range) ->
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    clipRect(size.width * range.first, 0f, size.width * range.second, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            ) {
                Box(Modifier.fillMaxSize().zoomable(zoomableState, enabled = true), Alignment.Center) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxHeight().width(4.dp).offset(x = with(density) { sliderX.toDp() - 2.dp })
                        .shadow(8.dp, RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.9f), Color.White.copy(0.95f), Color.White.copy(0.9f))
                            ),
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                        )
                )
                Box(
                    Modifier.fillMaxHeight().width(48.dp).offset(x = with(density) { sliderX.toDp() - 24.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { if (!isDragging) { haptic.gestureStart(); isDragging = true } },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    sliderPosition = (sliderPosition + dragAmount.x / containerSize.width).coerceIn(0f, 1f)
                                    if (!hasDraggedToCenter && sliderPosition in 0.48f..0.52f) {
                                        haptic.light(); hasDraggedToCenter = true
                                    } else if (hasDraggedToCenter && sliderPosition !in 0.45f..0.55f) {
                                        hasDraggedToCenter = false
                                    }
                                },
                                onDragEnd = { isDragging = false }
                            )
                        }
                ) {
                    Box(
                        Modifier.size(56.dp).align(Alignment.Center).clip(CircleShape)
                            .background(Color.White.copy(0.3f)), Alignment.Center
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}