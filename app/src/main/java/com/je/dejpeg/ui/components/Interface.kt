package com.je.dejpeg.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GroupedListSpacing: Dp = 2.dp
val ScreenHorizontalPadding: Dp = 16.dp

@Composable
fun segmentedListShapes(
    index: Int,
    count: Int,
    outer: CornerBasedShape = MaterialTheme.shapes.medium,
    inner: CornerBasedShape = MaterialTheme.shapes.small,
): ListItemShapes = CornerRole.forPosition(index, count).toListItemShapes(outer, inner)

data class CornerRole(
    val topStart: Boolean = false,
    val topEnd: Boolean = false,
    val bottomStart: Boolean = false,
    val bottomEnd: Boolean = false,
) {
    companion object {
        val None = CornerRole()
        val All = CornerRole(topStart = true, topEnd = true, bottomStart = true, bottomEnd = true)
        fun vertical(index: Int, count: Int): CornerRole = when {
            count <= 1 -> All
            index == 1 -> CornerRole(topStart = true, topEnd = true) // leading
            index == count -> CornerRole(bottomStart = true, bottomEnd = true) // trailing
            else -> None // center
        }

        fun horizontal(index: Int, count: Int): CornerRole = when {
            count <= 1 -> All
            index == 1 -> CornerRole(topStart = true, bottomStart = true) // start
            index == count -> CornerRole(topEnd = true, bottomEnd = true) // end
            else -> None // middle
        }

        fun forPosition(index: Int, count: Int): CornerRole = vertical(index, count)
    }
}

@Composable
fun CornerRole.toShape(
    outer: CornerBasedShape = MaterialTheme.shapes.medium,
    inner: CornerBasedShape = MaterialTheme.shapes.small,
): RoundedCornerShape = remember(this, outer, inner) {
    RoundedCornerShape(
        topStart = if (topStart) outer.topStart else inner.topStart,
        topEnd = if (topEnd) outer.topEnd else inner.topEnd,
        bottomStart = if (bottomStart) outer.bottomStart else inner.bottomStart,
        bottomEnd = if (bottomEnd) outer.bottomEnd else inner.bottomEnd,
    )
}

@Composable
fun CornerRole.toListItemShapes(
    outer: CornerBasedShape = MaterialTheme.shapes.medium,
    inner: CornerBasedShape = MaterialTheme.shapes.small,
    pressedOuter: CornerBasedShape = MaterialTheme.shapes.large,
    pressedInner: CornerBasedShape = MaterialTheme.shapes.medium,
): ListItemShapes {
    val defaults = ListItemDefaults.shapes()
    val idle = toShape(outer, inner)
    val pressed = toShape(pressedOuter, pressedInner)
    return remember(this, idle, pressed, defaults) {
        defaults.copy(shape = idle, pressedShape = pressed)
    }
}

@Composable
fun horizontalSegmentedShapes(
    index: Int,
    count: Int,
    outer: CornerBasedShape = MaterialTheme.shapes.medium,
    inner: CornerBasedShape = MaterialTheme.shapes.small,
): ListItemShapes = CornerRole.horizontal(index, count).toListItemShapes(outer, inner)

@SuppressLint("MissingHapticFeedback")
@Composable
fun MorphButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled, shapes = ButtonShapes(
            // the shape goes into a shape press that presses the shape into a pressed shape
            shape = RoundedCornerShape(21.dp), pressedShape = RoundedCornerShape(8.dp)
        ), interactionSource = interactionSource, colors = colors
    ) { Text(label) }
}

@Composable
fun rememberMaterialPressState(
    interactionSource: MutableInteractionSource, pressInMs: Int = 80, releaseMs: Int = 150
): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    progress.stop()
                    progress.animateTo(1f, animationSpec = tween(pressInMs))
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    progress.stop()
                    progress.animateTo(0f, animationSpec = tween(releaseMs))
                }
            }
        }
    }
    return remember { derivedStateOf { progress.value } }
}
