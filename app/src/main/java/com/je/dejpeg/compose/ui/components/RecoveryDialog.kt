package com.je.dejpeg.compose.ui.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.viewmodel.ImageItem
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.helpers.ImageLoadingHelper
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecoveryDialog(
    viewModel: ProcessingViewModel
) {
    data class RecoveryImage(val imageId: String, val label: String, val processedBitmap: android.graphics.Bitmap, val unprocessedBitmap: android.graphics.Bitmap?)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val recoveryImages = remember { mutableStateOf<List<RecoveryImage>>(emptyList()) }
    val showDialog = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        CacheManager.clearChunks(context)
        CacheManager.clearAbandonedImages(context)
        val cachedRecoveryImages = CacheManager.getRecoveryImages(context)
        if (cachedRecoveryImages.isNotEmpty()) {
            val loadedImages = mutableListOf<RecoveryImage>()
            for ((imageId, file) in cachedRecoveryImages) {
                val processedBitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                if (processedBitmap != null) {
                    val unprocessedFile = CacheManager.getUnprocessedImage(context, imageId)
                    val unprocessedBitmap = if (unprocessedFile != null) {
                        withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(unprocessedFile.absolutePath)
                        }
                    } else null
                    loadedImages.add(RecoveryImage(imageId, "Recovered_${imageId.take(8)}", processedBitmap, unprocessedBitmap))
                }
            }
            recoveryImages.value = loadedImages
            showDialog.value = true
        }
    }

    if (showDialog.value && recoveryImages.value.isNotEmpty()) {
        val count = recoveryImages.value.size
        val plural = count > 1
        val pluralSuffix = if (plural) "s" else ""
        val wereWas = stringResource(if (plural) R.string.were else R.string.was)
        val themIt = stringResource(if (plural) R.string.them else R.string.it)
        val recoveredImagePrefix = stringResource(R.string.recovered_image_prefix)
        val recoverButtonText = stringResource(R.string.recover)
        val discardButtonText = stringResource(R.string.discard)
        fun clearCache() {
            Log.d("RecoveryDialog", "User chose to discard recovery images")
            showDialog.value = false
            scope.launch(Dispatchers.IO) {
                CacheManager.getRecoveryImages(context).forEach { (id, _) ->
                    CacheManager.deleteRecoveryPair(context, id)
                }
            }
        }
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.recover_images_title, pluralSuffix)) },
            text = { 
                Text(stringResource(R.string.recover_images_message, count, pluralSuffix, wereWas, themIt))
            },
            confirmButton = { 
                TextButton(onClick = { 
                    haptic.medium()
                    recoveryImages.value.forEach { img ->
                        val processed = img.processedBitmap
                        viewModel.addImage(ImageItem(
                            id = img.imageId,
                            uri = null,
                            filename = recoveredImagePrefix,
                            inputBitmap = img.unprocessedBitmap ?: processed,
                            outputBitmap = processed,
                            thumbnailBitmap = ImageLoadingHelper.generateThumbnail(processed),
                            size = "${processed.width}x${processed.height}",
                            hasBeenSaved = false
                        ))
                    }
                    showDialog.value = false
                }) { Text(recoverButtonText) } 
            },
            dismissButton = { 
                TextButton(onClick = { 
                    haptic.light()
                    clearCache()
                }) { Text(discardButtonText) } 
            }
        )
    }
}
