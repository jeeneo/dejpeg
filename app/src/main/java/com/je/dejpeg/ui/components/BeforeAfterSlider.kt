/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import android.graphics.Bitmap
import android.graphics.Color.blue
import android.graphics.Color.red
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.je.dejpeg.HapticFeedbacks
import com.je.dejpeg.R
import com.je.dejpeg.ui.screens.rememberCheckerShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Stable
private class ComparisonTransform(private val maxZoom: Float) {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float, container: Size, content: Size) {
        val newScale = (scale * zoom).coerceIn(1f, maxZoom)
        val effectiveZoom = newScale / scale
        val center = Offset(container.width / 2f, container.height / 2f)
        offset = (offset + center - centroid) * effectiveZoom + centroid - center + pan
        scale = newScale
        offset = clampOffset(offset, newScale, container, content)
    }

    fun doubleTap(centroid: Offset, container: Size, content: Size) {
        if (scale > 1f) {
            scale = 1f; offset = Offset.Zero
        } else applyGesture(centroid, Offset.Zero, min(3f, maxZoom), container, content)
    }

    private fun clampOffset(o: Offset, s: Float, container: Size, content: Size): Offset {
        val w = content.width * s
        val h = content.height * s
        val maxX = max(0f, (w - container.width) / 2f)
        val maxY = max(0f, (h - container.height) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
}

@Composable
fun BeforeAfterSlider(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    modifier: Modifier = Modifier,
    glassSlider: Boolean,
) {
    val beforeImage = remember(beforeBitmap) {
        val hw = beforeBitmap.copy(Bitmap.Config.HARDWARE, false) ?: beforeBitmap
        hw.asImageBitmap()
    }
    val afterImage = remember(afterBitmap) {
        val hw = afterBitmap.copy(Bitmap.Config.HARDWARE, false) ?: afterBitmap
        hw.asImageBitmap()
    }
    val hasAlpha = beforeBitmap.hasAlpha() || afterBitmap.hasAlpha()
    val checkerShader = if (hasAlpha) rememberCheckerShader() else null
    val showLabels = true
    val enableZoom = true
    val sliderHandleSize: Dp = if (glassSlider) 48.dp else 48.dp
    val sliderLineWidth: Dp = if (glassSlider) 16.dp else 3.dp
    val labelPadding: Dp = 24.dp
    val maxZoomFactor = 20f
    val transform = remember(maxZoomFactor) { ComparisonTransform(maxZoomFactor) }
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val (sliderColor, iconColor) = remember(beforeBitmap) { calculateSliderColors(beforeBitmap) }
    val beforeLabel = stringResource(R.string.before)
    val afterLabel = stringResource(R.string.after)
    val zoomModifier = if (enableZoom) {
        Modifier
            .pointerInput(beforeImage) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    transform.applyGesture(
                        centroid,
                        pan,
                        zoom,
                        Size(size.width.toFloat(), size.height.toFloat()),
                        fittedContentSize(beforeImage, size)
                    )
                }
            }
            .pointerInput(beforeImage) {
                detectTapGestures(onDoubleTap = { tap ->
                    transform.doubleTap(
                        tap,
                        Size(size.width.toFloat(), size.height.toFloat()),
                        fittedContentSize(beforeImage, size)
                    )
                })
            }
    } else Modifier

    Box(modifier, Alignment.Center) {
        val backdrop = rememberLayerBackdrop {
            drawContent()
        }
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .then(zoomModifier)
            ) {
                containerSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                val split = size.width * sliderPosition
                drawHalf(beforeImage, 0f, split, transform.scale, transform.offset, checkerShader)
                drawHalf(
                    afterImage, split, size.width, transform.scale, transform.offset, checkerShader
                )
            }
            if (showLabels) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(0f, 0f, size.width * sliderPosition, size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }) {
                    ComparisonLabel(beforeLabel, Alignment.TopStart, labelPadding)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(size.width * sliderPosition, 0f, size.width, size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }) {
                    ComparisonLabel(afterLabel, Alignment.TopEnd, labelPadding)
                }
            }
        }
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
            ) {
                if (glassSlider) {
                    Box(
                        Modifier
                            .requiredHeight(with(density) { containerSize.height.toDp() + 32.dp })
                            .width(sliderLineWidth)
                            .offset(x = with(density) { sliderX.toDp() - sliderLineWidth / 2 })
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(50) },
                                effects = {
                                    blur(radius = 6.dp.toPx())
                                    lens(
                                        refractionHeight = 4.dp.toPx(),
                                        refractionAmount = 8.dp.toPx(),
                                        chromaticAberration = true
                                    )
                                })
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(sliderLineWidth)
                            .offset(x = with(density) { sliderX.toDp() - sliderLineWidth / 2 })
                            .background(sliderColor)
                    )
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .offset(x = with(density) { sliderX.toDp() - 32.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { HapticFeedbacks.gestureStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    sliderPosition =
                                        (sliderPosition + dragAmount.x / containerSize.width).coerceIn(
                                            0f, 1f
                                        )
                                })
                        }) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .then(
                                if (glassSlider) {
                                    Modifier.drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { CircleShape },
                                        effects = {
                                            lens(
                                                refractionHeight = 12.dp.toPx(),
                                                refractionAmount = 16.dp.toPx(),
                                                chromaticAberration = true
                                            )
                                        })
                                } else {
                                    Modifier
                                        .shadow(8.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(sliderColor)
                                }
                            )
                            .size(sliderHandleSize), contentAlignment = Alignment.Center
                    ) {
                        if (!glassSlider) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = stringResource(R.string.drag_to_compare),
                                tint = iconColor,
                                modifier = Modifier.size(sliderHandleSize * 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fittedContentSize(image: ImageBitmap, container: IntSize): Size {
    val fit =
        min(container.width / image.width.toFloat(), container.height / image.height.toFloat())
    return Size(image.width * fit, image.height * fit)
}

private fun DrawScope.drawHalf(
    image: ImageBitmap,
    clipLeft: Float,
    clipRight: Float,
    scale: Float,
    offset: Offset,
    checkerShader: ShaderBrush?
) {
    if (clipRight <= clipLeft) return
    val fit = min(size.width / image.width, size.height / image.height)
    val total = fit * scale
    val imgW = image.width * total
    val imgH = image.height * total
    val topLeft = Offset((size.width - imgW) / 2f, (size.height - imgH) / 2f) + offset

    val dstL = max(clipLeft, topLeft.x)
    val dstR = min(clipRight, topLeft.x + imgW)
    val dstT = max(0f, topLeft.y)
    val dstB = min(size.height, topLeft.y + imgH)
    if (dstR <= dstL || dstB <= dstT) return

    val srcL = floor((dstL - topLeft.x) / total).toInt().coerceIn(0, image.width - 1)
    val srcT = floor((dstT - topLeft.y) / total).toInt().coerceIn(0, image.height - 1)
    val srcR = ceil((dstR - topLeft.x) / total).toInt().coerceIn(srcL + 1, image.width)
    val srcB = ceil((dstB - topLeft.y) / total).toInt().coerceIn(srcT + 1, image.height)

    clipRect(dstL, dstT, dstR, dstB) {
        if (checkerShader != null) {
            drawRect(checkerShader, Offset(dstL, dstT), Size(dstR - dstL, dstB - dstT))
        }
        drawImage(
            image = image,
            srcOffset = IntOffset(srcL, srcT),
            srcSize = IntSize(srcR - srcL, srcB - srcT),
            dstOffset = IntOffset(
                (topLeft.x + srcL * total).roundToInt(), (topLeft.y + srcT * total).roundToInt()
            ),
            dstSize = IntSize(
                ((srcR - srcL) * total).roundToInt(), ((srcB - srcT) * total).roundToInt()
            ),
            filterQuality = if (total >= 3f) FilterQuality.None else FilterQuality.Low
        )
    }
}

@Composable
private fun ComparisonLabel(
    text: String, alignment: Alignment, padding: Dp, modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = alignment
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp))
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun calculateSliderColors(bitmap: Bitmap): Pair<Color, Color> {
    val luminances = mutableListOf<Int>()
    val cx = bitmap.width / 2
    val cy = bitmap.height / 2
    val sample = 10
    for (dy in -sample..sample) {
        for (dx in -sample..sample) {
            val x = (cx + dx).coerceIn(0, bitmap.width - 1)
            val y = (cy + dy).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap[x, y]
            val luminance = (red(pixel) + android.graphics.Color.green(pixel) + blue(
                pixel
            )) / 3
            luminances.add(luminance)
        }
    }
    val median = luminances.sorted()[luminances.size / 2]
    val inverted = 255 - median
    val sliderColor = if (kotlin.math.abs(inverted - median) < 30) {
        if (median > 127) Color.Black else Color.White
    } else {
        Color(inverted, inverted, inverted)
    }
    val sliderLuminance = (sliderColor.red + sliderColor.green + sliderColor.blue) / 3f
    val iconColor = if (sliderLuminance < 0.7f) Color.White else Color.Black
    return sliderColor to iconColor
}
