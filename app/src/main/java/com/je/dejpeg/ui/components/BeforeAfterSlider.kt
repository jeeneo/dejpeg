/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg.ui.components

import android.graphics.Bitmap
import android.graphics.Color.blue
import android.graphics.Color.red
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.je.dejpeg.R
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.ui.screens.rememberCheckerShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private class ComparisonTransform(val maxZoom: Float) {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    private val minRubberScale = Float.MIN_VALUE
    private val maxOverscrollPx = Float.MAX_VALUE
    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float, container: Size, content: Size) {
        val dampedZoom = if (zoom < 1f && scale <= 1f) zoom.toDouble().pow(0.4).toFloat() else zoom
        val newScale = (scale * dampedZoom).coerceIn(minRubberScale, maxZoom)
        val effectiveZoom = newScale / scale
        val center = Offset(container.width / 2f, container.height / 2f)
        val base = (offset + center - centroid) * effectiveZoom + centroid - center
        scale = newScale
        val max = maxOffset(newScale, container, content)
        offset = Offset(
            resistedAxis(base.x, pan.x, max.x), resistedAxis(base.y, pan.y, max.y)
        )
    }

    private fun resistedAxis(base: Float, pan: Float, max: Float): Float {
        val clamped = base.coerceIn(-max, max)
        val over = base - clamped
        val movingOutward = over != 0f && (over > 0f) == (pan > 0f)
        val movingOut = over == 0f && ((base >= max && pan > 0f) || (base <= -max && pan < 0f))
        val factor = when {
            movingOutward -> (1f - abs(over) / maxOverscrollPx).coerceIn(0f, 1f) * 0.5f
            movingOut -> 0.5f
            else -> 1f
        }
        return (base + pan * factor).coerceIn(-max - maxOverscrollPx, max + maxOverscrollPx)
    }

    suspend fun settle(container: Size, content: Size) {
        val startScale = scale
        val startOffset = offset
        val targetScale = scale.coerceIn(1f, maxZoom)
        val max = maxOffset(targetScale, container, content)
        val targetOffset = Offset(
            startOffset.x.coerceIn(-max.x, max.x), startOffset.y.coerceIn(-max.y, max.y)
        )
        if (startScale == targetScale && startOffset == targetOffset) return

        animate(
            0f, 1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium
            )
        ) { t, _ ->
            scale = startScale + (targetScale - startScale) * t
            offset = Offset(
                startOffset.x + (targetOffset.x - startOffset.x) * t,
                startOffset.y + (targetOffset.y - startOffset.y) * t
            )
        }
    }

    fun doubleTap(centroid: Offset, container: Size, content: Size) {
        if (scale > 1f) {
            scale = 1f; offset = Offset.Zero
        } else applyGesture(centroid, Offset.Zero, min(3f, maxZoom), container, content)
    }

    private fun maxOffset(s: Float, container: Size, content: Size) = Offset(
        max(0f, (content.width * s - container.width) / 2f),
        max(0f, (content.height * s - container.height) / 2f)
    )

    fun isOutOfBounds(container: Size, content: Size): Boolean {
        val targetScale = scale.coerceIn(1f, maxZoom)
        val max = maxOffset(targetScale, container, content)
        val eps = 0.5f
        return scale < 1f - 0.001f || abs(offset.x) > max.x + eps || abs(offset.y) > max.y + eps
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
    val sliderLineWidth: Dp = if (glassSlider) 12.dp else 6.dp
    val labelPadding: Dp = 24.dp
    val maxZoomFactor = Float.MAX_VALUE
    val transform = remember(maxZoomFactor) { ComparisonTransform(maxZoomFactor) }
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val (sliderColor, iconColor) = remember(beforeBitmap) { calculateSliderColors(beforeBitmap) }
    val beforeLabel = stringResource(R.string.before)
    val afterLabel = stringResource(R.string.after)
    val scope = rememberCoroutineScope()
    val zoomModifier = Modifier
        .pointerInput(beforeImage) {
            var settleJob: Job? = null
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                settleJob?.cancel()
                do {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    if (!canceled) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (zoom != 1f || pan != Offset.Zero) {
                            transform.applyGesture(
                                centroid,
                                pan,
                                zoom,
                                Size(size.width.toFloat(), size.height.toFloat()),
                                fittedContentSize(beforeImage, size)
                            )
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (!canceled && event.changes.any { it.pressed })
                val container = Size(size.width.toFloat(), size.height.toFloat())
                val content = fittedContentSize(beforeImage, size)
                if (transform.isOutOfBounds(container, content)) HapticPatterns.tap()
                settleJob = scope.launch {
                    transform.settle(container, content)
                }
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
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            val handleShape = RoundedCornerShape(50)

            // trackbar
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
                                    blur(8f.dp.toPx())
                                    lens(4f.dp.toPx(), 8f.dp.toPx(), true)
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

                // round center handles
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .offset(x = with(density) { sliderX.toDp() - 32.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { HapticPatterns.tap() },
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
                                        shape = { handleShape },
                                        effects = {
                                            blur(4f.dp.toPx())
                                            lens(16f.dp.toPx(), 24f.dp.toPx(), true)
                                        })
                                } else {
                                    Modifier.clip(CircleShape).background(sliderColor)
                                }
                            )
                            .size(
                                width = if (glassSlider) 64.dp else 48.dp,
                                height = if (glassSlider) 44.dp else 48.dp
                            ), contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                contentDescription = stringResource(R.string.drag_to_compare),
                                tint = iconColor,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = stringResource(R.string.drag_to_compare),
                                tint = iconColor,
                                modifier = Modifier.align(Alignment.CenterEnd)
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
    val sliderColor = if (abs(inverted - median) < 30) {
        if (median > 127) Color.Black else Color.White
    } else {
        Color(inverted, inverted, inverted)
    }
    val sliderLuminance = (sliderColor.red + sliderColor.green + sliderColor.blue) / 3f
    val iconColor = if (sliderLuminance < 0.7f) Color.White else Color.Black
    return sliderColor to iconColor
}
