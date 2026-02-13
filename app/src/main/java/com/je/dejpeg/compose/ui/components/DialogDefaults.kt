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

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.je.dejpeg.compose.utils.rememberHapticFeedback

object DialogDefaults {
    val Shape = RoundedCornerShape(16.dp)
}

@Composable
fun StyledAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = icon?.let { { Icon(it, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary) } },
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

@Composable
fun StyledAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable (() -> Unit)? = null,
    confirmText: String,
    icon: ImageVector? = null
) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        icon = icon,
        confirmButton = { Button(onClick = { haptic.light(); onDismiss() }) { Text(confirmText) } }
    )
}

@Composable
fun StyledConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    text: @Composable (() -> Unit)? = null,
    confirmText: String,
    dismissText: String,
    confirmHaptic: (() -> Unit)? = null,
    dismissHaptic: (() -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        dismissButton = {
            TextButton(onClick = { dismissHaptic?.invoke() ?: haptic.light(); onDismiss() }) { Text(dismissText) }
        },
        confirmButton = {
            Button(onClick = { confirmHaptic?.invoke() ?: haptic.light(); onConfirm() }) { Text(confirmText) }
        }
    )
}
