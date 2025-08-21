package com.je.dejpeg.utils

import android.app.Dialog
import android.content.*
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.je.dejpeg.*
import com.je.dejpeg.ModelManager
import com.je.dejpeg.models.ProcessingState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment
import android.media.MediaScannerConnection
import android.graphics.Bitmap
import io.noties.markwon.Markwon

class DialogManager(
    private val activity: MainActivity,
    private val vibrationManager: VibrationManager,
    private val modelManager: ModelManager,
    private val imageProcessor: ImageProcessor,
    private val notificationHandler: NotificationHandler
) {
    private var importProgressDialog: Dialog? = null
    private var importProgressBar: ProgressBar? = null
    private var importProgressText: TextView? = null

    fun promptModelSelection(modelPickerLauncher: ActivityResultLauncher<Intent>) {
        if (modelManager.hasActiveModel()) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_model_selection, null)
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.manage_models)
            .setMessage(R.string.no_models)
            .setView(dialogView)
            .setNeutralButton(R.string.import_model_button) { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream"))
                }
                modelPickerLauncher.launch(intent)
            }
            .setCancelable(false)
        .create()
  
        dialog.setCanceledOnTouchOutside(false)

        dialogView.findViewById<Button>(R.id.btn_fbcnn)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            val fbcnnLink = activity.getString(R.string.FBCNN_link)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fbcnnLink))
            activity.startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.btn_scunet)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            val scunetLink = activity.getString(R.string.SCUNet_link)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scunetLink))
            activity.startActivity(intent)
        }

        dialogView.findViewById<Button>(R.id.btn_experimental)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            val experimentalLink = activity.getString(R.string.experimental_link)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(experimentalLink))
            activity.startActivity(intent)
        }

        dialog.show()
    }

    fun showModelManagementDialog(
        modelPickerLauncher: ActivityResultLauncher<Intent>,
        showDeleteModelDialog: (List<String>) -> Unit,
        showModelDownloadDialog: () -> Unit
    ) {
        val models = modelManager.getInstalledModels()
        val activeModel = modelManager.getActiveModelName()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_model_management, null)
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.manage_models)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set up radio buttons for model selection
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.model_radio_group)
        models.forEachIndexed { index, modelName ->
            val radioButton = RadioButton(activity).apply {
                id = index
                text = modelName
                isChecked = modelName == activeModel
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        vibrationManager.vibrateMenuTap()
                        modelManager.unloadModel()
                        modelManager.setActiveModel(modelName)
                        Toast.makeText(activity, activity.getString(R.string.model_switched, modelName), Toast.LENGTH_SHORT).show()
                        activity.processButton.isEnabled = activity.images.isNotEmpty()
                        activity.updateStrengthSliderVisibility()
                    }
                }
            }
            radioGroup.addView(radioButton)
        }

        // Set up action buttons
        dialogView.findViewById<Button>(R.id.btn_import_model)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            modelPickerLauncher.launch(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_delete_model)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showDeleteModelDialog(models)
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_download_model)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showModelDownloadDialog()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vibrationManager.vibrateDialogChoice()
        }

        dialog.show()
    }

    fun showServiceInfoDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.background_service_title))
            .setMessage(activity.getString(R.string.background_service_message) + activity.getString(R.string.background_service_additional_message))
            .setPositiveButton(activity.getString(R.string.ok_button), null)
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
        .show()
    }

    fun showModelDownloadDialog(showModelManagementDialog: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.download_models_title)
            .setItems(arrayOf("FBCNN", "SCUNet", "experimental models")) { _, which ->
                vibrationManager.vibrateDialogChoice()
                val link = when (which) {
                    0 -> activity.getString(R.string.FBCNN_link)
                    1 -> activity.getString(R.string.SCUNet_link)
                    else -> activity.getString(R.string.experimental_link)
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                activity.startActivity(intent)
            }
            .setNegativeButton(R.string.cancel_button) { _, _ ->
                showModelManagementDialog()
            }
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
        .show()
    }

    fun showDeleteModelDialog(
        models: List<String>,
        showModelManagementDialog: () -> Unit,
        promptModelSelection: () -> Unit
    ) {
        val items = models.toTypedArray()
        val checkedItems = BooleanArray(models.size)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_models_title)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                vibrationManager.vibrateMenuTap()
                checkedItems[which] = isChecked
            }
            .setPositiveButton(activity.getString(R.string.delete_model_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        modelManager.deleteModel(models[i], object : ModelManager.ModelDeleteCallback {
                            override fun onModelDeleted(modelName: String) {
                                Toast.makeText(activity, activity.getString(R.string.model_deleted_toast, modelName), Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
            .setNegativeButton(activity.getString(R.string.cancel_button)) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                showModelManagementDialog()
            }
        .show()
    }

    fun copyAndLoadModel(
        modelUri: Uri,
        promptModelSelection: (() -> Unit)? = null
    ) {
        activity.runOnUiThread { showImportProgressDialog() }
        Thread {
            try {
                val callback = object : ModelManager.ModelCallback {
                    override fun onSuccess(modelName: String) {
                        activity.runOnUiThread {
                            dismissImportProgressDialog()
                            modelManager.setActiveModel(modelName)
                            Toast.makeText(activity.applicationContext, activity.getString(R.string.model_imported_toast_success), Toast.LENGTH_SHORT).show()
                            activity.processButton.isEnabled = activity.images.isNotEmpty()
                            activity.updateStrengthSliderVisibility()
                        }
                    }
                    override fun onError(error: String) {
                        activity.runOnUiThread {
                            dismissImportProgressDialog()
                            if (error.contains(":") && error.split(":").size >= 5) {
                                val parts = error.split(":", limit = 5)
                                val modelName = parts[0]
                                val title = parts[1]
                                val message = parts[2]
                                val positive = parts[3]
                                val negative = parts[4]
                                showModelWarningDialog(
                                    modelUri,
                                    modelName,
                                    title,
                                    message,
                                    positive,
                                    negative,
                                    promptModelSelection
                                )
                            } else if (error == "GENERIC_MODEL_WARNING") {
                                val modelName = try {
                                    val cursor = activity.contentResolver.query(modelUri, null, null, null, null)
                                    cursor?.use {
                                        if (it.moveToFirst()) {
                                            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                            if (idx >= 0) it.getString(idx) else null
                                        } else null
                                    } ?: modelUri.lastPathSegment ?: "unknown"
                                } catch (e: Exception) {
                                    modelUri.lastPathSegment ?: "unknown"
                                }
                                showModelWarningDialog(
                                    modelUri,
                                    modelName,
                                    activity.getString(R.string.generic_model_warning_title),
                                    activity.getString(R.string.generic_model_warning_message),
                                    activity.getString(R.string.generic_model_warning_positive),
                                    activity.getString(R.string.generic_model_warning_negative),
                                    promptModelSelection
                                )
                            } else {
                                showErrorDialog(error) {
                                    if (!modelManager.hasActiveModel()) promptModelSelection?.invoke()
                                }
                            }
                        }
                    }
                    override fun onProgress(progress: Int) {
                        activity.runOnUiThread { updateImportProgressDialog(progress) }
                    }
                }
                activity.runOnUiThread { showImportProgressDialog() }
                modelManager.importModel(modelUri, callback, false)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dismissImportProgressDialog()
                    showErrorDialog(e.message ?: "") {
                        if (!modelManager.hasActiveModel()) promptModelSelection?.invoke()
                    }
                }
            }
        }.start()
    }

    fun showModelWarningDialog(
        modelUri: Uri,
        modelName: String,
        title: String,
        message: String,
        positiveButton: String,
        negativeButton: String,
        promptModelSelection: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(activity)
            .setCancelable(false)
            .setTitle(title)
            .setMessage(
                "model: $modelName\n\n$message\n\nif you experience issues, please report them on GitHub with details about your device, OS, model, and image size."
            )
            .setPositiveButton(positiveButton) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                copyAndLoadModelForce(modelUri, promptModelSelection)
            }
            .setNegativeButton(negativeButton) { _, _ ->
                vibrationManager.vibrateDialogChoice()
                if (!modelManager.hasActiveModel()) {
                    promptModelSelection?.invoke()
                }
            }
            .show()
    }

    private fun copyAndLoadModelForce(
        modelUri: Uri,
        promptModelSelection: (() -> Unit)? = null
    ) {
        activity.runOnUiThread { showImportProgressDialog() }
        Thread {
            try {
                val callback = object : ModelManager.ModelCallback {
                    override fun onSuccess(modelName: String) {
                        activity.runOnUiThread {
                            dismissImportProgressDialog()
                            modelManager.setActiveModel(modelName)
                            Toast.makeText(activity.applicationContext, activity.getString(R.string.model_imported_toast_success), Toast.LENGTH_SHORT).show()
                            activity.processButton.isEnabled = activity.images.isNotEmpty()
                            activity.updateStrengthSliderVisibility()
                        }
                    }
                    override fun onError(error: String) {
                        activity.runOnUiThread {
                            dismissImportProgressDialog()
                            showErrorDialog(error) {
                                if (!modelManager.hasActiveModel()) promptModelSelection?.invoke()
                            }
                        }
                    }
                    override fun onProgress(progress: Int) {
                        activity.runOnUiThread { updateImportProgressDialog(progress) }
                    }
                }
                activity.runOnUiThread { showImportProgressDialog() }
                modelManager.importModel(modelUri, callback, true)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dismissImportProgressDialog()
                    showErrorDialog(e.message ?: "") {
                        if (!modelManager.hasActiveModel()) promptModelSelection?.invoke()
                    }
                }
            }
        }.start()
    }

    fun showErrorDialog(message: String, promptModelSelection: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.error_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.copy_button) { _: DialogInterface, _: Int ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("error", message)
                clipboard.setPrimaryClip(clip)
            }
            .setNegativeButton(R.string.dismiss_button, null)
            .setOnDismissListener {
                if (!modelManager.hasActiveModel()) promptModelSelection()
            }
        .show()
    }

    fun showWarningDialog(message: String, onContinue: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.warning_dialog_title)
            .setMessage(message)
            .setPositiveButton("continue") { _, _ -> onContinue() }
            .setNegativeButton("cancel", null)
        .show()
    }

    fun showImportProgressDialog() {
        if (importProgressDialog?.isShowing == true) return
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null)
        importProgressBar = dialogView.findViewById(R.id.progressBar)
        importProgressText = dialogView.findViewById(R.id.progressText)
        importProgressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.importing_model)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        importProgressDialog?.show()
    }

    fun updateImportProgressDialog(progress: Int) {
        importProgressDialog?.let {
            if (it.isShowing) {
                importProgressBar?.progress = progress
                importProgressText?.text = "$progress%"
            }
        }
    }

    fun dismissImportProgressDialog() {
        importProgressDialog?.dismiss()
        importProgressDialog = null
    }

    fun showImageRemovalDialog(
        position: Int,
        removeImage: (Int) -> Unit,
        removeAllImages: () -> Unit,
        removeAllExceptCurrent: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.remove_image_title)
            .setItems(arrayOf(
                activity.getString(R.string.remove_this_image_button),
                activity.getString(R.string.remove_all_images_button),
                activity.getString(R.string.remove_all_but_this_button)
            )) { _, which ->
                vibrationManager.vibrateDialogChoice()
                when (which) {
                    0 -> activity.removeImage(position)
                    1 -> activity.removeAllImages()
                    2 -> activity.removeAllExceptCurrent(position)
                }
            }
            .setNegativeButton(R.string.cancel_button) { _, _ ->
                vibrationManager.vibrateDialogChoice()
            }
        .show()
    }

    fun showConfigDialog(showModelManagementDialog: () -> Unit, clearSkipSaveDialogOption: () -> Unit) {
        val prefs = activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_config, null)
        
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.config_dialog_title)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btn_clear_picker_action)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .remove(activity.DEFAULT_ACTION_KEY)
                .apply()
            activity.defaultImageAction = -1
            Toast.makeText(activity, R.string.default_action_cleared_toast, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_manage_models)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showModelManagementDialog()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_clear_skip_dialog)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            clearSkipSaveDialogOption()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vibrationManager.vibrateDialogChoice()
        }

        dialog.show()
    }

    fun showImagePickerDialog(
        launchGalleryPicker: () -> Unit,
        launchInternalPhotoPicker: () -> Unit,
        launchDocumentsPicker: () -> Unit,
        launchCamera: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_image_picker, null)
        val setDefaultSwitch = dialogView.findViewById<MaterialSwitch>(R.id.setDefaultSwitch)

        setDefaultSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateSwitcher()
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pick_image_method)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set up picker buttons
        dialogView.findViewById<Button>(R.id.btn_gallery_picker)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            if (setDefaultSwitch.isChecked) {
                activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt(activity.DEFAULT_ACTION_KEY, 0)
                    .apply()
                activity.defaultImageAction = 0
            }
            launchGalleryPicker()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_internal_picker)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            if (setDefaultSwitch.isChecked) {
                activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt(activity.DEFAULT_ACTION_KEY, 1)
                    .apply()
                activity.defaultImageAction = 1
            }
            launchInternalPhotoPicker()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_documents_picker)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            if (setDefaultSwitch.isChecked) {
                activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt(activity.DEFAULT_ACTION_KEY, 2)
                    .apply()
                activity.defaultImageAction = 2
            }
            launchDocumentsPicker()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_camera)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            if (setDefaultSwitch.isChecked) {
                activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putInt(activity.DEFAULT_ACTION_KEY, 3)
                    .apply()
                activity.defaultImageAction = 3
            }
            launchCamera()
            dialog.dismiss()
        }

        // Set up help buttons
        dialogView.findViewById<ImageButton>(R.id.btn_gallery_help)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showHelpDialog(R.string.gallery_picker, R.string.gallery_picker_help_text)
        }

        dialogView.findViewById<ImageButton>(R.id.btn_internal_help)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showHelpDialog(R.string.internal_picker, R.string.internal_picker_help_text)
        }

        dialogView.findViewById<ImageButton>(R.id.btn_documents_help)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showHelpDialog(R.string.documents_picker, R.string.documents_picker_help_text)
        }

        dialogView.findViewById<ImageButton>(R.id.btn_camera_help)?.setOnClickListener {
            vibrationManager.vibrateDialogChoice()
            showHelpDialog(R.string.camera, R.string.camera_help_text)
        }

        dialog.setOnDismissListener {
            vibrationManager.vibrateDialogChoice()
        }

        dialog.show()
    }

    fun showAboutDialog(showFAQDialog: () -> Unit) {
        val markdown = """De*JPEG* is an open source app for removing compression and noise artifacts from images. Source on [GitHub](https://github.com/jeeneo/dejpeg) under the GPLv3 license.""".trimIndent()
        val textView = TextView(activity).apply {
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setPadding(64, 64, 64, 64)
        }
        val markwon = Markwon.create(activity)
        markwon.setMarkdown(textView, markdown)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.about_title)
            .setView(textView)
            .setPositiveButton(R.string.ok_button, null)
            .setNeutralButton(R.string.FAQ_button) { _, _ ->
                showFAQDialog()
            }
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
        .show()
    }

    fun showFAQDialog() {
        val markwon = Markwon.create(activity)
        val faqFiles = activity.assets.list("faq")?.sorted().orEmpty()

        if (faqFiles.isEmpty()) {
            return
        }

        val faqData = faqFiles.map { filename ->
            try {
                val content = activity.assets.open("faq/$filename").bufferedReader().use { it.readText() }
                val title = content.lines().firstOrNull { it.startsWith("##") }?.removePrefix("##")?.trim() ?: filename
                val strippedContent = content.lines().dropWhile { it.startsWith("##") }.joinToString("\n").trim()
                title to strippedContent
            } catch (e: Exception) {
                filename to "**Error loading this FAQ**\n\n${e.message}"
            }
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        faqData.forEach { (title, content) ->
            val contentView = TextView(activity).apply {
                visibility = View.GONE
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(32, 0, 0, 0)
                markwon.setMarkdown(this, content)
            }

            val headerView = TextView(activity).apply {
                text = "▶ $title"
                textSize = 18f
                setPadding(0, 16, 0, 8)
                setOnClickListener {
                    val isVisible = contentView.visibility == View.VISIBLE
                    contentView.visibility = if (isVisible) View.GONE else View.VISIBLE
                    text = if (isVisible) "▶ $title" else "▼ $title"
                    vibrationManager.vibrateSwitcher()
                }
            }

            container.addView(headerView)
            container.addView(contentView)
        }

        val scrollView = ScrollView(activity).apply {
            addView(container)
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.faq_dialog_title)
            .setView(scrollView)
            .setPositiveButton(R.string.ok_button, null)
            .setOnDismissListener { vibrationManager.vibrateDialogChoice() }
        .show()
    }

    private fun showHelpDialog(titleResId: Int, messageResId: Int) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(titleResId))
            .setMessage(activity.getString(messageResId))
            .setPositiveButton(R.string.ok_button, null)
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
        .show()
    }
}