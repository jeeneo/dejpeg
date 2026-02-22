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

package com.je.dejpeg.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.components.StyledAlertDialog

@Composable
fun OidnModelDialog(
    models: List<String>,
    active: String?,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()

    StyledAlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.oidn_model_management))
    }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (models.isEmpty()) {
                Text(stringResource(R.string.no_oidn_models_installed))
            } else {
                models.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp)).background(
                                if (name == active) MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.3f
                                )
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ).clickable { haptic.medium(); onSelect(name) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name,
                                fontWeight = if (name == active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (name == active) {
                                Text(
                                    stringResource(R.string.active),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        RadioButton(
                            selected = name == active,
                            onClick = { haptic.medium(); onSelect(name) })
                    }
                }
            }
        }
    }, confirmButton = {
        Column(
            modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (models.isNotEmpty()) {
                TextButton(
                    onClick = { haptic.light(); onDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
            Button(
                onClick = { haptic.medium(); onImport() }, modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_tza_model))
            }
        }
    })
}
