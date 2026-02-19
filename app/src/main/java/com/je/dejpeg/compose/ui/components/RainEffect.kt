/**
* Copyright (C) 2025/2026 dryerlint <codeberg.org/dryerlint>
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

/*
* If you use this code in your own project, please give credit
*/

package com.je.dejpeg.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

data class FallingLogo(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val scale: Float,
    val startTime: Long
)

@Composable
fun RainingLogoEffect(
    painter: Painter,
    logoSize: IntSize,
    modifier: Modifier = Modifier,
    particleCount: Int = 12,
    onTap: () -> Unit = {},
    spawnTrigger: Long = 0
) {
    var logos by remember { mutableStateOf<List<FallingLogo>>(emptyList()) }
    var nextId by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                frameTime = nanos / 1_000_000L
            }
        }
    }

    LaunchedEffect(frameTime) {
        logos = logos.filter { logo ->
            val elapsed = (frameTime - logo.startTime).toFloat()
            val elapsedSeconds = elapsed / 1000f
            val y = logo.startY + logo.velocityY * elapsedSeconds
            val scaledHeight = logoSize.height * logo.scale
            y < canvasSize.height + scaledHeight
        }
    }

    LaunchedEffect(spawnTrigger) {
        if (spawnTrigger > 0) {
            onTap()
            val newLogos = if (logos.size > 50) {
                val currentHugeCount = logos.count { it.scale >= 1.0f }
                val scale = 1.0f + (currentHugeCount * 0.2f)
                listOf(
                    FallingLogo(
                        id = nextId++,
                        startX = Random.nextFloat() * canvasSize.width,
                        startY = 0f,
                        velocityX = (Random.nextFloat() - 0.5f) * 2f,
                        velocityY = 100f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 180f - 90f,
                        scale = scale,
                        startTime = frameTime
                    )
                )
            } else {
                (0 until particleCount).map {
                    val startX = Random.nextFloat() * canvasSize.width
                    val startY = 0f
                    val velocityX = (Random.nextFloat() - 0.5f) * 2f
                    val velocityY = Random.nextFloat() * 100f + 150f

                    FallingLogo(
                        id = nextId++,
                        startX = startX,
                        startY = startY,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 180f - 90f,
                        scale = Random.nextFloat() * 0.25f + 0.15f,
                        startTime = frameTime
                    )
                }
            }
            logos = logos + newLogos
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (canvasSize.width == 0 || canvasSize.height == 0) {
                canvasSize = IntSize(size.width.toInt(), size.height.toInt())
            }
            logos.forEach { logo ->
                val elapsed = (frameTime - logo.startTime).toFloat()
                val elapsedSeconds = elapsed / 1000f
                val x = logo.startX + logo.velocityX * elapsedSeconds
                val y = logo.startY + logo.velocityY * elapsedSeconds
                val rotation = logo.rotation + logo.rotationSpeed * elapsedSeconds
                val alpha = 1f
                val scaledWidth = logoSize.width * logo.scale
                val scaledHeight = logoSize.height * logo.scale
                translate(x - scaledWidth / 2f, y - scaledHeight / 2f) {
                    rotate(
                        degrees = rotation,
                        pivot = Offset(scaledWidth / 2f, scaledHeight / 2f)
                    ) {
                        with(painter) {
                            draw(
                                size = androidx.compose.ui.geometry.Size(scaledWidth, scaledHeight),
                                alpha = alpha
                            )
                        }
                    }
                }
            }
        }
    }
}
