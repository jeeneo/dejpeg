package com.je.dejpeg.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.je.dejpeg.ui.utils.ImageActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import com.je.dejpeg.R

@Composable
fun BeforeAfterView(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    filename: String?,
    onDismiss: () -> Unit,
    onSaveRequest: ((String, Boolean, Boolean) -> Unit)? = null,
    onShare: ((Bitmap) -> Unit)? = null,
    showSaveAllOption: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val isDarkTheme = isSystemInDarkTheme()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    )) {
        val view = LocalView.current
        LaunchedEffect(isDarkTheme) {
            val window = ((view.parent as? android.view.ViewGroup)?.parent as? android.view.ViewGroup)?.let {
            try { it.javaClass.getDeclaredField("mWindow").apply { isAccessible = true }.get(it) as? Window } catch (_: Exception) { null }
            } ?: (context as? ComponentActivity)?.window
            window?.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
                insetsController?.setSystemBarsAppearance(
                if (!isDarkTheme) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN).let { flags ->
                if (!isDarkTheme) flags or (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0) else flags
                }
            }
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).windowInsetsPadding(WindowInsets.systemBars)) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(filename ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { haptic.light(); onDismiss() }) {
                            Icon(Icons.Filled.Close, "Close")
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (afterBitmap != null) SliderView(beforeBitmap, afterBitmap, haptic)
                    else AsyncImage(beforeBitmap, "Image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (afterBitmap != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceEvenly) {
                            IconButton(onClick = { haptic.light(); ImageActions.shareImage(context, afterBitmap) }) {
                                Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = {
                                haptic.medium()
                                if (onSaveRequest != null) {
                                    try {
                                        val skip = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getBoolean("skipSaveDialog", false)
                                        if (skip) onSaveRequest(filename ?: "", false, false) else showSaveDialog = true
                                    } catch (e: Exception) { showSaveDialog = true }
                                } else {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        ImageActions.saveImage(context, afterBitmap, filename = filename ?: "")
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Save, "Save", modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSaveDialog) {
        SaveImageDialog(filename ?: "", showSaveAllOption, false, { showSaveDialog = false }) { name, all, skip ->
            if (onSaveRequest != null) onSaveRequest(name, all, skip)
            else coroutineScope.launch(Dispatchers.IO) { afterBitmap?.let { ImageActions.saveImage(context, it, filename = name) } }
        }
    }
}

@Composable
private fun SliderView(beforeBitmap: Bitmap?, afterBitmap: Bitmap, haptic: com.je.dejpeg.ui.utils.HapticFeedbackPerformer) {
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 20f))
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var hasDraggedToCenter by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    
    Box(Modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }, Alignment.Center) {
        Box(Modifier.fillMaxSize().drawWithContent {
            clipRect(0f, 0f, size.width * sliderPosition, size.height) { this@drawWithContent.drawContent() }
        }) {
            Box(Modifier.fillMaxSize().zoomable(zoomableState, true), Alignment.Center) {
                Image((beforeBitmap?.asImageBitmap() ?: afterBitmap.asImageBitmap()), stringResource(id = R.string.before), Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            }
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            clipRect(size.width * sliderPosition, 0f, size.width, size.height) { this@drawWithContent.drawContent() }
        }) {
            Box(Modifier.fillMaxSize().zoomable(zoomableState, true), Alignment.Center) {
                Image(afterBitmap.asImageBitmap(), stringResource(id = R.string.after), Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            }
        }
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(4.dp).offset(x = with(density) { sliderX.toDp() - 2.dp }).shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(0.9f), Color.White.copy(0.95f), Color.White.copy(0.9f))), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                Box(Modifier.fillMaxHeight().width(48.dp).offset(x = with(density) { sliderX.toDp() - 24.dp }).pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            if (!isDragging) {
                                haptic.gestureStart()
                                isDragging = true
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            sliderPosition = (sliderPosition + dragAmount.x / containerSize.width).coerceIn(0f, 1f)
                            // slider haptic feedback
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
                }) {
                    Box(Modifier.size(56.dp).align(Alignment.Center).clip(CircleShape).background(Color.White.copy(0.3f)), Alignment.Center) {
                        Text("◀▶", color = Color.White, style = MaterialTheme.typography.titleMedium) // TODO: replace with icon sometime
                    }
                }
            }
        }
    }
}