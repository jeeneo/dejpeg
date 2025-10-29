package com.je.dejpeg.ui.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.je.dejpeg.R

enum class ImageSourceHelpType {
    GALLERY, INTERNAL, DOCUMENTS, CAMERA
}

@Composable
fun ImageSourceDialog(onDismiss: () -> Unit, onGallerySelected: () -> Unit, onInternalSelected: () -> Unit, onDocumentsSelected: () -> Unit, onCameraSelected: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
    var helpInfo by remember { mutableStateOf<ImageSourceHelpType?>(null) }
    var setAsDefault by remember { mutableStateOf(false) }
    val haptic = com.je.dejpeg.ui.utils.rememberHapticFeedback()
    val handleSelection: (String, () -> Unit) -> Unit = { key, action -> 
        if (setAsDefault) prefs.edit().putString("defaultImageSource", key).apply()
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = stringResource(R.string.select_image_source), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ImageSourceOption(Icons.Outlined.PhotoLibrary, stringResource(R.string.gallery), { haptic.medium(); handleSelection("gallery", onGallerySelected) }, { haptic.light(); helpInfo = ImageSourceHelpType.GALLERY })
                ImageSourceOption(Icons.Outlined.Photo, stringResource(R.string.internal_picker), { haptic.medium(); handleSelection("internal", onInternalSelected) }, { haptic.light(); helpInfo = ImageSourceHelpType.INTERNAL })
                ImageSourceOption(Icons.Outlined.Folder, stringResource(R.string.documents), { haptic.medium(); handleSelection("documents", onDocumentsSelected) }, { haptic.light(); helpInfo = ImageSourceHelpType.DOCUMENTS })
                ImageSourceOption(Icons.Outlined.CameraAlt, stringResource(R.string.camera), { haptic.medium(); handleSelection("camera", onCameraSelected) }, { haptic.light(); helpInfo = ImageSourceHelpType.CAMERA })
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.set_as_default), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = setAsDefault, onCheckedChange = { haptic.light(); setAsDefault = it })
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    helpInfo?.let { type ->
        val (titleRes, descRes) = when (type) {
            ImageSourceHelpType.GALLERY -> R.string.gallery_picker_title to R.string.gallery_picker_desc
            ImageSourceHelpType.INTERNAL -> R.string.internal_picker_title to R.string.internal_picker_desc
            ImageSourceHelpType.DOCUMENTS -> R.string.documents_picker_title to R.string.documents_picker_desc
            ImageSourceHelpType.CAMERA -> R.string.camera_title to R.string.camera_desc
        }
        HelpDialog(title = stringResource(titleRes), text = stringResource(descRes)) { helpInfo = null }
    }
}

@Composable
private fun ImageSourceOption(icon: ImageVector, title: String, onClick: () -> Unit, onHelpClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(title)
                }
            }
            IconButton(onClick = onHelpClick, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Default.Info, contentDescription = stringResource(R.string.help), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun HelpDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }, text = { Text(text = text, style = MaterialTheme.typography.bodyMedium) }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}