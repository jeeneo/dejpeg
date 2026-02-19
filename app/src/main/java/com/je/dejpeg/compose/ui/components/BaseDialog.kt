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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
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
fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
    dismissButtonText: String? = null,
    onDismissButton: (() -> Unit)? = null,
    isError: Boolean = false,
    context: Context? = null,
    showCopyButton: Boolean = isError,
    icon: ImageVector? = null,
    customButtons: (@Composable () -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val clipboardManager = remember(context) {
        context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val textToCopy = message ?: ""

    StyledAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = when {
            customButtons != null -> null
            dismissButtonText != null && onDismissButton != null -> {
                { TextButton(onClick = { haptic.light(); onDismissButton() }) { Text(dismissButtonText) } }
            }
            showCopyButton && isError && clipboardManager != null -> {
                {
                    TextButton(onClick = {
                        haptic.light()
                        clipboardManager.setPrimaryClip(ClipData.newPlainText(context?.getString(R.string.error) ?: "error", textToCopy))
                        context?.let { Toast.makeText(it, it.getString(R.string.error_copied), Toast.LENGTH_SHORT).show() }
                    }) { Text(stringResource(R.string.copy)) }
                }
            }
            else -> null
        },
        confirmButton = {
            if (customButtons != null) customButtons()
            else Button(onClick = { haptic.light(); onConfirm() }) {
                Text(confirmButtonText)
            }
        }
    )
}

@Composable
fun ErrorAlertDialog(
    title: String,
    errorMessage: String,
    onDismiss: () -> Unit,
    context: Context,
    confirmButtonText: String? = null
) {
    BaseDialog(
        title = title,
        message = errorMessage,
        onDismiss = onDismiss,
        confirmButtonText = confirmButtonText ?: stringResource(R.string.ok),
        onConfirm = onDismiss,
        isError = true,
        context = context,
        showCopyButton = true
    )
}

@Composable
fun SimpleAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmButtonText: String? = null,
    onConfirm: () -> Unit = onDismiss,
    dismissButtonText: String? = null,
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val resolvedText = confirmButtonText ?: stringResource(R.string.ok)
    StyledAlertDialog(
        onDismissRequest = { haptic.light(); onDismiss() },
        icon = icon,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        dismissButton = dismissButtonText?.let {
            { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(it) } }
        },
        confirmButton = {
            Button(onClick = { haptic.light(); onConfirm() }) { Text(resolvedText) }
        }
    )
}
