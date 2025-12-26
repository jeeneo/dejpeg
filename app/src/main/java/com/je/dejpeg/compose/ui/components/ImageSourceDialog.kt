package com.je.dejpeg.compose.ui.components

import androidx.annotation.IntDef
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.launch

private const val HELP_TYPE_GALLERY = 0
private const val HELP_TYPE_INTERNAL = 1
private const val HELP_TYPE_DOCUMENTS = 2
private const val HELP_TYPE_CAMERA = 3

@IntDef(HELP_TYPE_GALLERY, HELP_TYPE_INTERNAL, HELP_TYPE_DOCUMENTS, HELP_TYPE_CAMERA)
@Retention(AnnotationRetention.SOURCE)
private annotation class ImageSourceHelpType

@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit, 
    onGallerySelected: () -> Unit, 
    onInternalSelected: () -> Unit, 
    onDocumentsSelected: () -> Unit, 
    onCameraSelected: () -> Unit
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    var helpInfo by remember { mutableIntStateOf(-1) }
    var setAsDefault by remember { mutableStateOf(false) }
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    
    val handleSelection: suspend (String, () -> Unit) -> Unit = { key, action ->
        if (setAsDefault) {
            appPreferences.setDefaultImageSource(key)
        }
        onDismiss()
        action()
    }

    val dialogWidth = rememberDialogWidth()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogDefaults.Properties
    ) {
        ElevatedCard(
            modifier = Modifier.dialogWidth(dialogWidth).padding(16.dp),
            shape = DialogDefaults.Shape,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_image_source),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                ImageSource(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.gallery),
                    description = stringResource(R.string.gallery_picker_title),
                    onClick = {
                        haptic.medium()
                        scope.launch { handleSelection("gallery", onGallerySelected) }
                    },
                    onHelpClick = { haptic.light(); helpInfo = HELP_TYPE_GALLERY }
                )
                ImageSource(
                    icon = Icons.Outlined.Photo,
                    title = stringResource(R.string.internal_picker),
                    description = stringResource(R.string.internal_picker_title),
                    onClick = {
                        haptic.medium()
                        scope.launch { handleSelection("internal", onInternalSelected) }
                    },
                    onHelpClick = { haptic.light(); helpInfo = HELP_TYPE_INTERNAL }
                )
                ImageSource(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.documents),
                    description = stringResource(R.string.documents_picker_title),
                    onClick = {
                        haptic.medium()
                        scope.launch { handleSelection("documents", onDocumentsSelected) }
                    },
                    onHelpClick = { haptic.light(); helpInfo = HELP_TYPE_DOCUMENTS }
                )
                ImageSource(
                    icon = Icons.Outlined.CameraAlt,
                    title = stringResource(R.string.camera),
                    description = stringResource(R.string.camera_title),
                    onClick = {
                        haptic.medium()
                        scope.launch { handleSelection("camera", onCameraSelected) }
                    },
                    onHelpClick = { haptic.light(); helpInfo = HELP_TYPE_CAMERA }
                )
                Spacer(Modifier.height(4.dp))
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    MaterialSwitchPreference(
                        title = stringResource(R.string.set_as_default),
                        summary = stringResource(R.string.set_as_default_desc),
                        checked = setAsDefault,
                        onCheckedChange = { haptic.light(); setAsDefault = it },
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { haptic.light(); onDismiss() }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
    if (helpInfo >= 0) {
        val (titleRes, descRes) = when (helpInfo) {
            HELP_TYPE_GALLERY -> R.string.gallery_picker_title to R.string.gallery_picker_desc
            HELP_TYPE_INTERNAL -> R.string.internal_picker_title to R.string.internal_picker_desc
            HELP_TYPE_DOCUMENTS -> R.string.documents_picker_title to R.string.documents_picker_desc
            HELP_TYPE_CAMERA -> R.string.camera_title to R.string.camera_desc
            else -> R.string.gallery_picker_title to R.string.gallery_picker_desc
        }
        HelpDialog(
            title = stringResource(titleRes),
            text = stringResource(descRes)
        ) { helpInfo = -1 }
    }
}

@Composable
private fun ImageSource(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(1.dp, 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onHelpClick, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Info, contentDescription = stringResource(R.string.help), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HelpDialog(title: String, text: String, onDismiss: () -> Unit) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}