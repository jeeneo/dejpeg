/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.je.dejpeg.R
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.utils.CacheManager
import com.je.dejpeg.utils.helpers.ImageLoadingHelper
import com.je.dejpeg.utils.rememberHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecoveryDialog(
    imageRepository: ImageRepository
) {
    data class RecoveryImage(
        val imageId: String,
        val label: String,
        val processedBitmap: android.graphics.Bitmap,
        val unprocessedBitmap: android.graphics.Bitmap?
    )

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
            val existingIds = imageRepository.images.value.map { it.id }.toSet()
            val newRecoveryImages = cachedRecoveryImages.filter { (id, _) -> id !in existingIds }
            if (newRecoveryImages.isNotEmpty()) {
                val loadedImages = mutableListOf<RecoveryImage>()
                for ((imageId, file) in newRecoveryImages) {
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
                        loadedImages.add(
                            RecoveryImage(
                                imageId,
                                "Recovered_${imageId.take(8)}",
                                processedBitmap,
                                unprocessedBitmap
                            )
                        )
                    }
                }
                recoveryImages.value = loadedImages
                showDialog.value = loadedImages.isNotEmpty()
            }
        }
    }

    if (showDialog.value && recoveryImages.value.isNotEmpty()) {
        val count = recoveryImages.value.size
        val recoveredImagePrefix = stringResource(R.string.recovered_image_prefix)
        val recoverButtonText = stringResource(R.string.recover)
        val discardButtonText = stringResource(R.string.discard)

        fun clearCache() {
            Log.d("RecoveryDialog", "User chose to discard recovery images")
            showDialog.value = false
            scope.launch(Dispatchers.IO) {
                CacheManager.getRecoveryImages(context).forEach { (id, _) ->
                    CacheManager.deleteRecoveryPair(
                        context, id, deleteProcessed = true, deleteUnprocessed = true
                    )
                }
            }
        }

        StyledAlertDialog(
            onDismissRequest = {},
            title = { Text(pluralStringResource(R.plurals.recover_images_title, count, count)) },
            text = {
                Column {
                    Text(pluralStringResource(R.plurals.recover_images_message, count, count))
                    if (count <= 3) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recoveryImages.value.take(3).forEach { img ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Image(
                                        bitmap = img.processedBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.image),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recoveryImages.value.take(3).forEach { img ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    Image(
                                        bitmap = img.processedBitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.image),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${count - 3}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.light()
                    clearCache()
                }) { Text(discardButtonText) }
            },
            confirmButton = {
                MorphButton(
                    label = recoverButtonText, onClick = {
                        haptic.medium()
                        recoveryImages.value.forEach { img ->
                            val processed = img.processedBitmap
                            val unprocessedFile =
                                CacheManager.getUnprocessedImage(context, img.imageId)
                            val uri = unprocessedFile?.let {
                                FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", it
                                )
                            }
                            imageRepository.addImage(
                                ImageItem(
                                    id = img.imageId,
                                    uri = uri,
                                    filename = recoveredImagePrefix,
                                    inputBitmap = img.unprocessedBitmap ?: processed,
                                    outputBitmap = processed,
                                    thumbnailBitmap = ImageLoadingHelper.generateThumbnail(processed),
                                    size = "${processed.width}x${processed.height}",
                                    hasBeenSaved = false
                                )
                            )
                        }
                        showDialog.value = false
                    })
            })
    }
}
