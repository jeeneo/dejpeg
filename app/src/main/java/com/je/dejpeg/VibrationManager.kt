package com.je.dejpeg

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

class VibrationManager(private val context: Context) {
    
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        private const val LIGHT_VIBRATION_DURATION = 10L
        private const val MEDIUM_VIBRATION_DURATION = 25L
        private const val BUTTON_VIBRATION_DURATION = 5L
        private const val TOUCH_VIBRATION_DURATION = 3L
        private const val DIALOG_CHOICE_DURATION = 8L
        
        private val SUCCESS_PATTERN = longArrayOf(0, 10, 50, 10)
        private val ERROR_PATTERN = longArrayOf(0, 25, 25, 25)
        private val SINGLE_SUCCESS_PATTERN = longArrayOf(0, 10)
    }
    
    fun vibrateButton() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(BUTTON_VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(BUTTON_VIBRATION_DURATION)
            }
        }
    }
    
    fun vibrateSuccess() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(SUCCESS_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(SUCCESS_PATTERN, -1)
            }
        }
    }
    
    fun vibrateSingleSuccess() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(SINGLE_SUCCESS_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(SINGLE_SUCCESS_PATTERN, -1)
            }
        }
    }
    
    fun vibrateError() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(ERROR_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(ERROR_PATTERN, -1)
            }
        }
    }
    
    fun vibrateSliderChange() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(BUTTON_VIBRATION_DURATION, VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(BUTTON_VIBRATION_DURATION)
            }
        }
    }
    
    fun vibrateMenuTap() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(LIGHT_VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(LIGHT_VIBRATION_DURATION)
            }
        }
    }
    
    fun vibrateSliderTouch() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(TOUCH_VIBRATION_DURATION, VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(TOUCH_VIBRATION_DURATION)
            }
        }
    }

    fun vibrateDialogChoice() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(DIALOG_CHOICE_DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(DIALOG_CHOICE_DURATION)
            }
        }
    }

    fun vibrateModelDelete() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(20L)
            }
        }
    }
    
    fun performHapticFeedback(view: View, feedbackConstant: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        view.performHapticFeedback(feedbackConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
}