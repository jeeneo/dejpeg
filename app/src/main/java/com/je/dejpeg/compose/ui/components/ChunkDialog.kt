package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import kotlin.math.roundToInt

@Composable
fun ChunkDialog(
    chunk: Int,
    overlap: Int,
    onChunkChange: (Int) -> Unit,
    onOverlapChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var chunkSize by remember { mutableIntStateOf(chunk) }
    var overlapSize by remember { mutableIntStateOf(overlap) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val chunkPowers = generateSequence(16) { it * 2 }.takeWhile { it <= 2048 }.toList()
    val overlapPowers = generateSequence(8) { it * 2 }.takeWhile { it <= 256 }.toList()

    @Composable
    fun powerSlider(label: String, value: Int, powers: List<Int>, onChange: (Int) -> Unit) {
        var index by remember(value) { mutableIntStateOf(powers.indexOf(value).coerceAtLeast(0)) }
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Slider(
                value = index.toFloat(),
                onValueChange = {
                    val newIdx = it.roundToInt().coerceIn(powers.indices)
                    if (newIdx != index) {
                        index = newIdx
                        haptic.light()
                        onChange(powers[newIdx])
                    }
                },
                valueRange = 0f..(powers.lastIndex.toFloat()),
                steps = powers.size - 2
            )
            Text(
                "${powers[index]}px",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.chunk_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        ChunkGridPreview(chunkSize, overlapSize)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${chunkSize}×${chunkSize}px • Overlap: ${overlapSize}px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                powerSlider(stringResource(R.string.chunk_size), chunkSize, chunkPowers) { chunkSize = it }
                Spacer(Modifier.height(16.dp))
                powerSlider(stringResource(R.string.overlap_size), overlapSize, overlapPowers) { overlapSize = it }
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.note_chunk_overlap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.medium()
                android.util.Log.d("ChunkDialog", "Saving chunk_size: $chunkSize, overlap_size: $overlapSize")
                onChunkChange(chunkSize)
                onOverlapChange(overlapSize)
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ChunkGridPreview(chunkSize: Int, overlapSize: Int) {
    val numChunks = 3
    val validOverlap = overlapSize.coerceAtMost(chunkSize - 1)
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val totalSize = size.width
            val overlapRatio = if (chunkSize > 0) validOverlap.toFloat() / chunkSize.toFloat() else 0f
            val overlapRatioClamped = overlapRatio.coerceIn(0f, 0.9f)
            val effectiveChunkSize = totalSize / (numChunks - (numChunks - 1) * overlapRatioClamped)
            val overlapPixels = (effectiveChunkSize * overlapRatioClamped).coerceAtLeast(0f)

            for (row in 0 until numChunks) {
                for (col in 0 until numChunks) {
                    val x = col * (effectiveChunkSize - overlapPixels)
                    val y = row * (effectiveChunkSize - overlapPixels)
                    drawRect(
                        color = androidx.compose.ui.graphics.Color(0xFF90CAF9).copy(alpha = 0.4f),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(effectiveChunkSize, effectiveChunkSize)
                    )
                    drawRect(
                        color = androidx.compose.ui.graphics.Color(0xFF1976D2),
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(effectiveChunkSize, effectiveChunkSize),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                    if (overlapPixels > 2f) {
                        if (col < numChunks - 1) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(0xFFFF9800).copy(alpha = 0.5f),
                                topLeft = androidx.compose.ui.geometry.Offset(x + effectiveChunkSize - overlapPixels, y),
                                size = androidx.compose.ui.geometry.Size(overlapPixels, effectiveChunkSize)
                            )
                        }
                        if (row < numChunks - 1) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(0xFFFF9800).copy(alpha = 0.5f),
                                topLeft = androidx.compose.ui.geometry.Offset(x, y + effectiveChunkSize - overlapPixels),
                                size = androidx.compose.ui.geometry.Size(effectiveChunkSize, overlapPixels)
                            )
                        }
                    }
                }
            }
        }
    }
}