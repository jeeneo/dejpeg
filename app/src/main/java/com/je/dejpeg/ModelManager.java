package com.je.dejpeg;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.provider.OpenableColumns;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

import ai.onnxruntime.*;

import com.je.dejpeg.utils.VibrationManager;

/**
 * ModelManager handles the import, validation, and management of ONNX models.
 * 
 * FEATURES:
 * - Hash-based model validation
 * - Model-specific import warnings
 * - Progress tracking during import
 * - Automatic model optimization settings
 * 
 * MODEL WARNING SYSTEM:
 * The system automatically shows specific warning dialogs for certain models based on their hash.
 * To add a new model warning:
 * 1. Add the model hash to MODEL_HASHES
 * 2. Add a corresponding ModelWarning entry to MODEL_WARNINGS
 * 3. The warning will automatically appear when that model is imported
 * 
 * Example warning types:
 * - Experimental/beta models
 * - Hardware requirement warnings
 * - Memory usage warnings
 * - Compatibility warnings
 * - Performance warnings
 * 
 * The warning system integrates with DialogManager to show user-friendly dialogs
 * that allow users to proceed or cancel the import based on the warning content.
 */
public class ModelManager {
    private static final String PREFS_NAME = "ModelPrefs";
    private static final String ACTIVE_MODEL_KEY = "activeModel";

    private static final List<String> VALID_MODELS = Arrays.asList(
        "fbcnn_color.onnx", "fbcnn_gray.onnx", "fbcnn_gray_double.onnx",
        "scunet_color_real_gan.onnx", "scunet_color_real_psnr.onnx",
        "scunet_gray_15.onnx", "scunet_gray_25.onnx", "scunet_gray_50.onnx"
    );

    private static final Map<String, String> MODEL_HASHES = Map.ofEntries(
        Map.entry("fbcnn_color.onnx", "3bb0ff3060c217d3b3af95615157fca8a65506455cf4e3d88479e09efffec97f"),
        Map.entry("fbcnn_gray.onnx", "041b360fc681ae4b134e7ec98da1ae4c7ea57435e5abe701530d5a995a7a27b3"),
        Map.entry("fbcnn_gray_double.onnx", "83aca9febba0da828dbb5cc6e23e328f60f5ad07fa3de617ab1030f0a24d4f67"),
        Map.entry("scunet_color_real_gan.onnx", "5eb9a8015cf24477980d3a4eec2e35107c470703b98d257f7560cd3cf3f02922"),
        Map.entry("scunet_color_real_psnr.onnx", "341eb061ed4d7834dbe6cdab3fb509c887f82aa29be8819c7d09b3d9bfa4892d"),
        Map.entry("scunet_gray_15.onnx", "10d33552b5754ab9df018cb119e20e1f2b18546eff8e28954529a51e5a6ae255"),
        Map.entry("scunet_gray_25.onnx", "01b5838a85822ae21880062106a80078f06e7a82aa2ffc8847e32f4462b4c928"),
        Map.entry("scunet_gray_50.onnx", "a8d9cbbbb2696ac116a87a5055496291939ed873fe28d7f560373675bb970833"),
        Map.entry("1x_DitherDeleterV3-Smooth-32._115000_G.onnx", "4d36e4e33ac49d46472fe77b232923c1731094591a7b5646326698be851c80d7"),
        Map.entry("1x_Bandage-Smooth-64._105000_G.onnx", "ff04b61a9c19508bfa70431dbffc89e218ab0063de31396e5ce9ac9a2f117d20")
    );

    private static final Map<String, ModelWarning> MODEL_WARNINGS = Map.ofEntries(
        Map.entry("1x_DitherDeleterV3-Smooth-32._115000_G.onnx", new ModelWarning(
            "performance warning",
            "DitherDeleterV3 is resource-intensive and not recommended, it will take a long time to process images over 500px on even high-end devices",
            "import anyway",
            "cancel"
        )),
        Map.entry("1x_Bandage-Smooth-64._105000_G.onnx", new ModelWarning(
            "performance warning",
            "Bandage-Smooth is resource-intensive and not recommended, it will take a long time to process images over 500px on even high-end devices",
            "import anyway",
            "cancel"
        ))
    );
    
    public static class ModelWarning {
        public final String title;
        public final String message;
        public final String positiveButtonText;
        public final String negativeButtonText;
        
