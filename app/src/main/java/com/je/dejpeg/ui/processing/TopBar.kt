package com.je.dejpeg.ui.processing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageFlows
import com.je.dejpeg.ui.components.GroupedListSpacing
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import com.je.dejpeg.ui.components.horizontalSegmentedShapes

@Composable
fun TopBar(
    isLandscape: Boolean,
    isSelectionMode: Boolean,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
    onShowCancelAllDialogChange: (Boolean) -> Unit,
    images: List<ImageItem>,
    selectedImageIds: List<String>,
    processingViewModel: ProcessingViewModel,
    flows: ImageFlows,
    clearSelection: () -> Unit,
    tryProcess: (() -> Unit) -> Unit,
    takeProcess: () -> Unit,
    launchImportIntent: () -> Unit,
) {
    val displayCount = if (isSelectionMode) selectedImageIds.size else images.size
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val rotateModifier = if (isLandscape) {
        Modifier.rotate(if (isRtl) 90f else -90f)
    } else {
        Modifier
    }
    val shape2 = if (isLandscape && settingsExpanded) {
        RoundedCornerShape(topEnd = ScreenHorizontalPadding)
    } else {
        RoundedCornerShape(topEnd = 0.dp)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape2)
            .background(MaterialTheme.colorScheme.background)
            .padding(
                bottom = 8.dp, start = ScreenHorizontalPadding, end = ScreenHorizontalPadding
            ), Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        AnimatedContent(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
            targetState = isSelectionMode,
            label = "header_text",
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { if (targetState) it else -it }) togetherWith fadeOut(
                    spring(stiffness = Spring.StiffnessMedium)
                ) + slideOutVertically(
                    spring(dampingRatio = Spring.DampingRatioNoBouncy),
                    targetOffsetY = { if (targetState) -it else it })
            }) { selecting ->
            Text(
                if (selecting) pluralStringResource(
                    R.plurals.selected_count, displayCount, displayCount
                )
                else stringResource(R.string.images, images.size),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }

    val uiState by processingViewModel.uiState.collectAsState()
    val isProcessing = uiState is ProcessingUiState.Processing
    val hasImages = images.isNotEmpty()
    val allComplete =
        hasImages && images.all { it.outputBitmap != null && !it.isOutputStale && !it.isProcessing }

    val segmentCount = if (hasImages) 3 else 2

    val colors = MaterialTheme.colorScheme
    val processContainer by animateColorAsState(
        if (isProcessing) colors.errorContainer else colors.secondaryContainer,
        label = "fab_container"
    )
    val processContent by animateColorAsState(
        if (isProcessing) colors.onErrorContainer else colors.onSecondaryContainer,
        label = "fab_content"
    )
    val (processIcon, processLabelRes) = when {
        isProcessing -> Icons.Rounded.Close to R.string.cancel
        allComplete -> Icons.Rounded.Save to R.string.save_all
        else -> Icons.Rounded.PlayArrow to R.string.process
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(bottom = 8.dp, start = ScreenHorizontalPadding, end = ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(GroupedListSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentedListItem(
            onClick = { HapticPatterns.tap(); launchImportIntent() },
            shapes = horizontalSegmentedShapes(1, segmentCount),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp),
            colors = ListItemDefaults.colors(
                containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            leadingContent = {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_images))
            },
            content = {
                Text(
                    stringResource(R.string.add_images),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            })
        if (hasImages) {
            SegmentedListItem(
                onClick = {
                    HapticPatterns.tap()
                    if (isProcessing) {
                        onShowCancelAllDialogChange(true)
                    } else if (allComplete) {
                        flows.saveAllNow()
                    } else {
                        tryProcess { takeProcess() }
                    }
                },
                shapes = horizontalSegmentedShapes(2, segmentCount),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                colors = ListItemDefaults.colors(
                    containerColor = processContainer, contentColor = processContent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                leadingContent = {
                    Crossfade(processIcon, label = "fab_icon") {
                        Icon(it, contentDescription = null)
                    }
                },
                content = {
                    Crossfade(processLabelRes, label = "fab_text") {
                        Text(
                            stringResource(it),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }
                })
        }
        SegmentedListItem(
            onClick = {
                HapticPatterns.tap()
                if (!settingsExpanded) clearSelection()
                onSettingsExpandedChange(!settingsExpanded)
            },
            shapes = horizontalSegmentedShapes(segmentCount, segmentCount),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp),
            colors = ListItemDefaults.colors(
                containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            trailingContent = {
                Icon(
                    if (settingsExpanded) Icons.Rounded.KeyboardArrowDown
                    else Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = rotateModifier
                )
            },
            content = {
                Text(
                    "Settings",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            })
    }
}
