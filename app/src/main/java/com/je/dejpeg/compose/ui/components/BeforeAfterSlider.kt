/**
* Copyright (C) 2026 dryerlint <codeberg.org/dryerlint>
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
*
*/

/*
* Also please don't steal my work and claim it as your own, thanks.
*/

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.je.dejpeg.R
import com.je.dejpeg.compose.utils.HapticFeedbackPerformer
import com.je.dejpeg.compose.utils.rememberHapticFeedback
import com.je.dejpeg.data.AppPreferences
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@Composable
fun BeforeAfterSlider(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    enableZoom: Boolean = true,
    sliderHandleSize: Dp = 48.dp,
    sliderLineWidth: Dp = 3.dp,
    labelPadding: Dp = 24.dp,
    maxZoomFactor: Float = 20f
) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val isHapticEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    
    val zoomableState = if (enableZoom) {
        rememberZoomableState(
            ZoomSpec(
                maximum = ZoomLimit(factor = maxZoomFactor, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled),
                minimum = ZoomLimit(factor = 1f, overzoomEffect = if (isHapticEnabled) OverzoomEffect.RubberBanding else OverzoomEffect.Disabled)
            )
        )
    } else null
    
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val (sliderColor, iconColor) = remember(beforeBitmap) {
        calculateSliderColors(beforeBitmap)
    }

    val beforeLabel = stringResource(R.string.before)
    val afterLabel = stringResource(R.string.after)

    Box(
        modifier.onGloballyPositioned { containerSize = it.size },
        Alignment.Center
    ) {
        ImageHalf(
            bitmap = beforeBitmap,
            clipRange = 0f to sliderPosition,
            label = if (showLabels) beforeLabel else null,
            labelAlignment = Alignment.TopStart,
            labelPadding = labelPadding,
            zoomableModifier = if (zoomableState != null) Modifier.zoomable(zoomableState) else Modifier
        )
        
        ImageHalf(
            bitmap = afterBitmap,
            clipRange = sliderPosition to 1f,
            label = if (showLabels) afterLabel else null,
            labelAlignment = Alignment.TopEnd,
            labelPadding = labelPadding,
            zoomableModifier = if (zoomableState != null) Modifier.zoomable(zoomableState) else Modifier
        )
        
        if (containerSize.width > 0) {
            val sliderX = containerSize.width * sliderPosition
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(sliderLineWidth)
                        .offset(x = with(density) { sliderX.toDp() - sliderLineWidth / 2 })
                        .background(sliderColor)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(64.dp)
                        .offset(x = with(density) { sliderX.toDp() - 32.dp })
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { haptic.gestureStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    sliderPosition = (sliderPosition + dragAmount.x / containerSize.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {}
                            )
                        }
                ) {
                    Box(
                        Modifier
                            .size(sliderHandleSize)
                            .align(Alignment.Center)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(sliderColor),
                        Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Drag to compare",
                            tint = iconColor,
                            modifier = Modifier.size(sliderHandleSize * 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactBeforeAfterSlider(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    enableZoom: Boolean = true
) {
    BeforeAfterSlider(
        beforeBitmap = beforeBitmap,
        afterBitmap = afterBitmap,
        modifier = modifier,
        showLabels = showLabels,
        enableZoom = enableZoom,
        sliderHandleSize = 36.dp,
        sliderLineWidth = 2.dp,
        labelPadding = 8.dp,
        maxZoomFactor = 10f
    )
}

@Composable
private fun ImageHalf(
    bitmap: Bitmap,
    clipRange: Pair<Float, Float>,
    label: String?,
    labelAlignment: Alignment,
    labelPadding: Dp,
    zoomableModifier: Modifier
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                clipRect(
                    size.width * clipRange.first,
                    0f,
                    size.width * clipRange.second,
                    size.height
                ) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(zoomableModifier),
            Alignment.Center
        ) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
        }
        
        if (label != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(labelPadding),
                contentAlignment = labelAlignment
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        label,
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
            val luminance = (android.graphics.Color.red(pixel) + 
                           android.graphics.Color.green(pixel) + 
                           android.graphics.Color.blue(pixel)) / 3
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
