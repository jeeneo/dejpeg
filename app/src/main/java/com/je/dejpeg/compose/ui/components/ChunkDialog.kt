package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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