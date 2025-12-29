package com.je.dejpeg.compose.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import com.je.dejpeg.compose.ui.components.BatteryOptimizationDialog
import com.je.dejpeg.compose.ui.components.RemoveImageDialog
import com.je.dejpeg.compose.ui.components.CancelProcessingDialog
import com.je.dejpeg.compose.ui.components.ImageSourceDialog
import com.je.dejpeg.compose.ui.components.LoadingDialog
import com.je.dejpeg.compose.utils.ImageActions
import com.je.dejpeg.compose.ui.viewmodel.ImageItem
import com.je.dejpeg.compose.ui.viewmodel.ProcessingUiState
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.compose.ModelManager
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.SaveImageDialog
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    onNavigateToBeforeAfter: (String) -> Unit = {},
    onNavigateToBrisque: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    initialSharedUris: List<Uri> = emptyList(),
    onRemoveSharedUri: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences.getInstance(context) }
    val defaultImageSource by appPreferences.defaultImageSource.collectAsState(initial = null)
    
    val images by viewModel.images.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val globalStrength by viewModel.globalStrength.collectAsState()
    val supportsStrength = viewModel.supportsStrengthAdjustment()
    val shouldShowNoModelDialog by viewModel.shouldShowNoModelDialog.collectAsState()
    val shouldShowBatteryOptimizationDialog by viewModel.shouldShowBatteryOptimizationDialog.collectAsState()
    val haptic = rememberHapticFeedback()
    val deprecatedModelWarning by viewModel.deprecatedModelWarning.collectAsState()
    val isLoadingImages by viewModel.isLoadingImages.collectAsState()
    val loadingImagesProgress by viewModel.loadingImagesProgress.collectAsState()
    var imageIdToRemove by remember { mutableStateOf<String?>(null) }
    var imageIdToCancel by remember { mutableStateOf<String?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var overwriteDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            imageIdToRemove = null
            imageIdToCancel = null
            showImageSourceDialog = false
            showCancelAllDialog = false
            saveErrorMessage = null
            overwriteDialogState = null
        }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val handleImageRemoval: (String) -> Unit = { imageId ->
        images.firstOrNull { it.id == imageId }?.let { image ->
            when {
                image.isProcessing && viewModel.isCurrentlyProcessing(imageId) -> imageIdToCancel = imageId
                image.isProcessing && !viewModel.isCurrentlyProcessing(imageId) -> {
                    viewModel.removeImage(imageId, cleanupCache = true)
                }
                image.outputBitmap != null && !image.hasBeenSaved -> imageIdToRemove = imageId
                else -> {
                    image.uri?.let { uri ->
                        try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                        try { onRemoveSharedUri(uri) } catch (_: Exception) { }
                    }
                    viewModel.removeImage(imageId, cleanupCache = true)
                }
            }
        }
    }

    val performRemoval: (String) -> Unit = { imageId ->
        val targetUri = images.firstOrNull { it.id == imageId }?.uri
        targetUri?.let { uri ->
            try { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            try { onRemoveSharedUri(uri) } catch (_: Exception) { }
        }
        viewModel.removeImage(imageId, force = true, cleanupCache = true)
        imageIdToRemove = null
        imageIdToCancel = null
    }
    
    LaunchedEffect(images) {
        imageIdToRemove = imageIdToRemove?.takeIf { id -> images.any { it.id == id } }
        imageIdToCancel = imageIdToCancel?.takeIf { id -> images.any { it.id == id } }
        overwriteDialogState = overwriteDialogState?.takeIf { (id, _) -> images.any { it.id == id } }
    }

    LaunchedEffect(Unit) { viewModel.initialize(context) }

    val processedShareUris = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(initialSharedUris) {
        if (initialSharedUris.isNotEmpty()) {
            val existing = images.mapNotNull { it.uri?.toString() }.toSet()
            val toAdd = initialSharedUris.filter { uri -> uri.toString() !in existing && uri.toString() !in processedShareUris.value }
            if (toAdd.isNotEmpty()) {
                viewModel.addImagesFromUris(context, toAdd)
                processedShareUris.value += toAdd.map { it.toString() }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) clipData.getItemAt(i).uri?.let { uris.add(it) }
            } ?: result.data?.data?.let { uris.add(it) } ?: viewModel.getCameraPhotoUri()?.let {
                uris.add(it)
                viewModel.clearCameraPhotoUri()
            }
            viewModel.addImagesFromUris(context, uris)
        }
    }
        
    LaunchedEffect(Unit) { viewModel.setImagePickerLauncher(imagePickerLauncher) }

    fun importImage() {
        haptic.light()
        when (defaultImageSource) {
            "gallery" -> viewModel.launchGalleryPicker(context)
            "internal" -> viewModel.launchInternalPhotoPicker(context)
            "documents" -> viewModel.launchDocumentsPicker(context)
            "camera" -> viewModel.launchCamera(context)
            else -> showImageSourceDialog = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .onSizeChanged { headerHeightPx = it.height },
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.images, images.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FloatingActionButton(
                onClick = { importImage() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, stringResource(R.string.add_images)) }
        }

        if (images.isNotEmpty() && supportsStrength) {
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.strength, globalStrength.toInt()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    var prevStrength by remember { mutableFloatStateOf(globalStrength) }
                    Slider(value = globalStrength, onValueChange = { val v = (it / 5).roundToInt() * 5f; if (v != prevStrength) { haptic.light(); prevStrength = v }; viewModel.setGlobalStrength(v) }, valueRange = 0f..100f, steps = 19, modifier = Modifier.fillMaxWidth().height(24.dp))
                }
            }
        }

        if (images.isEmpty()) {
            val offsetDp = -(headerHeightPx / 2 / density.density).dp
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .offset(y = offsetDp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(48.dp))
                    Box(
                        Modifier
                            .width(240.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { importImage() }
                            .drawBehind {
                                val strokeWidth = 2.dp.toPx()
                                val cornerRadius = 20.dp.toPx()
                                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                drawRoundRect(
                                    color = Color.Gray,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    size = size,
                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                                    style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                                )
                            }
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.no_images_yet),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.tap_to_add_images),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        else {
            val isProcessingActive = (uiState is ProcessingUiState.Processing) || images.any { it.isCancelling }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, { it.id }) { image ->
                    val swipeState = remember { mutableStateOf(0f) }
                    SwipeToDismissWrapper(swipeState, image.isProcessing, { handleImageRemoval(image.id) }) {
                        ImageCard(
                            image = image,
                            supportsStrength = supportsStrength,
                            onStrengthChange = { viewModel.updateImageStrength(image.id, it) },
                            onRemove = { handleImageRemoval(image.id) },
                            onProcess = { if (!viewModel.hasActiveModel()) viewModel.showNoModelDialog() else viewModel.processImage(image.id) },
                            onBrisque = { haptic.light(); onNavigateToBrisque(image.id) },
                            onClick = { if (image.outputBitmap != null) { haptic.light(); onNavigateToBeforeAfter(image.id) } }
                        )
                    }
                    LaunchedEffect(imageIdToRemove, imageIdToCancel) { if (imageIdToRemove != image.id && imageIdToCancel != image.id) swipeState.value = 0f }
                }
            }
        }

        if (images.isNotEmpty()){
            Button(
                { if (uiState is ProcessingUiState.Processing) { haptic.heavy(); showCancelAllDialog = true } else { if (!viewModel.hasActiveModel()) viewModel.showNoModelDialog() else { haptic.medium(); viewModel.processImages() } } },
                Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                colors = if (uiState is ProcessingUiState.Processing) ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (uiState is ProcessingUiState.Processing) stringResource(R.string.cancel_processing) else stringResource(R.string.process_all), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
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
                    viewModel.cancelProcessingForImage(targetId)
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
                haptic.medium()
                viewModel.dismissNoModelDialog()
                onNavigateToSettings()
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
                haptic.medium()
                viewModel.dismissDeprecatedModelWarning()
                onNavigateToSettings()
            }
        )
    }

    saveErrorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text(stringResource(R.string.error_saving_image_title)) },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { haptic.light(); saveErrorMessage = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    overwriteDialogState?.let { (id, fn) ->
        SaveImageDialog(
            defaultFilename = fn,
            showSaveAllOption = false,
            initialSaveAll = false,
            hideOptions = true,
            onDismissRequest = { overwriteDialogState = null },
            onSave = { name, _, _ ->
                viewModel.saveImage(context, id, name, 
                    onSuccess = { performRemoval(id); overwriteDialogState = null },
                    onError = { overwriteDialogState = null; saveErrorMessage = it }
                )
            }
        )
    }
    
    if (isLoadingImages) {
        val progress = loadingImagesProgress
        LoadingDialog(
            title = stringResource(R.string.loading_images),
            progress = progress?.let { it.first.toFloat() / it.second.toFloat() },
            progressText = progress?.let { stringResource(R.string.loading_image_progress, it.first, it.second) }
        )
    }
}

