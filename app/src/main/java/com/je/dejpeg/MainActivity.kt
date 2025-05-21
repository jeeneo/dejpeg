package com.je.djpeg

import android.app.Dialog
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.smarttoolfactory.beforeafter.BeforeAfterImage
import com.smarttoolfactory.beforeafter.OverlayStyle
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {
    private lateinit var selectButton: Button
    private lateinit var processButton: Button
    private lateinit var strengthSeekBar: Slider
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private lateinit var pageIndicator: TextView
    private lateinit var cancelButton: Button

    private lateinit var modelManager: ModelManager
    private lateinit var imageProcessor: ImageProcessor

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

    private lateinit var composeView: ComposeView

    private var isProcessing: Boolean = false

    private data class ProcessingImage(
        val inputBitmap: Bitmap,
        var outputBitmap: Bitmap? = null
    )

    private var images = mutableListOf<ProcessingImage>()
    private var currentPage = 0

    private val outputFormatState = mutableStateOf("PNG")
    private val lastStrengthState = mutableStateOf(0.5f)

    // Control preview visibility
    private var showPreviews by mutableStateOf(true)
    // Control filmstrip visibility
    private var showFilmstrip by mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Toast.makeText(this, getString(R.string.opencv_error), Toast.LENGTH_LONG).show()
        }
        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.selectButton)
        processButton = findViewById(R.id.processButton)
        strengthSeekBar = findViewById(R.id.strengthSeekBar)
        logRecyclerView = findViewById(R.id.logRecyclerView)
        pageIndicator = findViewById(R.id.pageIndicator)
        cancelButton = findViewById(R.id.cancelButton)

        logAdapter = LogAdapter()
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        logRecyclerView.adapter = logAdapter

        logAdapter.setOnLogAddedListener {
            logRecyclerView.post {
                logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
            }
        }

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor(this, modelManager)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        outputFormatState.value = prefs.getString(OUTPUT_FORMAT_KEY, "PNG") ?: "PNG"
        lastStrengthState.value = prefs.getFloat(STRENGTH_FACTOR_KEY, 0.5f)
        strengthSeekBar.value = lastStrengthState.value * 100f

        // Image picker
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

        // Model picker
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
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_image)
                .setItems(arrayOf(getString(R.string.single_image), getString(R.string.multiple_images))) { _, which ->
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
            if (images.isNotEmpty()) processWithModel()
        }

        cancelButton.setOnClickListener {
            cancelProcessing()
        }

        processButton.isEnabled = false

        strengthSeekBar.addOnChangeListener { _, value, _ ->
            val snapped = (value / 5).toInt() * 5f
            strengthSeekBar.value = snapped
            lastStrengthState.value = snapped / 100f
            if (images.size > 1 && !applyStrengthToAll && currentPage < perImageStrengthFactors.size) {
                perImageStrengthFactors[currentPage] = lastStrengthState.value
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(STRENGTH_FACTOR_KEY, lastStrengthState.value)
                .apply()
        }

        intent?.let {
            if (Intent.ACTION_SEND == it.action && it.type?.startsWith("image/") == true) {
                val imageUri = it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (imageUri != null) onImageSelected(imageUri)
            }
        }

        if (!modelManager.hasActiveModel()) promptModelSelection()

        composeView = findViewById(R.id.composeView)
        updateComposeView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setupNotificationChannel()
        }
    }

    private fun promptModelSelection() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setMessage(R.string.no_models)
            .setPositiveButton(R.string.import_model) { _, _ ->
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
                modelManager.unloadModel()
                imageProcessor.unloadModel()
                modelManager.setActiveModel(models[which])
                logAdapter.addLog(getString(R.string.model_switched, models[which]))
                Toast.makeText(this, getString(R.string.model_switched, models[which]), Toast.LENGTH_SHORT).show()
                processButton.isEnabled = images.isNotEmpty()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.import_button) { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setNeutralButton(R.string.delete_button) { _, _ -> showDeleteModelDialog(models) }
            .show()
    }

    private fun showDeleteModelDialog(models: List<String>) {
        val items = models.toTypedArray()
        val checkedItems = BooleanArray(models.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_models)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        modelManager.deleteModel(models[i])
                        logAdapter.addLog(getString(R.string.model_deleted, models[i]))
                    }
                }
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun copyAndLoadModel(modelUri: Uri) {
        runOnUiThread { showImportProgressDialog() }
        logAdapter.addLog(getString(R.string.importing_model_message))

        Thread {
            try {
                val callback = object : ModelManager.ModelCallback {
                    override fun onSuccess(modelName: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            modelManager.setActiveModel(modelName)
                            Toast.makeText(applicationContext, getString(R.string.model_imported_toast), Toast.LENGTH_SHORT).show()
                            logAdapter.addLog(getString(R.string.model_imported, modelName))
                            logAdapter.addLog(getString(R.string.model_switched, modelName))
                            processButton.isEnabled = images.isNotEmpty()
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            dismissImportProgressDialog()
                            Toast.makeText(applicationContext, getString(R.string.error_importing_model), Toast.LENGTH_LONG).show()
                            logAdapter.addLog(getString(R.string.error_importing_model, error))
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
                    logAdapter.addLog(getString(R.string.invalid_model))
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
            logAdapter.addLog(getString(R.string.error_opening_image, e.message))
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
            logAdapter.addLog(getString(R.string.error_loading_image, e.message))
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
                    perImageStrengthFactors.add(lastStrengthState.value)
                    loadedCount++
                } catch (e: Exception) {
                    logAdapter.addLog(getString(R.string.error_loading_image_n, i + 1, e.message))
                }
            }

            if (loadedCount > 0) {
                currentPage = 0
                updatePageIndicator()
                processButton.isEnabled = modelManager.hasActiveModel()
                Toast.makeText(this, getString(R.string.images_loaded, loadedCount), Toast.LENGTH_SHORT).show()
                showPreviews = true
                showFilmstrip = false
                updateComposeView()
            } else {
                logAdapter.addLog(getString(R.string.no_images_loaded))
                Toast.makeText(this, getString(R.string.no_images_loaded), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            logAdapter.addLog(getString(R.string.error_loading_images, e.message))
            Toast.makeText(this, getString(R.string.error_loading_images, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageViews(bitmap: Bitmap) {
        images.clear()
        images.add(ProcessingImage(bitmap))
        perImageStrengthFactors.clear()
        perImageStrengthFactors.add(lastStrengthState.value)
        currentPage = 0
        updatePageIndicator()
        processButton.isEnabled = modelManager.hasActiveModel()
        Toast.makeText(this, getString(R.string.image_loaded), Toast.LENGTH_SHORT).show()
        showPreviews = true
        showFilmstrip = false
        updateComposeView()
    }

    private fun handleImageAction(action: String) {
        val currentImage = images.getOrNull(currentPage)?.outputBitmap ?: return
        when (action) {
            "save" -> saveImageToGallery(currentImage)
            "share" -> shareImage(currentImage)
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        try {
            val fileName = "DeJPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$fileName.${outputFormatState.value.lowercase()}")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(
                    if (outputFormatState.value == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.PNG,
                    100, out
                )
            }
            MediaScannerConnection.scanFile(this, arrayOf(outputFile.toString()), null, null)
            Toast.makeText(this, getString(R.string.image_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_saving_image, e.message), Toast.LENGTH_SHORT).show()
            logAdapter.addLog(getString(R.string.error_saving_image, e.message))
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
            startActivity(Intent.createChooser(shareIntent, "Share image"))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_sharing_image, e.message), Toast.LENGTH_SHORT).show()
            logAdapter.addLog(getString(R.string.error_sharing_image, e.message))
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun ImageContent() {
        if (images.isEmpty()) {
            Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
            ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.placeholder),
                contentDescription = null,
                modifier = Modifier.size(200.dp),
                tint = Color.Unspecified
            )
            }
            return
        }

        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                            isIndeterminate = true
                            val density = context.resources.displayMetrics.density
                            val widthPx = (200 * density).toInt()
                            layoutParams = FrameLayout.LayoutParams(
                                widthPx,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.CENTER
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.Center)
                )
            }
            return
        }

        val pagerState = rememberPagerState(pageCount = { images.size })
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(pagerState.currentPage) {
            if (currentPage != pagerState.currentPage) {
                currentPage = pagerState.currentPage
                if (images.size > 1 && !applyStrengthToAll && currentPage < perImageStrengthFactors.size) {
                    lastStrengthState.value = perImageStrengthFactors[currentPage]
                    strengthSeekBar.value = (lastStrengthState.value * 100f).let { ((it / 5).toInt() * 5).toFloat() }
                }
                updatePageIndicator()
            }
        }
        LaunchedEffect(currentPage) {
            if (pagerState.currentPage != currentPage) {
                pagerState.scrollToPage(currentPage)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (images.size > 1) {
                    Text(
                        text = getString(R.string.page_indicator, currentPage + 1, images.size),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val image = images[page]

                    if (image.outputBitmap != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BeforeAfterImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                beforeImage = image.inputBitmap.asImageBitmap(),
                                afterImage = image.outputBitmap!!.asImageBitmap(),
                                enableProgressWithTouch = true,
                                enableZoom = true,
                                contentScale = ContentScale.Crop,
                                overlayStyle = OverlayStyle(
                                    dividerWidth = 2.dp,
                                    thumbShape = RoundedCornerShape(50),
                                    thumbBackgroundColor = Color(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.selected_item_dark)),
                                    thumbTintColor = Color.White,
                                    dividerColor = Color(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.selected_item_dark)),
                                    thumbSize = 36.dp,
                                    thumbPositionPercent = 50f,
                                ),
                            )
                        }
                    } else {
                        Image(
                            bitmap = image.inputBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (!isProcessing && images[currentPage].outputBitmap != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp)
                    ) {
                        IconButton(
                            onClick = { handleImageAction("save") },
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = getString(R.string.save),

                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { handleImageAction("share") },
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = getString(R.string.share),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(images.size) { index ->
                    val image = images[index]
                    val isSelected = index == pagerState.currentPage
                    var showMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .height(64.dp)
                            .width(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected)
                                    Color(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.selected_item_dark))
                                else
                                    Color.Transparent
                            )
                            .padding(if (isSelected) 2.dp else 0.dp)
                            .combinedClickable(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                onLongClick = {
                                    showMenu = true
                                }
                            )
                    ) {
                        val bitmap = image.outputBitmap ?: image.inputBitmap
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f))
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(getString(R.string.remove_image), color = Color.White) },

                                onClick = {
                                    showMenu = false
                                    removeImageAt(index)
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(currentPage)
                                    }
                                }
                            )
                            if (images.size > 1) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(getString(R.string.remove_all_images), color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        removeAllImages()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getString(R.string.strength, (lastStrengthState.value * 100).toInt()),
                    modifier = Modifier.padding(end = 8.dp),
                    color = Color.White
                )

                Spacer(modifier = Modifier.weight(1f))

                if (images.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = getString(R.string.apply_to_all),
                            color = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        androidx.compose.material3.Switch(
                            checked = applyStrengthToAll,
                            onCheckedChange = { checked ->
                                applyStrengthToAll = checked
                                if (checked) {
                                    val currentStrength = lastStrengthState.value
                                    perImageStrengthFactors.replaceAll { currentStrength }
                                } else {
                                    if (currentPage < perImageStrengthFactors.size) {
                                        val currentImageStrength = perImageStrengthFactors[currentPage]
                                        lastStrengthState.value = currentImageStrength
                                        strengthSeekBar.value = (currentImageStrength * 100f)
                                            .let { ((it / 5).toInt() * 5).toFloat() }
                                    }
                                }
                                updateComposeView()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color.Gray,
                                checkedTrackColor = Color(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.selected_item_dark)).copy(alpha = 0.6f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.selected_item_dark))
                            )
                        )
                    }
                }
            }
        }
    }

    private fun removeImageAt(index: Int) {
        if (index in images.indices) {
            images.removeAt(index)
            perImageStrengthFactors.removeAt(index)
            if (currentPage >= images.size) currentPage = images.size - 1
            if (images.isEmpty()) {
                processButton.isEnabled = false
            }
            updatePageIndicator()
            updateComposeView()
        }
    }

    private fun removeAllImages() {
        images.clear()
        perImageStrengthFactors.clear()
        currentPage = 0
        processButton.isEnabled = false
        updatePageIndicator()
        updateComposeView()
    }

    private fun updateComposeView() {
        composeView.setContent {
            MaterialTheme {
                ImageContent()
            }
        }
    }

    private fun updatePageIndicator() {
        if (images.size > 1 && !isProcessing) {
            pageIndicator.visibility = View.GONE
        } else {
            pageIndicator.visibility = View.GONE
        }
    }

    private fun updateButtonVisibility() {
        runOnUiThread {
            processButton.visibility = if (isProcessing) View.GONE else View.VISIBLE
            cancelButton.visibility = if (isProcessing) View.VISIBLE else View.GONE
            selectButton.isEnabled = !isProcessing
            processButton.isEnabled = images.isNotEmpty() && modelManager.hasActiveModel()
        }
    }

    private fun cancelProcessing() {
        if (isProcessing) {
            imageProcessor.cancelProcessing()
            isProcessing = false
            dismissProcessingNotification()
            updateButtonVisibility()
            updatePageIndicator()
            updateComposeView()
            Toast.makeText(this, getString(R.string.processing_cancelled), Toast.LENGTH_SHORT).show()
            logAdapter.addLog(getString(R.string.processing_cancelled))
        }
    }

    private fun processWithModel() {
        isProcessing = true
        updateButtonVisibility()
        updatePageIndicator()
        updateComposeView()

        val isBatch = images.size > 1
        var completedCount = 0

        fun processNext(index: Int) {
            if (!isProcessing || index >= images.size) {
                isProcessing = false
                if (completedCount > 0) {
                    showCompletionNotification(completedCount)
                }
                dismissProcessingNotification()
                updateButtonVisibility()
                updatePageIndicator()
                showFilmstrip = true
                updateComposeView()
                return
            }

            val image = images[index]
            val strength = if (applyStrengthToAll) lastStrengthState.value * 100f else perImageStrengthFactors[index] * 100f

            imageProcessor.processImage(
                image.inputBitmap,
                strength,
                object : ImageProcessor.ProcessCallback {
                    override fun onProgress(message: String) {
                        runOnUiThread {
                            if (isProcessing) {
                                logAdapter.addLog(message)
                                showProcessingNotification(index + 1, images.size)
                            }
                        }
                    }
                    override fun onComplete(result: Bitmap) {
                        runOnUiThread {
                            if (isProcessing) {
                                image.outputBitmap = result
                                completedCount++
                                updateComposeView()
                                processNext(index + 1)
                            }
                        }
                    }
                    override fun onError(error: String) {
                        runOnUiThread {
                            if (isProcessing) {
                                logAdapter.addLog(
                                    if (isBatch) getString(R.string.error_loading_image_n, index + 1, error)
                                    else getString(R.string.error_loading_image, error)
                                )
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

        NotificationManagerCompat.from(this).notify(2, notification)
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "processing_channel",
                getString(R.string.processing_updates),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.processing_updates_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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
            .build()

        NotificationManagerCompat.from(this).notify(1, notification)
    }

    private fun dismissProcessingNotification() {
        NotificationManagerCompat.from(this).cancel(1)
    }

    private fun showConfigDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.config)
            .setSingleChoiceItems(
                arrayOf(getString(R.string.png), getString(R.string.bmp)),
                if (outputFormatState.value == "BMP") FORMAT_BMP else FORMAT_PNG
            ) { dialog: DialogInterface, which: Int ->
                outputFormatState.value = if (which == FORMAT_BMP) "BMP" else "PNG"
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(OUTPUT_FORMAT_KEY, outputFormatState.value)
                    .apply()
                Toast.makeText(this, getString(R.string.output_format_set, outputFormatState.value), Toast.LENGTH_SHORT).show()
                logAdapter.addLog(getString(R.string.output_format_set, outputFormatState.value))
                dialog.dismiss()
            }
            .setNeutralButton(R.string.manage_models) { _, _ -> showModelManagementDialog() }
            .setNegativeButton(R.string.cancel_button, null)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_config -> {
                showConfigDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}