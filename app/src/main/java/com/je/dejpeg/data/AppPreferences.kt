package com.je.dejpeg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object PreferenceKeys {
    val SKIP_SAVE_DIALOG = booleanPreferencesKey("skipSaveDialog")
    val DEFAULT_IMAGE_SOURCE = stringPreferencesKey("defaultImageSource")
    val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("hapticFeedbackEnabled")
    val COMPAT_MODEL_CLEANUP = booleanPreferencesKey("compatModelCleanup")
    val COMPAT_BRISQUE_CLEANUP = booleanPreferencesKey("compatBrisqueCleanup")
    val STARTER_MODEL_EXTRACTED = booleanPreferencesKey("starterModelExtracted")
    val ACTIVE_MODEL = stringPreferencesKey("activeModel")
    val CURRENT_PROCESSING_MODEL = stringPreferencesKey("current_processing_model")
    val CHUNK_SIZE = intPreferencesKey("chunk_size")
    val OVERLAP_SIZE = intPreferencesKey("overlap_size")
    val GLOBAL_STRENGTH = floatPreferencesKey("global_strength")
    val BRISQUE_COARSE_STEP = intPreferencesKey("brisque_coarse_step")
    val BRISQUE_FINE_STEP = intPreferencesKey("brisque_fine_step")
    val BRISQUE_FINE_RANGE = intPreferencesKey("brisque_fine_range")
    val BRISQUE_MIN_WIDTH_RATIO = floatPreferencesKey("brisque_min_width_ratio")
    val BRISQUE_WEIGHT = floatPreferencesKey("brisque_weight")
    val BRISQUE_SHARPNESS_WEIGHT = floatPreferencesKey("brisque_sharpness_weight")
}

data class BrisqueSettings(
    val coarseStep: Int = DEFAULT_BRISQUE_COARSE_STEP,
    val fineStep: Int = DEFAULT_BRISQUE_FINE_STEP,
    val fineRange: Int = DEFAULT_BRISQUE_FINE_RANGE,
    val minWidthRatio: Float = DEFAULT_BRISQUE_MIN_WIDTH_RATIO,
    val brisqueWeight: Float = DEFAULT_BRISQUE_WEIGHT,
    val sharpnessWeight: Float = DEFAULT_BRISQUE_SHARPNESS_WEIGHT
)

private const val DEFAULT_BRISQUE_COARSE_STEP = 20
private const val DEFAULT_BRISQUE_FINE_STEP = 5
private const val DEFAULT_BRISQUE_FINE_RANGE = 30
private const val DEFAULT_BRISQUE_MIN_WIDTH_RATIO = 0.5f
private const val DEFAULT_BRISQUE_WEIGHT = 0.7f
private const val DEFAULT_BRISQUE_SHARPNESS_WEIGHT = 0.3f

class AppPreferences(private val context: Context) {

    companion object {
        const val DEFAULT_CHUNK_SIZE = 512
        const val DEFAULT_OVERLAP_SIZE = 16
        const val DEFAULT_GLOBAL_STRENGTH = 50f
    }

