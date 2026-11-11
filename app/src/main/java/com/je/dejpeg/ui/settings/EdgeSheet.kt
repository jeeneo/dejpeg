/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.ui.components.ScreenHorizontalPadding
import kotlin.math.roundToInt

enum class SheetEdge { Bottom, End }

@Stable
class EdgeSheetState(initialSizePx: Float) {
    var sizePx by mutableFloatStateOf(initialSizePx)
    var animating by mutableStateOf(false)
}

@Composable
fun rememberEdgeSheetState(expanded: Boolean, expandedSizePx: Float): EdgeSheetState =
    remember { EdgeSheetState(if (expanded) expandedSizePx else 0f) }

@Composable
fun Modifier.reserveSheetSpace(state: EdgeSheetState, edge: SheetEdge): Modifier {
    return layout { measurable, constraints ->
        val inset = state.sizePx.roundToInt().coerceAtLeast(0)
        val w = if (edge == SheetEdge.End) {
            (constraints.maxWidth - inset).coerceAtLeast(0)
        } else {
            constraints.maxWidth
        }
        val h = if (edge == SheetEdge.Bottom) {
            (constraints.maxHeight - inset).coerceAtLeast(0)
        } else {
            constraints.maxHeight
        }
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}

private val HandleThickness = 21.dp
private const val SettleThreshold = 0.9f

@Composable
fun EdgeSheet(
    state: EdgeSheetState,
    edge: SheetEdge,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedSizePx: Float,
    background: Color,
    modifier: Modifier = Modifier,
    backProgress: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val vertical = edge == SheetEdge.Bottom
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dragSign = if (!vertical && isRtl) -1f else 1f
    val progress = backProgress.coerceIn(0f, 1f)
    var pastThreshold by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, expandedSizePx, progress) {
        val target = if (expanded) {
            (expandedSizePx * (1f - progress * 0.2f)).coerceAtLeast(0f)
        } else {
            0f
        }
        if (target == state.sizePx) return@LaunchedEffect
        if (progress > 0f) {
            state.animating = false
            state.sizePx = target
        } else {
            state.animating = true
            try {
                animate(
                    initialValue = state.sizePx, targetValue = target, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ -> state.sizePx = value }
            } finally {
                state.animating = false
            }
        }
    }

    Box(
        modifier
            .layout { measurable, constraints ->
                val size = state.sizePx.roundToInt().coerceAtLeast(0)
                val c = if (vertical) {
                    Constraints.fixed(constraints.maxWidth, size)
                } else {
                    Constraints.fixed(size, constraints.maxHeight)
                }
                val placeable = measurable.measure(c)
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .clipToBounds()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(if (vertical) Alignment.TopCenter else Alignment.CenterStart)
                .then(
                    if (vertical) {
                        Modifier
                            .fillMaxWidth()
                            .height(HandleThickness)
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .width(HandleThickness)
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = expanded && progress == 0f
                ) {
                    HapticPatterns.tap()
                    onExpandedChange(false)
                }
                .draggable(
                    orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
                    enabled = expanded && !state.animating && progress == 0f,
                    state = rememberDraggableState { delta ->
                        state.sizePx =
                            (state.sizePx - delta * dragSign).coerceIn(0f, expandedSizePx)
                        val past = state.sizePx >= expandedSizePx * SettleThreshold
                        if (past != pastThreshold) {
                            pastThreshold = past
                            HapticPatterns.tap()
                        }
                    },
                    onDragStarted = {
                        pastThreshold = state.sizePx >= expandedSizePx * SettleThreshold
                    },
                    onDragStopped = {
                        val settled = state.sizePx >= expandedSizePx * SettleThreshold
                        animate(
                            initialValue = state.sizePx,
                            targetValue = if (settled) expandedSizePx else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) { value, _ -> state.sizePx = value }
                        pastThreshold = settled
                        onExpandedChange(settled)
                    })
        ) {
            Box(
                Modifier
                    .size(if (vertical) DpSize(48.dp, 4.dp) else DpSize(4.dp, 48.dp))
                    .background(Color.Gray, RoundedCornerShape(50))
            )
        }

        Surface(
            color = background, shape = if (vertical) {
                RoundedCornerShape(
                    topStart = ScreenHorizontalPadding, topEnd = ScreenHorizontalPadding
                )
            } else {
                RoundedCornerShape(
                    topStart = ScreenHorizontalPadding, bottomStart = ScreenHorizontalPadding
                )
            }, modifier = Modifier
                .padding(
                    top = if (vertical) HandleThickness else 0.dp,
                    start = if (vertical) 0.dp else HandleThickness
                )
                .fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize(), content = content)
        }
    }
}
