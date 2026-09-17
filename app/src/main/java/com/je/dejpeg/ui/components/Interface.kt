package com.je.dejpeg.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GroupedListSpacing: Dp = 2.dp
val ScreenHorizontalPadding: Dp = 16.dp

@Composable
fun segmentedShapes(index: Int, count: Int) = ListItemDefaults.segmentedShapes(index - 1, count)

data class CornerRole(
    val topStart: Boolean = false,
    val topEnd: Boolean = false,
    val bottomStart: Boolean = false,
    val bottomEnd: Boolean = false,
) {
    companion object {
        val None = CornerRole()
        val All = CornerRole(topStart = true, topEnd = true, bottomStart = true, bottomEnd = true)
        fun forPosition(index: Int, count: Int): CornerRole = when {
            count <= 1 -> All
            index == 1 -> CornerRole(topStart = true, topEnd = true) // leading
            index == count -> CornerRole(bottomStart = true, bottomEnd = true) // trailing
            else -> None // center
        }
    }
}

fun CornerRole.toShape(outer: Dp = ScreenHorizontalPadding, inner: Dp = 6.dp): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (topStart) outer else inner,
        topEnd = if (topEnd) outer else inner,
        bottomStart = if (bottomStart) outer else inner,
        bottomEnd = if (bottomEnd) outer else inner,
    )

@Composable
fun CornerRole.toListItemShapes(
    outer: Dp = ScreenHorizontalPadding,
    inner: Dp = 6.dp,
): ListItemShapes = remember(this, outer, inner) {
    val idle = toShape(outer, inner)
    ListItemShapes(
        shape = idle,
        selectedShape = idle,
        pressedShape = RoundedCornerShape(outer),
        focusedShape = idle,
        hoveredShape = idle,
        draggedShape = idle,
    )
}

@Composable
fun horizontalSegmentedShapes(
    index: Int,
    count: Int,
    defaultShapes: ListItemShapes = ListItemDefaults.shapes(),
    roundedShape: CornerBasedShape = RoundedCornerShape(percent = 50),
): ListItemShapes {
    val normalizedIndex = index - 1
    return remember(normalizedIndex, count, defaultShapes, roundedShape) {
        fun Shape.correctedOrSelf(): Shape {
            if (this !is CornerBasedShape) return this
            return when {
                count == 1 -> copy(
                    topStart = roundedShape.topStart,
                    topEnd = roundedShape.topEnd,
                    bottomStart = roundedShape.bottomStart,
                    bottomEnd = roundedShape.bottomEnd,
                )

                normalizedIndex == 0 -> copy(
                    topStart = roundedShape.topStart,
                    bottomStart = roundedShape.bottomStart,
                )

                normalizedIndex == count - 1 -> copy(
                    topEnd = roundedShape.topEnd,
                    bottomEnd = roundedShape.bottomEnd,
                )

                else -> this
            }
        }

        defaultShapes.copy(
            shape = defaultShapes.shape.correctedOrSelf(),
            selectedShape = defaultShapes.selectedShape.correctedOrSelf(),
            pressedShape = defaultShapes.pressedShape.correctedOrSelf(),
            focusedShape = defaultShapes.focusedShape.correctedOrSelf(),
            hoveredShape = defaultShapes.hoveredShape.correctedOrSelf(),
            draggedShape = defaultShapes.draggedShape.correctedOrSelf(),
        )
    }
}

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

