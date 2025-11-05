package com.je.dejpeg.compose.ui.components

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
fun ImageSourceDialog(
    onDismiss: () -> Unit, 
    onGallerySelected: () -> Unit, 
    onInternalSelected: () -> Unit, 
    onDocumentsSelected: () -> Unit, 
    onCameraSelected: () -> Unit
) {
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
        // Changed to ElevatedCard with better elevation
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Enhanced header with icon
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
                
                ModernImageSourceOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.gallery),
                    description = stringResource(R.string.gallery_picker_title),
                    onClick = { haptic.medium(); handleSelection("gallery", onGallerySelected) },
                    onHelpClick = { haptic.light(); helpInfo = ImageSourceHelpType.GALLERY }
                )
                
                ModernImageSourceOption(
                    icon = Icons.Outlined.Photo,
                    title = stringResource(R.string.internal_picker),
                    description = stringResource(R.string.internal_picker_title),
                    onClick = { haptic.medium(); handleSelection("internal", onInternalSelected) },
                    onHelpClick = { haptic.light(); helpInfo = ImageSourceHelpType.INTERNAL }
                )
                ModernImageSourceOption(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.documents),
                    description = stringResource(R.string.documents_picker_title),
                    onClick = { haptic.medium(); handleSelection("documents", onDocumentsSelected) },
                    onHelpClick = { haptic.light(); helpInfo = ImageSourceHelpType.DOCUMENTS }
                )
                ModernImageSourceOption(
                    icon = Icons.Outlined.CameraAlt,
                    title = stringResource(R.string.camera),
                    description = stringResource(R.string.camera_title),
                    onClick = { haptic.medium(); handleSelection("camera", onCameraSelected) },
                    onHelpClick = { haptic.light(); helpInfo = ImageSourceHelpType.CAMERA }
                )
                Spacer(Modifier.height(4.dp))
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.set_as_default),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.set_as_default_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = setAsDefault,
                            onCheckedChange = { haptic.light(); setAsDefault = it }
                        )
                    }
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
    helpInfo?.let { type ->
        val (titleRes, descRes) = when (type) {
            ImageSourceHelpType.GALLERY -> R.string.gallery_picker_title to R.string.gallery_picker_desc
            ImageSourceHelpType.INTERNAL -> R.string.internal_picker_title to R.string.internal_picker_desc
            ImageSourceHelpType.DOCUMENTS -> R.string.documents_picker_title to R.string.documents_picker_desc
            ImageSourceHelpType.CAMERA -> R.string.camera_title to R.string.camera_desc
        }
        HelpDialog(
            title = stringResource(titleRes),
            text = stringResource(descRes)
        ) { helpInfo = null }
    }
}

@Composable
private fun ModernImageSourceOption(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
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
            FilledTonalButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}