@Composable
fun SwipeToDismissWrapper(swipeOffset: MutableState<Float>, isProcessing: Boolean, onDismissed: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val animatedOffset by animateFloatAsState(swipeOffset.value, label = "swipe")
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val haptic = rememberHapticFeedback()
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
    val haptic = rememberHapticFeedback()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.no_model_installed_title)) }, 
        text = { Text(stringResource(R.string.no_model_installed_desc)) }, 
        confirmButton = { TextButton(onClick = { haptic.medium(); onGoToSettings() }) { Text(stringResource(R.string.go_to_settings)) } }, 
        dismissButton = { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun BatteryOptimizationDialogForProcessing(onDismiss: () -> Unit, context: Context) {
    val errorMessage = context.getString(R.string.cannot_open_link_detail)
    BatteryOptimizationDialog(onDismissRequest = onDismiss, onOpenSettings = {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Intent(
                Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        } catch (e: Exception) { Toast.makeText(context, "$errorMessage: ${e.message ?: ""}", Toast.LENGTH_SHORT).show() }
    })
}

@Composable
fun ChunkProgressIndicator(completedChunks: Int, totalChunks: Int) {
    val target = if (totalChunks > 0) (completedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = target, label = "chunk_progress")
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCard(image: ImageItem, supportsStrength: Boolean, onStrengthChange: (Float) -> Unit, onRemove: () -> Unit, onProcess: () -> Unit, onBrisque: () -> Unit, onClick: () -> Unit) {
    val haptic = rememberHapticFeedback()
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
                        if (image.totalChunks > 1) {
                            ChunkProgressIndicator(completedChunks = image.completedChunks, totalChunks = image.totalChunks)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (image.progress.isNotEmpty()) Text(text = image.progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, maxLines = 1)
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
            Row(
                Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (image.isProcessing) {
                    IconButton({ haptic.heavy(); onRemove() }, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cancel_processing), tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton({ haptic.medium(); onProcess() }, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.process), tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    }
                    IconButton({ haptic.heavy(); onRemove() }, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                    IconButton({ onBrisque() }, Modifier.size(36.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("B", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeprecatedModelWarningDialog(
    modelName: String,
    warning: ModelManager.ModelWarning,
    onContinue: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
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
