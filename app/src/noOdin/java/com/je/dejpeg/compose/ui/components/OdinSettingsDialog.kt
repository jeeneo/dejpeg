package com.je.dejpeg.compose.ui.components

import androidx.compose.runtime.Composable

@Composable
fun OdinSettingsDialog(
    hdr: Boolean,
    srgb: Boolean,
    quality: Int,
    maxMemoryMB: Int,
    numThreads: Int,
    onHdrChange: (Boolean) -> Unit,
    onSrgbChange: (Boolean) -> Unit,
    onQualityChange: (Int) -> Unit,
    onMaxMemoryChange: (Int) -> Unit,
    onNumThreadsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    onDismiss() // boop
}
