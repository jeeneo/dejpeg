/*
 * SPDX-FileCopyrightText: 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeToDismissBox(
    onQualifiedStartToEnd: () -> (() -> Unit)?,
    onQualifiedEndToStart: () -> (() -> Unit)?,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    gesturesEnabled: Boolean = true,
    positionalThreshold: (totalWidth: Float) -> Float = { totalWidth -> totalWidth / 2f },
    backgroundContent: @Composable (offsetPx: Float, maxWidthPx: Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val currentStartToEnd by rememberUpdatedState(onQualifiedStartToEnd)
    val currentEndToStart by rememberUpdatedState(onQualifiedEndToStart)
    val currentThreshold by rememberUpdatedState(positionalThreshold)
    val currentEnableStartToEnd by rememberUpdatedState(enableDismissFromStartToEnd)
    val currentEnableEndToStart by rememberUpdatedState(enableDismissFromEndToStart)
    val scope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var maxWidthPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val canInteract =
        gesturesEnabled && (enableDismissFromStartToEnd || enableDismissFromEndToStart)

    Box(modifier = modifier
        .onSizeChanged { maxWidthPx = it.width.coerceAtLeast(1).toFloat() }
        .pointerInput(canInteract) {
            if (!canInteract) return@pointerInput
            detectHorizontalDragGestures(onDragStart = {
                settleJob?.cancel()
                velocityTracker.resetTracking()
            }, onHorizontalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val lowerBound = if (currentEnableEndToStart) -maxWidthPx else 0f
                val upperBound = if (currentEnableStartToEnd) maxWidthPx else 0f
                dragOffset = (dragOffset + dragAmount).coerceIn(lowerBound, upperBound)
            }, onDragEnd = {
                val endVelocity = velocityTracker.calculateVelocity().x
                val thresholdPx = currentThreshold(maxWidthPx)
                val velocityThresholdPx = 125.dp.toPx()
                val startToEndQualified =
                    dragOffset > 0f && (dragOffset >= thresholdPx || endVelocity >= velocityThresholdPx)
                val endToStartQualified =
                    dragOffset < 0f && (dragOffset <= -thresholdPx || endVelocity <= -velocityThresholdPx)
                val targetSign = when {
                    startToEndQualified && currentEnableStartToEnd -> 1f
                    endToStartQualified && currentEnableEndToStart -> -1f
                    else -> 0f
                }
                val onDismissed: (() -> Unit)? = when {
                    targetSign > 0f -> currentStartToEnd()
                    targetSign < 0f -> currentEndToStart()
                    else -> null
                }
                settleJob = scope.launch {
                    if (targetSign != 0f && onDismissed != null) {
                        animate(
                            initialValue = dragOffset,
                            targetValue = maxWidthPx * targetSign,
                            animationSpec = tween(
                                durationMillis = 120, easing = FastOutLinearInEasing
                            )
                        ) { value, _ -> dragOffset = value }
                        onDismissed()
                    } else {
                        animate(
                            initialValue = dragOffset, targetValue = 0f, animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) { value, _ -> dragOffset = value }
                    }
                }
            }, onDragCancel = {
                settleJob = scope.launch {
                    animate(
                        initialValue = dragOffset, targetValue = 0f, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { value, _ -> dragOffset = value }
                }
            })
        }) {
        Box(Modifier.matchParentSize()) {
            backgroundContent(dragOffset, maxWidthPx)
        }
        Box(
            modifier = Modifier.offset { IntOffset(dragOffset.roundToInt(), 0) }) {
            content()
        }
    }
}
