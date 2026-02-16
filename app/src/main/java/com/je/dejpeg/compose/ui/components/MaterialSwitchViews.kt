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
*
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.components

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.materialswitch.MaterialSwitch
import com.je.dejpeg.R

/*
 * helper composables to use Material XML Switches in Jetpack Compose cause XML switches give a better UX (ur finger go brrr)
 */

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
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.dialog_switch_row, android.widget.FrameLayout(context), false) as LinearLayout
        },
        update = { view ->
            val labelView = view.findViewById<TextView>(R.id.switch_label)
            val switchView = view.findViewById<MaterialSwitch>(R.id.material_switch)
            
            labelView.text = label
            labelView.setTextColor(onSurfaceColor)
            applySwitchColors(switchView, primaryColor, onPrimaryColor, surfaceVariantColor, outlineColor)
            
            switchView.isChecked = checked
            switchView.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
            view.setOnClickListener {
                switchView.isChecked = !switchView.isChecked
            }
        }
    )
}

@Composable
fun MaterialSwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.preference_switch_row, android.widget.FrameLayout(context), false) as LinearLayout
        },
        update = { view ->
            val titleView = view.findViewById<TextView>(R.id.preference_title)
            val summaryView = view.findViewById<TextView>(R.id.preference_summary)
            val switchView = view.findViewById<MaterialSwitch>(R.id.preference_switch)
            
            titleView.text = title
            titleView.setTextColor(onSurfaceColor)
            summaryView.text = summary
            summaryView.setTextColor(onSurfaceVariantColor)
            applySwitchColors(switchView, primaryColor, onPrimaryColor, surfaceVariantColor, outlineColor)
            
            switchView.isChecked = checked
            switchView.isEnabled = enabled
            
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else 0.38f
            
            switchView.setOnCheckedChangeListener { _, isChecked ->
                if (enabled) {
                    onCheckedChange(isChecked)
                }
            }
            view.setOnClickListener {
                if (enabled) {
                    switchView.isChecked = !switchView.isChecked
                }
            }
        }
    )
}

private fun applySwitchColors(
    switchView: MaterialSwitch,
    primaryColor: Int,
    onPrimaryColor: Int,
    surfaceVariantColor: Int,
    outlineColor: Int
) {
    switchView.trackTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(primaryColor, surfaceVariantColor)
    )
    switchView.thumbTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(onPrimaryColor, outlineColor)
    )
    switchView.trackDecorationTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(primaryColor, outlineColor)
    )
}
