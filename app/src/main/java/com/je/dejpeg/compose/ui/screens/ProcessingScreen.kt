package com.je.dejpeg.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.max
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.je.dejpeg.compose.ui.components.BatteryOptimizationDialog
import com.je.dejpeg.compose.ui.components.RemoveImageDialog
import com.je.dejpeg.compose.ui.components.CancelProcessingDialog
import com.je.dejpeg.compose.ui.components.ImageSourceDialog
import com.je.dejpeg.ui.utils.ImageActions
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(viewModel: ProcessingViewModel, navController: NavController, initialSharedUris: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val globalStrength by viewModel.globalStrength.collectAsState()
    val supportsStrength = viewModel.supportsStrengthAdjustment()
    val shouldShowNoModelDialog by viewModel.shouldShowNoModelDialog.collectAsState()
    val shouldShowBatteryOptimizationDialog by viewModel.shouldShowBatteryOptimizationDialog.collectAsState()
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val deprecatedModelWarning by viewModel.deprecatedModelWarning.collectAsState()
    var imageIdToRemove by remember { mutableStateOf<String?>(null) }
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    val handleImageRemoval: (String) -> Unit = { imageId ->
        images.firstOrNull { it.id == imageId }?.let { image ->
            when {
                image.isProcessing -> imageIdToCancel = imageId
                image.outputBitmap != null && !image.hasBeenSaved -> imageIdToRemove = imageId
                else -> viewModel.removeImage(imageId)
            }
        }
    }

    val performRemoval: (String) -> Unit = { imageId ->
        viewModel.removeImage(imageId, force = true)
        imageIdToRemove = null
        imageIdToCancel = null
    }
    
    LaunchedEffect(images) {
        imageIdToRemove?.let { id ->
            if (images.none { it.id == id }) {
                imageIdToRemove = null
            }
        }
        imageIdToCancel?.let { id ->
            if (images.none { it.id == id }) {
                imageIdToCancel = null
            }
        }
        overwriteDialogState?.let { (id, _) ->
            if (images.none { it.id == id }) {
                overwriteDialogState = null
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.initialize(context) }

    val processedShareUris = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(initialSharedUris, images) {
        if (initialSharedUris.isNotEmpty()) {
            val existing = images.mapNotNull { it.uri?.toString() }.toSet()
            val toAdd = initialSharedUris.filter { uri -> uri.toString() !in existing && uri.toString() !in processedShareUris.value }
            toAdd.forEach { viewModel.addImageFromUri(context, it) }
            if (toAdd.isNotEmpty()) processedShareUris.value += toAdd.map { it.toString() }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let { uris.add(it) }
            } ?: result.data?.data?.let { uris.add(it) } ?: viewModel.getCameraPhotoUri()?.let {
                uris.add(it)
                viewModel.clearCameraPhotoUri()
            }
            uris.forEach { viewModel.addImageFromUri(context, it) }
        }
    }
        
    LaunchedEffect(Unit) { viewModel.setImagePickerLauncher(imagePickerLauncher) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.images, images.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FloatingActionButton(
                onClick = {
                    haptic.light()
                    when (context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE).getString("defaultImageSource", null)) {
                        "gallery" -> viewModel.launchGalleryPicker(context)
                        "internal" -> viewModel.launchInternalPhotoPicker(context)
                        "documents" -> viewModel.launchDocumentsPicker(context)
                        "camera" -> viewModel.launchCamera(context)
                        else -> showImageSourceDialog = true
                    }
                }, 
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) { 
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_images)) 
            }
        }

        if (images.isNotEmpty() && supportsStrength) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp), 
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.strength, globalStrength.toInt()), 
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    var prevStrength by remember { mutableFloatStateOf(globalStrength) }
                    Slider(value = globalStrength, onValueChange = { val v = (it / 5).roundToInt() * 5f; if (v != prevStrength) { haptic.light(); prevStrength = v }; viewModel.setGlobalStrength(v) }, valueRange = 0f..100f, steps = 20, modifier = Modifier.fillMaxWidth().height(24.dp))
                }
            }
        }
        
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.no_images_yet), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tap_to_add_images), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it.id }) { image ->
                    val swipeState = remember { mutableStateOf(0f) }
                    SwipeToDismissWrapper(
                        swipeOffset = swipeState,
                        isProcessing = image.isProcessing,
                        onDismissed = { handleImageRemoval(image.id) }
                    ) {
                        ImageCard(
                            image = image,
                            supportsStrength = supportsStrength,
                            onStrengthChange = { viewModel.updateImageStrength(image.id, it) },
                            onRemove = { handleImageRemoval(image.id) },
                            onProcess = {
                                if (!viewModel.hasActiveModel()) viewModel.showNoModelDialog()
                                else viewModel.processImage(image.id)
                            },
                            onBrisque = {
                                haptic.light()
                                navController.navigate(com.je.dejpeg.ui.Screen.BRISQUE.createRoute(image.id))
                            },
                            onClick = {
                                if (image.outputBitmap != null) {
                                    haptic.light()
                                    navController.navigate(com.je.dejpeg.ui.Screen.BeforeAfter.createRoute(image.id))
                                }
                            }
                        )
                    }
                    LaunchedEffect(imageIdToRemove, imageIdToCancel) {
                        if (imageIdToRemove != image.id && imageIdToCancel != image.id) swipeState.value = 0f
                    }
                }
            }
        }

        if (images.isNotEmpty()) {
            Button(
                onClick = { 
                    if (uiState is ProcessingUiState.Processing) {
                        haptic.heavy()
                        showCancelAllDialog = true
                    } else {
                        if (!viewModel.hasActiveModel()) {
                            viewModel.showNoModelDialog()
                        } else {
                            haptic.medium()
                            viewModel.processImages()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp),
                colors = if (uiState is ProcessingUiState.Processing) 
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
                else 
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) { 
                Text(
                    if (uiState is ProcessingUiState.Processing) 
                        stringResource(R.string.cancel_processing) 
                    else 
                        stringResource(R.string.process_all), 
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ) 
            }
        }
    }

    imageIdToRemove?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            RemoveImageDialog(
                imageFilename = image.filename,
                hasOutput = image.outputBitmap != null,
                onDismissRequest = { imageIdToRemove = null },
                onRemove = { performRemoval(targetId) },
                onSaveAndRemove = {
                    if (ImageActions.checkFileExists(context, image.filename)) {
                        imageIdToRemove = null
                        overwriteDialogState = Pair(targetId, image.filename)
                    } else {
                        viewModel.saveImage(
                            context = context,
                            imageId = targetId,
                            onSuccess = { performRemoval(targetId) },
                            onError = { 
                                imageIdToRemove = null
                                saveErrorMessage = it
                            }
                        )
                    }
                }
            )
        } ?: run { imageIdToRemove = null }
    }

    imageIdToCancel?.let { targetId ->
        images.firstOrNull { it.id == targetId }?.let { image ->
            CancelProcessingDialog(
                imageFilename = image.filename,
                onDismissRequest = { imageIdToCancel = null },
                onConfirm = { 
                    viewModel.cancelProcessingService(targetId)
                    imageIdToCancel = null
                }
            )
        } ?: run { imageIdToCancel = null }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showImageSourceDialog = false },
            onGallerySelected = { viewModel.launchGalleryPicker(context) },
            onInternalSelected = { viewModel.launchInternalPhotoPicker(context) },
            onDocumentsSelected = { viewModel.launchDocumentsPicker(context) },
            onCameraSelected = { viewModel.launchCamera(context) }
        )
    }

    if (showCancelAllDialog) {
        CancelProcessingDialog(
            imageFilename = stringResource(R.string.all_images),
            onDismissRequest = { showCancelAllDialog = false },
            onConfirm = {
                viewModel.cancelProcessing()
                showCancelAllDialog = false
            }
        )
    }

    if (shouldShowNoModelDialog) {
        NoModelDialog(
            onDismiss = { viewModel.dismissNoModelDialog() },
            onGoToSettings = {
                viewModel.dismissNoModelDialog()
                navController.navigate("settings")
            }
        )
    }

    if (shouldShowBatteryOptimizationDialog) {
        BatteryOptimizationDialogForProcessing(
            onDismiss = { viewModel.dismissBatteryOptimizationDialog() },
            context = context
        )
    }

    deprecatedModelWarning?.let { warning ->
        val activeModelName = viewModel.getActiveModelName() ?: ""
        DeprecatedModelWarningDialog(
            modelName = activeModelName,
            warning = warning,
            onContinue = { viewModel.dismissDeprecatedModelWarning() },
            onGoToSettings = {
                viewModel.dismissDeprecatedModelWarning()
                navController.navigate("settings")
            }
        )
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

    overwriteDialogState?.let { (id, fn) ->
        com.je.dejpeg.compose.ui.components.SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogState = null },
            onSave = { name, _, _ ->
                viewModel.saveImage(
                    context = context,
                    imageId = id,
                    filename = name,
                    onSuccess = { 
                        performRemoval(id)
                        overwriteDialogState = null
                    },
                    onError = { 
                        overwriteDialogState = null
                        saveErrorMessage = it
                    }
                )
            }
        )
    }
}

