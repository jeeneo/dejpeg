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

package com.je.dejpeg.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow

data class Pref<T>(
    val key: Preferences.Key<*>,
    val default: T,
    val flow: (AppPreferences) -> Flow<T>,
    val set: suspend AppPreferences.(T) -> Unit
)

object Prefs {
    val CHUNK_SIZE = Pref(
        key = intPreferencesKey("chunk_size"),
        default = AppPreferences.DEFAULT_CHUNK_SIZE,
        flow = { it.chunkSize },
        set = { setChunkSize(it) }
    )

    val OVERLAP_SIZE = Pref(
        key = intPreferencesKey("overlap_size"),
        default = AppPreferences.DEFAULT_OVERLAP_SIZE,
        flow = { it.overlapSize },
        set = { setOverlapSize(it) }
    )

    val ONNX_DEVICE_THREADS = Pref(
        key = intPreferencesKey("onnx_device_threads"),
        default = AppPreferences.DEFAULT_ONNX_DEVICE_THREADS,
        flow = { it.onnxDeviceThreads },
        set = { setOnnxDeviceThreads(it) }
    )

    val GLOBAL_STRENGTH = Pref(
        key = floatPreferencesKey("global_strength"),
        default = AppPreferences.DEFAULT_GLOBAL_STRENGTH,
        flow = { it.globalStrength },
        set = { setGlobalStrength(it) }
    )

    val PROCESSING_MODE = Pref(
        key = stringPreferencesKey("processing_mode"),
        default = ProcessingMode.ONNX,
        flow = { it.processingMode },
        set = { setProcessingMode(it) }
    )

    val OIDN_HDR = Pref(
        key = booleanPreferencesKey("oidn_hdr"),
        default = false,
        flow = { it.oidnHdr },
        set = { setOidnHdr(it) }
    )

    val OIDN_SRGB = Pref(
        key = booleanPreferencesKey("oidn_srgb"),
        default = false,
        flow = { it.oidnSrgb },
        set = { setOidnSrgb(it) }
    )

    val OIDN_QUALITY = Pref(
        key = intPreferencesKey("oidn_quality"),
        default = AppPreferences.DEFAULT_OIDN_QUALITY,
        flow = { it.oidnQuality },
        set = { setOidnQuality(it) }
    )

    val OIDN_MAX_MEMORY_MB = Pref(
        key = intPreferencesKey("oidn_max_memory_mb"),
        default = AppPreferences.DEFAULT_OIDN_MAX_MEMORY_MB,
        flow = { it.oidnMaxMemoryMB },
        set = { setOidnMaxMemoryMB(it) }
    )

    val OIDN_NUM_THREADS = Pref(
        key = intPreferencesKey("oidn_num_threads"),
        default = AppPreferences.DEFAULT_OIDN_NUM_THREADS,
        flow = { it.oidnNumThreads },
        set = { setOidnNumThreads(it) }
    )

    val OIDN_INPUT_SCALE = Pref(
        key = floatPreferencesKey("oidn_input_scale"),
        default = AppPreferences.DEFAULT_OIDN_INPUT_SCALE,
        flow = { it.oidnInputScale },
        set = { setOidnInputScale(it) }
    )
}
