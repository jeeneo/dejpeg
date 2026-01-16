package com.je.dejpeg.compose.ui

import androidx.compose.runtime.Composable
import com.je.dejpeg.compose.ui.screens.BRISQUEScreen
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel

object BrisqueFeature {
    const val isEnabled = true

    @Composable
    fun Screen(
        processingViewModel: ProcessingViewModel,
        imageId: String,
        onBack: () -> Unit
    ) {
        BRISQUEScreen(
            processingViewModel = processingViewModel,
            imageId = imageId,
            onBack = onBack
        )
    }
}