@Composable
fun SwipeToDismissWrapper(swipeOffset: MutableState<Float>, isProcessing: Boolean, onDismissed: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val animatedOffset by animateFloatAsState(swipeOffset.value, label = "swipe")
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalResources.current.displayMetrics.density
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    var hasStartedDrag by remember { mutableStateOf(false) }
    var hasReachedThreshold by remember { mutableStateOf(false) }
    val cardHeight = 96.dp
    Box(Modifier.fillMaxWidth().height(cardHeight).onSizeChanged { widthPx = it.width }) {
        if (animatedOffset > 0f) Box(
            Modifier
                .width((animatedOffset / density).dp)
                .height(cardHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEF5350)), 
            Alignment.Center
        ) {
            Icon(if (isProcessing) Icons.Filled.Close else Icons.Filled.Delete, if (isProcessing) "Cancel" else "Delete", Modifier.size(28.dp), Color.White)
        }
        Box(Modifier.fillMaxWidth().height(cardHeight).offset { IntOffset(animatedOffset.roundToInt(), 0) }.pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { if (!hasStartedDrag) { haptic.gestureStart(); hasStartedDrag = true } }, onHorizontalDrag = { _, dragAmount -> swipeOffset.value = max(0f, swipeOffset.value + dragAmount); if (widthPx > 0 && swipeOffset.value > widthPx * 0.35f && !hasReachedThreshold) { haptic.medium(); hasReachedThreshold = true } else if (swipeOffset.value <= widthPx * 0.35f) hasReachedThreshold = false }, onDragEnd = { if (widthPx > 0 && swipeOffset.value > widthPx * 0.35f) { haptic.heavy(); onDismissed() }; scope.launch { swipeOffset.value = 0f; hasStartedDrag = false; hasReachedThreshold = false } })
        }) { content() }
    }
}

