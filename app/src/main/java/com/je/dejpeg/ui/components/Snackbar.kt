package com.je.dejpeg.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MAX_SNACKBARS = 4

private val snapbackSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow
)

@Composable
fun SnackySnackbarBox(
    snackbarHostState: SnackySnackbarHostState, content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        content()

        LaunchedEffect(snackbarHostState) {
            SnackySnackbarController.events.collect { event ->
                launch { snackbarHostState.show(event) }
            }
        }

        snackbarHostState.stack.forEachIndexed { index, snackbarData ->
            val stackDepth = snackbarHostState.stack.size - 1 - index

            key(snackbarData) {
                val animatedOffset by animateDpAsState(
                    targetValue = (stackDepth * 12).dp, animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ), label = "snackbar-stack-offset"
                )

                AnimatedVisibility(
                    visible = snackbarHostState.stack.contains(snackbarData),
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .zIndex((MAX_SNACKBARS - stackDepth).toFloat())
                        .offset { IntOffset(0, animatedOffset.roundToPx()) }
                        .scale(1f - stackDepth * 0.05f)
                        .alpha(1f - stackDepth * 0.2f)) {
                    SnackbarContent(
                        snackbarData = snackbarData,
                        onDismiss = snackbarData::dismiss
                    )
                }
            }
        }
    }
}

object SnackySnackbarController {
    private val _events = Channel<SnackySnackbarEvents>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    suspend fun pushEvent(event: SnackySnackbarEvents) = _events.send(event)
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

    val progress = remember(snackbarData) { Animatable(1f) }
    LaunchedEffect(snackbarData) {
        durationMs ?: return@LaunchedEffect
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs.toInt(), easing = LinearEasing)
        )
        onDismiss()
    }

    val dragOffsetY = remember(snackbarData) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    val dragModifier = Modifier.pointerInput(onDismiss) {
        detectVerticalDragGestures(onDragEnd = {
            scope.launch {
                if (dragOffsetY.value.let { it <= -dismissThresholdPx || it >= dismissThresholdPx }) {
                    onDismiss()
                } else {
                    dragOffsetY.animateTo(0f, snapbackSpec)
                }
            }
        }, onDragCancel = {
            scope.launch { dragOffsetY.animateTo(0f, snapbackSpec) }
        }) { change, dragAmount ->
            change.consume()
            val rawOffset = dragOffsetY.value + dragAmount
            if (rawOffset <= -dismissThresholdPx || rawOffset >= dismissThresholdPx) {
                onDismiss()
                return@detectVerticalDragGestures
            }
            scope.launch {
                dragOffsetY.snapTo(rawOffset * if (rawOffset < 0f) 0.9f else 0.6f)
            }
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .then(dragModifier)) {
        Column {
            Text(
                text = snackbarData.event.message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            if (durationMs != null) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.large),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }
    }
}
