package com.je.dejpeg.compose.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.rememberHapticFeedback

data class ModelDownloadItem(
    val name: String,
    val url: String,
    val description: String = ""
)

@Composable
fun DownloadModelDialog(
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val dialogWidth = rememberDialogWidth()
    val models = listOf(
        ModelDownloadItem(
            stringResource(R.string.fbcnn_model),
            "https://github.com/jeeneo/fbcnn-mobile/releases/latest",
            "JPEG restoration"
        ),
        ModelDownloadItem(
            stringResource(R.string.scunet_model),
            "https://github.com/jeeneo/scunet-mobile/releases/latest",
            "Image denoising"
        ),
        ModelDownloadItem(
            stringResource(R.string.experimental_models),
            "https://github.com/jeeneo/dejpeg-experimental/releases/latest",
            "Untested other models"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                stringResource(R.string.download_models),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.choose_model_to_download),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                models.forEach { model ->
                    ModelDownloadCard(
                        model = model,
                        onDownloadClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, model.url.toUri())
                                )
                                onSuccess(model.name)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.cannot_open_link),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ModelDownloadCard(
    model: ModelDownloadItem,
    onDownloadClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (model.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = {
                    haptic.medium()
                    onDownloadClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.download))
            }
        }
    }
}