package com.je.dejpeg.compose.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.je.dejpeg.compose.utils.rememberHapticFeedback

object DialogDefaults {
    val MinWidth = 280.dp
    val MaxWidthFraction = 0.9f
    val MaxWidthMin = 280f
    val MaxWidthMax = 560f
    val Shape = RoundedCornerShape(28.dp)
    val Properties = DialogProperties(usePlatformDefaultWidth = false)
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rememberDialogWidth(): Dp {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp * DialogDefaults.MaxWidthFraction)
        .coerceIn(DialogDefaults.MaxWidthMin, DialogDefaults.MaxWidthMax).dp
}

fun Modifier.dialogWidth(maxWidth: Dp): Modifier =
    widthIn(min = DialogDefaults.MinWidth, max = maxWidth)

/**
 * Unified AlertDialog wrapper with consistent styling.
 * Handles width, shape, colors, and optional haptic feedback automatically.
 */
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
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = icon?.let { { Icon(it, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary) } },
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

/** Simple single-button dialog with haptic feedback. */
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
        confirmButton = { TextButton(onClick = { haptic.light(); onDismiss() }) { Text(confirmText) } }
    )
}

/** Two-button confirm/dismiss dialog with haptic feedback. */
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
        confirmButton = {
            TextButton(onClick = { confirmHaptic?.invoke() ?: haptic.light(); onConfirm() }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = { dismissHaptic?.invoke() ?: haptic.light(); onDismiss() }) { Text(dismissText) }
        }
    )
}
