package com.je.dejpeg.compose.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

@Composable
fun BaseDialog(
    title: String,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismissButton: (() -> Unit)? = null,
    isError: Boolean = false,
    context: Context? = null,
    showCopyButton: Boolean = isError,
    onConfirmHaptic: (() -> Unit)? = null,
    onDismissHaptic: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    customButtons: (@Composable () -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val dialogWidth = rememberDialogWidth()
    val clipboardManager = remember(context) {
        context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    
    val textToCopy = message ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = { Text(title) },
        text = {
            if (content != null) {
                content()
            } else if (message != null) {
                Text(message)
            }
        },
        confirmButton = {
            if (customButtons != null) {
                customButtons()
            } else {
                TextButton(
                    onClick = {
                        onConfirmHaptic?.invoke() ?: haptic.light()
                        onConfirm()
                    }
                ) {
                    Text(confirmButtonText)
                }
            }
        },
        dismissButton = if (customButtons != null) {
            null
        } else if (dismissButtonText != null && onDismissButton != null) {
            {
                TextButton(
                    onClick = {
                        onDismissHaptic?.invoke() ?: haptic.light()
                        onDismissButton()
                    }
                ) {
                    Text(dismissButtonText)
                }
            }
        } else if (showCopyButton && isError && clipboardManager != null) {
            {
                TextButton(
                    onClick = {
                        haptic.light()
                        val clip = ClipData.newPlainText("error", textToCopy)
                        clipboardManager.setPrimaryClip(clip)
                        context?.let {
                            Toast.makeText(it, it.getString(R.string.error_copied), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Copy")
                }
            }
        } else {
            null
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
) {
    BaseDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        confirmButtonText = confirmButtonText,
        onConfirm = onDismiss,
        icon = icon
    )
}

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
) {
    BaseDialog(
        title = title,
        message = message,
        onDismiss = onDismiss,
        confirmButtonText = confirmButtonText,
        onConfirm = onConfirm,
        dismissButtonText = dismissButtonText,
        onDismissButton = onDismiss,
        onConfirmHaptic = confirmHaptic,
        onDismissHaptic = dismissHaptic
    )
}
