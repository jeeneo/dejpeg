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
import com.je.dejpeg.ImageProcessor.ProcessingState

class MainActivity : AppCompatActivity() {
    private lateinit var selectButton: Button
    private lateinit var processButton: Button
    private lateinit var strengthSeekBar: RangeSlider
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
    private var showPreviews = true
    private var showFilmstrip = false
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
    private lateinit var processingAnimation: ProcessingAnimation
    private lateinit var vibrationManager: VibrationManager

    private lateinit var notificationHandler: NotificationHandler
    
    private lateinit var placeholderContainer: LinearLayout
    
    private lateinit var processingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        (application as dejpeg).registerActivityLifecycleCallbacks(AppLifecycleTracker)

        vibrationManager = VibrationManager(this)
        notificationHandler = NotificationHandler(this)
        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.selectButton)
        processButton = findViewById(R.id.processButton)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)
        applyToAllSwitch = findViewById(R.id.applyToAllSwitch)
        cancelButton = findViewById(R.id.cancelButton)
        applyToAllSwitch.visibility = View.GONE

        placeholderContainer = findViewById(R.id.placeholderContainer)
        processingText = findViewById(R.id.processingText)

        applyToAllSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateButton()
            if (applyToAllSwitch.isChecked) {
                strengthSeekBar.setValues(lastStrength * 100f)
            } else if (currentPage < perImageStrengthFactors.size) {
                strengthSeekBar.setValues(perImageStrengthFactors[currentPage] * 100f)
            }
        }

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor(this, modelManager)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        outputFormat = prefs.getString(OUTPUT_FORMAT_KEY, "PNG") ?: "PNG"
        lastStrength = prefs.getFloat(STRENGTH_FACTOR_KEY, 0.5f)
        strengthSeekBar.setValues(lastStrength * 100f)

        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            try {
                if (result.resultCode == RESULT_OK && result.data != null) {
                    result.data?.let { data ->
                        if (data.clipData != null) {
                            handleMultipleImages(data.clipData!!)
                        } else {
                            data.data?.let { uri ->
                                val inputStream = contentResolver.openInputStream(uri)
                                val options = BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                BitmapFactory.decodeStream(inputStream, null, options)
                                inputStream?.close()
        
                                val width = options.outWidth
                                val height = options.outHeight
        
                                if (width > 7000 || height > 7000) {
                                    showWarningDialog("the selected image is very large (${width}x${height}), this can cause issues") {
                                        onImageSelected(uri)
                                    }
                                } else {
                                    onImageSelected(uri)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                showErrorDialog(getString(R.string.error_opening_image_dialog) + " " + e.message)
            }
        }    

        modelPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val modelUri = result.data!!.data
                if (modelUri != null) copyAndLoadModel(modelUri)
            } else {
                // if (!modelManager.hasActiveModel()) {
                showModelManagementDialog()
                // }
            }
        }

        selectButton.setOnClickListener {
            vibrationManager.vibrateButton()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_image)
                .setItems(arrayOf(getString(R.string.single_image), getString(R.string.multiple_images))) { _, which ->
                    vibrationManager.vibrateDialogChoice()
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

        beforeAfterView = findViewById(R.id.beforeAfterView)
        beforeAfterView.setButtonCallback(object : BeforeAfterImageView.ButtonCallback {
            override fun onShareClicked() {
                vibrationManager.vibrateButton()
                val currentImage = images.getOrNull(currentPage)?.outputBitmap
                if (currentImage != null) {
                    shareImage(currentImage)
                }
            }
            
            override fun onSaveClicked() {
                vibrationManager.vibrateButton()
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

        val serviceIntent = Intent(this, AppBackgroundService::class.java)
        startService(serviceIntent)

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
            else -> false
            }
        }
        
        intent?.let {
            if (it.getBooleanExtra("show_service_info", false)) {
                showServiceInfoDialog()
            }
        }
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
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setMessage(R.string.no_models)
            .setNeutralButton(R.string.import_model_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setPositiveButton("FBCNN", null)
            .setNegativeButton("SCUNet", null)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                vibrationManager.vibrateDialogChoice()
                val fbcnnLink = getString(R.string.FBCNN_link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fbcnnLink))
                startActivity(intent)
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
                vibrationManager.vibrateDialogChoice()
                val scunetLink = getString(R.string.SCUNet_link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scunetLink))
                startActivity(intent)
            }
        }

        dialog.show()
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
                Toast.makeText(this, getString(R.string.model_switched, models[which]), Toast.LENGTH_SHORT).show()
                processButton.isEnabled = images.isNotEmpty()
                updateStrengthSliderVisibility()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.import_model_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setNeutralButton(R.string.delete_model_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showDeleteModelDialog(models)
            }
            .setNegativeButton(R.string.download_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showModelDownloadDialog()
            }
            .show()
    }
    
    private fun showServiceInfoDialog() {
        MaterialAlertDialogBuilder(this)
        .setTitle("DeJPEG service")
        .setMessage(getString(R.string.background_service_message) + getString(R.string.background_service_additional_message))
        .setPositiveButton(getString(R.string.ok_button), null)
        .show()
    }

    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.download_models_title)
            .setItems(arrayOf("FBCNN", "SCUNet")) { _, which ->
                vibrationManager.vibrateDialogChoice()
                val link = when (which) {
                    0 -> getString(R.string.FBCNN_link)
                    else -> getString(R.string.SCUNet_link)
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showModelManagementDialog()
            }
            .show()
    }

    private fun showDeleteModelDialog(models: List<String>) {
        val items = models.toTypedArray()
        val checkedItems = BooleanArray(models.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_models_title)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                vibrationManager.vibrateMenuTap()
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.delete_model_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        modelManager.deleteModel(models[i], object : ModelManager.ModelDeleteCallback {
                            override fun onModelDeleted(modelName: String) {
                                Toast.makeText(this@MainActivity, getString(R.string.model_deleted_toast, modelName), Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showModelManagementDialog()
            }
            .show()
    }

    private fun copyAndLoadModel(modelUri: Uri) {
        runOnUiThread { showImportProgressDialog() }
        Thread {
            try {
                val callback = object : ModelManager.ModelCallback {
                    override fun onSuccess(modelName: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            modelManager.setActiveModel(modelName)
                            Toast.makeText(applicationContext, getString(R.string.model_imported_toast_success), Toast.LENGTH_SHORT).show()
                            processButton.isEnabled = images.isNotEmpty()
                            updateStrengthSliderVisibility()
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            showErrorDialog(getString(R.string.model_imported_dialog_error))
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
                    showErrorDialog(getString(R.string.model_imported_dialog_error, e.message))
                }
            }
        }.start()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(R.string.error_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.copy_button) { _: DialogInterface, _: Int ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("error", message)
                clipboard.setPrimaryClip(clip)
            }
            .setNegativeButton(R.string.dismiss_button, null)
            .setOnDismissListener {
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
            .show()
    }
    
    private fun showWarningDialog(message: String, onContinue: () -> Unit
    ) {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(R.string.warning_dialog_title)
            .setMessage(message)
            .setPositiveButton("continue") { _, _ -> onContinue() }
            .setNegativeButton("cancel", null)
            .show()
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
            showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message)
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
            showErrorDialog(getString(R.string.error_loading_image_dialog) + " " + e.message)
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
            val oversizedUris = mutableListOf<Uri>()
            val regularUris = mutableListOf<Uri>()
    
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
    
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val inputStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()
    
                if (options.outWidth > 6000 || options.outHeight > 6000) {
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
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
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
                val message = "one or more selected images are very large (exceeds 7000px), this might affect performance."
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
            Toast.makeText(this, getString(R.string.image_saved_toast), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showErrorDialog(getString(R.string.error_saving_image_dialog) + " " + e.message)
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
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_image)))
        } catch (e: Exception) {
            showErrorDialog(getString(R.string.error_sharing_image_dialog) + " " + e.message)
        }
    }

    private fun updateButtonVisibility() {
        runOnUiThread {
            val isProcessing = ProcessingState.getInstance().isProcessing() || this.isProcessing
            processButton.visibility = if (isProcessing) View.GONE else View.VISIBLE
            cancelButton.visibility = if (isProcessing) View.VISIBLE else View.GONE
            selectButton.isEnabled = !isProcessing
            processButton.isEnabled = images.isNotEmpty() && modelManager.hasActiveModel()
            beforeAfterView.visibility = if (isProcessing) View.GONE else View.VISIBLE
            filmstripRecyclerView.visibility = if (!isProcessing && images.size > 1) View.VISIBLE else View.GONE
            processingAnimation.visibility = if (isProcessing) View.VISIBLE else View.GONE
            processingText.visibility = if (isProcessing) View.VISIBLE else View.GONE
            
            // Ensure text is shown properly
            if (isProcessing) {
                processingText.bringToFront()
                processingAnimation.bringToFront()
            }
        }
    }

    private fun cancelProcessing() {
        if (ProcessingState.getInstance().isProcessing()) {
            imageProcessor.cancelProcessing()
            ProcessingState.getInstance().reset()
            dismissProcessingNotification()
            updateButtonVisibility()
            updateImageViews()
            stopService(Intent(this, AppBackgroundService::class.java))
            Toast.makeText(this, getString(R.string.processing_cancelled_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun processWithModel() {
        isProcessing = true
        updateButtonVisibility()
        processingAnimation.visibility = View.VISIBLE
        processingText.visibility = View.VISIBLE
        beforeAfterView.visibility = View.GONE
        filmstripRecyclerView.visibility = View.GONE
        
        val serviceIntent = Intent(this, AppBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val isBatch = images.size > 1
        var completedCount = 0
        showProcessingNotification(1, images.size)

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
                showFilmstrip = true
                updateImageViews()
                return
            }

            val image = images[index]
            val strength = if (applyStrengthToAll) lastStrength * 100f else perImageStrengthFactors[index] * 100f

            imageProcessor.processImage(
                image.inputBitmap,
                strength,
                object : ImageProcessor.ProcessCallback {
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
                                updateImageViews()
                                processNext(index + 1)
                            }
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            if (isProcessing) {
                                vibrationManager.vibrateError()
                                notificationHandler.showErrorNotification(error)
                                Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                processNext(index + 1)
                            }
                        }
                    }
                    override fun onProgress(message: String) {
                        runOnUiThread {
                            processingText.text = message
                            notificationHandler.showProcessingNotification(index + 1, images.size, message)
                        }
                    }
                },
                index,
                images.size
            )
        }

        processNext(0)
    }

    private fun showProcessingNotification(currentImage: Int, totalImages: Int, chunkProgress: String? = null) {
        notificationHandler.showProcessingNotification(currentImage, totalImages, chunkProgress)
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
        notificationHandler.clearAllNotifications()
    }

    override fun onPause() {
        super.onPause()
        val state = ProcessingState.getInstance()
        if (state.isProcessing() && !isFinishing && !isChangingConfigurations) {
            notificationHandler.showProcessingNotification(
                state.getDisplayImageNumber(),
                state.getTotalImages(),
                state.getProgressString(this)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationHandler.clearAllNotifications()
        stopService(Intent(this, AppBackgroundService::class.java))
    }

    private fun updateStrengthSliderVisibility() {
        if (!::imageProcessor.isInitialized) return
        
        val shouldShowStrength = !imageProcessor.isActiveModelSCUNet()
        runOnUiThread {
            strengthSeekBar.visibility = if (shouldShowStrength) View.VISIBLE else View.GONE
        }
        updateImageViews()
    }
    
    private fun updateImageViews() {
        if (images.isEmpty()) {
            beforeAfterView.clearImages()
            filmstripRecyclerView.visibility = View.GONE
            processingAnimation.visibility = View.GONE
            processingText.visibility = View.GONE
            placeholderContainer.visibility = View.VISIBLE
            return
        }

        placeholderContainer.visibility = View.GONE
        val processingState = ProcessingState.getInstance()

        if (isProcessing || processingState.isProcessing()) {
            beforeAfterView.visibility = View.GONE
            filmstripRecyclerView.visibility = View.GONE
            processingAnimation.visibility = View.VISIBLE
            processingText.visibility = View.VISIBLE
            return
        }

        processingAnimation.visibility = View.GONE
        processingText.visibility = View.GONE

        beforeAfterView.visibility = View.VISIBLE
        val currentImage = images[currentPage]
        beforeAfterView.setBeforeImage(currentImage.inputBitmap)
        beforeAfterView.setAfterImage(currentImage.outputBitmap)

        filmstripRecyclerView.visibility = if (images.size > 1) View.VISIBLE else View.GONE
        filmstripAdapter.submitList(images.toList())
        filmstripRecyclerView.scrollToPosition(currentPage)
    }

    private fun showImageRemovalDialog(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_image_title)
            .setItems(arrayOf(
                getString(R.string.remove_this_image_button),
                getString(R.string.remove_all_images_button)
            )) { _, which ->
                vibrationManager.vibrateDialogChoice()
                when (which) {
                    0 -> removeImage(position)
                    1 -> removeAllImages()
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
            }
            .show()
    }

    private fun removeImage(position: Int) {
        if (position < 0 || position >= images.size) return
        
        images.removeAt(position)
        perImageStrengthFactors.removeAt(position)
        
        when {
            images.isEmpty() -> {
                currentPage = 0
                beforeAfterView.clearImages()
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

    private fun removeAllImages() {
        images.clear()
        perImageStrengthFactors.clear()
        currentPage = 0
        beforeAfterView.clearImages()
        applyToAllSwitch.visibility = View.GONE
        updateImageViews()
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

    private fun showConfigDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.config_dialog_title)
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
            Toast.makeText(this, getString(R.string.output_format_set_toast, outputFormat), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        .setNeutralButton(R.string.manage_models_button) { _, _ ->
            vibrationManager.vibrateDialogChoice()
            showModelManagementDialog()
        }
        .setNegativeButton(R.string.cancel_button) { _, _ ->
            vibrationManager.vibrateDialogChoice()
        }
        .show()
    }
}