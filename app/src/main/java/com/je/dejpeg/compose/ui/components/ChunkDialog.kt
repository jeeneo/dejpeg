package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    val chunkPowers = generateSequence(16) { it * 2 }.takeWhile { it <= 2048 }.toList()
    val overlapPowers = generateSequence(8) { it * 2 }.takeWhile { it <= 256 }.toList()

    @Composable
    fun powerSlider(
        label: String,
        value: Int,
        powers: List<Int>,
        maxAllowed: Int = Int.MAX_VALUE,
        onChange: (Int) -> Unit
    ) {
        val availablePowers = powers.filter { it < maxAllowed }
        val effectivePowers = availablePowers.ifEmpty { listOf(powers.first()) }
        val clampedValue = value.coerceAtMost(effectivePowers.last())
        var index by remember(clampedValue, effectivePowers) { 
            mutableIntStateOf(effectivePowers.indexOf(clampedValue).coerceAtLeast(0)) 
        }
        LaunchedEffect(maxAllowed) {
            if (value >= maxAllowed && effectivePowers.isNotEmpty()) {
                onChange(effectivePowers.last())
            }
        }
        
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Slider(
                value = index.toFloat(),
                onValueChange = {
                    val newIdx = it.roundToInt().coerceIn(effectivePowers.indices)
                    if (newIdx != index) {
                        index = newIdx
                        haptic.light()
                        onChange(effectivePowers[newIdx])
                    }
                },
                valueRange = 0f..(effectivePowers.lastIndex.toFloat().coerceAtLeast(0f)),
                steps = (effectivePowers.size - 2).coerceAtLeast(0),
                enabled = effectivePowers.size > 1
            )
            Row {
                Text(
                    "${effectivePowers[index]}px",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chunk_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                powerSlider(stringResource(R.string.chunk_size), chunkSize, chunkPowers) { chunkSize = it }
                if (chunkSize >= 2048) {
                    Text(
                        stringResource(R.string.large_chunk_warning, chunkSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                powerSlider(
                    label = stringResource(R.string.overlap_size),
                    value = overlapSize,
                    powers = overlapPowers,
                    maxAllowed = chunkSize,
                    onChange = { overlapSize = it }
                )
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        stringResource(R.string.note_chunk_info, chunkSize),
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
