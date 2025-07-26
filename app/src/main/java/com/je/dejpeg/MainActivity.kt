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
import android.media.ExifInterface
import android.graphics.Matrix
import java.io.InputStream
import com.je.dejpeg.models.ProcessingState

import com.je.dejpeg.utils.ProcessingAnimation
import com.je.dejpeg.utils.VibrationManager
import com.je.dejpeg.utils.NotificationHandler
import com.je.dejpeg.utils.BeforeAfterImageView
import com.je.dejpeg.dejpeg

import android.text.method.LinkMovementMethod
import io.noties.markwon.Markwon
import com.je.dejpeg.utils.DialogManager

class MainActivity : AppCompatActivity() {
    public lateinit var selectButton: Button
    public lateinit var processButton: Button
    public lateinit var strengthSeekBar: RangeSlider
    public lateinit var pageIndicator: TextView
    public lateinit var cancelButton: Button

    private lateinit var modelManager: ModelManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var applyToAllSwitch: MaterialSwitch

    private lateinit var imageDimensionsText: TextView

    private var importProgressDialog: Dialog? = null
    private var importProgressBar: ProgressBar? = null
    private var importProgressText: TextView? = null

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelPickerLauncher: ActivityResultLauncher<Intent>

    public val PREFS_NAME = "AppPrefs"
    public val STRENGTH_FACTOR_KEY = "strengthFactor"
    public val PROGRESS_FORMAT_KEY = "progressFormat"
    public val DEFAULT_PICKER_KEY = "defaultPicker"
    public val DEFAULT_ACTION_KEY = "defaultImagePickerAction"
    public val FORMAT_PNG = 0
    public val PICKER_GALLERY = 0
    public val PICKER_INTERNAL = 1

    private var defaultPicker = PICKER_GALLERY
    public var defaultImageAction: Int = -1

    private lateinit var beforeAfterView: BeforeAfterImageView

    private var isProcessing: Boolean = false

    public data class ProcessingImage(
        val inputBitmap: Bitmap,
        var outputBitmap: Bitmap? = null
    )

    public var images = mutableListOf<ProcessingImage>()
    private var currentPage = 0

    private var lastStrength = 0.50f
    private var showPreviews = true
    private var showFilmstrip = false
    private var perImageStrengthFactors = mutableListOf<Float>()
    private var applyStrengthToAll = true

