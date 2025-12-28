package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.compose.ui.viewmodel.ProcessingViewModel

@Composable
fun ModelDialog(
    models: List<String>,
    active: String?,
    viewModel: ProcessingViewModel,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    val context = LocalContext.current
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.model_management)) },
        text = {
            Column {
                if (models.isEmpty()) {
                    Text(stringResource(R.string.no_models_installed))
                } else {
                    models.forEach { name ->
                        val warning = viewModel.getModelWarning(name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { haptic.medium(); onSelect(name) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        name,
                                        fontWeight = if (name == active) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (warning != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.warning_emoji),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (name == active) {
                                    Text(
                                        stringResource(R.string.active),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (warning != null) {
                                    Text(
                                        stringResource(warning.titleResId),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            RadioButton(
                                selected = name == active,
                                onClick = { haptic.medium(); onSelect(name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    haptic.light()
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://codeberg.org/dryerlint/dejpeg/src/branch/main/models")
                    )
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.download))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { haptic.medium(); onImport() }) {
                    Text(stringResource(R.string.import_text))
                }
                if (models.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { haptic.light(); onDelete() }) {
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    )
}

@Composable
fun ModelGridCard(
    name: String,
    isActive: Boolean,
    hasWarning: Boolean,
    onSelect: () -> Unit
) {
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.light(); onSelect() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isActive) 2.dp else 1.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isActive) Icons.Filled.CheckCircle else Icons.Filled.Memory,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (isActive || hasWarning) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.Start) {
                        if (isActive) {
                            Text(
                                stringResource(R.string.active),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (hasWarning) {
                            if (isActive) Spacer(Modifier.width(6.dp))
                            Text(
                                "⚠️",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            if (isActive) {
                RadioButton(
                    selected = true,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun DeleteDialog(
    models: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    val dialogWidth = rememberDialogWidth()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogWidth(dialogWidth),
        properties = DialogDefaults.Properties,
        shape = DialogDefaults.Shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.delete_models)) },
        text = {
            Column {
                models.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.light()
                                if (selected.contains(name)) selected.remove(name)
                                else selected.add(name)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected.contains(name),
                            onCheckedChange = {
                                haptic.light()
                                if (it) selected.add(name)
                                else selected.remove(name)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { haptic.heavy(); onConfirm(selected.toList()) }) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}