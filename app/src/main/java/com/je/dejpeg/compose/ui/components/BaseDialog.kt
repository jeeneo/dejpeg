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

/**
 * Unified reusable alert dialog component for consistent styling across the app.
 * Supports simple message dialogs, custom content, error dialogs with copy functionality,
 * and flexible button configurations.
 *
 * @param title Dialog title
 * @param message Simple text message (mutually exclusive with content)
 * @param content Custom composable content (mutually exclusive with message)
 * @param onDismiss Called when dialog is dismissed
 * @param confirmButtonText Text for primary action button
 * @param onConfirm Called when confirm button is clicked
 * @param dismissButtonText Optional text for secondary button
 * @param onDismissButton Optional callback for dismiss button
 * @param isError If true, applies error styling and enables copy-to-clipboard
 * @param context Required only if isError=true (for clipboard access)
 * @param showCopyButton If true and isError=true, shows copy button
 * @param onConfirmHaptic Optional custom haptic feedback for confirm button
 * @param onDismissHaptic Optional custom haptic feedback for dismiss button
 * @param icon Optional icon to display in the dialog header
 * @param modifier Optional modifier for the dialog
 * @param customButtons Optional custom buttons composable (replaces confirm/dismiss buttons)
 */
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

/**
 * Convenience overload for error dialogs with automatic copy functionality.
 */
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

/**
 * Convenience overload for info/help dialogs with an icon.
 */
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

/**
 * Convenience overload for confirm dialogs.
 */
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
