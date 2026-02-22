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

import android.content.res.ColorStateList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.materialswitch.MaterialSwitch

@Composable
fun MaterialSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        AndroidView(factory = { context ->
            MaterialSwitch(context).apply {
                trackTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(primaryColor, surfaceVariantColor)
                )
                thumbTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(onPrimaryColor, outlineColor)
                )
                trackDecorationTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(primaryColor, outlineColor)
                )
            }
        }, update = { switchView ->
            switchView.isChecked = checked
            switchView.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
        })
    }
}

@Composable
fun MaterialSwitchPreference(
    title: String,
    // summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            // Text(
            //     text = summary,
            //     style = MaterialTheme.typography.bodySmall,
            //     color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            //             else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            // )
        }
        AndroidView(factory = { context ->
            MaterialSwitch(context).apply {
                trackTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(primaryColor, surfaceVariantColor)
                )
                thumbTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(onPrimaryColor, outlineColor)
                )
                trackDecorationTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ), intArrayOf(primaryColor, outlineColor)
                )
            }
        }, update = { switchView ->
            switchView.isChecked = checked
            switchView.isEnabled = enabled
            switchView.setOnCheckedChangeListener { _, isChecked ->
                if (enabled) onCheckedChange(isChecked)
            }
        })
    }
}
