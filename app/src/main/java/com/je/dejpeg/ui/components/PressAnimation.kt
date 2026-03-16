package com.je.dejpeg.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

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
