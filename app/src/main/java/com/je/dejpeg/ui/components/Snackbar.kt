/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

@file:Suppress("SpellCheckingInspection")

package com.je.dejpeg.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MAX_SNACKBARS = 4

private val snapbackSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow
)

private const val DISMISS_VELOCITY_THRESHOLD = 600f

@Composable
fun SnackySnackbarBox(
    snackbarHostState: SnackySnackbarHostState,
    controller: ActivitySnackySnackbarController,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        content()

        LaunchedEffect(controller) {
            controller.events.collect { event ->
                launch { snackbarHostState.show(event) }
            }
        }

        snackbarHostState.stack.forEachIndexed { index, snackbarData ->
            val stackDepth = snackbarHostState.stack.size - 1 - index

            key(snackbarData) {
                val animatedOffset by animateDpAsState(
                    targetValue = (stackDepth * 10).dp, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ), label = "snackbar-stack-offset"
                )

                AnimatedVisibility(
                    visible = snackbarHostState.stack.contains(snackbarData),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .zIndex((MAX_SNACKBARS - stackDepth).toFloat())
                        .offset { IntOffset(0, animatedOffset.roundToPx()) }
                        .scale(1f - stackDepth * 0.035f)
                        .alpha(1f - stackDepth * 0.15f)) {
                    SnackbarContent(
                        snackbarData = snackbarData, onDismiss = snackbarData::dismiss
                    )
                }
            }
        }
    }
}

class ActivitySnackySnackbarController {
    private val _events = Channel<SnackySnackbarEvents>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    suspend fun pushEvent(event: SnackySnackbarEvents) = _events.send(event)
}

object SnackySnackbarController {
    @Volatile
    private var bound: ActivitySnackySnackbarController? = null

    fun bind(controller: ActivitySnackySnackbarController) {
        bound = controller
    }

    fun unbind(controller: ActivitySnackySnackbarController) {
        if (bound == controller) bound = null
    }

    suspend fun pushEvent(event: SnackySnackbarEvents) {
        bound?.pushEvent(event)
    }
}

sealed interface SnackySnackbarEvents {
    val message: String

    data class MessageEvent(
        override val message: String,
        val duration: SnackbarDuration = SnackbarDuration.Short,
        val icon: Int? = null
    ) : SnackySnackbarEvents
}

enum class SnackbarDuration { Short, Long }

class SnackySnackbarData(
    val event: SnackySnackbarEvents, private val cont: CancellableContinuation<Unit>
) {
    fun dismiss() {
        if (cont.isActive) cont.resume(Unit)
    }
}

class SnackySnackbarHostState {
    val stack: SnapshotStateList<SnackySnackbarData> =
        mutableListOf<SnackySnackbarData>().toMutableStateList()

    suspend fun show(event: SnackySnackbarEvents) {
        var data: SnackySnackbarData? = null
        suspendCancellableCoroutine { cont ->
            data = SnackySnackbarData(event, cont)
            cont.invokeOnCancellation { stack.remove(data) }
            if (stack.size >= MAX_SNACKBARS) {
                stack.firstOrNull()?.dismiss()
                stack.removeFirstOrNull()
            }
            stack.add(data)
        }
        stack.remove(data)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SnackbarContent(
    snackbarData: SnackySnackbarData, onDismiss: () -> Unit
) {
    val durationMs: Long? =
        when ((snackbarData.event as? SnackySnackbarEvents.MessageEvent)?.duration) {
            SnackbarDuration.Short -> 4000L
            SnackbarDuration.Long -> 10000L
            else -> null
        }

    val offsetX = remember(snackbarData) { Animatable(0f) }
    val offsetY = remember(snackbarData) { Animatable(-120f) } // starts off-screen
    val progress = remember(snackbarData) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }

    suspend fun dismissInDirection(dx: Float, dy: Float) {
        if (isDismissing) return
        isDismissing = true
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val exitX = offsetX.value + (dx / dist) * 1200f
        val exitY = offsetY.value + (dy / dist) * 1200f
        coroutineScope {
            launch { offsetX.animateTo(exitX, tween(220)) }
            launch { offsetY.animateTo(exitY, tween(220)) }
        }
        onDismiss()
    }

    LaunchedEffect(snackbarData) {
        offsetY.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
        )
        durationMs ?: return@LaunchedEffect
        progress.animateTo(
            1f, tween(durationMillis = durationMs.toInt(), easing = LinearEasing)
        )
        dismissInDirection(0f, -1f)
    }

    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 60.dp.toPx() }

    val dragModifier = Modifier.pointerInput(onDismiss) {
        val velocityTracker = VelocityTracker()

        detectDragGestures(onDragEnd = {
            val velocity = velocityTracker.calculateVelocity()
            val velX = velocity.x
            val velY = velocity.y
            val speed = sqrt(velX * velX + velY * velY)
            val dist = sqrt(offsetX.value * offsetX.value + offsetY.value * offsetY.value)

            scope.launch {
                when {
                    speed >= DISMISS_VELOCITY_THRESHOLD -> dismissInDirection(velX, velY)

                    dist >= dismissThresholdPx -> dismissInDirection(
                        offsetX.value, offsetY.value
                    )

                    else -> {
                        launch { offsetX.animateTo(0f, snapbackSpec) }
                        launch { offsetY.animateTo(0f, snapbackSpec) }
                    }
                }
            }
        }, onDragCancel = {
            scope.launch {
                launch { offsetX.animateTo(0f, snapbackSpec) }
                launch { offsetY.animateTo(0f, snapbackSpec) }
            }
        }) { change, dragAmount ->
            change.consume()
            velocityTracker.addPosition(change.uptimeMillis, change.position)

            scope.launch {
                offsetX.snapTo(offsetX.value + dragAmount.x)
                offsetY.snapTo(
                    offsetY.value + when {
                        offsetY.value + dragAmount.y > 0f -> dragAmount.y * 0.4f
                        else -> dragAmount.y
                    }
                )
            }
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .offset {
                IntOffset(
                    offsetX.value.roundToInt(), offsetY.value.roundToInt()
                )
            }
            .then(dragModifier)) {
        Column {
            Text(
                text = snackbarData.event.message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            if (durationMs != null) {
                LinearWavyProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    amplitude = { 0.4f },
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}