        public ModelWarning(String title, String message, String positiveButtonText, String negativeButtonText) {
            this.title = title;
            this.message = message;
            this.positiveButtonText = positiveButtonText;
            this.negativeButtonText = negativeButtonText;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private OrtSession currentSession;
    private OrtEnvironment ortEnv;
    private final VibrationManager vibrationManager;

    public ModelManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.vibrationManager = new VibrationManager(context);
    }

    public boolean hasActiveModel() {
        String activeModel = getActiveModelName();
        return activeModel != null && new File(context.getFilesDir(), activeModel).exists();
    }

    public void unloadModel() {
        closeResource(currentSession, "session");
        currentSession = null;
        closeResource(ortEnv, "environment");
        ortEnv = null;
        System.gc();
    }

    private void closeResource(AutoCloseable resource, String name) {
        if (resource != null) {
            try { resource.close(); } 
            catch (Exception e) { Log.e("ModelManager", "error closing " + name + ": " + e.getMessage()); }
        }
    }

    public OrtSession loadModel() throws Exception {
        unloadModel();
        String activeModel = getActiveModelName();
        if (activeModel == null) throw new Exception("No active model set");

        File modelFile = new File(context.getFilesDir(), activeModel);
        if (!modelFile.exists()) throw new Exception("Model file not found");

        if (ortEnv == null) ortEnv = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        configureSessionOptions(opts, activeModel);
        currentSession = ortEnv.createSession(modelFile.getAbsolutePath(), opts);

        prefs.edit().putString("current_processing_model", activeModel).apply();
        return currentSession;
    }

    private void configureSessionOptions(OrtSession.SessionOptions opts, String modelName) {
        int processors = Runtime.getRuntime().availableProcessors();
        try {
            opts.setIntraOpNumThreads(processors <= 2 ? 1 : (processors * 3) / 4);
        } catch (OrtException e) {
            Log.e("ModelManager", "Error setting IntraOpNumThreads: " + e.getMessage());
        }
        try {
            opts.setInterOpNumThreads(4);
        } catch (OrtException e) {
            Log.e("ModelManager", "Error setting InterOpNumThreads: " + e.getMessage());
        }
        try {
            if (modelName.startsWith("fbcnn_")) {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            }
            if (modelName.startsWith("scunet_")) {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
            }
        } catch (OrtException e) {
            Log.e("ModelManager", "Error setting OptimizationLevel: " + e.getMessage());
        }
    }

    public List<String> getInstalledModels() {
        File[] files = context.getFilesDir().listFiles((dir, name) -> name.toLowerCase().endsWith(".onnx"));
        List<String> models = new ArrayList<>();
        if (files != null) for (File f : files) models.add(f.getName());
        return models;
    }

    public static class ResolveResult {
        public final String matchedModel, expectedHash, actualHash, filename;
        public final boolean hashMatches;
        public final ModelWarning modelWarning;
        
        public ResolveResult(String m, boolean h, String e, String a, String f) {
            matchedModel = m; 
            hashMatches = h; 
            expectedHash = e; 
            actualHash = a; 
            filename = f;
            modelWarning = m != null ? MODEL_WARNINGS.get(m) : null;
        }
    }

    public ResolveResult resolveHashOnly(Uri modelUri) throws Exception {
        String filename = resolveFilename(modelUri);
        String actualHash = computeFileHash(modelUri);
        for (var entry : MODEL_HASHES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(actualHash))
                return new ResolveResult(entry.getKey(), true, entry.getValue(), actualHash, filename);
        }
        return new ResolveResult(null, false, null, actualHash, filename);
    }

