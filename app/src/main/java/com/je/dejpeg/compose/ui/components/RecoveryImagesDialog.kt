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
import androidx.compose.ui.unit.dp
import com.je.dejpeg.compose.ui.viewmodel.ImageItem
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.compose.utils.helpers.ImageLoadingHelper
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun RecoveryImagesDialog(
    viewModel: ProcessingViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val recoveryImages = remember { mutableStateOf<List<Pair<String, Pair<android.graphics.Bitmap, android.graphics.Bitmap?>>>>(emptyList()) }
    val showDialog = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val cachedRecoveryImages = CacheManager.getRecoveryImages(context)
        if (cachedRecoveryImages.isNotEmpty()) {
            val loadedImages = mutableListOf<Pair<String, Pair<android.graphics.Bitmap, android.graphics.Bitmap?>>>()
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
                    loadedImages.add("Recovered_${imageId.take(8)}" to (processedBitmap to unprocessedBitmap))
                }
            }
            recoveryImages.value = loadedImages
            showDialog.value = true
        }
    }
    
    if (showDialog.value && recoveryImages.value.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { 
                Log.d("RecoveryImagesDialog", "User chose to discard recovery images")
                showDialog.value = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val cachedImages = CacheManager.getRecoveryImages(context)
                        cachedImages.forEach { (imageId, _) ->
                            CacheManager.deleteRecoveryPair(context, imageId)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Recover Processed Images?") },
            text = { 
                Text("Found ${recoveryImages.value.size} processed image(s) that were not saved before closing. Would you like to recover them?")
            },
            confirmButton = { 
                TextButton(onClick = { 
                    haptic.medium()
                    recoveryImages.value.forEach { (_, bitmapPair) ->
                        val (processedBitmap, unprocessedBitmap) = bitmapPair
                        val inputBitmap = unprocessedBitmap ?: processedBitmap
                        viewModel.addImage(ImageItem(
                            id = UUID.randomUUID().toString(),
                            uri = null,
                            filename = "Recovered",
                            inputBitmap = inputBitmap,
                            outputBitmap = processedBitmap,
                            thumbnailBitmap = ImageLoadingHelper.generateThumbnail(processedBitmap),
                            size = "${processedBitmap.width}x${processedBitmap.height}",
                            hasBeenSaved = false
                        ))
                    }
                    showDialog.value = false
                }) { 
                    Text("Recover") 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { 
                    haptic.light()
                    Log.d("RecoveryImagesDialog", "User chose to discard recovery images")
                    showDialog.value = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val cachedImages = CacheManager.getRecoveryImages(context)
                            cachedImages.forEach { (imageId, _) ->
                                CacheManager.deleteRecoveryPair(context, imageId)
                            }
                        }
                    }
                }) { 
                    Text("Discard") 
                } 
            }
        )
    }
}
