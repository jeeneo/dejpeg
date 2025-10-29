package com.je.dejpeg.ui.screens

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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.utils.ImageActions
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeforeAfterScreen(viewModel: ProcessingViewModel, imageId: String, navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val images by viewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    var showSaveDialog by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()
    
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
                navigationIcon = {
                    IconButton(onClick = { haptic.light(); navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
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
                IconButton(onClick = { haptic.light(); ImageActions.shareImage(context, afterBitmap) }) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = {
                    haptic.medium()
                    val skip = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getBoolean("skipSaveDialog", false)
                    if (skip) viewModel.saveImage(context, imageId) else showSaveDialog = true
                }) {
                    Icon(Icons.Filled.Save, "Save", modifier = Modifier.size(32.dp))
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
        SaveImageDialog(filename, showSaveAllOption, false, { showSaveDialog = false }) { name, all, skip ->
            if (skip) context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putBoolean("skipSaveDialog", true).apply()
            if (all) viewModel.saveAllImages(context)
            else coroutineScope.launch(Dispatchers.IO) { afterBitmap?.let { ImageActions.saveImage(context, it, filename = name) } }
            showSaveDialog = false
        }
    }
}

@Composable
private fun SliderView(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    haptic: com.je.dejpeg.ui.utils.HapticFeedbackPerformer
) {
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 20f))
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var hasDraggedToCenter by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size },
        Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(0f, 0f, size.width * sliderPosition, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zoomable(zoomableState, true),
                Alignment.Center
            ) {
                Image(
                    beforeBitmap.asImageBitmap(),
                    stringResource(id = R.string.before),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(size.width * sliderPosition, 0f, size.width, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zoomable(zoomableState, true),
                Alignment.Center
            ) {
                Image(
                    afterBitmap.asImageBitmap(),
                    stringResource(id = R.string.after),
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            }
        }
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .offset(x = with(density) { sliderX.toDp() - 2.dp })
                        .shadow(
                            8.dp,
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(0.9f),
                                    Color.White.copy(0.95f),
                                    Color.White.copy(0.9f)
                                )
                            ),
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                        )
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .offset(x = with(density) { sliderX.toDp() - 24.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!isDragging) {
                                        haptic.gestureStart()
                                        isDragging = true
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    sliderPosition = (sliderPosition + dragAmount.x / containerSize.width)
                                        .coerceIn(0f, 1f)
                                    
                                    if (!hasDraggedToCenter && sliderPosition >= 0.48f && sliderPosition <= 0.52f) {
                                        haptic.light()
                                        hasDraggedToCenter = true
                                    } else if (hasDraggedToCenter && (sliderPosition < 0.45f || sliderPosition > 0.55f)) {
                                        hasDraggedToCenter = false
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                }
                            )
                        }
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.3f)),
                        Alignment.Center
                    ) {
                        Text(
                            "◀▶",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}