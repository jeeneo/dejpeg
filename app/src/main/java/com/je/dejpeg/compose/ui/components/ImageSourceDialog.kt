/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*
*/

/*
* Also please don't steal my work and claim it as your own, thanks.
*/

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.je.dejpeg.R
import com.je.dejpeg.data.AppPreferences
import kotlinx.coroutines.launch

private const val HELP_TYPE_GALLERY = 0
private const val HELP_TYPE_INTERNAL = 1
private const val HELP_TYPE_DOCUMENTS = 2
private const val HELP_TYPE_CAMERA = 3

@Composable
fun ImageSourceDialog(
    onDismiss: () -> Unit, 
    onGallerySelected: () -> Unit, 
    onInternalSelected: () -> Unit, 
    onDocumentsSelected: () -> Unit, 
    onCameraSelected: () -> Unit
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
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

    Dialog(
        onDismissRequest = onDismiss
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    icon = painterResource(id = R.drawable.ic_gallery),
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    MaterialSwitchPreference(
                        title = stringResource(R.string.set_as_default),
                        summary = stringResource(R.string.set_as_default_desc),
                        checked = setAsDefault,
                        onCheckedChange = { haptic.light(); setAsDefault = it }
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
    icon: Any,
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
                        when (icon) {
                            is ImageVector -> Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            is Painter -> Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
    InfoAlertDialog(
        title = title,
        message = text,
        onDismiss = onDismiss,
        icon = Icons.Default.Info
    )
}
