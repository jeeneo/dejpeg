package com.je.dejpeg.ui.processing

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageFlows

@Composable
fun ProcessingFloatingToolbar(
    isLandscape: Boolean,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
    onShowCancelAllDialogChange: (Boolean) -> Unit,
    images: List<ImageItem>,
    processingViewModel: ProcessingViewModel,
    flows: ImageFlows,
    clearSelection: () -> Unit,
    tryProcess: (() -> Unit) -> Unit,
    takeProcess: () -> Unit,
    launchImportIntent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by processingViewModel.uiState.collectAsState()
    val isProcessing = uiState is ProcessingUiState.Processing
    val hasImages = images.isNotEmpty()
    val allComplete =
        hasImages && images.all { it.outputBitmap != null && !it.isOutputStale && !it.isProcessing }

    val primaryIcon = when {
        isProcessing -> Icons.Rounded.Close
        allComplete -> Icons.Rounded.Save
        else -> Icons.Rounded.PlayArrow
    }
    val primaryLabel = when {
        isProcessing -> R.string.cancel
        allComplete -> R.string.save_all
        else -> R.string.process
    }
    val onPrimary = {
        HapticPatterns.tap()
        when {
            isProcessing -> onShowCancelAllDialogChange(true)
            allComplete -> flows.saveAllNow()
            else -> tryProcess { takeProcess() }
        }
    }
    val onAdd = { HapticPatterns.tap(); launchImportIntent() }
    val onSettings = {
        HapticPatterns.tap()
        if (!settingsExpanded) clearSelection()
        onSettingsExpandedChange(!settingsExpanded)
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val chevron = if (isLandscape) Modifier.rotate(if (isRtl) 90f else -90f) else Modifier
    if (isLandscape) {
        VerticalFloatingToolbar(
            expanded = hasImages,
            modifier = modifier,
            expandedShadowElevation = 0.dp,
            collapsedShadowElevation = 0.dp,
            leadingContent = {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.add_images))
                }
            },
            content = { SettingsToggle(settingsExpanded, onSettings, chevron) },
            trailingContent = {
                PrimaryPill(
                    icon = primaryIcon,
                    label = primaryLabel,
                    isProcessing = isProcessing,
                    onClick = onPrimary,
                    isLandscape = isLandscape,
                )
            },
        )
    } else {
        HorizontalFloatingToolbar(
            expanded = hasImages,
            modifier = modifier,
            expandedShadowElevation = 0.dp,
            collapsedShadowElevation = 0.dp,
            leadingContent = {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.add_images))
                }
            },
            content = { SettingsToggle(settingsExpanded, onSettings, chevron) },
            trailingContent = {
                PrimaryPill(
                    icon = primaryIcon,
                    label = primaryLabel,
                    isProcessing = isProcessing,
                    onClick = onPrimary,
                    isLandscape = isLandscape,
                )
            },
        )
    }
}

@SuppressLint("MissingHapticFeedback")
@Composable
private fun PrimaryPill(
    icon: ImageVector,
    label: Int,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isProcessing) colors.errorContainer else colors.secondaryContainer, label = "primary_bg"
    )
    val content by animateColorAsState(
        if (isProcessing) colors.onErrorContainer else colors.onSecondaryContainer,
        label = "primary_fg"
    )
    if (isLandscape) {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container, contentColor = content
            )
        ) {
            Crossfade(icon, label = "primary_icon") { Icon(it, contentDescription = null) }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .animateContentSize()
                .heightIn(min = 40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = container, contentColor = content
            ),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Crossfade(icon, label = "primary_icon") { Icon(it, null, Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            Crossfade(label, label = "primary_label") {
                Text(stringResource(it), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@SuppressLint("MissingHapticFeedback")
@Composable
private fun SettingsToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    IconToggleButton(
        checked = expanded,
        onCheckedChange = { onToggle() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = cs.primaryContainer,
            checkedContentColor = cs.onPrimaryContainer,
        ),
    ) {
        Crossfade(expanded, label = "settings_icon") { open ->
            Icon(
                if (open) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.settings),
                modifier = modifier,
            )
        }
    }
}
