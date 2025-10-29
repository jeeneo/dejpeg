package com.je.dejpeg.ui.utils

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

object HapticFeedback {
    private const val PREFS_NAME = "AppPrefs"
    private const val PREF_HAPTIC_ENABLED = "hapticFeedbackEnabled"

    private fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_HAPTIC_ENABLED, true)
    }

    fun light(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun medium(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun heavy(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun click(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun longPress(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun reject(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun confirm(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun error(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun success(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun gestureStart(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun gestureEnd(view: View, context: Context) {
        if (!isEnabled(context)) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedbackPerformer {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { HapticFeedbackPerformer(view, context) }
}

class HapticFeedbackPerformer(private val view: View, private val context: Context) {
    fun light() = HapticFeedback.light(view, context)
    fun medium() = HapticFeedback.medium(view, context)
    fun heavy() = HapticFeedback.heavy(view, context)
    fun click() = HapticFeedback.click(view, context)
    fun longPress() = HapticFeedback.longPress(view, context)
    fun reject() = HapticFeedback.reject(view, context)
    fun confirm() = HapticFeedback.confirm(view, context)
    fun error() = HapticFeedback.error(view, context)
    fun success() = HapticFeedback.success(view, context)
    fun gestureStart() = HapticFeedback.gestureStart(view, context)
    fun gestureEnd() = HapticFeedback.gestureEnd(view, context)
}
