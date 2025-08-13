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
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_model)
            .setMessage(R.string.no_models)
            .setNeutralButton(R.string.import_model_button) { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setPositiveButton("FBCNN", null)
            .setNegativeButton("SCUNet", null)
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                vibrationManager.vibrateDialogChoice()
                val fbcnnLink = activity.getString(R.string.FBCNN_link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fbcnnLink))
                activity.startActivity(intent)
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
                vibrationManager.vibrateDialogChoice()
                val scunetLink = activity.getString(R.string.SCUNet_link)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scunetLink))
                activity.startActivity(intent)
            }
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
        val items = models.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.select_model)
            .setSingleChoiceItems(items, models.indexOf(activeModel))
            { dialog: DialogInterface, which: Int ->
                vibrationManager.vibrateMenuTap()
                modelManager.unloadModel()
                modelManager.setActiveModel(models[which])
                Toast.makeText(activity, activity.getString(R.string.model_switched, models[which]), Toast.LENGTH_SHORT).show()
                activity.processButton.isEnabled = activity.images.isNotEmpty()
                activity.updateStrengthSliderVisibility()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.import_model_button) { _, _ ->
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                modelPickerLauncher.launch(intent)
            }
            .setNeutralButton(R.string.delete_model_button) { _, _ ->
                showDeleteModelDialog(models)
            }
            .setNegativeButton(R.string.download_button) { _, _ ->
                showModelDownloadDialog()
            }
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
            .show()
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
            .setItems(arrayOf("FBCNN", "SCUNet")) { _, which ->
                vibrationManager.vibrateDialogChoice()
                val link = when (which) {
                    0 -> activity.getString(R.string.FBCNN_link)
                    else -> activity.getString(R.string.SCUNet_link)
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
        force: Boolean = false,
        showHashMismatchDialog: (Uri, String, String, String) -> Unit
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
                            if (error.startsWith("HASH_MISMATCH:")) {
                                val parts = error.split(":")
                                val modelName = parts.getOrNull(1) ?: "unknown"
                                val expected = parts.getOrNull(2) ?: "?"
                                val actual = parts.getOrNull(3) ?: "?"
                                showHashMismatchDialog(modelUri, modelName, expected, actual)
                            } else {
                                showErrorDialog(activity.getString(R.string.model_imported_dialog_error, error), {})
                            }
                        }
                    }
                    override fun onProgress(progress: Int) {
                        activity.runOnUiThread { updateImportProgressDialog(progress) }
                    }
                }
                modelManager.importModel(modelUri, callback, force)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dismissImportProgressDialog()
                    val msg = e.message ?: ""
                    if (msg.startsWith("HASH_MISMATCH:")) {
                        val parts = msg.split(":")
                        val modelName = parts.getOrNull(1) ?: "unknown"
                        val expected = parts.getOrNull(2) ?: "?"
                        val actual = parts.getOrNull(3) ?: "?"
                        showHashMismatchDialog(modelUri, modelName, expected, actual)
                    } else {
                        showErrorDialog(activity.getString(R.string.model_imported_dialog_error, e.message), {})
                    }
                }
            }
        }.start()
    }

    fun showHashMismatchDialog(
        modelUri: Uri,
        modelName: String,
        expected: String,
        actual: String,
        copyAndLoadModel: (Uri, Boolean) -> Unit
    ) {
        activity.runOnUiThread {
            MaterialAlertDialogBuilder(activity)
                .setTitle("import warning")
                .setMessage(
                    "the picked model isn't officially supported\n\n" +
                    "model: $modelName\n" +
                    "this is in an experimental stage, and these models might perform slower, produce unexpected results, or straight up crash/error out, proceed with caution.\n\n" +
                    "if this model was on the Experimental Models page and has problems when processing, file an issue on GitHub and mention the image size, model type, device and OS version.\n\n"
                )
                .setPositiveButton("I understand") { _, _ ->
                    vibrationManager.vibrateDialogChoice()
                    copyAndLoadModel(modelUri, true)
                }
                .setNegativeButton("cancel", null)
                .show()
        }
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

    fun showConfigDialog(showModelManagementDialog: () -> Unit) {
        val prefs = activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_config, null)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.config_dialog_title)
            .setPositiveButton(R.string.clear_default_action) { _, _ ->
                activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .remove(activity.DEFAULT_ACTION_KEY)
                    .apply()
                activity.defaultImageAction = -1
                Toast.makeText(activity, R.string.default_action_cleared_toast, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.manage_models_button) { _, _ ->
                showModelManagementDialog()
            }
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
            .setView(dialogView)
            .show()
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

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pick_image_method)
            .setView(dialogView)
            .setItems(arrayOf(
                activity.getString(R.string.gallery_picker),
                activity.getString(R.string.internal_picker),
                activity.getString(R.string.documents_picker),
                activity.getString(R.string.camera)
            )) { _, which ->
                if (setDefaultSwitch.isChecked) {
                    activity.getSharedPreferences(activity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putInt(activity.DEFAULT_ACTION_KEY, which)
                        .apply()
                    activity.defaultImageAction = which
                }
                when (which) {
                    0 -> launchGalleryPicker()
                    1 -> launchInternalPhotoPicker()
                    2 -> launchDocumentsPicker()
                    3 -> launchCamera()
                }
            }
            .setOnDismissListener {
                vibrationManager.vibrateDialogChoice()
            }
            .show()
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
                }
            }

            container.addView(headerView)
            container.addView(contentView)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.faq_dialog_title)
            .setView(ScrollView(activity).apply { addView(container) })
            .setPositiveButton(R.string.ok_button, null)
            .setOnDismissListener { vibrationManager.vibrateDialogChoice() }
            .show()
    }
}