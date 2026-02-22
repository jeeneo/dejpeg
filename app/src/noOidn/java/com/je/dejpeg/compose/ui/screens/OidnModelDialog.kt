package com.je.dejpeg.compose.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun OidnModelDialog(
    models: List<String>,
    active: String?,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    onDismiss() // boop
}
