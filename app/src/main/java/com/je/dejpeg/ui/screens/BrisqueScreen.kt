/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.je.dejpeg.R
import com.je.dejpeg.data.BrisqueSettings
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.CornerRole
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.MorphButton
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import com.je.dejpeg.ui.components.StyledAlertDialog
import com.je.dejpeg.ui.components.toListItemShapes
import com.je.dejpeg.ui.components.toShape
import com.je.dejpeg.ui.viewmodel.BrisqueViewModel
import com.je.dejpeg.ui.viewmodel.SaveState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BRISQUEScreen(
    imageRepository: ImageRepository,
    imageId: String,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val images by imageRepository.images.collectAsState()
    val image =
        images.firstOrNull { it.id == imageId } ?: run { LaunchedEffect(Unit) { onBack() }; return }

    val viewModel: BrisqueViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BrisqueViewModel(image.inputBitmap, image.filename) }
        })
    val state by viewModel.imageState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf(false) }

    LaunchedEffect(image.id) {
        viewModel.initialize(context)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        TopAppBar(title = {
            Text(
                stringResource(R.string.brisque_analysis),
                style = MaterialTheme.typography.titleMedium
            )
        }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back_desc))
            }
        }, actions = {
            IconButton(onClick = {
                showInfo = true
            }) { Icon(Icons.Rounded.Info, stringResource(R.string.info_desc)) }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, stringResource(R.string.settings_desc))
            }
            IconButton(
                onClick = { viewModel.saveCurrentImage(context) }) {
                Icon(Icons.Rounded.Save, stringResource(R.string.brisque_save_image_desc))
            }
        })

        LaunchedEffect(state.originalBitmap) {
            if (state.originalBitmap.height >= 1920 || state.originalBitmap.width >= 1920) {
                showSizeWarning = true
            }
        }

        Image(
            bitmap = (state.descaledBitmap ?: state.originalBitmap).asImageBitmap(),
            contentDescription = image.filename,
            modifier = Modifier
                .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low
        )

        val scrollState = rememberScrollState()
        val info = state.descaleInfo
        val progress = state.descaleProgress

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenHorizontalPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing)
            ) {
                ScoreCard(
                    shape = CornerRole(topStart = true).toShape(),
                    label = stringResource(R.string.brisque_label),
                    value = state.brisqueScore?.let { String.format(Locale.US, "%.1f", it) }
                        ?: NOT_ASSESSED,
                    info = state.brisqueScore?.let { brisqueScoreInfo(it) } ?: ScoreInfo(
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f))
                ScoreCard(
                    shape = CornerRole(topEnd = true).toShape(),
                    label = stringResource(R.string.brisque_sharpness),
                    value = state.sharpnessScore?.let { String.format(Locale.US, "%.2f", it) }
                        ?: NOT_ASSESSED,
                    info = state.sharpnessScore?.let { sharpnessScoreInfo(it) } ?: ScoreInfo(
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f))
            }

            SegmentedListItem(
                shapes = CornerRole.forPosition(2, 3).toListItemShapes(),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.onSecondary
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(
                        label = stringResource(R.string.brisque_original),
                        value = info?.let { "${it.originalWidth}×${it.originalHeight}" }
                            ?: NOT_ASSESSED)
                    InfoRow(
                        label = stringResource(R.string.brisque_descaled),
                        value = if (!progress?.currentSize.isNullOrEmpty()) progress.currentSize else info?.let { "${it.detectedWidth}×${it.detectedHeight}" }
                            ?: NOT_ASSESSED)
                }
            }
            val enabled = !state.isBusy
            val colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.onSecondary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing)
            ) {
                SegmentedListItem(
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    shapes = CornerRole(bottomStart = true).toListItemShapes(),
                    onClick = { viewModel.assessQuality(context) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                if (state.brisqueScore == null) R.string.brisque_assess
                                else R.string.brisque_reassess
                            ), style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                SegmentedListItem(
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    shapes = CornerRole(bottomEnd = true).toListItemShapes(),
                    onClick = {
                        if (state.descaledBitmap != null) showConfirm = true
                        else viewModel.descaleImage()
                    }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.brisque_descale),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            state.assessError?.let {
                ErrorText(stringResource(R.string.brisque_assessment_error), it)
            }
            state.descaleError?.let {
                ErrorText(stringResource(R.string.brisque_descale_error), it)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(modifier = Modifier.height(10.dp))
                if (state.isBusy && !state.isDescaling) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearWavyProgressIndicator(
                        progress = { (progress?.currentStep ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    progress?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.isBusy) {
                    MorphButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.cancelWork(context) },
                        label = stringResource(R.string.cancel)
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    if (showInfo) StyledAlertDialog(
        onDismissRequest = { showInfo = false },
        confirmButton = { showInfo = false },
        dismissButton = { showInfo = false },
        title = { Text(stringResource(R.string.brisque_about_title)) },
        contents = { Text(stringResource(R.string.brisque_about_message)) },
        confirmButtonText = stringResource(R.string.ok)
    )

    if (showSizeWarning) StyledAlertDialog(
        onDismissRequest = { showSizeWarning = false },
        confirmButton = { showSizeWarning = false },
        dismissButton = { showSizeWarning = false },
        title = { Text("Size warning") },
        contents = { Text("This image is quite large, descaling it might cause issues or crashes") },
        confirmButtonText = stringResource(R.string.ok)
    )

    if (showConfirm) {
        StyledAlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                showConfirm = false
                viewModel.descaleImage()
            },
            dismissButton = { showConfirm = false },
            title = { Text(stringResource(R.string.brisque_descale_again_title)) },
            contents = { Text(stringResource(R.string.brisque_descale_again_message)) },
            confirmButtonText = stringResource(R.string.yes),
            dismissButtonText = stringResource(R.string.cancel)
        )
    }

    if (showSettings) {
        BrisqueSettingsDialog(
            settings = settings,
            imageWidth = image.inputBitmap.width,
            imageHeight = image.inputBitmap.height,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                viewModel.updateSettings(newSettings)
                showSettings = false
            })
    }

    (saveState as? SaveState.Saving)?.let { SaveProgressDialog(it) }

    (saveState as? SaveState.Error)?.let { err ->
        StyledAlertDialog(
            onDismissRequest = { viewModel.dismissSaveError() },
            confirmButton = {
                showConfirm = false
                viewModel.descaleImage()
            },
            dismissButton = { viewModel.dismissSaveError() },
            title = { Text(stringResource(R.string.error_saving_image_title)) },
            contents = { Text(err.message) },
            confirmButtonText = stringResource(R.string.ok)
        )
    }
}

@Composable
private fun ScoreCard(
    label: String = "", value: String, info: ScoreInfo, modifier: Modifier, shape: Shape
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = info.color.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(GroupedListSpacing)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = info.color
            )
            if (label.isNotEmpty()) {
                Text(
                    info.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorText(title: String, error: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ScoreInfo(val color: Color, val label: String = "")

private const val NOT_ASSESSED = "..."

@Composable
private fun brisqueScoreInfo(score: Float): ScoreInfo = when {
    score < 30 -> ScoreInfo(Color(0xFF4CAF50), stringResource(R.string.brisque_score_excellent))
    score < 55 -> ScoreInfo(Color(0xFF2196F3), stringResource(R.string.brisque_score_fair))
    score < 70 -> ScoreInfo(Color(0xFFFFC107), stringResource(R.string.brisque_score_poor))
    score < 85 -> ScoreInfo(Color(0xFFFF9800), stringResource(R.string.brisque_score_bad))
    else -> ScoreInfo(Color(0xFFF44336), stringResource(R.string.brisque_score_horrible))
}

@Composable
private fun sharpnessScoreInfo(score: Float): ScoreInfo = when {
    score >= 60 -> ScoreInfo(Color(0xFF4CAF50), stringResource(R.string.sharpness_very_sharp))
    score >= 45 -> ScoreInfo(Color(0xFF2196F3), stringResource(R.string.sharpness_sharp))
    score >= 35 -> ScoreInfo(Color(0xFFFFC107), stringResource(R.string.sharpness_moderate))
    score >= 10 -> ScoreInfo(Color(0xFFFF9800), stringResource(R.string.sharpness_soft))
    else -> ScoreInfo(Color(0xFFF44336), stringResource(R.string.sharpness_blurry))
}

@Composable
private fun BrisqueSettingsDialog(
    settings: BrisqueSettings,
    imageWidth: Int,
    imageHeight: Int,
    onDismiss: () -> Unit,
    onSave: (BrisqueSettings) -> Unit,
) {
    var coarseStep by remember { mutableFloatStateOf(settings.coarseStep.toFloat()) }
    var fineStep by remember { mutableFloatStateOf(settings.fineStep.toFloat()) }
    var fineRange by remember { mutableFloatStateOf(settings.fineRange.toFloat()) }
    var minWidthRatio by remember { mutableFloatStateOf(settings.minWidthRatio) }
    var brisqueWeight by remember { mutableFloatStateOf(settings.brisqueWeight) }
    var sharpnessWeight by remember { mutableFloatStateOf(settings.sharpnessWeight) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brisque_settings_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingSlider(
                    label = stringResource(R.string.brisque_coarse_stepping),
                    value = coarseStep,
                    onValueChange = { coarseStep = it },
                    valueRange = 10f..50f
                )
                SettingSlider(
                    label = stringResource(R.string.brisque_fine_stepping),
                    value = fineStep,
                    onValueChange = { fineStep = it },
                    valueRange = 1f..10f
                )
                SettingSlider(
                    label = stringResource(R.string.brisque_fine_range),
                    value = fineRange,
                    onValueChange = { fineRange = it },
                    valueRange = 10f..100f,
                    stepSize = 5f
                )
                SettingSlider(
                    label = stringResource(R.string.brisque_minimum_image_size),
                    value = minWidthRatio,
                    onValueChange = { minWidthRatio = it },
                    valueRange = 0.1f..0.9f,
                    stepSize = 0.05f,
                    format = {
                        "${(imageWidth * it).toInt()}×${(imageHeight * it).toInt()}px " + "(${
                            String.format(
                                Locale.US, "%.0f", it * 100
                            )
                        }%)"
                    })
                SettingSlider(
                    label = stringResource(R.string.brisque_weight),
                    value = brisqueWeight,
                    onValueChange = { brisqueWeight = it },
                    valueRange = 0f..1f,
                    stepSize = 0.05f,
                    format = { String.format(Locale.US, "%.2f", it) })
                SettingSlider(
                    label = stringResource(R.string.brisque_sharpness_weight),
                    value = sharpnessWeight,
                    onValueChange = { sharpnessWeight = it },
                    valueRange = 0f..1f,
                    stepSize = 0.05f,
                    format = { String.format(Locale.US, "%.2f", it) })
            }
        },
        confirmButton = {
            MorphButton(onClick = {
                onSave(
                    BrisqueSettings(
                        coarseStep.roundToInt(),
                        fineStep.roundToInt(),
                        fineRange.roundToInt(),
                        minWidthRatio,
                        brisqueWeight,
                        sharpnessWeight
                    )
                )
            }, label = stringResource(R.string.save))
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    coarseStep = 20f
                    fineStep = 5f
                    fineRange = 30f
                    minWidthRatio = 0.5f
                    brisqueWeight = 0.7f
                    sharpnessWeight = 0.3f
                }) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        })
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 1f,
    format: (Float) -> String = { "${it.roundToInt()}px" }
) {
    val stepCount = ((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt()
    val sliderState = remember(stepCount, valueRange) {
        SliderState(
            value = value, steps = (stepCount - 1).coerceAtLeast(0), trackRange = valueRange
        )
    }
    LaunchedEffect(value) {
        if (sliderState.value != value) {
            sliderState.value = value
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            state = sliderState,
            onValueChange = { raw ->
                val index =
                    ((raw - valueRange.start) / stepSize).roundToInt().coerceIn(0, stepCount)
                onValueChange(
                    (valueRange.start + index * stepSize).coerceIn(
                        valueRange.start, valueRange.endInclusive
                    )
                )
            },
            colors = SliderDefaults.colors(),
            interactionSource = remember { MutableInteractionSource() })
    }
}
