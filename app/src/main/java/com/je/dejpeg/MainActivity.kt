package com.je.dejpeg

import android.app.*
import android.content.*
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.*
import android.view.*
import android.widget.*
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.je.dejpeg.models.ProcessingState
import com.je.dejpeg.utils.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import android.provider.OpenableColumns

class MainActivity : AppCompatActivity() {
    lateinit var selectButton: Button
    lateinit var processButton: Button
    lateinit var strengthSeekBar: RangeSlider
    lateinit var pageIndicator: TextView
    lateinit var cancelButton: Button
    private lateinit var modelManager: ModelManager
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var applyToAllSwitch: MaterialSwitch
    private lateinit var imageDimensionsText: TextView
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var modelPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var beforeAfterView: BeforeAfterImageView
    private lateinit var filmstripAdapter: FilmstripAdapter
    private lateinit var filmstripRecyclerView: RecyclerView
    private lateinit var processingAnimation: ProcessingAnimation
    private lateinit var vibrationManager: VibrationManager
    private lateinit var notificationHandler: NotificationHandler
    private lateinit var placeholderContainer: LinearLayout
    private lateinit var processingText: TextView
    private lateinit var dialogManager: DialogManager

    val PREFS_NAME = "AppPrefs"
    val STRENGTH_FACTOR_KEY = "strengthFactor"
    val DEFAULT_PICKER_KEY = "defaultPicker"
    val DEFAULT_ACTION_KEY = "defaultImagePickerAction"
    val SKIP_SAVE_DIALOG_KEY = "skipSaveDialog"
    val PICKER_GALLERY = 0
    val PICKER_INTERNAL = 1

