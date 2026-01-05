package com.je.dejpeg.compose.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

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
    onConfirmHaptic: (() -> Unit)? = null,
    onDismissHaptic: (() -> Unit)? = null,
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
                { TextButton(onClick = { onDismissHaptic?.invoke() ?: haptic.light(); onDismissButton() }) { Text(dismissButtonText) } }
            }
            showCopyButton && isError && clipboardManager != null -> {
                {
                    TextButton(onClick = {
                        haptic.light()
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("error", textToCopy))
                        context?.let { Toast.makeText(it, it.getString(R.string.error_copied), Toast.LENGTH_SHORT).show() }
                    }) { Text("Copy") }
                }
            }
            else -> null
        },
        confirmButton = {
            if (customButtons != null) customButtons()
            else Button(onClick = { onConfirmHaptic?.invoke() ?: haptic.light(); onConfirm() }) {
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
    confirmButtonText: String = "OK"
) {
    BaseDialog(
        title = title,
        message = errorMessage,
        onDismiss = onDismiss,
        confirmButtonText = confirmButtonText,
        onConfirm = onDismiss,
        isError = true,
        context = context,
        showCopyButton = true
    )
}

@Composable
fun InfoAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    icon: ImageVector,
    confirmButtonText: String = "OK"
) = StyledAlertDialog(onDismiss, title, { Text(message) }, confirmButtonText, icon)

@Composable
fun ConfirmAlertDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String = "OK",
    dismissButtonText: String = "Cancel",
    confirmHaptic: (() -> Unit)? = null,
    dismissHaptic: (() -> Unit)? = null
) = StyledConfirmDialog(onDismiss, onConfirm, title, { Text(message) }, confirmButtonText, dismissButtonText, confirmHaptic, dismissHaptic)
