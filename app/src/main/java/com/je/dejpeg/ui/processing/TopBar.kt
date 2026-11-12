package com.je.dejpeg.ui.processing

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.ui.components.ScreenHorizontalPadding

data class TopBarState(
    val isLandscape: Boolean,
    val isSelectionMode: Boolean,
    val settingsExpanded: Boolean,
    val images: List<ImageItem>,
    val selectedImageIds: List<String>,
    val uiState: ProcessingUiState,
)

@SuppressLint("MissingHapticFeedback")
@Composable
fun TopBar(state: TopBarState, actions: ProcessingActions) {
    val colors = MaterialTheme.colorScheme
    val isProcessing = state.uiState is ProcessingUiState.Processing
    val images = state.images
    val hasImages = images.isNotEmpty()
    val allComplete =
        hasImages && images.all { it.outputBitmap != null && !it.isOutputStale && !it.isProcessing }

    val container by animateColorAsState(
        if (isProcessing) colors.errorContainer else colors.secondaryContainer, label = "primary_bg"
    )
    val content by animateColorAsState(
        if (isProcessing) colors.onErrorContainer else colors.onSecondaryContainer,
        label = "primary_fg"
    )
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
    val onPrimary: () -> Unit = {
        HapticPatterns.tap()
        when {
            isProcessing -> actions.onRequestCancelAll()
            allComplete -> actions.onSaveAll()
            else -> actions.onProcess(null)
        }
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val chevron = if (state.isLandscape) Modifier.rotate(if (isRtl) 90f else -90f) else Modifier

    BarSurface(state.isLandscape, state.settingsExpanded) {
        AnimatedContent(
            targetState = state.isSelectionMode,
            modifier = Modifier.weight(1f),
            label = "bar_morph",
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) { if (targetState) it / 2 else -it / 2 } togetherWith fadeOut(
                    spring(stiffness = Spring.StiffnessMedium)
                ) + slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) {
                    if (targetState) -it / 2 else it / 2
                }
            },
        ) { selecting ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selecting) {
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        actions.onClearSelection()
                    }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.cancel))
                    }
                    TitleText(state.selectedImageIds.size.toString(), Modifier.weight(1f))
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        actions.onRequestSave(state.selectedImageIds, false)
                    }) { Icon(Icons.Rounded.Save, stringResource(R.string.save)) }
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        actions.onRequestRemoval(state.selectedImageIds)
                    }) {
                        Icon(
                            Icons.Rounded.Delete, stringResource(R.string.remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            HapticPatterns.tap()
                            actions.onProcess(null)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer,
                        ),
                    ) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.process)) }
                } else {
                    TitleText(stringResource(R.string.images, images.size), Modifier.weight(1f))
                    SettingsToggle(
                        expanded = state.settingsExpanded,
                        onToggle = {
                            HapticPatterns.tap()
                            if (!state.settingsExpanded) actions.onClearSelection()
                            actions.onSettingsExpandedChange(!state.settingsExpanded)
                        },
                        modifier = chevron,
                    )
                    if (hasImages) {
                        PrimaryPill(
                            icon = primaryIcon,
                            label = primaryLabel,
                            container = container,
                            contentColor = content,
                            onClick = onPrimary,
                        )
                        IconButton(onClick = {
                            HapticPatterns.tap()
                            actions.onImport()
                        }) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.add_images))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarSurface(
    isLandscape: Boolean,
    settingsExpanded: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = if (isLandscape && settingsExpanded) {
        RoundedCornerShape(topEnd = ScreenHorizontalPadding)
    } else RoundedCornerShape(topEnd = 0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun TitleText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(end = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}

@SuppressLint("MissingHapticFeedback")
@Composable
private fun PrimaryPill(
    icon: ImageVector,
    @StringRes label: Int,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .animateContentSize()
            .heightIn(min = 40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = contentColor
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
