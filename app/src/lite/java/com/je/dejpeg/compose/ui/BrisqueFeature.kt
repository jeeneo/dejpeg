package com.je.dejpeg.compose.ui

import androidx.compose.runtime.Composable
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel

object BrisqueFeature {
    const val isEnabled = false

    @Composable
    fun Screen(
        processingViewModel: ProcessingViewModel,
        imageId: String,
        onBack: () -> Unit
    ) {
        onBack()
    }
}