    private String resolveFilename(Uri uri) throws Exception {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx).trim();
            }
        }
        String path = uri.getPath();
        if (path != null && path.contains("/")) return path.substring(path.lastIndexOf('/') + 1).trim();
        String lastSeg = uri.getLastPathSegment();
        if (lastSeg != null) return lastSeg.trim();
        throw new Exception("Could not determine filename");
    }

    private String computeFileHash(Uri fileUri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            for (int r; (r = is.read(buf)) != -1;) digest.update(buf, 0, r);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        }
    }

    public boolean importModel(Uri modelUri, boolean force) throws Exception {
        return importModel(modelUri, null, force);
    }

    public boolean importModel(Uri modelUri, ModelCallback callback, boolean force) throws Exception {
        ResolveResult result;
        try { result = resolveHashOnly(modelUri); }
        catch (Exception e) {
            if (callback != null && !force) {
                callback.onError("GENERIC_MODEL_WARNING");
                return false;
            }
            return importModelInternal(modelUri, uriLastName(modelUri), callback);
        }

        if (result.modelWarning != null && !force) {
            if (callback != null) callback.onError(
                result.matchedModel + ":" +
                result.modelWarning.title + ":" +
                result.modelWarning.message + ":" +
                result.modelWarning.positiveButtonText + ":" +
                result.modelWarning.negativeButtonText
            );
            return false;
        }
        if (result.matchedModel == null && !force) {
            if (callback != null) {
                callback.onError("GENERIC_MODEL_WARNING");
                return false;
            }
        }

        return importModelInternal(modelUri, result.filename, callback);
    }

    private String uriLastName(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        if (name == null) throw new Exception("Could not determine filename");
        return name;
    }

    private boolean importModelInternal(Uri uri, String filename, ModelCallback callback) throws Exception {
        File modelFile = new File(context.getFilesDir(), filename);
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (callback != null) {
                long size = context.getContentResolver().openFileDescriptor(uri, "r").getStatSize();
                copyWithProgress(is, modelFile, size, callback);
            } else copyWithoutProgress(is, modelFile);
        }
        if (!hasActiveModel()) setActiveModel(filename);
        if (callback != null) callback.onSuccess(filename);
        Log.d("ModelManager", "Successfully imported model: " + filename);
        return true;
    }

    private void copyWithProgress(InputStream is, File output, long size, ModelCallback callback) throws IOException {
        try (FileOutputStream os = new FileOutputStream(output)) {
            byte[] buf = new byte[8192];
            long total = 0;
            for (int r; (r = is.read(buf)) != -1;) {
                os.write(buf, 0, r);
                total += r;
                callback.onProgress((int)((total * 100) / size));
            }
        }
    }

    private void copyWithoutProgress(InputStream is, File output) throws IOException {
        try (FileOutputStream os = new FileOutputStream(output)) {
            byte[] buf = new byte[8192];
            for (int r; (r = is.read(buf)) != -1;) os.write(buf, 0, r);
        }
    }

    public void deleteModel(String modelName, ModelDeleteCallback callback) {
        File modelFile = new File(context.getFilesDir(), modelName);
        if (modelFile.exists()) {
            modelFile.delete();
            vibrationManager.vibrateModelDelete();
            if (callback != null) callback.onModelDeleted(modelName);
        }
        if (modelName.equals(getActiveModelName())) {
            List<String> remaining = getInstalledModels();
            if (!remaining.isEmpty()) setActiveModel(remaining.get(0));
            else prefs.edit().remove(ACTIVE_MODEL_KEY).apply();
        }
    }

    public void setActiveModel(String modelName) {
        prefs.edit().putString(ACTIVE_MODEL_KEY, modelName).apply();
        unloadModel();
    }

    public String getActiveModelName() {
        return prefs.getString(ACTIVE_MODEL_KEY, null);
    }

    public boolean isColorModel(String modelName) { return modelName != null && modelName.contains("color"); }
    public boolean isKnownModel(String modelName) { return modelName != null && VALID_MODELS.contains(modelName); }

    // /**
    //  * Check if a specific model has import warnings
    //  */
    // public boolean hasModelWarning(String modelName) {
    //     return modelName != null && MODEL_WARNINGS.containsKey(modelName);
    // }

    /**
    //  * Get the warning information for a specific model
    //  */
    // public ModelWarning getModelWarning(String modelName) {
    //     return modelName != null ? MODEL_WARNINGS.get(modelName) : null;
    // }

    // /**
    //  * Get all models that have warnings
    //  */
    // public Set<String> getModelsWithWarnings() {
    //     return MODEL_WARNINGS.keySet();
    // }

    // /**
    //  * Add a new model warning (useful for runtime configuration)
    //  */
    // public void addModelWarning(String modelName, ModelWarning warning) {
    //     Log.d("ModelManager", "Would add warning for model: " + modelName);
    // }

    public interface ModelCallback { void onSuccess(String modelName); void onError(String error); void onProgress(int progress); }
    public interface ModelDeleteCallback { void onModelDeleted(String modelName); }
}