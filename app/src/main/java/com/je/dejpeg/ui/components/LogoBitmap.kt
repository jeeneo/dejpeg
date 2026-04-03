package com.je.dejpeg.ui.components

import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import com.je.dejpeg.R

@Composable
fun rememberThemedLogoBitmap(sizePx: Int = 256): ImageBitmap {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val (bgColor, fgColor) = if (isDark) {
        colorScheme.surface to colorScheme.primary
    } else {
        colorScheme.inverseSurface to colorScheme.primaryContainer
    }
    return remember(bgColor, fgColor, sizePx) {
        rasterizeThemedLogo(
            context = context,
            sizePx = sizePx,
            bgArgb = bgColor.toArgb(),
            fgArgb = fgColor.toArgb(),
        )
    }
}

fun rasterizeThemedLogo(
    context: android.content.Context,
    sizePx: Int,
    bgArgb: Int,
    fgArgb: Int,
): ImageBitmap {
    val bgDrawable = requireNotNull(
        AppCompatResources.getDrawable(context, R.drawable.dejpeg_logo_curved_bg)?.mutate()
    )
    val fgDrawable = requireNotNull(
        AppCompatResources.getDrawable(context, R.drawable.dejpeg_logo_curved_fg)?.mutate()
    )
    DrawableCompat.setTint(bgDrawable, bgArgb)
    DrawableCompat.setTint(fgDrawable, fgArgb)
    val outBmp = createBitmap(sizePx, sizePx)
    Canvas(outBmp).also { canvas ->
        bgDrawable.setBounds(0, 0, sizePx, sizePx)
        fgDrawable.setBounds(0, 0, sizePx, sizePx)
        bgDrawable.draw(canvas)
        fgDrawable.draw(canvas)
    }
    return outBmp.asImageBitmap()
}
