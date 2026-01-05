package com.je.dejpeg.compose.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.je.dejpeg.data.AppPreferences

object HapticFeedback {

    fun light(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    fun medium(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
    fun heavy(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    fun error(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    fun gestureStart(view: View, isEnabled: Boolean) {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedbackPerformer {
    val view = LocalView.current
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val isEnabled by appPreferences.hapticFeedbackEnabled.collectAsState(initial = true)
    return remember(view, isEnabled) { HapticFeedbackPerformer(view, isEnabled) }
}

class HapticFeedbackPerformer(private val view: View, private val isEnabled: Boolean) {
    fun light() = HapticFeedback.light(view, isEnabled)
    fun medium() = HapticFeedback.medium(view, isEnabled)
    fun heavy() = HapticFeedback.heavy(view, isEnabled)
    fun error() = HapticFeedback.error(view, isEnabled)
    fun gestureStart() = HapticFeedback.gestureStart(view, isEnabled)
}
