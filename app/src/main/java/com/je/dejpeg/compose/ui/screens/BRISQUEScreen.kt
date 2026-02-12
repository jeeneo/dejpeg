/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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
*
*/

/*
* Also please don't steal my work and claim it as your own, thanks.
*/

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.je.dejpeg.compose.ui.components.ConfirmAlertDialog
import com.je.dejpeg.compose.ui.components.DialogDefaults
import com.je.dejpeg.compose.ui.components.StyledAlertDialog
import com.je.dejpeg.compose.ui.viewmodel.BrisqueViewModel
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.compose.utils.brisque.BRISQUEDescaler
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.data.BrisqueSettings
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import kotlin.math.roundToInt

// note: do not add to strings.xml yet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BRISQUEScreen(
    processingViewModel: ProcessingViewModel,
    imageId: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val images by processingViewModel.images.collectAsState()
    val image = images.firstOrNull { it.id == imageId } ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val brisqueViewModel: BrisqueViewModel = viewModel()
    val brisqueState by brisqueViewModel.imageState.collectAsState()
    val brisqueSettings by brisqueViewModel.settings.collectAsState()
    var showImageModal by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showBRISQUESettings by remember { mutableStateOf(false) }

    BackHandler { onBack() }
    LaunchedEffect(image.id) { brisqueViewModel.initialize(context, image.inputBitmap, image.filename) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text("BRISQUE analysis", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = { haptic.light(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { haptic.light(); showInfoDialog = true }) { Icon(Icons.Filled.Info, "Info") }
                IconButton(onClick = { haptic.light(); showBRISQUESettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                IconButton(
                    onClick = {
                        haptic.medium()
                        brisqueViewModel.saveCurrentImage(context,
                            { Toast.makeText(context, "Image saved successfully", Toast.LENGTH_SHORT).show() })
                            { error -> Toast.makeText(context, "Failed to save: $error", Toast.LENGTH_SHORT).show() }
                    },
                    enabled = brisqueState != null
                ) {
                    Icon(Icons.Filled.Save, "Save image")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                (brisqueState?.descaledBitmap ?: brisqueState?.originalBitmap)?.let {
                    Box(Modifier.fillMaxWidth().height(220.dp).clickable { haptic.light(); showImageModal = true }, Alignment.Center) {
                        Image(bitmap = it.asImageBitmap(), contentDescription = image.filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
            item {
                brisqueState?.descaleInfo?.let { info ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Descale analysis", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    InfoRow(label = "Original", value = "${info.originalWidth}×${info.originalHeight}", isSmall = true)
                                    InfoRow(label = "Descaled", value = "${info.detectedWidth}×${info.detectedHeight}", isSmall = true)
                                    InfoRow(label = "BRISQUE", value = "${String.format(Locale.US, "%.1f", info.brisqueScore)} (${getScoreInfo(info.brisqueScore, isBRISQUE = true).label})", isSmall = true, color = getScoreInfo(info.brisqueScore, isBRISQUE = true).color)
                                    InfoRow(label = "Sharpness", value = "${String.format(Locale.US, "%.1f", info.sharpness)} (${getScoreInfo(info.sharpness, isBRISQUE = false).label})", isSmall = true, color = getScoreInfo(info.sharpness, isBRISQUE = false).color)
                                }
                            }
                        }
                    }
                }
            }
            item {
                brisqueState?.brisqueScore?.let { score ->
                    val sharpness = brisqueState?.sharpnessScore
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val qualityInfo = getScoreInfo(score, isBRISQUE = true)
                        ScoreCard("BRISQUE", String.format(java.util.Locale.US, "%.1f", score), qualityInfo.label, qualityInfo.color, Modifier.weight(1f))
                        if (sharpness != null) {
                            val sharpnessInfo = getScoreInfo(sharpness, isBRISQUE = false)
                            ScoreCard("Sharpness", String.format(java.util.Locale.US, "%.2f", sharpness), sharpnessInfo.label, sharpnessInfo.color, Modifier.weight(1f))
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
                    Button(onClick = { haptic.medium(); if (brisqueState?.descaledBitmap != null) showConfirm = true else brisqueViewModel.descaleImage(context) }, modifier = Modifier.fillMaxWidth().height(40.dp), enabled = brisqueState?.isAssessing == false && brisqueState?.isDescaling == false, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), contentPadding = PaddingValues(0.dp)) {
                        if (brisqueState?.isDescaling == true) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (brisqueState?.isDescaling == true) "Descaling..." else "Descale", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                brisqueState?.assessError?.let { ErrorCard("Assessment error:", it) }
                brisqueState?.descaleError?.let { ErrorCard("Descale error:", it) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (showConfirm) ConfirmDialog(onConfirm = { haptic.medium(); brisqueViewModel.descaleImage(context); showConfirm = false }, onDismiss = { showConfirm = false })
    if (showImageModal) (brisqueState?.descaledBitmap ?: brisqueState?.originalBitmap)?.let { ImageViewerModal(bitmap = it, filename = image.filename, onDismiss = { showImageModal = false }) }
    if (showInfoDialog) InfoDialog(onDismiss = { showInfoDialog = false })
    if (showBRISQUESettings) BRISQUESettings(settings = brisqueSettings, brisqueViewModel = brisqueViewModel, imageWidth = image.inputBitmap.width, imageHeight = image.inputBitmap.height, onDismiss = { showBRISQUESettings = false })
    if (brisqueState?.isDescaling == true) brisqueState?.descaleProgress?.let { DescaleProgressDialog(progress = it, logMessages = brisqueState?.descaleLog!!, onCancel = { haptic.medium(); brisqueViewModel.cancelDescaling(context) }) }
}

@Composable
private fun ScoreCard(label: String, value: String, sublabel: String, color: Color, modifier: Modifier) {
    Card(modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorCard(title: String, error: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isSmall: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = if (isSmall) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        Text(label, style = style, color = color)
        Text(value, style = style, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun ConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    ConfirmAlertDialog(
        title = "Descale again?",
        message = "This will descale the image again, which may produce worse results, continue?",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmButtonText = "Yes",
        dismissButtonText = "Cancel",
        confirmHaptic = { haptic.medium() },
        dismissHaptic = { haptic.light() }
    )
}

@Composable
private fun ImageViewerModal(bitmap: Bitmap, filename: String, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxSize().zoomable(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 20f))), Alignment.Center) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            }
            IconButton(onClick = { haptic.light(); onDismiss() }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))) {
                Icon(Icons.Filled.Close, "Close", Modifier.size(28.dp))
            }
        }
    }
}

private data class ScoreInfo(val color: Color, val label: String)
private fun getScoreInfo(value: Float, isBRISQUE: Boolean = true): ScoreInfo = when {
    isBRISQUE -> when {
        value < 30 -> ScoreInfo(Color(0xFF4CAF50), "excellent") 
        value < 55 -> ScoreInfo(Color(0xFF2196F3), "fair")
        value < 70 -> ScoreInfo(Color(0xFFFFC107), "poor")
        value < 85 -> ScoreInfo(Color(0xFFFF9800), "bad")
        else -> ScoreInfo(Color(0xFFF44336), "horrible!")
    }
    else -> when {
        value >= 60 -> ScoreInfo(Color(0xFF4CAF50), "very sharp")
        value >= 45 -> ScoreInfo(Color(0xFF2196F3), "sharp")
        value >= 35 -> ScoreInfo(Color(0xFFFFC107), "moderate")
        value >= 10 -> ScoreInfo(Color(0xFFFF9800), "soft")
        else -> ScoreInfo(Color(0xFFF44336), "blurry")
    }
}

@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About BRISQUE") },
        text = {
            Text(
                "BRISQUE (Blind/Referenceless Image Spatial Quality Evaluator) is a no-reference image quality score.\n\nIn plain English, it takes an image and assigns it a quality value between 0 and 100, lower being best and higher being worse.\n\nUsing this with a sharpness estimate can provide a semi-reliable method for getting an image that's been scaled to a larger size back down to close to it's original resolution.\n\nImages like this include screenshots of screenshots, overscaled memes, or old photos scanned at large DPIs.\n\nYou shouldn't need to apply this to every image, but only ones that look blurry or don't match the images true size.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = { haptic.light(); onDismiss() }) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun DescaleProgressDialog(progress: BRISQUEDescaler.ProgressUpdate, logMessages: List<String>, onCancel: () -> Unit) {
    val haptic = rememberHapticFeedback()
    val logListState = rememberLazyListState()
    LaunchedEffect(logMessages.size) { if (logMessages.isNotEmpty()) logListState.animateScrollToItem(maxOf(0, logMessages.size - 1)) }
    Dialog(onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp, max = 500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Descaling image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(progress = { progress.currentStep.toFloat() / progress.totalSteps.toFloat() }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${progress.currentStep}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(progress.phase, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (progress.currentSize.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current size:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(progress.currentSize, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(progress.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text("Log:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    if (logMessages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Waiting for logs...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else LazyColumn(Modifier.fillMaxSize().padding(12.dp), state = logListState, verticalArrangement = Arrangement.spacedBy(4.dp)) { items(logMessages) { LogEntry(text = it, isActive = it == logMessages.lastOrNull()) } }
                }
                OutlinedButton(onClick = { haptic.heavy(); onCancel() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", Modifier.size(18.dp))
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
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BRISQUESettings(
    settings: BrisqueSettings,
    brisqueViewModel: BrisqueViewModel,
    imageWidth: Int,
    imageHeight: Int,
    onDismiss: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    var coarseStep by remember { mutableFloatStateOf(settings.coarseStep.toFloat()) }
    var fineStep by remember { mutableFloatStateOf(settings.fineStep.toFloat()) }
    var fineRange by remember { mutableFloatStateOf(settings.fineRange.toFloat()) }
    var minWidthRatio by remember { mutableStateOf(settings.minWidthRatio) }
    var brisqueWeight by remember { mutableStateOf(settings.brisqueWeight) }
    var sharpnessWeight by remember { mutableStateOf(settings.sharpnessWeight) }
    var expandedInfo by remember { mutableStateOf<String?>(null) }
    fun Float.clamp(min: Float, max: Float) = coerceIn(min, max)

    @Composable
    fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, stepSize: Float = 1f, format: (Float) -> String = { "${it.toInt()}px" }, infoText: String = "") {
        val haptic = rememberHapticFeedback()
        val steps = ((range.endInclusive - range.start) / stepSize).toInt()
        var index by remember(value) { mutableIntStateOf(((value - range.start) / stepSize).roundToInt().coerceIn(0, steps)) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$label: ${format(range.start + (index * stepSize))}", style = MaterialTheme.typography.labelMedium)
                if (infoText.isNotEmpty()) IconButton(onClick = { haptic.light(); expandedInfo = if (expandedInfo == label) null else label }, Modifier.size(24.dp)) { Icon(Icons.Filled.Info, "Info", Modifier.size(18.dp)) }
            }
            Slider(value = index.toFloat(), onValueChange = { newIdx ->
                val newIndex = newIdx.roundToInt().coerceIn(0, steps)
                if (newIndex != index) {
                    index = newIndex
                    haptic.light()
                    onValueChange(range.start + (newIndex * stepSize))
                }
            }, valueRange = 0f..steps.toFloat(), steps = maxOf(0, steps - 1), modifier = Modifier.fillMaxWidth())
            if (expandedInfo == label && infoText.isNotEmpty()) Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BRISQUE settings") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                item { SettingSlider("Coarse stepping", coarseStep, { coarseStep = it.clamp(10f, 50f) }, 10f..50f, 1f, infoText = "Steps (px) per iteration in the initial scan") }
                item { SettingSlider("Fine stepping", fineStep, { fineStep = it.clamp(1f, 10f) }, 1f..10f, 1f, infoText = "Steps (px) per iteration in the second scan") }
                item { SettingSlider("Fine range", fineRange, { fineRange = it.clamp(10f, 100f) }, 10f..100f, 5f, infoText = "Range around the coarse result to explore") }
                item { SettingSlider("Minimum image size", minWidthRatio, { minWidthRatio = it.clamp(0.1f, 0.9f) }, 0.1f..0.9f, 0.05f, format = { val px = (imageWidth * it).toInt(); val py = (imageHeight * it).toInt(); "${px}×${py}px (${String.format(Locale.US, "%.0f", it * 100)}%)" }, infoText = "Minimum size of the final image the algorithm is willing to scale down to") }
                item { SettingSlider("BRISQUE weight", brisqueWeight, { brisqueWeight = it.clamp(0f, 1f) }, 0f..1f, 0.05f, format = { "%.2f".format(it) }, infoText = "BRISQUE confidence in calculating") }
                item { SettingSlider("Sharpness weight", sharpnessWeight, { sharpnessWeight = it.clamp(0f, 1f) }, 0f..1f, 0.05f, format = { "%.2f".format(it) }, infoText = "Sharpness confidence in calculating") }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { haptic.light(); onDismiss() }) { Text("Cancel") }
                TextButton(onClick = {
                    haptic.light()
                    coarseStep = 20f
                    fineStep = 5f
                    fineRange = 30f
                    minWidthRatio = 0.5f
                    brisqueWeight = 0.7f
                    sharpnessWeight = 0.3f
                }) { Text("Reset") }
                Button(onClick = {
                    haptic.medium()
                    brisqueViewModel.updateSettings(
                        BrisqueSettings(coarseStep.toInt(), fineStep.toInt(), fineRange.toInt(), minWidthRatio, brisqueWeight, sharpnessWeight)
                    )
                    onDismiss()
                }) { Text("Save") }
            }
        },
        dismissButton = null
    )
}
