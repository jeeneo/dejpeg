package com.je.dejpeg

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.graphics.Outline

class MainActivity : AppCompatActivity() {
    private lateinit var selectButton: Button
    private lateinit var processButton: Button
    private lateinit var strengthSeekBar: RangeSlider
    // private lateinit var logRecyclerView: RecyclerView
    // private lateinit var logAdapter: LogAdapter
    private lateinit var pageIndicator: TextView
    private lateinit var cancelButton: Button

    private lateinit var modelManager: ModelManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var applyToAllSwitch: MaterialSwitch

    private var importProgressDialog: Dialog? = null
    private var importProgressBar: ProgressBar? = null
    private var importProgressText: TextView? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelPickerLauncher: ActivityResultLauncher<Intent>

    private val PREFS_NAME = "AppPrefs"
    private val OUTPUT_FORMAT_KEY = "outputFormat"
    private val STRENGTH_FACTOR_KEY = "strengthFactor"
    private val FORMAT_PNG = 0
    private val FORMAT_BMP = 1

    private lateinit var beforeAfterView: BeforeAfterImageView

    private var isProcessing: Boolean = false

    private data class ProcessingImage(
        val inputBitmap: Bitmap,
        var outputBitmap: Bitmap? = null
    )

    private var images = mutableListOf<ProcessingImage>()
    private var currentPage = 0

    private var outputFormat = "PNG"
    private var lastStrength = 0.5f

    // Control preview visibility
    private var showPreviews = true
    // Control filmstrip visibility
    private var showFilmstrip = false

    // Add per-image strength factor support
    private var perImageStrengthFactors = mutableListOf<Float>()
    private var applyStrengthToAll = true

