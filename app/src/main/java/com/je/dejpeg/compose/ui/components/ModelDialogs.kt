package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.net.toUri

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
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_management)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                                        fontWeight = if (name == active) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (models.isNotEmpty()) {
                TextButton(
                        onClick = { haptic.light(); onDelete() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = {
                    haptic.light()
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        "https://codeberg.org/dryerlint/dejpeg/src/branch/main/models".toUri()
                    )
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.download))
                }
                Button(onClick = { haptic.medium(); onImport() }) {
                    Text(stringResource(R.string.import_text))
                }
            }
        }
    )
}

@Composable
fun DeleteDialog(
    models: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    val haptic = com.je.dejpeg.compose.utils.rememberHapticFeedback()
    StyledAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_models)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                        Text(
                            name,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { haptic.light(); onDismiss() }) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = { haptic.heavy(); onConfirm(selected.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    )
}
