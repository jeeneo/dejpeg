package com.je.dejpeg.ui.processing

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.je.dejpeg.R
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageFlows
import com.je.dejpeg.ui.components.ScreenHorizontalPadding

@Composable
fun ProcessingTopBar(
    isLandscape: Boolean,
    settingsExpanded: Boolean,
    isSelectionMode: Boolean,
    selectedImageIds: List<String>,
    images: List<ImageItem>,
    clearSelection: () -> Unit,
    tryProcess: (() -> Unit) -> Unit,
    takeProcess: () -> Unit,
    flows: ImageFlows,
) {
    val colors = MaterialTheme.colorScheme

    BarSurface(isLandscape, settingsExpanded) {
        AnimatedContent(
            targetState = isSelectionMode,
            modifier = Modifier.weight(1f),
            label = "bar_morph",
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) { if (targetState) it / 2 else -it / 2 } togetherWith fadeOut(
                    spring(
                        stiffness = Spring.StiffnessMedium
                    )
                ) + slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) { if (targetState) -it / 2 else it / 2 }
            },
        ) { selecting ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selecting) {
                    IconButton(onClick = { HapticPatterns.tap(); clearSelection() }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.cancel))
                    }
                    TitleText(selectedImageIds.size.toString(), Modifier.weight(1f))
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        flows.requestSave(selectedImageIds, false)
                    }) { Icon(Icons.Rounded.Save, stringResource(R.string.save)) }
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        flows.requestRemoval(selectedImageIds)
                    }) {
                        Icon(
                            Icons.Rounded.Delete, stringResource(R.string.remove),
                            tint = colors.error,
                        )
                    }
                    FilledIconButton(
                        onClick = { HapticPatterns.tap(); tryProcess { takeProcess() }; clearSelection() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer,
                        ),
                    ) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.process)) }
                } else {
                    TitleText(
                        stringResource(R.string.images, images.size), Modifier.weight(1f)
                    )
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
