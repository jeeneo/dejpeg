package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

object DialogDefaults {
    val MinWidth = 280.dp
    val MaxWidthFraction = 0.9f
    val MaxWidthMin = 280f
    val MaxWidthMax = 560f
    val Shape = RoundedCornerShape(28.dp)
    val Properties = DialogProperties(usePlatformDefaultWidth = false)
}

@Composable
fun rememberDialogWidth(): Dp {
    val configuration = LocalConfiguration.current
    return (configuration.screenWidthDp * DialogDefaults.MaxWidthFraction)
        .coerceIn(DialogDefaults.MaxWidthMin, DialogDefaults.MaxWidthMax).dp
}

fun Modifier.dialogWidth(maxWidth: Dp): Modifier =
    widthIn(min = DialogDefaults.MinWidth, max = maxWidth)