    val skipSaveDialog: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SKIP_SAVE_DIALOG] ?: false
    }

    val defaultImageSource: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.DEFAULT_IMAGE_SOURCE]
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    val compatModelCleanup: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.COMPAT_MODEL_CLEANUP] ?: false
    }

    val compatBrisqueCleanup: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.COMPAT_BRISQUE_CLEANUP] ?: false
    }

    val chunkSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.CHUNK_SIZE] ?: DEFAULT_CHUNK_SIZE
    }

    val overlapSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.OVERLAP_SIZE] ?: DEFAULT_OVERLAP_SIZE
    }

    val globalStrength: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.GLOBAL_STRENGTH] ?: DEFAULT_GLOBAL_STRENGTH
    }

    val activeModel: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_MODEL]
    }

    val currentProcessingModel: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.CURRENT_PROCESSING_MODEL]
    }

    val starterModelExtracted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] ?: false
    }

    val brisqueSettings: Flow<BrisqueSettings> = context.dataStore.data.map { prefs ->
        BrisqueSettings(
            coarseStep = prefs[PreferenceKeys.BRISQUE_COARSE_STEP] ?: DEFAULT_BRISQUE_COARSE_STEP,
            fineStep = prefs[PreferenceKeys.BRISQUE_FINE_STEP] ?: DEFAULT_BRISQUE_FINE_STEP,
            fineRange = prefs[PreferenceKeys.BRISQUE_FINE_RANGE] ?: DEFAULT_BRISQUE_FINE_RANGE,
            minWidthRatio = prefs[PreferenceKeys.BRISQUE_MIN_WIDTH_RATIO] ?: DEFAULT_BRISQUE_MIN_WIDTH_RATIO,
            brisqueWeight = prefs[PreferenceKeys.BRISQUE_WEIGHT] ?: DEFAULT_BRISQUE_WEIGHT,
            sharpnessWeight = prefs[PreferenceKeys.BRISQUE_SHARPNESS_WEIGHT] ?: DEFAULT_BRISQUE_SHARPNESS_WEIGHT
        )
    }

    suspend fun setSkipSaveDialog(skip: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SKIP_SAVE_DIALOG] = skip
        }
    }

    suspend fun setDefaultImageSource(source: String?) {
        context.dataStore.edit { prefs ->
            if (source == null) {
                prefs.remove(PreferenceKeys.DEFAULT_IMAGE_SOURCE)
            } else {
                prefs[PreferenceKeys.DEFAULT_IMAGE_SOURCE] = source
            }
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun clearDefaultImageSource() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.DEFAULT_IMAGE_SOURCE)
        }
    }

    suspend fun setCompatModelCleanup(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.COMPAT_MODEL_CLEANUP] = completed
        }
    }

    suspend fun getCompatModelCleanupImmediate(): Boolean = compatModelCleanup.first()

    suspend fun setCompatBrisqueCleanup(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.COMPAT_BRISQUE_CLEANUP] = completed
        }
    }

    suspend fun getCompatBrisqueCleanupImmediate(): Boolean = compatBrisqueCleanup.first()

    suspend fun setChunkSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.CHUNK_SIZE] = size
        }
    }

    suspend fun setOverlapSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.OVERLAP_SIZE] = size
        }
    }

    suspend fun setGlobalStrength(strength: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.GLOBAL_STRENGTH] = strength
        }
    }

    suspend fun setBrisqueSettings(settings: BrisqueSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.BRISQUE_COARSE_STEP] = settings.coarseStep
            prefs[PreferenceKeys.BRISQUE_FINE_STEP] = settings.fineStep
            prefs[PreferenceKeys.BRISQUE_FINE_RANGE] = settings.fineRange
            prefs[PreferenceKeys.BRISQUE_MIN_WIDTH_RATIO] = settings.minWidthRatio
            prefs[PreferenceKeys.BRISQUE_WEIGHT] = settings.brisqueWeight
            prefs[PreferenceKeys.BRISQUE_SHARPNESS_WEIGHT] = settings.sharpnessWeight
        }
    }

    suspend fun getChunkSizeImmediate(): Int = chunkSize.first()
    suspend fun getOverlapSizeImmediate(): Int = overlapSize.first()

    suspend fun setActiveModel(modelName: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACTIVE_MODEL] = modelName
        }
    }

    suspend fun clearActiveModel() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.ACTIVE_MODEL)
        }
    }

    suspend fun setCurrentProcessingModel(modelName: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.CURRENT_PROCESSING_MODEL] = modelName
        }
    }

    suspend fun getActiveModel(): String? = activeModel.first()

    suspend fun setStarterModelExtracted(extracted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.STARTER_MODEL_EXTRACTED] = extracted
        }
    }

    suspend fun getStarterModelExtractedImmediate(): Boolean = starterModelExtracted.first()
}