@Composable
fun NoModelDialog(onDismiss: () -> Unit, onGoToSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.no_model_installed_title)) }, 
        text = { Text(stringResource(R.string.no_model_installed_desc)) }, 
        confirmButton = { TextButton(onClick = onGoToSettings) { Text(stringResource(R.string.go_to_settings)) } }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun BatteryOptimizationDialogForProcessing(onDismiss: () -> Unit, context: Context) {
    val errorMessage = context.getString(R.string.cannot_open_link_detail)
    BatteryOptimizationDialog(onDismissRequest = onDismiss, onOpenSettings = {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Intent(android.provider.Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) { android.widget.Toast.makeText(context, "$errorMessage: ${e.message ?: ""}", android.widget.Toast.LENGTH_SHORT).show() }
    })
}

@Composable
fun CountdownTimer(initialTimeMillis: Long, startTimeMillis: Long, isActive: Boolean) {
    var displayText by remember { mutableStateOf("") }
    val finishingUpText = stringResource(R.string.finishing_up)
    LaunchedEffect(initialTimeMillis, startTimeMillis, isActive) {
        if (!isActive || initialTimeMillis <= 0) { displayText = ""; return@LaunchedEffect }
        while (isActive) {
            val elapsedMillis = System.currentTimeMillis() - startTimeMillis
            val remainingMillis = (initialTimeMillis - elapsedMillis).coerceAtLeast(0)
            if (remainingMillis <= 0) { displayText = finishingUpText; break }
            val seconds = remainingMillis / 1000
            displayText = when {
                seconds < 60 -> "$seconds s"
                seconds < 3600 -> { val minutes = seconds / 60; val remainingSeconds = seconds % 60; if (remainingSeconds == 0L) if (minutes == 1L) "1 minute" else "$minutes m" else if (minutes == 1L) "1m, $remainingSeconds s" else "$minutes m, $remainingSeconds s" }
                else -> { val hours = seconds / 3600; val remainingMinutes = (seconds % 3600) / 60; if (remainingMinutes == 0L) if (hours == 1L) "1 h" else "$hours h" else if (hours == 1L) "1 h, $remainingMinutes m" else "$hours h, $remainingMinutes m" }
            }
            delay(1000)
        }
    }
    if (displayText.isNotEmpty()) Text(text = displayText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, maxLines = 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCard(image: ImageItem, supportsStrength: Boolean, onStrengthChange: (Float) -> Unit, onRemove: () -> Unit, onProcess: () -> Unit, onBrisque: () -> Unit, onClick: () -> Unit) {
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(enabled = image.outputBitmap != null) { haptic.light(); onClick() }, 
        shape = RoundedCornerShape(16.dp), 
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxSize(), 
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val displayBitmap = image.thumbnailBitmap ?: image.outputBitmap ?: image.inputBitmap
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(), 
                        contentDescription = image.filename, 
                        modifier = Modifier.fillMaxSize(), 
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Top) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = image.filename, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Text(text = image.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    if (image.isProcessing) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (image.progress.isNotEmpty()) Text(text = image.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            else Spacer(modifier = Modifier.weight(1f))
                            if (image.timeEstimateMillis > 0) CountdownTimer(initialTimeMillis = image.timeEstimateMillis, startTimeMillis = image.timeEstimateStartMillis, isActive = image.isProcessing)
                        }
                    } else if (image.outputBitmap != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.status_complete_ui), 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (image.isProcessing) IconButton(onClick = { haptic.heavy(); onRemove() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_processing), tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp)) }
                else {
                    IconButton(onClick = { haptic.medium(); onProcess() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.process), tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { haptic.heavy(); onRemove() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove), tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { onBrisque() }, modifier = Modifier.size(36.dp)) { Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_brisque), contentDescription = stringResource(R.string.brisque), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
fun DeprecatedModelWarningDialog(
    modelName: String,
    warning: com.je.dejpeg.ModelManager.ModelWarning,
    onContinue: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onContinue,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(warning.titleResId)) },
        text = { 
            Column {
                Text("Active model: $modelName", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(warning.messageResId))
            }
        },
        confirmButton = { 
            TextButton(onClick = { haptic.medium(); onContinue() }) { 
                Text(stringResource(R.string.ok)) 
            } 
        },
        dismissButton = { 
            TextButton(onClick = { haptic.light(); onGoToSettings() }) { 
                Text(stringResource(R.string.go_to_settings)) 
            } 
        }
    )
}