    private val notificationPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) {
            setupNotificationChannel()
        } else {
            Toast.makeText(this, "Notification permission denied. Background updates will not be shown.", Toast.LENGTH_SHORT).show()
        }
    }

    private val NOTIFICATION_ID_PROCESSING = 1
    private val NOTIFICATION_ID_COMPLETION = 2
    private val NOTIFICATION_ID_ERROR = 3

    private lateinit var filmstripAdapter: FilmstripAdapter
    private lateinit var filmstripRecyclerView: RecyclerView

    // Add reference for loading bar
    private lateinit var processingProgressBar: ProgressBar

    private lateinit var vibrationManager: VibrationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrationManager = VibrationManager(this)

        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Toast.makeText(this, getString(R.string.opencv_error), Toast.LENGTH_LONG).show()
        }
        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.selectButton)
        processButton = findViewById(R.id.processButton)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)
        applyToAllSwitch = findViewById(R.id.applyToAllSwitch)
        // pageIndicator = findViewById(R.id.pageIndicator)
        cancelButton = findViewById(R.id.cancelButton)
        // Hide the applyToAllSwitch initially
        applyToAllSwitch.visibility = View.GONE

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor(this, modelManager)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        outputFormat = prefs.getString(OUTPUT_FORMAT_KEY, "PNG") ?: "PNG"
        lastStrength = prefs.getFloat(STRENGTH_FACTOR_KEY, 0.5f)
        strengthSeekBar.setValues(lastStrength * 100f)

        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                if (result.data?.clipData != null) {
                    handleMultipleImages(result.data?.clipData!!)
                } else {
                    val uri = result.data?.data
                    if (uri != null) onImageSelected(uri)
                }
            }
        }

        modelPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val modelUri = result.data!!.data
                if (modelUri != null) copyAndLoadModel(modelUri)
            } else {
                if (!modelManager.hasActiveModel()) {
                    promptModelSelection()
                }
            }
        }

        selectButton.setOnClickListener {
            vibrationManager.vibrateButton()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_image)
                .setItems(arrayOf(getString(R.string.single_image), getString(R.string.multiple_images))) { _, which ->
                    vibrationManager.vibrateDialogChoice() // Add choice feedback
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        if (which == 1) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }
                    imagePickerLauncher.launch(intent)
                }
                .show()
        }

        processButton.setOnClickListener {
            vibrationManager.vibrateButton()
            if (images.isNotEmpty()) processWithModel()
        }

        cancelButton.setOnClickListener {
            vibrationManager.vibrateButton()
            cancelProcessing()
        }

        processButton.isEnabled = false

        strengthSeekBar.addOnChangeListener { _, value, fromUser ->
            vibrationManager.vibrateSliderTouch() // Add touch feedback
            if (fromUser) {
                vibrationManager.vibrateSliderChange()
            }
            val snapped = (value / 5).toInt() * 5f
            strengthSeekBar.setValues(snapped)
            lastStrength = snapped / 100f
            if (images.size > 1 && !applyStrengthToAll && currentPage < perImageStrengthFactors.size) {
                perImageStrengthFactors[currentPage] = lastStrength
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(STRENGTH_FACTOR_KEY, lastStrength)
                .apply()
        }

        intent?.let {
            if (Intent.ACTION_SEND == it.action && it.type?.startsWith("image/") == true) {
                val imageUri = it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (imageUri != null) onImageSelected(imageUri)
            }
        }

        if (!modelManager.hasActiveModel()) promptModelSelection()

        beforeAfterView = findViewById(R.id.beforeAfterView)
        filmstripRecyclerView = findViewById(R.id.filmstripRecyclerView)
        // Find the ProgressBar
        processingProgressBar = findViewById(R.id.processingProgressBar)
        setupFilmstrip()
        updateImageViews()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setupNotificationChannel()
        }

        // Hide or show strength slider based on model type
        updateStrengthSliderVisibility()

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === this@MainActivity) {
                    clearAllNotifications()
                }
            }
        })
    }

    private fun promptModelSelection() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setMessage(R.string.no_models)
            .setPositiveButton(R.string.import_model) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setCancelable(false)
            .show()
    }

    private fun showModelManagementDialog() {
        val models = modelManager.getInstalledModels()
        val activeModel = modelManager.getActiveModelName()
        val items = models.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setSingleChoiceItems(items, models.indexOf(activeModel)) { dialog: DialogInterface, which: Int ->
                vibrationManager.vibrateMenuTap()
                modelManager.unloadModel()
                imageProcessor.unloadModel()
                modelManager.setActiveModel(models[which])
                // // logAdapter.addLog(getString(R.string.model_switched, models[which]))
                Toast.makeText(this, getString(R.string.model_switched, models[which]), Toast.LENGTH_SHORT).show()
                processButton.isEnabled = images.isNotEmpty()
                updateStrengthSliderVisibility()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.import_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setNeutralButton(R.string.delete_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showDeleteModelDialog(models)
            }
            .show()
    }

    private fun showDeleteModelDialog(models: List<String>) {
        val items = models.toTypedArray()
        val checkedItems = BooleanArray(models.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_models)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                vibrationManager.vibrateMenuTap() // Use menu tap vibration for checkboxes
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        modelManager.deleteModel(models[i], object : ModelManager.ModelDeleteCallback {
                            override fun onModelDeleted(modelName: String) {
                                Toast.makeText(this@MainActivity, getString(R.string.model_deleted, modelName), Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
            }
            .show()
    }

    private fun copyAndLoadModel(modelUri: Uri) {
        runOnUiThread { showImportProgressDialog() }
        // // logAdapter.addLog(getString(R.string.importing_model_message))

        Thread {
            try {
                val callback = object : ModelManager.ModelCallback {
                    override fun onSuccess(modelName: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            modelManager.setActiveModel(modelName)
                            Toast.makeText(applicationContext, getString(R.string.model_imported_toast), Toast.LENGTH_SHORT).show()
                            // // logAdapter.addLog(getString(R.string.model_imported, modelName))
                            // // logAdapter.addLog(getString(R.string.model_switched, modelName))
                            processButton.isEnabled = images.isNotEmpty()
                            updateStrengthSliderVisibility()
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            Toast.makeText(applicationContext, getString(R.string.error_importing_model), Toast.LENGTH_LONG).show()
                            // logAdapter.addLog(getString(R.string.error_importing_model, error))
                            promptModelSelection()
                        }
                    }
                    override fun onProgress(progress: Int) {
                        runOnUiThread { updateImportProgressDialog(progress) }
                    }
                }
                modelManager.importModel(modelUri, callback)
            } catch (e: Exception) {
                runOnUiThread {
                    dismissImportProgressDialog()
                    Toast.makeText(applicationContext, getString(R.string.invalid_model), Toast.LENGTH_LONG).show()
                    // logAdapter.addLog(getString(R.string.invalid_model))
                    promptModelSelection()
                }
            }
        }.start()
    }

    private fun showImportProgressDialog() {
        if (importProgressDialog?.isShowing == true) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        importProgressBar = dialogView.findViewById(R.id.progressBar)
        importProgressText = dialogView.findViewById(R.id.progressText)
        importProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.importing_model)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        importProgressDialog?.show()
    }

    private fun updateImportProgressDialog(progress: Int) {
        importProgressDialog?.let {
            if (it.isShowing) {
                importProgressBar?.progress = progress
                importProgressText?.text = "$progress%"
            }
        }
    }

    private fun dismissImportProgressDialog() {
        importProgressDialog?.dismiss()
        importProgressDialog = null
    }

    private fun openImageInViewer(bitmap: Bitmap) {
        try {
            val isOutput = images.getOrNull(currentPage)?.outputBitmap === bitmap
            val fileName = "temp_image_${System.currentTimeMillis()}" + if (isOutput) ".png" else ".jpg"
            val cachePath = File(cacheDir, fileName)
            FileOutputStream(cachePath).use { fos ->
                if (isOutput) bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                else bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            }
            val photoURI = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.provider", cachePath
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(photoURI, if (isOutput) "image/png" else "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // logAdapter.addLog(getString(R.string.error_opening_image, e.message))
            Toast.makeText(this, getString(R.string.error_opening_image_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun onImageSelected(selectedImage: Uri) {
        try {
            var mimeType = contentResolver.getType(selectedImage)
            if (mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(selectedImage.toString())
                )
            }
            val bitmap = if (mimeType == "image/webp") {
                convertWebpToBitmap(selectedImage)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, selectedImage)
            }
            updateImageViews(bitmap)
        } catch (e: IOException) {
            // logAdapter.addLog(getString(R.string.error_loading_image, e.message))
            Toast.makeText(this, getString(R.string.error_loading_image, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertWebpToBitmap(webpUri: Uri): Bitmap {
        val webpBitmap = MediaStore.Images.Media.getBitmap(contentResolver, webpUri)
        val bmpFile = File(cacheDir, "input_temp.bmp")
        FileOutputStream(bmpFile).use { webpBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return BitmapFactory.decodeFile(bmpFile.absolutePath)
    }

    private fun handleMultipleImages(clipData: ClipData) {
        try {
            images.clear()
            perImageStrengthFactors.clear()
            var loadedCount = 0
            for (i in 0 until clipData.itemCount) {
                try {
                    val uri = clipData.getItemAt(i).uri
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    images.add(ProcessingImage(bitmap))
                    perImageStrengthFactors.add(lastStrength)
                    loadedCount++
                } catch (e: Exception) {
                    // logAdapter.addLog(getString(R.string.error_loading_image_n, i + 1, e.message))
                }
            }

            if (loadedCount > 0) {
                currentPage = 0
                processButton.isEnabled = modelManager.hasActiveModel()
                applyToAllSwitch.visibility = if (loadedCount > 1) View.VISIBLE else View.GONE
                Toast.makeText(this, getString(R.string.images_loaded, loadedCount), Toast.LENGTH_SHORT).show()
                showPreviews = true
                showFilmstrip = false
                updateImageViews()
            } else {
                // logAdapter.addLog(getString(R.string.no_images_loaded))
                Toast.makeText(this, getString(R.string.no_images_loaded), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // logAdapter.addLog(getString(R.string.error_loading_images, e.message))
            Toast.makeText(this, getString(R.string.error_loading_images, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageViews(bitmap: Bitmap) {
        images.clear()
        images.add(ProcessingImage(bitmap))
        perImageStrengthFactors.clear()
        perImageStrengthFactors.add(lastStrength)
        currentPage = 0
        beforeAfterView.clearImages() // Clear existing images first
        beforeAfterView.setBeforeImage(bitmap)
        beforeAfterView.setAfterImage(null) // Explicitly set after image to null
        processButton.isEnabled = modelManager.hasActiveModel()
        Toast.makeText(this, getString(R.string.image_loaded), Toast.LENGTH_SHORT).show()
        showPreviews = true
        showFilmstrip = false
        applyToAllSwitch.visibility = View.GONE
        updateStrengthSliderVisibility()
        updateImageViews()
    }

    // private fun updateImageViews() {
    //     if (images.isEmpty()) {
    //         beforeAfterView.clearImages()
    //         filmstripRecyclerView.visibility = View.GONE
    //         processingProgressBar.visibility = View.GONE
    //         return
    //     }

    //     // Show loading bar if processing
    //     if (isProcessing) {
    //         beforeAfterView.visibility = View.GONE
    //         filmstripRecyclerView.visibility = View.GONE
    //         processingProgressBar.visibility = View.VISIBLE
    //         return
    //     } else {
    //         processingProgressBar.visibility = View.GONE
    //     }

    //     beforeAfterView.visibility = View.VISIBLE
    //     val currentImage = images[currentPage]
    //     beforeAfterView.setBeforeImage(currentImage.inputBitmap)
    //     beforeAfterView.setAfterImage(currentImage.outputBitmap)

    //     // Hide slider if only before image is available
    //     if (beforeAfterView.hasOnlyBeforeImage()) {
    //         // Do not call setAfterImage with null if it expects a non-null Bitmap
    //         // Optionally, you can add a method in BeforeAfterImageView to clear the after image if needed
    //     }

    //     filmstripRecyclerView.visibility = if (images.size > 1) View.VISIBLE else View.GONE
    //     filmstripAdapter.submitList(images.toList())
    //     filmstripRecyclerView.scrollToPosition(currentPage)
    // }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val fileName = "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), 
                "$fileName.${outputFormat.lowercase()}")
            
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(
                    if (outputFormat == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.PNG,
                    100, out
                )
            }
            
            MediaScannerConnection.scanFile(this, arrayOf(outputFile.toString()), null, null)
            Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
            // logAdapter.addLog(getString(R.string.image_saved))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_saving_image, e.message), Toast.LENGTH_SHORT).show()
            // logAdapter.addLog(getString(R.string.error_saving_image, e.message))
        }
    }

    private fun shareImage(bitmap: Bitmap) {
        try {
            val cachePath = File(cacheDir, "shared_image.png")
            FileOutputStream(cachePath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.provider", cachePath
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_sharing_image, e.message), Toast.LENGTH_SHORT).show()
            // logAdapter.addLog(getString(R.string.error_sharing_image, e.message))
        }
    }

    private fun updateButtonVisibility() {
        runOnUiThread {
            processButton.visibility = if (isProcessing) View.GONE else View.VISIBLE
            cancelButton.visibility = if (isProcessing) View.VISIBLE else View.GONE
            selectButton.isEnabled = !isProcessing
            processButton.isEnabled = images.isNotEmpty() && modelManager.hasActiveModel()
            // Hide image view and filmstrip when processing, show loading bar
            beforeAfterView.visibility = if (isProcessing) View.GONE else View.VISIBLE
            filmstripRecyclerView.visibility = if (isProcessing) View.GONE else filmstripRecyclerView.visibility
            processingProgressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        }
    }

    private fun cancelProcessing() {
        if (isProcessing) {
            imageProcessor.cancelProcessing()
            isProcessing = false
            dismissProcessingNotification()
            updateButtonVisibility()
            // updatePageIndicator()
            updateImageViews()
            Toast.makeText(this, getString(R.string.processing_cancelled), Toast.LENGTH_SHORT).show()
            // logAdapter.addLog(getString(R.string.processing_cancelled))
        }
    }

    private fun processWithModel() {
        isProcessing = true
        updateButtonVisibility()
        // updatePageIndicator()
        updateImageViews()

        val isBatch = images.size > 1
        var completedCount = 0
        showProcessingNotification(0, images.size)

        fun processNext(index: Int) {
            if (!isProcessing || index >= images.size) {
                isProcessing = false
                if (completedCount > 0) {
                    showCompletionNotification(completedCount)
                } else {
                    clearAllNotifications()
                }
                dismissProcessingNotification()
                updateButtonVisibility()
                // updatePageIndicator()
                showFilmstrip = true
                updateImageViews()
                return
            }

            val image = images[index]
            val strength = if (applyStrengthToAll) lastStrength * 100f else perImageStrengthFactors[index] * 100f

            showProcessingNotification(index + 1, images.size)

            imageProcessor.processImage(
                image.inputBitmap,
                strength,
                object : ImageProcessor.ProcessCallback {
                    override fun onProgress(message: String) {
                        runOnUiThread {
                            if (isProcessing) {
                                // Log only unique and meaningful messages
                                when {
                                    // message.contains("Loading model") -> // logAdapter.addLog(message)
                                    // message.contains("Processing chunk") -> Unit // Skip chunk-specific logs
                                    // message.contains("Processing complete") -> Unit // Skip redundant completion logs
                                    // else -> // logAdapter.addLog(message)
                                }
                            }
                        }
                    }
                    override fun onComplete(result: Bitmap) {
                        runOnUiThread {
                            if (isProcessing) {
                                image.outputBitmap = result
                                completedCount++
                                if (!isBatch || index == images.size - 1) {
                                    vibrationManager.vibrateSuccess()
                                } else {
                                    vibrationManager.vibrateSingleSuccess()
                                }
                                // logAdapter.addLog(getString(R.string.processing_complete_single))
                                updateImageViews()
                                processNext(index + 1)
                            }
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            if (isProcessing) {
                                vibrationManager.vibrateError()
                                val errorMessage = if (isBatch) 
                                    getString(R.string.error_loading_image_n, index + 1, error)
                                else 
                                    getString(R.string.error_loading_image, error)
                                
                                showErrorNotification(errorMessage)
                                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                                processNext(index + 1)
                            }
                        }
                    }
                },
                index,
                images.size
            )
        }

        processNext(0)
    }

    private fun showProcessingNotification(currentImage: Int, totalImages: Int) {
        val notificationTitle = if (totalImages > 1) {
            getString(R.string.processing_batch, currentImage, totalImages)
        } else {
            getString(R.string.processing_single)
        }

        val notification = NotificationCompat.Builder(this, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_PROCESSING, notification)
    }

    private fun showErrorNotification(error: String) {
        vibrationManager.vibrateError()
        val notification = NotificationCompat.Builder(this, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.processing_error))
            .setContentText(error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).apply {
            cancel(NOTIFICATION_ID_PROCESSING)
            notify(NOTIFICATION_ID_ERROR, notification)
        }
    }

    private fun showCompletionNotification(totalImages: Int) {
        val notificationTitle = if (totalImages > 1) {
            getString(R.string.processing_complete_batch, totalImages)
        } else {
            getString(R.string.processing_complete_single)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(getString(R.string.processing_complete_description))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).apply {
            cancel(NOTIFICATION_ID_PROCESSING)
            notify(NOTIFICATION_ID_COMPLETION, notification)
        }
    }

    private fun dismissProcessingNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_PROCESSING)
    }

    private fun clearAllNotifications() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(NOTIFICATION_ID_PROCESSING)
        notificationManager.cancel(NOTIFICATION_ID_COMPLETION)
        notificationManager.cancel(NOTIFICATION_ID_ERROR)
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "processing_channel",
                getString(R.string.processing_updates),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.processing_updates_description)
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showConfigDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.config)
            .setSingleChoiceItems(
                arrayOf(getString(R.string.png), getString(R.string.bmp)),
                if (outputFormat == "BMP") FORMAT_BMP else FORMAT_PNG
            ) { dialog: DialogInterface, which: Int ->
                vibrationManager.vibrateDialogChoice()
                outputFormat = if (which == FORMAT_BMP) "BMP" else "PNG"
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(OUTPUT_FORMAT_KEY, outputFormat)
                    .apply()
                Toast.makeText(this, getString(R.string.output_format_set, outputFormat), Toast.LENGTH_SHORT).show()
                // logAdapter.addLog(getString(R.string.output_format_set, outputFormat))
                dialog.dismiss()
            }
            .setNeutralButton(R.string.manage_models) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showModelManagementDialog()
            }
            .setNegativeButton(R.string.cancel_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
            }
            .show()
    }

    private fun showDialog(
        title: Int,
        message: Int? = null,
        items: Array<String>? = null,
        checkedItem: Int = -1,
        onItemSelected: ((Int) -> Unit)? = null,
        positiveButton: Int? = null,
        onPositive: (() -> Unit)? = null,
        neutralButton: Int? = null,
        onNeutral: (() -> Unit)? = null,
        negativeButton: Int? = null,
        onNegative: (() -> Unit)? = null,
        cancellable: Boolean = true
    ) {
        MaterialAlertDialogBuilder(this).apply {
            setTitle(title)
            message?.let { setMessage(it) }
            items?.let { itemsArray ->
                if (checkedItem >= 0) {
                    setSingleChoiceItems(itemsArray, checkedItem) { dialog, which ->
                        onItemSelected?.invoke(which)
                        dialog.dismiss()
                    }
                } else {
                    setItems(itemsArray) { _, which ->
                        onItemSelected?.invoke(which)
                    }
                }
            }
            positiveButton?.let { setPositiveButton(it) { _, _ -> onPositive?.invoke() } }
            neutralButton?.let { setNeutralButton(it) { _, _ -> onNeutral?.invoke() } }
            negativeButton?.let { setNegativeButton(it) { _, _ -> onNegative?.invoke() } }
            setCancelable(cancellable)
        }.show()
    }

    private fun updateStrengthSliderVisibility() {
        if (!::imageProcessor.isInitialized) return
        
        val shouldShowStrength = !imageProcessor.isActiveModelSCUNet()
        runOnUiThread {
            strengthSeekBar.visibility = if (shouldShowStrength) View.VISIBLE else View.GONE
        }
        updateImageViews() // Trigger recomposition to update strength UI in Compose
    }
    
    private fun updateImageViews() {
        if (images.isEmpty()) {
            beforeAfterView.clearImages()
            filmstripRecyclerView.visibility = View.GONE
            processingProgressBar.visibility = View.GONE
            return
        }

        // Show loading bar if processing
        if (isProcessing) {
            beforeAfterView.visibility = View.GONE
            filmstripRecyclerView.visibility = View.GONE
            processingProgressBar.visibility = View.VISIBLE
            return
        } else {
            processingProgressBar.visibility = View.GONE
        }

        beforeAfterView.visibility = View.VISIBLE
        val currentImage = images[currentPage]
        beforeAfterView.setBeforeImage(currentImage.inputBitmap)
        beforeAfterView.setAfterImage(currentImage.outputBitmap)

        // Hide slider if only before image is available
        if (beforeAfterView.hasOnlyBeforeImage()) {
            // Do not call setAfterImage with null if it expects a non-null Bitmap
            // Optionally, you can add a method in BeforeAfterImageView to clear the after image if needed
        }

        filmstripRecyclerView.visibility = if (images.size > 1) View.VISIBLE else View.GONE
        filmstripAdapter.submitList(images.toList())
        filmstripRecyclerView.scrollToPosition(currentPage)
    }

    private fun setupFilmstrip() {
        filmstripAdapter = FilmstripAdapter { position ->
            if (position != currentPage) {
                currentPage = position
                beforeAfterView.resetView()
                updateImageViews()
            }
        }
        filmstripRecyclerView.adapter = filmstripAdapter
        filmstripRecyclerView.visibility = View.GONE
    }

    private inner class FilmstripAdapter(
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<FilmstripAdapter.ViewHolder>() {
        private var items = mutableListOf<ProcessingImage>()

        fun submitList(newItems: List<ProcessingImage>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.filmstrip_item_width),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(android.R.color.darker_gray)
                // Add padding between images
                val padding = (resources.displayMetrics.density * 6).toInt()
                setPadding(padding, padding, padding, padding)
                // Set rounded corners using outline provider (API 21+)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radius = (resources.displayMetrics.density * 8) // change this value to adjust the corner radius, larger = more rounded
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
                clipToOutline = true
            }
            return ViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position == currentPage)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
            init {
                imageView.setOnClickListener { 
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(position)
                    }
                }
            }

            fun bind(item: ProcessingImage, isSelected: Boolean) {
                imageView.setImageBitmap(item.outputBitmap ?: item.inputBitmap)
                imageView.alpha = if (isSelected) 1f else 0.6f
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_config -> {
                vibrationManager.vibrateMenuTap()
                showConfigDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllNotifications()
    }
}