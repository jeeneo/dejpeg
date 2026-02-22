/**
* Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
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
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OidnSettingsDialog(
    hdr: Boolean,
    srgb: Boolean,
    quality: Int,
    maxMemoryMB: Int,
    numThreads: Int,
    onHdrChange: (Boolean) -> Unit,
    onSrgbChange: (Boolean) -> Unit,
    onQualityChange: (Int) -> Unit,
    onMaxMemoryChange: (Int) -> Unit,
    onNumThreadsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                stringResource(R.string.oidn_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // HDR toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.oidn_hdr),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.oidn_hdr_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = hdr, onCheckedChange = onHdrChange)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // sRGB toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.oidn_srgb),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.oidn_srgb_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = srgb, onCheckedChange = onSrgbChange)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.oidn_quality),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val qualityOptions = listOf(
                    0 to stringResource(R.string.oidn_quality_default),
                    4 to stringResource(R.string.oidn_quality_fast),
                    5 to stringResource(R.string.oidn_quality_balanced),
                    6 to stringResource(R.string.oidn_quality_high)
                )
                qualityOptions.chunked(2).forEach { rowOptions ->
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowOptions.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = quality == value,
                                onClick = { onQualityChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = rowOptions.size)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (rowOptions.size < 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Max memory slider (0-4096)
                Text(
                    stringResource(R.string.oidn_max_memory),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.oidn_max_memory_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = maxMemoryMB.toFloat(),
                    onValueChange = { onMaxMemoryChange((it / 256).roundToInt() * 256) },
                    valueRange = 0f..4096f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (maxMemoryMB == 0) "unlimited" else "${maxMemoryMB} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Threads slider (0-16)
                Text(
                    stringResource(R.string.oidn_num_threads),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.oidn_num_threads_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = numThreads.toFloat(),
                    onValueChange = { onNumThreadsChange(it.roundToInt()) },
                    valueRange = 0f..16f,
                    steps = 15,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (numThreads == 0) "auto" else "$numThreads",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
