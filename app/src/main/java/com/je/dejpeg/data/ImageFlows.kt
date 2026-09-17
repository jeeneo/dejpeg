package com.je.dejpeg.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.je.dejpeg.ui.components.RemoveImageDialog
import com.je.dejpeg.ui.components.SaveImageDialog
import com.je.dejpeg.ui.viewmodel.ImageItem
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.utils.ImageActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class SaveRequest(
    val imageIds: List<String>,
    val removeAfter: Boolean = false,
)

sealed interface PendingAction {
    data class ConfirmRemoval(val ids: List<String>) : PendingAction
    data class Save(val request: SaveRequest) : PendingAction
    data class Overwrite(val request: SaveRequest, val filename: String) : PendingAction
}

@Stable
class ImageFlows internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appPreferences: AppPreferences,
    private val viewModel: ProcessingViewModel,
    private val releaseSharedUri: (Uri) -> Unit,
) {
    internal var images: List<ImageItem> = emptyList()
    internal var showSaveDialog: Boolean = true

    var pending by mutableStateOf<PendingAction?>(null)
        private set

    fun requestRemoval(ids: Collection<String>) {
        val targets = ids.mapNotNull { id -> images.firstOrNull { it.id == id } }
        performRemoval(targets.filter { it.outputBitmap == null && !it.isProcessing }.map { it.id })
        val needsConfirmation = targets.filter { it.outputBitmap != null }.map { it.id }
        if (needsConfirmation.isNotEmpty()) pending =
            PendingAction.ConfirmRemoval(needsConfirmation)
    }

    fun requestSave(ids: Collection<String>, removeAfter: Boolean): Boolean {
        val validIds = ids.mapNotNull { id -> images.firstOrNull { it.id == id } }
            .filter { it.outputBitmap != null }.map { it.id }
        if (validIds.isEmpty()) return false
        val request = SaveRequest(validIds, removeAfter)
        if (validIds.size > 1) {
            performSave(request, null, false); return false
        } else if (showSaveDialog) {
            pending = PendingAction.Save(request); return true
        } else {
            gate(request, null); return false
        }
    }

    fun saveAllNow() {
        val ids = images.filter { it.outputBitmap != null }.map { it.id }
        if (ids.isNotEmpty()) performSave(SaveRequest(ids), null, false)
    }

    fun confirmRemoval(saveFirst: Boolean) {
        val action = pending as? PendingAction.ConfirmRemoval ?: return
        pending = null
        if (saveFirst) requestSave(action.ids, true) else performRemoval(action.ids)
    }

    fun confirmSave(name: String?, saveAll: Boolean, disablePrompt: Boolean) {
        val action = pending as? PendingAction.Save ?: return
        pending = null
        if (disablePrompt) scope.launch { appPreferences.setShowSaveDialog(false) }
        if (saveAll) {
            val ids = images.filter { it.outputBitmap != null }.map { it.id }
            if (ids.isNotEmpty()) performSave(SaveRequest(ids), null, false)
        } else {
            gate(action.request, name)
        }
    }

    fun confirmOverwrite(name: String) {
        val action = pending as? PendingAction.Overwrite ?: return
        pending = null
        performSave(action.request, name, true)
    }

    fun dismiss() {
        pending = null
    }

    fun prune() {
        val validIds = images.mapTo(mutableSetOf()) { it.id }
        fun List<String>.stillValid() = filter(validIds::contains).takeIf { it.isNotEmpty() }
        pending = when (val action = pending) {
            is PendingAction.ConfirmRemoval -> action.ids.stillValid()
                ?.let { action.copy(ids = it) }

            is PendingAction.Save -> action.request.imageIds.stillValid()
                ?.let { action.copy(request = action.request.copy(imageIds = it)) }

            is PendingAction.Overwrite -> action.request.imageIds.stillValid()
                ?.let { action.copy(request = action.request.copy(imageIds = it)) }

            null -> null
        }
    }

    private fun gate(request: SaveRequest, filename: String?) {
        val single = request.imageIds.singleOrNull()?.let { id ->
            images.firstOrNull { it.id == id }
        }
        val resolved = filename ?: single?.filename
        if (single != null && resolved != null && ImageActions.checkFileExists(context, resolved)) {
            pending = PendingAction.Overwrite(request, resolved)
        } else {
            performSave(request, resolved.takeIf { single != null }, false)
        }
    }

    private fun performSave(request: SaveRequest, baseFilename: String?, overwrite: Boolean) {
        viewModel.saveImage(
            context = context,
            imageIds = request.imageIds,
            baseFilename = baseFilename,
            overwrite = overwrite,
            onComplete = request.removeAfter.takeIf { it }?.let {
                { performRemoval(request.imageIds) }
            } ?: {})
    }

    private fun performRemoval(ids: List<String>) {
        ids.forEach { id ->
            images.firstOrNull { it.id == id }?.uri?.let(releaseSharedUri)
            viewModel.removeImage(id, force = true, cleanupCache = true)
        }
    }
}

@Composable
fun rememberImageFlows(
    images: List<ImageItem>,
    showSaveDialog: Boolean,
    processingViewModel: ProcessingViewModel,
    appPreferences: AppPreferences,
    onRemoveSharedUri: (Uri) -> Unit,
): ImageFlows {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flows = remember {
        ImageFlows(
            context, scope, appPreferences, processingViewModel, onRemoveSharedUri
        )
    }
    val currentImages by rememberUpdatedState(images)
    val currentShowSaveDialog by rememberUpdatedState(showSaveDialog)
    SideEffect {
        flows.images = currentImages
        flows.showSaveDialog = currentShowSaveDialog
    }
    return flows
}

@Composable
fun ImageFlowDialogs(flows: ImageFlows) {
    when (val action = flows.pending) {
        is PendingAction.ConfirmRemoval -> {
            val targets = flows.images.filter { it.id in action.ids }
            RemoveImageDialog(
                imageFilename = targets.singleOrNull()?.filename,
                count = targets.size,
                onDismissRequest = flows::dismiss,
                onRemove = { flows.confirmRemoval(false) },
                onSaveAndRemove = { flows.confirmRemoval(true) },
            )
        }

        is PendingAction.Save -> {
            val single = action.request.imageIds.singleOrNull()?.let { id ->
                flows.images.firstOrNull { it.id == id }
            }
            SaveImageDialog(
                defaultFilename = single?.filename.orEmpty(),
                showSaveAllOption = flows.images.any { it.outputBitmap != null },
                initialSaveAll = false,
                overwriteMode = false,
                onDismissRequest = flows::dismiss,
                onSave = { name, all, skip -> flows.confirmSave(name, all, skip) },
            )
        }

        is PendingAction.Overwrite -> SaveImageDialog(
            defaultFilename = action.filename,
            showSaveAllOption = false,
            initialSaveAll = false,
            overwriteMode = true,
            onDismissRequest = flows::dismiss,
            onSave = { name, _, _ -> flows.confirmOverwrite(name) },
        )

        null -> Unit
    }
}
