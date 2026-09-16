/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.je.dejpeg.HapticFeedbacks
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeBox(
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
    val density = LocalDensity.current
    val velocityTracker = remember { VelocityTracker() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var maxWidthPx by remember { mutableFloatStateOf(0f) }
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }
    val collapseFraction = remember { Animatable(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val canInteract =
        gesturesEnabled && (enableDismissFromStartToEnd || enableDismissFromEndToStart)

    Box(
        modifier = modifier
            .onSizeChanged {
                maxWidthPx = it.width.coerceAtLeast(1).toFloat()
                if (collapseFraction.value == 1f) measuredHeightPx = it.height.toFloat()
            }
            .then(
                if (collapseFraction.value < 1f) {
                    Modifier.height(with(density) {
                            (measuredHeightPx * collapseFraction.value).toInt().coerceAtLeast(0)
                                .toDp()
                        }).clipToBounds()
                } else Modifier
            )
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
                            collapseFraction.animateTo(
                                targetValue = 0f, animationSpec = tween(
                                    durationMillis = 110, easing = FastOutSlowInEasing
                                )
                            )
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

@Composable
fun CardWrapper(
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> (() -> Unit)?,
    onSwipeRight: () -> (() -> Unit)?,
    swapSwipeActions: Boolean,
    rightSwipeEnabled: Boolean = true,
    isProcessing: Boolean = false,
    hasOutputBitmap: Boolean = false,
    content: @Composable () -> Unit
) {
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdFrac = 0.4f
    val leftSwipeIcon = if (swapSwipeActions) {
        if (isProcessing) Icons.Rounded.Close else Icons.Rounded.Delete
    } else {
        if (hasOutputBitmap) Icons.Rounded.Save else Icons.Rounded.PlayArrow
    }
    val leftSwipeIconTint =
        if (swapSwipeActions) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val leftSwipeBgColor =
        if (swapSwipeActions) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.tertiaryContainer
    val rightSwipeIcon = if (swapSwipeActions) {
        if (hasOutputBitmap) Icons.Rounded.Save else Icons.Rounded.PlayArrow
    } else {
        if (isProcessing) Icons.Rounded.Close else Icons.Rounded.Delete
    }
    val rightSwipeIconTint =
        if (swapSwipeActions) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val rightSwipeBgColor =
        if (swapSwipeActions) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.onError

    SwipeBox(
        onQualifiedStartToEnd = { HapticFeedbacks.light(); currentOnSwipeRight() },
        onQualifiedEndToStart = { HapticFeedbacks.light(); currentOnSwipeLeft() },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = rightSwipeEnabled,
        positionalThreshold = { totalWidth -> totalWidth * thresholdFrac },
        backgroundContent = { offsetPx, maxWidthPx ->
            Box(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val isRight = offsetPx > 0f
                val revealedPx = abs(offsetPx).coerceIn(0f, maxWidthPx)
                val thresholdPx = maxWidthPx * thresholdFrac
                val rawProgress =
                    if (thresholdPx > 0f) (revealedPx / thresholdPx).coerceIn(0f, 1f) else 0f
                val progress = FastOutSlowInEasing.transform(rawProgress)
                val armed = rawProgress >= 1f
                LaunchedEffect(isRight, armed) {
                    if (armed) HapticFeedbacks.heavy()
                }
                val idleColor = MaterialTheme.colorScheme.surfaceVariant
                val activeColor = if (isRight) rightSwipeBgColor else leftSwipeBgColor
                val contColor = lerp(idleColor, activeColor, progress)
                val iconTint = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    if (isRight) rightSwipeIconTint else leftSwipeIconTint,
                    progress
                )
                val icon = if (isRight) rightSwipeIcon else leftSwipeIcon
                val revealedDp = with(density) { revealedPx.toDp() }
                val edgeAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd
                val visible = revealedPx > 1f
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 60),
                    label = "swipeAlpha"
                )
                val armedAnim = remember { Animatable(0f) }
                LaunchedEffect(armed) {
                    armedAnim.animateTo(
                        targetValue = if (armed) 1f else 0f, animationSpec = if (armed) {
                            spring(
                                dampingRatio = Spring.DampingRatioHighBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        } else {
                            tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        }
                    )
                }
                val armedFactor = armedAnim.value.coerceIn(0f, 1f)
                val iconSize = lerp(24.dp, 32.dp, armedFactor)
                val cornerRadius = lerp(revealedDp, 14.dp, armedFactor)
                val halfIconSize = iconSize / 2

                // extent the inset just far enough so that the user doesn't see it move minus the spacing
                val fixedInset = 34.dp - 2.dp

                val iconCenterFromEdge = maxOf(fixedInset, revealedDp / 2)
                val iconOffset = iconCenterFromEdge - halfIconSize

                // visually inspired from Gmail,
                // its icon lives behind the background
                Box(
                    modifier = Modifier
                        .align(edgeAlignment)
                        .width(revealedDp)
                        .fillMaxHeight()
                        .padding(start = 2.dp, end = 2.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(contColor)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .align(edgeAlignment)
                            .offset(x = if (isRight) iconOffset else -iconOffset)
                            .requiredSize(iconSize)
                    )
                }
            }
        }) {
        content()
    }
}
