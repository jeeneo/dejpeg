package com.je.dejpeg.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.je.dejpeg.R

@Composable
fun rememberThemedLogoBitmap(sizePx: Int = 256): ImageBitmap {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.surface
    val fgColor = MaterialTheme.colorScheme.primary
    return remember(bgColor, fgColor, sizePx) {
        rasterizeThemedLogo(
            context = context,
            drawableRes = R.drawable.dejpeg_logo_curved,
            sizePx = sizePx,
            bgArgb = bgColor.toArgb(),
            fgArgb = fgColor.toArgb(),
        )
    }
}

fun rasterizeThemedLogo(
    context: android.content.Context,
    drawableRes: Int,
    sizePx: Int,
    bgArgb: Int,
    fgArgb: Int,
): ImageBitmap {
    val srcBmp = createBitmap(sizePx, sizePx)
    Canvas(srcBmp).also { canvas ->
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val drawable = requireNotNull(ContextCompat.getDrawable(context, drawableRes)?.mutate())
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
    }
    val pixels = IntArray(sizePx * sizePx)
    srcBmp.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    srcBmp.recycle()
    val bgR = Color.red(bgArgb)
    val bgG = Color.green(bgArgb)
    val bgB = Color.blue(bgArgb)
    val fgR = Color.red(fgArgb)
    val fgG = Color.green(fgArgb)
    val fgB = Color.blue(fgArgb)
    val thresholdLow = 50f
    val thresholdHigh = 110f
    val invRange = 1f / (thresholdHigh - thresholdLow)
    for (i in pixels.indices) {
        val px = pixels[i]
        val a = Color.alpha(px)
        if (a == 0) continue
        val lum = 0.3f * Color.red(px) + 0.6f * Color.green(px) + 0.1f * Color.blue(px)
        val t = ((lum - thresholdLow) * invRange).coerceIn(0f, 1f)
        val smoothT = t * t * (3f - 2f * t)
        val r = (bgR + smoothT * (fgR - bgR)).toInt()
        val g = (bgG + smoothT * (fgG - bgG)).toInt()
        val b = (bgB + smoothT * (fgB - bgB)).toInt()
        pixels[i] = Color.argb(a, r, g, b)
    }
    val outBmp = createBitmap(sizePx, sizePx)
    outBmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return outBmp.asImageBitmap()
}