    private var defaultPicker = PICKER_GALLERY
    var defaultImageAction: Int = -1
    private var skipSaveDialog = false
    private var isProcessing = false
    private var currentPhotoUri: Uri? = null
    private var currentPage = 0
    private var lastStrength = 0.5f
    private var showPreviews = true
    private var showFilmstrip = false
    private var isProcessingQueue = false
    private val processingQueue = mutableListOf<QueueItem>()
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) setupNotificationChannel()
    }

    data class ProcessingImage(val inputBitmap: Bitmap, var outputBitmap: Bitmap? = null, val originalFilename: String? = null)
    data class QueueItem(val image: ProcessingImage, val strength: Float, val requiresChunking: Boolean, val index: Int)

    var images = mutableListOf<ProcessingImage>()
    private var perImageStrengthFactors = mutableListOf<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { (application as dejpeg).registerActivityLifecycleCallbacks(AppLifecycleTracker) } catch (e: Exception) { showErrorDialog("failed to start background service (try disabling battery optimizations/restrictions), exception: ${e.message}") }
        vibrationManager = VibrationManager(this)
        notificationHandler = NotificationHandler(this)
        setContentView(R.layout.activity_main)
        imageDimensionsText = findViewById(R.id.imageDimensionsText)
        selectButton = findViewById(R.id.selectButton)
        processButton = findViewById(R.id.processButton)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)
        applyToAllSwitch = findViewById(R.id.applyToAllSwitch)
        cancelButton = findViewById(R.id.cancelButton)
        placeholderContainer = findViewById(R.id.placeholderContainer)
        processingText = findViewById(R.id.processingText)
        applyToAllSwitch.visibility = View.GONE

        applyToAllSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateSwitcher()
            strengthSeekBar.setValues(if (applyToAllSwitch.isChecked) lastStrength * 100f else perImageStrengthFactors.getOrNull(currentPage)?.times(100f) ?: lastStrength * 100f)
        }

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor(this, modelManager)
        dialogManager = DialogManager(this, vibrationManager, modelManager, imageProcessor, notificationHandler)

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).apply {
            lastStrength = getFloat(STRENGTH_FACTOR_KEY, 0.5f)
            strengthSeekBar.setValues(lastStrength * 100f)
            defaultPicker = getInt(DEFAULT_PICKER_KEY, PICKER_GALLERY)
            defaultImageAction = getInt(DEFAULT_ACTION_KEY, -1)
            skipSaveDialog = getBoolean(SKIP_SAVE_DIALOG_KEY, false)
        }

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == RESULT_OK) {
                    result.data?.clipData?.let { handleMultipleImages(it) }
                        ?: currentPhotoUri?.let { onImageSelected(it); currentPhotoUri = null }
                }
            } catch (e: Exception) { showErrorDialog(getString(R.string.error_opening_image_dialog) + " " + e.message) }
        }

        modelPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                result.data!!.data?.let { modelUri ->
                    val name = contentResolver.query(modelUri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
                    } ?: modelUri.lastPathSegment
                    if (name != null && name.endsWith(".onnx", true)) copyAndLoadModel(modelUri)
                    else { Toast.makeText(this, "please select an onnx file only", Toast.LENGTH_SHORT).show(); promptModelSelection() }
                }
            } else if (modelManager.hasActiveModel()) showModelManagementDialog() else promptModelSelection()
        }

        selectButton.setOnClickListener {
            vibrationManager.vibrateButton()
            when (defaultImageAction) {
                0 -> launchGalleryPicker()
                1 -> launchInternalPhotoPicker()
                2 -> launchDocumentsPicker()
                3 -> launchCamera()
                -1 -> showImagePickerDialog()
            }
        }
        processButton.setOnClickListener { vibrationManager.vibrateButton(); if (images.isNotEmpty()) processWithModel() }
        cancelButton.setOnClickListener { vibrationManager.vibrateButton(); cancelProcessing() }
        processButton.isEnabled = false

        strengthSeekBar.addOnChangeListener { _, value, fromUser ->
            vibrationManager.vibrateSliderTouch()
            if (fromUser) vibrationManager.vibrateSliderChange()
            val snapped = (value / 5).toInt() * 5f
            strengthSeekBar.setValues(snapped)
            lastStrength = snapped / 100f
            if (!applyToAllSwitch.isChecked && currentPage < perImageStrengthFactors.size) perImageStrengthFactors[currentPage] = lastStrength
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putFloat(STRENGTH_FACTOR_KEY, lastStrength).apply()
        }

        beforeAfterView = findViewById(R.id.beforeAfterView)
        beforeAfterView.setButtonCallback(object : BeforeAfterImageView.ButtonCallback {
            override fun onShareClicked() { images.getOrNull(currentPage)?.outputBitmap?.let { shareImage(it) } }
            override fun onSaveClicked() {
                images.getOrNull(currentPage)?.outputBitmap?.let {
                    if (images.count { it.outputBitmap != null } > 1) showSaveImageDialog(it, true)
                    else saveImageToGallery(it)
                }
            }
        })
        filmstripRecyclerView = findViewById(R.id.filmstripRecyclerView)
        processingAnimation = findViewById(R.id.processingAnimation)
        setupFilmstrip()
        updateImageViews()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else notificationHandler.setupNotificationChannel()

        updateStrengthSliderVisibility()
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener { item ->
            if (isProcessing) { Toast.makeText(this, getString(R.string.processing_in_progress_toast), Toast.LENGTH_SHORT).show(); true }
            else when (item.itemId) {
                R.id.action_config -> { vibrationManager.vibrateMenuTap(); showConfigDialog(); true }
                R.id.action_about -> { vibrationManager.vibrateMenuTap(); showAboutDialog(); true }
                else -> false
            }
        }
        intent?.let { if (it.getBooleanExtra("show_service_info", false)) showServiceInfoDialog() }
        startForegroundService(Intent(this, AppBackgroundService::class.java))
        intent?.let {
            if (Intent.ACTION_SEND == it.action && it.type?.startsWith("image/") == true) {
                val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                imageUri?.let { onImageSelected(it) }
            }
        }
        if (!modelManager.hasActiveModel()) promptModelSelection()
    }

    private fun getCorrectlyOrientedBitmap(uri: Uri): Bitmap {
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }!!
        val orientation = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return if (rotation != 0) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else bitmap
    }

    private fun extractFilenameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) c.getString(idx) else null
            } ?: uri.lastPathSegment?.substringBeforeLast(".")
        } catch (_: Exception) { uri.lastPathSegment?.substringBeforeLast(".") }
            ?.takeIf { !it.isNullOrBlank() && !it.contains(Regex("[<>:\"/\\|?*]")) }
    }

    private fun generateUniqueFilename(base: String, existing: Set<String>): String {
        if (!existing.contains(base)) return base
        var counter = 1
        var newName = "${base}_$counter"
        while (existing.contains(newName)) { counter++; newName = "${base}_$counter" }
        return newName
    }

    fun removeImage(pos: Int) {
        if (pos !in images.indices) return
        images.removeAt(pos)
        perImageStrengthFactors.removeAt(pos)
        when {
            images.isEmpty() -> { currentPage = 0; beforeAfterView.clearImages(); imageDimensionsText.visibility = View.GONE; applyToAllSwitch.visibility = View.GONE }
            currentPage >= images.size -> { currentPage = images.size - 1; updateImageViews() }
            else -> updateImageViews()
        }
        applyToAllSwitch.visibility = if (images.size > 1) View.VISIBLE else View.GONE
    }

    fun removeAllImages() {
        images.clear(); perImageStrengthFactors.clear(); currentPage = 0
        beforeAfterView.clearImages(); imageDimensionsText.visibility = View.GONE
        applyToAllSwitch.visibility = View.GONE; updateImageViews()
    }

    fun removeAllExceptCurrent(pos: Int) {
        if (pos !in images.indices) return
        val keepImage = images[pos]; val keepStrength = perImageStrengthFactors[pos]
        images.clear(); perImageStrengthFactors.clear()
        images.add(keepImage); perImageStrengthFactors.add(keepStrength)
        currentPage = 0; updateImageViews(); applyToAllSwitch.visibility = View.GONE
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { if (it.getBooleanExtra("show_service_info", false)) showServiceInfoDialog() }
    }

    private fun promptModelSelection() = dialogManager.promptModelSelection(modelPickerLauncher)
    private fun showModelManagementDialog() = dialogManager.showModelManagementDialog(modelPickerLauncher, ::showDeleteModelDialog, ::showModelDownloadDialog)
    private fun showServiceInfoDialog() = dialogManager.showServiceInfoDialog()
    private fun showModelDownloadDialog() = dialogManager.showModelDownloadDialog(::showModelManagementDialog)
    private fun showDeleteModelDialog(models: List<String>) = dialogManager.showDeleteModelDialog(models, ::showModelManagementDialog, ::promptModelSelection)
    private fun copyAndLoadModel(modelUri: Uri, force: Boolean = false) = dialogManager.copyAndLoadModel(modelUri, force, ::showExperimentalModelDialog)
    private fun showExperimentalModelDialog(modelUri: Uri, modelName: String, expected: String, actual: String) = dialogManager.showHashMismatchDialog(modelUri, modelName, expected, actual, ::copyAndLoadModel, modelPickerLauncher)
    private fun showErrorDialog(message: String) = dialogManager.showErrorDialog(message, ::promptModelSelection)
    private fun showWarningDialog(message: String, onContinue: () -> Unit) = dialogManager.showWarningDialog(message, onContinue)
    private fun showImportProgressDialog() = dialogManager.showImportProgressDialog()
    private fun updateImportProgressDialog(progress: Int) = dialogManager.updateImportProgressDialog(progress)
    private fun dismissImportProgressDialog() = dialogManager.dismissImportProgressDialog()
    private fun showImageRemovalDialog(position: Int) = dialogManager.showImageRemovalDialog(position, ::removeImage, ::removeAllImages, ::removeAllExceptCurrent)
    private fun showConfigDialog() = dialogManager.showConfigDialog(::showModelManagementDialog, ::clearSkipSaveDialogOption)
    private fun showImagePickerDialog() = dialogManager.showImagePickerDialog(::launchGalleryPicker, ::launchInternalPhotoPicker, ::launchDocumentsPicker, ::launchCamera)
    private fun showAboutDialog() = dialogManager.showAboutDialog(::showFAQDialog)
    private fun showFAQDialog() = dialogManager.showFAQDialog()
    private fun clearSkipSaveDialogOption() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(SKIP_SAVE_DIALOG_KEY).apply()
        skipSaveDialog = false
        Toast.makeText(this, R.string.save_dialog_skip_cleared_toast, Toast.LENGTH_SHORT).show()
    }

    private fun launchGalleryPicker() = imagePickerLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
    private fun launchInternalPhotoPicker() = imagePickerLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val tempFile = File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
            val photoURI = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", tempFile)
            currentPhotoUri = photoURI
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            imagePickerLauncher.launch(intent)
        } catch (e: IOException) { showErrorDialog(getString(R.string.error_camera_dialog) + " " + e.message) }
    }
    private fun launchDocumentsPicker() = imagePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })

    private fun processQueue() {
        if (isProcessingQueue || processingQueue.isEmpty()) return
        isProcessingQueue = true
        val item = processingQueue.removeAt(0)
        imageProcessor.processImage(
            item.image.inputBitmap, item.strength,
            object : ImageProcessor.ProcessCallback {
                override fun onComplete(result: Bitmap) = runOnUiThread {
                    item.image.outputBitmap = result
                    isProcessingQueue = false
                    if (processingQueue.isNotEmpty()) processQueue()
                    else {
                        isProcessing = false
                        ProcessingState.markAllImagesCompleted(this@MainActivity)
                        updateButtonVisibility()
                        showFilmstrip = true
                        updateImageViews()
                    }
                }
                override fun onError(error: String) = runOnUiThread {
                    isProcessingQueue = false
                    showErrorDialog(error)
                    if (processingQueue.isNotEmpty()) processQueue()
                }
                override fun onProgress(message: String) = runOnUiThread {
                    val info = message.takeIf { !it.isNullOrBlank() } ?: ProcessingState.getStatusString(this@MainActivity)
                    processingText.text = info
                    notificationHandler.showProgressNotification(info)
                }
            },
            item.index, images.size
        )
    }

    private fun updateButtonVisibility() {
        processButton.visibility = if (isProcessing) View.GONE else View.VISIBLE
        cancelButton.visibility = if (isProcessing) View.VISIBLE else View.GONE
        selectButton.isEnabled = !isProcessing
        processButton.isEnabled = images.isNotEmpty() && modelManager.hasActiveModel()
        beforeAfterView.visibility = if (isProcessing) View.GONE else View.VISIBLE
        filmstripRecyclerView.visibility = if (!isProcessing && images.size > 1) View.VISIBLE else View.GONE
        processingAnimation.visibility = if (isProcessing) View.VISIBLE else View.GONE
        processingText.visibility = if (isProcessing) View.VISIBLE else View.GONE
    }

    fun cancelProcessing() {
        imageProcessor.cancelProcessing()
        dismissProcessingNotification()
        Toast.makeText(this, getString(R.string.processing_cancelled_toast), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun processWithModel() {
        isProcessing = true
        updateButtonVisibility()
        processingAnimation.visibility = View.VISIBLE
        processingText.visibility = View.VISIBLE
        processingText.text = getString(R.string.processing_preparing)
        beforeAfterView.visibility = View.GONE
        filmstripRecyclerView.visibility = View.GONE
        processingQueue.clear()
        isProcessingQueue = false
        images.forEachIndexed { idx, img ->
            val strength = if (applyToAllSwitch.isChecked) lastStrength * 100f else perImageStrengthFactors[idx] * 100f
            val needsChunking = img.inputBitmap.width > ImageProcessor.DEFAULT_CHUNK_SIZE || img.inputBitmap.height > ImageProcessor.DEFAULT_CHUNK_SIZE
            processingQueue.add(QueueItem(img, strength, needsChunking, idx))
        }
        processingQueue.sortBy { !it.requiresChunking }
        ProcessingState.setProcessingOrder(processingQueue.map { it.index })
        ProcessingState.queuedImages = images.size
        ProcessingState.allImagesCompleted = false
        processQueue()
    }

    private fun showErrorNotification(error: String) = notificationHandler.showErrorNotification(error)
    private fun showCompletionNotification(totalImages: Int) = notificationHandler.showCompletionNotification(totalImages)
    private fun dismissProcessingNotification() = notificationHandler.dismissProcessingNotification()
    private fun clearAllNotifications() = notificationHandler.clearAllNotifications()
    private fun setupNotificationChannel() = notificationHandler.setupNotificationChannel()

    override fun onDestroy() {
        super.onDestroy()
        notificationHandler.clearAllNotifications()
        stopService(Intent(this, AppBackgroundService::class.java))
    }

    fun updateStrengthSliderVisibility() {
        if (!::modelManager.isInitialized) return
        val shouldShowStrength = modelManager.getActiveModelName()?.contains("fbcnn", true) == true
        strengthSeekBar.visibility = if (shouldShowStrength) View.VISIBLE else View.GONE
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
        if (isProcessing && !ProcessingState.allImagesCompleted) {
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
        imageDimensionsText.text = "${currentImage.inputBitmap.width}x${currentImage.inputBitmap.height}"
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
            val filename = extractFilenameFromUri(selectedImage)
            updateImageViews(bitmap, filename)
        } catch (e: IOException) { showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message) }
    }

    private fun handleMultipleImages(clipData: ClipData) {
        try {
            val oversizedUris = mutableListOf<Uri>()
            val regularUris = mutableListOf<Uri>()
            val blockedUris = mutableListOf<Uri>()
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                when {
                    options.outWidth > 8000 || options.outHeight > 8000 -> blockedUris.add(uri)
                    options.outWidth > 4000 || options.outHeight > 4000 -> oversizedUris.add(uri)
                    else -> regularUris.add(uri)
                }
            }
            val loadImages = {
                images.clear(); perImageStrengthFactors.clear()
                val allUris = regularUris + oversizedUris
                allUris.forEach {
                    try {
                        val bitmap = getCorrectlyOrientedBitmap(it)
                        val filename = extractFilenameFromUri(it)
                        images.add(ProcessingImage(bitmap, originalFilename = filename))
                        perImageStrengthFactors.add(lastStrength)
                    } catch (e: Exception) { showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message) }
                }
                if (images.isNotEmpty()) {
                    currentPage = 0
                    processButton.isEnabled = modelManager.hasActiveModel()
                    applyToAllSwitch.visibility = if (images.size > 1 && modelManager.getActiveModelName()?.startsWith("fbcnn_") == true) View.VISIBLE else View.GONE
                    strengthSeekBar.setValues(perImageStrengthFactors[currentPage] * 100f)
                    Toast.makeText(this, getString(R.string.batch_images_loaded_toast, images.size), Toast.LENGTH_SHORT).show()
                    showPreviews = true; showFilmstrip = false
                    updateImageViews()
                } else Toast.makeText(this, getString(R.string.no_images_loaded_toast), Toast.LENGTH_SHORT).show()
            }
            if (oversizedUris.isNotEmpty()) showWarningDialog("one or more images are very large (exceeds 4000px), this might affect performance.", loadImages)
            if (blockedUris.isNotEmpty()) showWarningDialog("one or more images were too large and not loaded (exceeded 8000px), please resize or manually split using an image editor.", loadImages)
            else if (oversizedUris.isEmpty()) loadImages()
        } catch (e: Exception) { showErrorDialog(getString(R.string.error_loading_all_images_batch_dialog, e.message)) }
    }

    private fun updateImageViews(bitmap: Bitmap, filename: String? = null) {
        images.clear()
        images.add(ProcessingImage(bitmap, originalFilename = filename))
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
        if (skipSaveDialog) {
            val filename = images.getOrNull(currentPage)?.originalFilename
            saveImageWithDefaultName(bitmap, filename)
        } else showSaveImageDialog(bitmap, false)
    }

    private fun showSaveImageDialog(bitmap: Bitmap, isSaveAll: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_image, null)
        val filenameEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.filenameEditText)
        val saveAllSwitch = dialogView.findViewById<MaterialSwitch>(R.id.saveAllSwitch)
        val skipDialogSwitch = dialogView.findViewById<MaterialSwitch>(R.id.skipDialogSwitch)
        val defaultFileName = images.getOrNull(currentPage)?.originalFilename ?: "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        filenameEditText.setText(defaultFileName)
        if (images.size > 1 && images.any { it.outputBitmap != null }) {
            saveAllSwitch.visibility = View.VISIBLE
            saveAllSwitch.isChecked = isSaveAll
        } else saveAllSwitch.visibility = View.GONE
        saveAllSwitch.setOnCheckedChangeListener { _, _ -> vibrationManager.vibrateSwitcher() }
        skipDialogSwitch.setOnCheckedChangeListener { _, _ -> vibrationManager.vibrateSwitcher() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.save_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save_dialog_save_button) { _, _ ->
                val filename = filenameEditText.text.toString().trim()
                val saveAll = saveAllSwitch.isChecked
                val skipNextTime = skipDialogSwitch.isChecked
                if (skipNextTime != skipSaveDialog) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(SKIP_SAVE_DIALOG_KEY, skipNextTime).apply()
                    skipSaveDialog = skipNextTime
                }
                if (saveAll) saveAllProcessedImages(filename) else saveImageWithDefaultName(bitmap, filename)
            }
            .setNegativeButton(R.string.save_dialog_cancel_button, null)
            .create().show()
    }

    private fun stripExtension(filename: String?) = filename?.substringBeforeLast('.', filename)

    private fun saveImageWithDefaultName(bitmap: Bitmap, customFilename: String?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressText)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.saving_image_title).setView(dialogView).setCancelable(false).create()
        dialog.show()
        Thread {
            try {
                val fileNameRaw = customFilename?.takeIf { it.isNotBlank() } ?: "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                val fileName = stripExtension(fileNameRaw)
                val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$fileName.png")
                runOnUiThread { progressText.text = getString(R.string.saving_status) }
                FileOutputStream(outputFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                runOnUiThread { progressText.text = getString(R.string.saving_status); progressBar.progress = 50 }
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

    private fun saveAllProcessedImages(baseFilename: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.progressText)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.saving_image_title).setView(dialogView).setCancelable(false).create()
        dialog.show()
        Thread {
            try {
                val processedImages = images.filter { it.outputBitmap != null }
                runOnUiThread { progressText.text = getString(R.string.save_dialog_saving_all) }
                val usedFilenames = mutableSetOf<String>()
                processedImages.forEachIndexed { idx, img ->
                    val preferredFilenameRaw = if (processedImages.size > 1) img.originalFilename ?: "${baseFilename}_${idx + 1}" else img.originalFilename ?: baseFilename
                    val preferredFilename = stripExtension(preferredFilenameRaw)
                    val fileName = generateUniqueFilename(preferredFilename ?: "DeJPEG_${idx + 1}", usedFilenames)
                    usedFilenames.add(fileName)
                    val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$fileName.png")
                    FileOutputStream(outputFile).use { img.outputBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    MediaScannerConnection.scanFile(this, arrayOf(outputFile.toString()), null, null)
                    runOnUiThread { progressBar.progress = ((idx + 1) * 100 / processedImages.size) }
                }
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.save_dialog_all_saved_toast), Toast.LENGTH_SHORT).show()
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
            FileOutputStream(cachePath).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", cachePath)
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
            onClick = { pos ->
                if (pos != currentPage) {
                    currentPage = pos
                    if (!applyToAllSwitch.isChecked && pos < perImageStrengthFactors.size) strengthSeekBar.setValues(perImageStrengthFactors[pos] * 100f)
                    beforeAfterView.resetView()
                    updateImageViews()
                }
            },
            onLongClick = { pos -> vibrationManager.vibrateMenuTap(); showImageRemovalDialog(pos) }
        )
        filmstripRecyclerView.adapter = filmstripAdapter
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        filmstripRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this,
            if (isLandscape) androidx.recyclerview.widget.LinearLayoutManager.VERTICAL else androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        filmstripRecyclerView.visibility = View.GONE
    }

    private inner class FilmstripAdapter(
        private val onClick: (Int) -> Unit,
        private val onLongClick: (Int) -> Unit
    ) : RecyclerView.Adapter<FilmstripAdapter.ViewHolder>() {
        private var items = mutableListOf<ProcessingImage>()
        fun submitList(newItems: List<ProcessingImage>) { items.clear(); items.addAll(newItems); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val isLandscape = parent.context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    if (isLandscape) ViewGroup.LayoutParams.MATCH_PARENT else resources.getDimensionPixelSize(R.dimen.filmstrip_item_width),
                    if (isLandscape) resources.getDimensionPixelSize(R.dimen.filmstrip_item_width) else ViewGroup.LayoutParams.MATCH_PARENT
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
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], position == currentPage)
        override fun getItemCount() = items.size
        inner class ViewHolder(private val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
            init {
                imageView.setOnClickListener { bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onClick(it) } }
                imageView.setOnLongClickListener { bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onLongClick(it) }; true }
            }
            fun bind(item: ProcessingImage, isSelected: Boolean) {
                imageView.setImageBitmap(item.outputBitmap ?: item.inputBitmap)
                imageView.alpha = if (isSelected) 1f else 0.6f
            }
        }
    }
}