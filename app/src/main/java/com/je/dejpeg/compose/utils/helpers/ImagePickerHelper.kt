/**
* Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.utils.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class ImagePickerHelper(
    private val context: Context,
    private var launcher: ActivityResultLauncher<Intent>? = null
) {
    private var currentPhotoUri: Uri? = null

    fun setLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.launcher = launcher
    }

    fun launchGalleryPicker() {
        launch(
            Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun launchInternalPhotoPicker() {
        launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun launchDocumentsPicker() {
        launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }

    fun launchCamera(): Result<Uri> {
        return try {
            val tempFile = File(
                context.cacheDir,
                "temp_camera_${System.currentTimeMillis()}.jpg"
            )
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                tempFile
            )
            currentPhotoUri = photoUri

            launch(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }
            )
            Result.success(photoUri)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    fun getCameraPhotoUri(): Uri? = currentPhotoUri

    fun clearCameraPhotoUri() {
        currentPhotoUri?.path?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
        currentPhotoUri = null
    }
    private fun launch(intent: Intent) {
        launcher?.launch(intent)
    }
}