    private val notificationPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) {
            setupNotificationChannel()
        } else {
            // Toast.makeText(this, "notifications denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val NOTIFICATION_ID_PROCESSING = 1
    private val NOTIFICATION_ID_COMPLETION = 2
    private val NOTIFICATION_ID_ERROR = 3

    private lateinit var filmstripAdapter: FilmstripAdapter
    private lateinit var filmstripRecyclerView: RecyclerView
    private lateinit var processingAnimation: ProcessingAnimation
    private lateinit var vibrationManager: VibrationManager

    private lateinit var notificationHandler: NotificationHandler
    
    private lateinit var placeholderContainer: LinearLayout
    
    private lateinit var processingText: TextView

    private var currentPhotoUri: Uri? = null

    private val processingQueue = mutableListOf<QueueItem>()
    private var isProcessingQueue = false

    private data class QueueItem(
        val image: ProcessingImage,
        val strength: Float,
        val requiresChunking: Boolean,
        val index: Int
    )

    private lateinit var dialogManager: DialogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            (application as dejpeg).registerActivityLifecycleCallbacks(AppLifecycleTracker)
        } catch (e: Exception) {
            showErrorDialog("failed to register lifecycle callbacks (try disabling battery optimization/restrictions), exception: " + e.message)
        }

        vibrationManager = VibrationManager(this)
        notificationHandler = NotificationHandler(this)
        setContentView(R.layout.activity_main)
        imageDimensionsText = findViewById(R.id.imageDimensionsText)

        selectButton = findViewById(R.id.selectButton)
        processButton = findViewById(R.id.processButton)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)
        applyToAllSwitch = findViewById(R.id.applyToAllSwitch)
        cancelButton = findViewById(R.id.cancelButton)
        applyToAllSwitch.visibility = View.GONE

        placeholderContainer = findViewById(R.id.placeholderContainer)
        processingText = findViewById(R.id.processingText)

        applyToAllSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateSwitcher()
            if (applyToAllSwitch.isChecked) {
                strengthSeekBar.setValues(lastStrength * 100f)
            } else if (currentPage < perImageStrengthFactors.size) {
                strengthSeekBar.setValues(perImageStrengthFactors[currentPage] * 100f)
            }
        }

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor(this, modelManager)

        // Initialize dialogManager after all dependencies are ready
        dialogManager = DialogManager(
            activity = this,
            vibrationManager = vibrationManager,
            modelManager = modelManager,
            imageProcessor = imageProcessor,
            notificationHandler = notificationHandler
        )

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lastStrength = prefs.getFloat(STRENGTH_FACTOR_KEY, 0.5f)
        strengthSeekBar.setValues(lastStrength * 100f)
        defaultPicker = prefs.getInt(DEFAULT_PICKER_KEY, PICKER_GALLERY)
        defaultImageAction = prefs.getInt(DEFAULT_ACTION_KEY, -1)

        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            try {
                if (result.resultCode == RESULT_OK) {
                    if (result.data?.data != null) {
                        result.data?.data?.let { uri ->
                            val inputStream = contentResolver.openInputStream(uri)
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeStream(inputStream, null, options)
                            inputStream?.close()

                            val width = options.outWidth
                            val height = options.outHeight

                            if (width > 4000 || height > 4000) {
                                showWarningDialog("the selected image is very large (${width}x${height}), (over 4000px) this can cause issues") {
                                    onImageSelected(uri)
                                }
                            } else {
                                onImageSelected(uri)
                            }
                        }
                    } else if (result.data?.clipData != null) {
                        handleMultipleImages(result.data!!.clipData!!)
                    } else if (currentPhotoUri != null) {
                        onImageSelected(currentPhotoUri!!)
                        currentPhotoUri = null
                    }
                }
            } catch (e: Exception) {
                showErrorDialog(getString(R.string.error_opening_image_dialog) + " " + e.message)
            }
        }    

        modelPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val modelUri = result.data!!.data
                if (modelUri != null) copyAndLoadModel(modelUri)
            } else {
                if (modelManager.hasActiveModel()) {
                    showModelManagementDialog()
                }
                else if (!modelManager.hasActiveModel()) {
                    promptModelSelection()
                }
            }
        }

        selectButton.setOnClickListener { vibrationManager.vibrateButton()
            if (defaultImageAction != -1) {
                when (defaultImageAction) {
                    0 -> launchGalleryPicker()
                    1 -> launchInternalPhotoPicker()
                    2 -> launchDocumentsPicker()
                    3 -> launchCamera()
                }
                return@setOnClickListener
            }
            showImagePickerDialog()
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
            vibrationManager.vibrateSliderTouch()
            if (fromUser) {
                vibrationManager.vibrateSliderChange()
            }
            val snapped = (value / 5).toInt() * 5f
            strengthSeekBar.setValues(snapped)
            lastStrength = snapped / 100f
            
            if (!applyToAllSwitch.isChecked && currentPage < perImageStrengthFactors.size) {
                perImageStrengthFactors[currentPage] = lastStrength
            }
            
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(STRENGTH_FACTOR_KEY, lastStrength)
                .apply()
        }

        beforeAfterView = findViewById(R.id.beforeAfterView)
        beforeAfterView.setButtonCallback(object : BeforeAfterImageView.ButtonCallback {
            override fun onShareClicked() {
                val currentImage = images.getOrNull(currentPage)?.outputBitmap
                if (currentImage != null) {
                    shareImage(currentImage)
                }
            }
            
            override fun onSaveClicked() {
                val currentImage = images.getOrNull(currentPage)?.outputBitmap
                if (currentImage != null) {
                    saveImageToGallery(currentImage)
                }
            }
        })
        filmstripRecyclerView = findViewById(R.id.filmstripRecyclerView)
        processingAnimation = findViewById(R.id.processingAnimation)
        setupFilmstrip()
        updateImageViews()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationHandler.setupNotificationChannel()
        }

        updateStrengthSliderVisibility()

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (isProcessing) {
                Toast.makeText(this, getString(R.string.processing_in_progress_toast), Toast.LENGTH_SHORT).show()
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                R.id.action_config -> {
                    vibrationManager.vibrateMenuTap()
                    showConfigDialog()
                    true
                }
                R.id.action_about -> {
                    vibrationManager.vibrateMenuTap()
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
        intent?.let {
            if (it.getBooleanExtra("show_service_info", false)) {
                showServiceInfoDialog()
            }
        }
        val serviceIntent = Intent(this, AppBackgroundService::class.java)
        startForegroundService(serviceIntent)
        // ImageProcessor.clearDirectory(ImageProcessor.chunkDir)
        // ImageProcessor.clearDirectory(ImageProcessor.processedDir)

        intent?.let {
            if (Intent.ACTION_SEND == it.action && it.type?.startsWith("image/") == true) {
                val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (imageUri != null) onImageSelected(imageUri)
            }
        }
        if (!modelManager.hasActiveModel()) promptModelSelection()
    }

    private fun getCorrectlyOrientedBitmap(uri: Uri): Bitmap {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val exif = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    public fun removeImage(position: Int) {
        if (position < 0 || position >= images.size) return
        
        images.removeAt(position)
        perImageStrengthFactors.removeAt(position)
        
        when {
            images.isEmpty() -> {
                currentPage = 0
                beforeAfterView.clearImages()
                imageDimensionsText.visibility = View.GONE
                applyToAllSwitch.visibility = View.GONE
            }
            currentPage >= images.size -> {
                currentPage = images.size - 1
                updateImageViews()
            }
            else -> {
                updateImageViews()
            }
        }
        
        applyToAllSwitch.visibility = if (images.size > 1) View.VISIBLE else View.GONE
    }

    public fun removeAllImages() {
        images.clear()
        perImageStrengthFactors.clear()
        currentPage = 0
        beforeAfterView.clearImages()
        imageDimensionsText.visibility = View.GONE
        applyToAllSwitch.visibility = View.GONE
        updateImageViews()
    }

    public fun removeAllExceptCurrent(position: Int) {
        if (position < 0 || position >= images.size) return
        
        val keepImage = images[position]
        val keepStrength = perImageStrengthFactors[position]
        
        images.clear()
        perImageStrengthFactors.clear()
        
        images.add(keepImage)
        perImageStrengthFactors.add(keepStrength)
        
        currentPage = 0
        updateImageViews()
        applyToAllSwitch.visibility = View.GONE
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            if (it.getBooleanExtra("show_service_info", false)) {
                showServiceInfoDialog()
            }
        }
    }

    private fun promptModelSelection() {
        dialogManager.promptModelSelection(modelPickerLauncher)
    }

    private fun showModelManagementDialog() {
        dialogManager.showModelManagementDialog(modelPickerLauncher, ::showDeleteModelDialog, ::showModelDownloadDialog)
    }

    private fun showServiceInfoDialog() {
        dialogManager.showServiceInfoDialog()
    }

    private fun showModelDownloadDialog() {
        dialogManager.showModelDownloadDialog(::showModelManagementDialog)
    }

    private fun showDeleteModelDialog(models: List<String>) {
        dialogManager.showDeleteModelDialog(models, ::showModelManagementDialog, ::promptModelSelection)
    }

    private fun copyAndLoadModel(modelUri: Uri, force: Boolean = false) {
        dialogManager.copyAndLoadModel(modelUri, force, ::showHashMismatchDialog)
    }

    private fun showHashMismatchDialog(modelUri: Uri, modelName: String, expected: String, actual: String) {
        dialogManager.showHashMismatchDialog(modelUri, modelName, expected, actual, ::copyAndLoadModel)
    }

    private fun showErrorDialog(message: String) {
        dialogManager.showErrorDialog(message, ::promptModelSelection)
    }

    private fun showWarningDialog(message: String, onContinue: () -> Unit) {
        dialogManager.showWarningDialog(message, onContinue)
    }

    private fun showImportProgressDialog() {
        dialogManager.showImportProgressDialog()
    }

    private fun updateImportProgressDialog(progress: Int) {
        dialogManager.updateImportProgressDialog(progress)
    }

    private fun dismissImportProgressDialog() {
        dialogManager.dismissImportProgressDialog()
    }

    private fun showImageRemovalDialog(position: Int) {
        dialogManager.showImageRemovalDialog(position, ::removeImage, ::removeAllImages, ::removeAllExceptCurrent)
    }

    private fun showConfigDialog() {
        dialogManager.showConfigDialog(::showModelManagementDialog)
    }

    private fun showImagePickerDialog() {
        dialogManager.showImagePickerDialog(::launchGalleryPicker, ::launchInternalPhotoPicker, ::launchDocumentsPicker, ::launchCamera)
    }

    private fun showAboutDialog() {
        dialogManager.showAboutDialog(::showFAQDialog)
    }

    private fun showFAQDialog() {
        dialogManager.showFAQDialog()
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        imagePickerLauncher.launch(intent)
    }

    private fun launchInternalPhotoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        imagePickerLauncher.launch(intent)
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val tempFile = File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                storageDir
            )
            val photoURI = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                tempFile
            )
            currentPhotoUri = photoURI
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            imagePickerLauncher.launch(intent)
        } catch (e: IOException) {
            showErrorDialog(getString(R.string.error_camera_dialog) + " " + e.message)
        }
    }

    private fun launchDocumentsPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        imagePickerLauncher.launch(intent)
    }

    private fun processQueue() {
        if (isProcessingQueue || processingQueue.isEmpty()) return
        isProcessingQueue = true

        val item = processingQueue.removeAt(0)
        imageProcessor.processImage(
            item.image.inputBitmap,
            item.strength,
            object : ImageProcessor.ProcessCallback {
                override fun onComplete(result: Bitmap) {
                    runOnUiThread {
                        item.image.outputBitmap = result
                        isProcessingQueue = false
                        if (processingQueue.isNotEmpty()) {
                            processQueue()
                        } else {
                            isProcessing = false
                            ProcessingState.Companion.markAllImagesCompleted(context = this@MainActivity)
                            updateButtonVisibility()
                            showFilmstrip = true
                            updateImageViews()
                        }
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        isProcessingQueue = false
                        showErrorDialog(error)
                        if (processingQueue.isNotEmpty()) {
                            processQueue()
                        }
                    }
                }

                override fun onProgress(message: String) {
                    runOnUiThread {
                        processingText.text = ProcessingState.getStatusString(this@MainActivity)
                        notificationHandler.showProgressNotification(message)
                    }
                }
            },
            item.index,
            images.size
        )
    }

    private fun updateButtonVisibility() {
        runOnUiThread {
            processButton.visibility = if (isProcessing) View.GONE else View.VISIBLE
            cancelButton.visibility = if (isProcessing) View.VISIBLE else View.GONE
            selectButton.isEnabled = !isProcessing
            processButton.isEnabled = images.isNotEmpty() && modelManager.hasActiveModel()
            beforeAfterView.visibility = if (isProcessing) View.GONE else View.VISIBLE
            filmstripRecyclerView.visibility = if (!isProcessing && images.size > 1) View.VISIBLE else View.GONE
            processingAnimation.visibility = if (isProcessing) View.VISIBLE else View.GONE
            processingText.visibility = if (isProcessing) View.VISIBLE else View.GONE
        }
    }

    public fun cancelProcessing() {
            imageProcessor.cancelProcessing()
            dismissProcessingNotification()
            Toast.makeText(this, getString(R.string.processing_cancelled_toast), Toast.LENGTH_SHORT).show()
            // this is totally needed 100% i'm not lazy at all (no really, I don't wanna have to deal with native code execution termination)
            val restartIntent = Intent(this, MainActivity::class.java)
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(restartIntent)
            android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun processWithModel() {
        isProcessing = true
        updateButtonVisibility()
        processingAnimation.visibility = View.VISIBLE
        processingText.visibility = View.VISIBLE
        processingText.text = getString(R.string.loading_status)
        beforeAfterView.visibility = View.GONE
        filmstripRecyclerView.visibility = View.GONE
        processingQueue.clear()
        isProcessingQueue = false
        images.forEachIndexed { index, image -> 
            val strength = if (applyToAllSwitch.isChecked) lastStrength * 100f else perImageStrengthFactors[index] * 100f
            val needsChunking = image.inputBitmap.width > ImageProcessor.DEFAULT_CHUNK_SIZE || image.inputBitmap.height > ImageProcessor.DEFAULT_CHUNK_SIZE
            processingQueue.add(QueueItem(image, strength, needsChunking, index))
        }
        processingQueue.sortBy { !it.requiresChunking }
        ProcessingState.setProcessingOrder(processingQueue.map { it.index })
        ProcessingState.queuedImages = images.size
        ProcessingState.Companion.allImagesCompleted = false
        processQueue()
    }

    private fun showErrorNotification(error: String) {
        notificationHandler.showErrorNotification(error)
    }

    private fun showCompletionNotification(totalImages: Int) {
        notificationHandler.showCompletionNotification(totalImages)
    }

    private fun dismissProcessingNotification() {
        notificationHandler.dismissProcessingNotification()
    }

    private fun clearAllNotifications() {
        notificationHandler.clearAllNotifications()
    }

    private fun setupNotificationChannel() {
        notificationHandler.setupNotificationChannel()
    }

    override fun onResume() {
        super.onResume()
        // notificationHandler.clearAllNotifications()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationHandler.clearAllNotifications()
        stopService(Intent(this, AppBackgroundService::class.java))
    }

    public fun updateStrengthSliderVisibility() {
        if (!::modelManager.isInitialized) return
        val activeModel = modelManager.getActiveModelName()
        val shouldShowStrength = activeModel?.contains("fbcnn", ignoreCase = true) == true
        runOnUiThread {
            strengthSeekBar.visibility = if (shouldShowStrength) View.VISIBLE else View.GONE
        }
        updateImageViews()
    }
    
    private fun updateImageViews() {
        if (images.isEmpty()) {
            beforeAfterView.clearImages()
            imageDimensionsText.visibility = View.GONE
            filmstripRecyclerView.visibility = View.GONE
            processingAnimation.visibility = View.GONE
            processingText.visibility = View.GONE
            placeholderContainer.visibility = View.VISIBLE
            return
        }

        if (isProcessing && !com.je.dejpeg.models.ProcessingState.Companion.allImagesCompleted) {
            beforeAfterView.clearImages()
            imageDimensionsText.visibility = View.GONE
            filmstripRecyclerView.visibility = View.GONE
            processingAnimation.visibility = View.VISIBLE
            processingText.visibility = View.VISIBLE
            placeholderContainer.visibility = View.GONE
            return
        }

        placeholderContainer.visibility = View.GONE

        processingAnimation.visibility = View.GONE
        processingText.visibility = View.GONE

        beforeAfterView.visibility = View.VISIBLE
        val currentImage = images[currentPage]

        val width = currentImage.inputBitmap.width
        val height = currentImage.inputBitmap.height
        imageDimensionsText.text = "${width}x${height}"
        imageDimensionsText.visibility = View.VISIBLE

        beforeAfterView.setBeforeImage(currentImage.inputBitmap)
        beforeAfterView.setAfterImage(currentImage.outputBitmap)

        filmstripRecyclerView.visibility = if (images.size > 1) View.VISIBLE else View.GONE
        filmstripAdapter.submitList(images.toList())
        filmstripRecyclerView.scrollToPosition(currentPage)
    }

    private fun onImageSelected(selectedImage: Uri) {
        try {
            val bitmap = getCorrectlyOrientedBitmap(selectedImage)
            updateImageViews(bitmap)
        } catch (e: IOException) {
            showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message)
        }
    }


    private fun handleMultipleImages(clipData: ClipData) {
        try {
            val oversizedUris = mutableListOf<Uri>()
            val regularUris = mutableListOf<Uri>()

            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val inputStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (options.outWidth > 4000 || options.outHeight > 4000) {
                    oversizedUris.add(uri)
                } else {
                    regularUris.add(uri)
                }
            }

            val loadImages: () -> Unit = {
                images.clear()
                perImageStrengthFactors.clear()
                var loadedCount = 0

                val allUris = regularUris + oversizedUris
                for (uri in allUris) {
                    try {
                        val bitmap = getCorrectlyOrientedBitmap(uri)
                        images.add(ProcessingImage(bitmap))
                        perImageStrengthFactors.add(lastStrength)
                        loadedCount++
                    } catch (e: Exception) {
                        showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message)
                        continue
                    }
                }

                if (loadedCount > 0) {
                    currentPage = 0
                    processButton.isEnabled = modelManager.hasActiveModel()
                    applyToAllSwitch.visibility = if (loadedCount > 1) View.VISIBLE else View.GONE
                    strengthSeekBar.setValues(perImageStrengthFactors[currentPage] * 100f)
                    Toast.makeText(this, getString(R.string.batch_images_loaded_toast, loadedCount), Toast.LENGTH_SHORT).show()
                    showPreviews = true
                    showFilmstrip = false
                    updateImageViews()
                } else {
                    Toast.makeText(this, getString(R.string.no_images_loaded_toast), Toast.LENGTH_SHORT).show()
                }
            }

            if (oversizedUris.isNotEmpty()) {
                val message = "one or more selected images are very large (exceeds 4000px), this might affect performance."
                showWarningDialog(message, onContinue = loadImages)
            } else {
                loadImages()
            }

        } catch (e: Exception) {
            showErrorDialog(getString(R.string.error_loading_all_images_batch_dialog, e.message))
        }
    }


    private fun updateImageViews(bitmap: Bitmap) {
        images.clear()
        images.add(ProcessingImage(bitmap))
        perImageStrengthFactors.clear()
        perImageStrengthFactors.add(lastStrength)
        currentPage = 0
        beforeAfterView.clearImages()
        imageDimensionsText.visibility = View.GONE
        beforeAfterView.setBeforeImage(bitmap)
        beforeAfterView.setAfterImage(null)
        processButton.isEnabled = modelManager.hasActiveModel()
        Toast.makeText(this, getString(R.string.single_image_loaded_toast), Toast.LENGTH_SHORT).show()
        showPreviews = true
        showFilmstrip = false
        applyToAllSwitch.visibility = View.GONE
        updateStrengthSliderVisibility()
        updateImageViews()
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressText)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.saving_image_title)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        dialog.show()

        Thread {
            try {
                val fileName = "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), 
                    "$fileName.png")
                    
                runOnUiThread { progressText.text = getString(R.string.saving_status) }
                
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                runOnUiThread { 
                    progressText.text = getString(R.string.saving_status)
                    progressBar.progress = 50 // "halfway done" TvT this is just a placeholder and im lazy
                }
                
                MediaScannerConnection.scanFile(this, arrayOf(outputFile.toString()), null, null)
                
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.image_saved_toast), Toast.LENGTH_SHORT).show()
                    vibrationManager.vibrateButton()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    showErrorDialog(getString(R.string.error_saving_image_dialog) + " " + e.message)
                    vibrationManager.vibrateError()
                }
            }
        }.start()
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
            vibrationManager.vibrateButton()
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_image)))
        } catch (e: Exception) {
            showErrorDialog(getString(R.string.error_sharing_image_dialog) + " " + e.message)
            vibrationManager.vibrateError()
        }
    }

    private fun setupFilmstrip() {
        filmstripAdapter = FilmstripAdapter(
            onClick = { position ->
                if (position != currentPage) {
                    currentPage = position
                    if (!applyToAllSwitch.isChecked && position < perImageStrengthFactors.size) {
                        strengthSeekBar.setValues(perImageStrengthFactors[position] * 100f)
                    }
                    beforeAfterView.resetView()
                    updateImageViews()
                }
            },
            onLongClick = { position ->
                vibrationManager.vibrateMenuTap()
                showImageRemovalDialog(position)
            }
        )
        filmstripRecyclerView.adapter = filmstripAdapter
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            filmstripRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, 
                androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false)
        } else {
            filmstripRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, 
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        }
        
        filmstripRecyclerView.visibility = View.GONE
    }

    private inner class FilmstripAdapter(
        private val onClick: (Int) -> Unit,
        private val onLongClick: (Int) -> Unit
    ) : RecyclerView.Adapter<FilmstripAdapter.ViewHolder>() {
        private var items = mutableListOf<ProcessingImage>()

        fun submitList(newItems: List<ProcessingImage>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val isLandscape = parent.context.resources.configuration.orientation == 
                android.content.res.Configuration.ORIENTATION_LANDSCAPE

            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    if (isLandscape) ViewGroup.LayoutParams.MATCH_PARENT 
                    else resources.getDimensionPixelSize(R.dimen.filmstrip_item_width),
                    if (isLandscape) resources.getDimensionPixelSize(R.dimen.filmstrip_item_width) 
                    else ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(android.R.color.darker_gray)
                val padding = (resources.displayMetrics.density * 6).toInt()
                setPadding(padding, padding, padding, padding)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radius = (resources.displayMetrics.density * 8)
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
                        onClick(position)
                    }
                }
                imageView.setOnLongClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onLongClick(position)
                    }
                    true
                }
            }

            fun bind(item: ProcessingImage, isSelected: Boolean) {
                imageView.setImageBitmap(item.outputBitmap ?: item.inputBitmap)
                imageView.alpha = if (isSelected) 1f else 0.6f
            }
        }
    }
}