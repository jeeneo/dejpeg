/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.utils.helpers.ImageLoadingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val images = MutableStateFlow<List<ImageItem>>(emptyList())
    val sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val isLoadingImages = MutableStateFlow(false)
    val loadingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val savingImagesProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    fun addImage(item: ImageItem) {
        images.value += item
    }

    fun addSharedUri(uri: Uri) {
        if (sharedUris.value.any { it == uri }) return
        sharedUris.value += uri
    }

    fun addImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isLoadingImages.value = true
            loadingImagesProgress.value = Pair(0, uris.size)
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    try {
                        ImageLoadingHelper.loadBitmapWithRotation(context, uri)?.let { bmp ->
                            val imageId = UUID.randomUUID().toString()
                            uri.path?.substringAfterLast('/')?.let { filename ->
                                if (filename.startsWith("temp_camera_")) {
                                    val tempFile = File(context.cacheDir, filename)
                                    if (tempFile.exists()) {
                                        val unprocessedFile =
                                            File(context.cacheDir, "${imageId}_unprocessed.jpg")
                                        if (tempFile.renameTo(unprocessedFile)) {
                                            Log.d(
                                                "ImageRepository",
                                                "Renamed camera temp file to ${unprocessedFile.name}"
                                            )
                                        }
                                    }
                                }
                            }
                            val imageItem = ImageItem(
                                id = imageId,
                                uri = uri,
                                filename = ImageLoadingHelper.getFileNameFromUri(context, uri),
                                inputBitmap = bmp,
                                thumbnailBitmap = ImageLoadingHelper.generateThumbnail(bmp),
                                size = "${bmp.width}x${bmp.height}",
                                
                            )
                            withContext(Dispatchers.Main) {
                                addImage(imageItem)
                                loadingImagesProgress.value = Pair(index + 1, uris.size)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ImageRepository", "Failed to load image: $uri - ${e.message}")
                    }
                }
            }

            isLoadingImages.value = false
            loadingImagesProgress.value = null
        }
    }

    fun removeImage(id: String) {
        images.value = images.value.filter { it.id != id }
    }

    fun getImageById(id: String) = images.value.find { it.id == id }

    fun updateImageState(id: String, transform: (ImageItem) -> ImageItem) {
        images.value = images.value.map { if (it.id == id) transform(it) else it }
    }

    fun markImageAsSaved(imageId: String) {
        updateImageState(imageId) { it.copy(hasBeenSaved = true) }
    }

    companion object {
        @Volatile
        private var instance: ImageRepository? = null

        fun getInstance(context: Context): ImageRepository {
            return instance ?: synchronized(this) {
                instance ?: ImageRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
