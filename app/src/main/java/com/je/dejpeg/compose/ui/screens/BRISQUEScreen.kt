package com.je.dejpeg.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.FilterQuality
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.je.dejpeg.R
import com.je.dejpeg.ui.viewmodel.BrisqueViewModel
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BRISQUEScreen(
    processingViewModel: ProcessingViewModel,
    imageId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val images by processingViewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId }
    val brisqueViewModel: BrisqueViewModel = viewModel()
    val brisqueState by brisqueViewModel.imageState.collectAsState()
    
    var showImageModal by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()
    
    DisposableEffect(isDarkTheme) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        onDispose { }
    }
    
    BackHandler { navController.popBackStack() }
    if (image == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    
    LaunchedEffect(image.id) {
        brisqueViewModel.initialize(context, image.inputBitmap, image.filename)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BRISQUE analysis", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { haptic.light(); navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.medium()
                            brisqueViewModel.saveCurrentImage(
                                context = context,
                                onSuccess = { Toast.makeText(context, "Image saved successfully", Toast.LENGTH_SHORT).show() },
                                onError = { error -> Toast.makeText(context, "Failed to save: $error", Toast.LENGTH_SHORT).show() }
                            )
                        },
                        enabled = brisqueState != null
                    ) {
                        Icon(Icons.Filled.Save, "Save image")
                    }
                    IconButton(onClick = { haptic.light(); showInfoDialog = true }, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.Info, "Info")
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            
            item {
                val displayBitmap = brisqueState?.descaledBitmap ?: brisqueState?.originalBitmap
                displayBitmap?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(220.dp).clickable { haptic.light(); showImageModal = true },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
                            Image(bitmap = it.asImageBitmap(), contentDescription = image.filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(image.filename, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        InfoRow(label = "Size", value = image.size, isSmall = true)
                        val dimensions = if (brisqueState?.descaledBitmap != null) "${brisqueState?.descaledBitmap?.width}×${brisqueState?.descaledBitmap?.height} (descaled)" else "${image.inputBitmap.width}×${image.inputBitmap.height}"
                        InfoRow(label = "Dimensions", value = dimensions, isSmall = true)
                    }
                }
            }
            item {
                brisqueState?.descaleInfo?.let { info ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Descale analysis", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InfoRow(label = "Original", value = "${info.originalWidth}×${info.originalHeight}", isSmall = true)
                                    InfoRow(label = "Calculated", value = "${info.detectedWidth}×${info.detectedHeight}", isSmall = true)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InfoRow(label = "BRISQUE", value = String.format("%.1f", info.brisqueScore), isSmall = true)
                                    InfoRow(label = "Sharpness", value = String.format("%.1f", info.sharpness), isSmall = true)
                                }
                            }
                        }
                    }
                }
            }
            item {
                brisqueState?.brisqueScore?.let { score ->
                    val qualityColor = getQualityColor(score)
                    val qualityLabel = getQualityLabel(score)
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, qualityColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = qualityColor.copy(alpha = 0.12f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Quality assessment", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = qualityColor)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(String.format("%.1f", score), style = MaterialTheme.typography.headlineSmall, color = qualityColor, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(qualityLabel, style = MaterialTheme.typography.bodySmall, color = qualityColor, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { haptic.medium(); brisqueViewModel.assessQuality(context) }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = brisqueState?.isAssessing == false && brisqueState?.isDescaling == false, contentPadding = PaddingValues(0.dp)) {
                        if (brisqueState?.isAssessing == true) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Assessing...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text(if (brisqueState?.brisqueScore != null) "Reassess" else "Assess", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = { 
                            haptic.medium()
                            if (brisqueState?.descaledBitmap != null) {
                                showConfirm = true
                            } else {
                                brisqueViewModel.descaleImage(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        enabled = brisqueState?.isAssessing == false && brisqueState?.isDescaling == false,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        if (brisqueState?.isDescaling == true) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Descaling...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Text("Descale", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                brisqueState?.assessError?.let { error ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Assessment error:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                brisqueState?.descaleError?.let { error ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Descale error:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (showConfirm) confirmDialog(onConfirm = { haptic.medium(); showConfirm = false; brisqueViewModel.descaleImage(context) }, onDismiss = { showConfirm = false })
    if (showImageModal) (brisqueState?.descaledBitmap ?: brisqueState?.originalBitmap)?.let { ImageViewerModal(bitmap = it, filename = image.filename, onDismiss = { showImageModal = false }) }
    if (showInfoDialog) InfoDialog(context = context, onDismiss = { showInfoDialog = false })
    brisqueState?.let { state ->
        if (state.isDescaling) {
            state.descaleProgress?.let { progress ->
                DescaleProgressDialog(
                    progress = progress,
                    logMessages = state.descaleLog,
                    onCancel = {
                        haptic.medium()
                        brisqueViewModel.cancelDescaling(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isSmall: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = if (isSmall) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        Text(label, style = style)
        Text(value, style = style, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun confirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descale again?") },
        text = { Text("This will descale the image again, which may produce worse results, continue?") },
        confirmButton = { Button(onClick = onConfirm) { Text("Yes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImageViewerModal(bitmap: Bitmap, filename: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxSize().zoomable(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 20f)), true), Alignment.Center) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))) {
                Icon(Icons.Filled.Close, "Close", Modifier.size(28.dp))
            }
        }
    }
}

private fun getQualityColor(score: Float): Color = when {
    score < 30 -> Color(0xFF4CAF50)  // green
    score < 55 -> Color(0xFF2196F3)  // blue
    score < 70 -> Color(0xFFFFC107)  // amber
    score < 85 -> Color(0xFFFF9800)  // orange
    else -> Color(0xFFF44336)        // red
}

private fun getQualityLabel(score: Float): String = when {
    score < 30 -> "excellent"
    score < 55 -> "fair"
    score < 70 -> "poor"
    score < 85 -> "bad"
    else -> "horrible!"
}

@Composable
private fun InfoDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About BRISQUE") },
        text = { Text("BRISQUE (Blind/Referenceless Image Spatial Quality Evaluator) is a no-reference image quality score.\n\nIn plain English, it takes an image and assigns it a quality value between 0 and 100, lower being best and higher being worse.\n\nUsing this with a sharpness estimate can provide a semi-reliable method for getting an image that's been scaled to a larger size back down to close to it's original resolution.\n\nImages like this include screenshots of screenshots, overscaled memes, or old photos scaned at large DPIs.\n\nYou shouldn't need to apply this to every image, but only ones that look blurry or blocky.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun DescaleProgressDialog(
    progress: com.je.dejpeg.utils.BrisqueDescaler.ProgressUpdate,
    logMessages: List<String>,
    onCancel: () -> Unit
) {
    val logListState = rememberLazyListState()
    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) logListState.animateScrollToItem(maxOf(0, logMessages.size - 1))
    }
    Dialog(onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Descaling image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = progress.currentStep.toFloat() / progress.totalSteps.toFloat(), modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${progress.currentStep}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(progress.phase, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (progress.currentSize.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Size:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(progress.currentSize, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Text(progress.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text("Log:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (logMessages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Waiting for logs...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), state = logListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(logMessages) { message ->
                                    LogEntry(text = message, isActive = message == logMessages.lastOrNull())
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun LogEntry(text: String, isActive: Boolean = false) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp), color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}