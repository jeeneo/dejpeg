package com.je.dejpeg;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.provider.OpenableColumns;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;

import com.je.dejpeg.utils.VibrationManager;

public class ModelManager {
    private static final String PREFS_NAME = "ModelPrefs";
    private static final String ACTIVE_MODEL_KEY = "activeModel";
    private static final List<String> VALID_MODELS = Arrays.asList(
            "fbcnn_color.onnx",
            "fbcnn_gray.onnx",
            "fbcnn_gray_double.onnx",
            "scunet_color_real_gan.onnx",
            "scunet_color_real_psnr.onnx",
            "scunet_gray_15.onnx",
            "scunet_gray_25.onnx",
            "scunet_gray_50.onnx"
    );
    private static final Map<String, String> MODEL_HASHES = new HashMap<>();
    static {
        MODEL_HASHES.put("fbcnn_color.onnx", "3bb0ff3060c217d3b3af95615157fca8a65506455cf4e3d88479e09efffec97f");
        MODEL_HASHES.put("fbcnn_gray.onnx", "041b360fc681ae4b134e7ec98da1ae4c7ea57435e5abe701530d5a995a7a27b3");
        MODEL_HASHES.put("fbcnn_gray_double.onnx", "83aca9febba0da828dbb5cc6e23e328f60f5ad07fa3de617ab1030f0a24d4f67");
        MODEL_HASHES.put("scunet_color_real_gan.onnx", "5eb9a8015cf24477980d3a4eec2e35107c470703b98d257f7560cd3cf3f02922");
        MODEL_HASHES.put("scunet_color_real_psnr.onnx", "341eb061ed4d7834dbe6cdab3fb509c887f82aa29be8819c7d09b3d9bfa4892d");
        MODEL_HASHES.put("scunet_gray_15.onnx", "10d33552b5754ab9df018cb119e20e1f2b18546eff8e28954529a51e5a6ae255");
        MODEL_HASHES.put("scunet_gray_25.onnx", "01b5838a85822ae21880062106a80078f06e7a82aa2ffc8847e32f4462b4c928");
        MODEL_HASHES.put("scunet_gray_50.onnx", "a8d9cbbbb2696ac116a87a5055496291939ed873fe28d7f560373675bb970833");
    }

    private final Context context;
    private final SharedPreferences prefs;
    private OrtSession currentSession = null;
    private OrtEnvironment ortEnv = null;
    private final VibrationManager vibrationManager;

    public ModelManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.vibrationManager = new VibrationManager(context);
    }

    public boolean hasActiveModel() {
        String activeModel = prefs.getString(ACTIVE_MODEL_KEY, null);
        if (activeModel == null) return false;
        return new File(context.getFilesDir(), activeModel).exists();
    }

    public void unloadModel() {
        if (currentSession != null) {
            try {
                currentSession.close();
            } catch (Exception e) {
                Log.e("ModelManager", "error closing session: " + e.getMessage());
            }
            currentSession = null;
        }
        if (ortEnv != null) {
            try {
                ortEnv.close();
            } catch (Exception e) {
                Log.e("ModelManager", "error closing environment: " + e.getMessage());
            }
            ortEnv = null;
        }
        System.gc();
    }

    public OrtSession loadModel() throws Exception {
        if (currentSession != null) {
            return currentSession;
        }
        String activeModel = prefs.getString(ACTIVE_MODEL_KEY, null);
        if (activeModel == null) throw new Exception("No active model set");
        File modelFile = new File(context.getFilesDir(), activeModel);
        if (!modelFile.exists()) throw new Exception("Model file not found");
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment();
        }

        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        if (activeModel.startsWith("scunet_")) {
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
        }

        currentSession = ortEnv.createSession(modelFile.getAbsolutePath(), sessionOptions);
        return currentSession;
    }

    public List<String> getInstalledModels() {
        List<String> models = new ArrayList<>();
        File[] files = context.getFilesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (VALID_MODELS.contains(file.getName())) {
                    models.add(file.getName());
                }
            }
        }
        return models;
    }

    public static class ResolveResult {
        public final String matchedModel;
        public final boolean hashMatches;
        public final String expectedHash;
        public final String actualHash;

        public ResolveResult(String matchedModel, boolean hashMatches, String expectedHash, String actualHash) {
            this.matchedModel = matchedModel;
            this.hashMatches = hashMatches;
            this.expectedHash = expectedHash;
            this.actualHash = actualHash;
        }
    }

    public ResolveResult resolveFilenameWithHash(Uri modelUri) throws Exception {
        String filename = null;
        
        try (Cursor cursor = context.getContentResolver().query(modelUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex);
                    Log.d("ModelManager", "Got filename from cursor: " + filename);
                }
            }
        }

        if (filename == null) {
            String path = modelUri.getPath();
            if (path != null) {
                filename = path.substring(path.lastIndexOf('/') + 1);
            }
        }

        if (filename == null) {
            filename = modelUri.getLastPathSegment();
        }

        if (filename == null) {
            throw new Exception("Could not determine filename");
        }

        int queryIndex = filename.indexOf('?');
        if (queryIndex > 0) {
            filename = filename.substring(0, queryIndex);
        }
        
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex > 0) {
            filename = filename.substring(slashIndex + 1);
        }
        
        final String cleanFilename = filename.trim();

        String matchedModel = VALID_MODELS.stream()
            .filter(model -> model.equalsIgnoreCase(cleanFilename))
            .findFirst()
            .orElseThrow(() -> new Exception(
                "invalid model filename: " + cleanFilename + 
                "\nexpected one of: " + String.join(", ", VALID_MODELS)
            ));

        String expectedHash = MODEL_HASHES.get(matchedModel);
        String actualHash = null;
        boolean hashMatches = true;
        if (expectedHash != null) {
            actualHash = computeFileHash(modelUri);
            hashMatches = expectedHash.equalsIgnoreCase(actualHash);
        }
        return new ResolveResult(matchedModel, hashMatches, expectedHash, actualHash);
    }

    private String computeFileHash(Uri fileUri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new Exception("Failed to compute file hash", e);
        }
    }

    public boolean importModel(Uri modelUri, boolean force) throws Exception {
        ResolveResult result = resolveFilenameWithHash(modelUri);
        if (!result.hashMatches && !force) {
            throw new Exception("HASH_MISMATCH:" + result.matchedModel + ":" + result.expectedHash + ":" + result.actualHash);
        }
        return importModelInternal(modelUri, result.matchedModel, null);
    }

    public boolean importModel(Uri modelUri, ModelCallback callback, boolean force) throws Exception {
        ResolveResult result = resolveFilenameWithHash(modelUri);
        if (!result.hashMatches && !force) {
            if (callback != null) {
                callback.onError("HASH_MISMATCH:" + result.matchedModel + ":" + result.expectedHash + ":" + result.actualHash);
            }
            return false;
        }
        return importModelInternal(modelUri, result.matchedModel, callback);
    }

    // Overloads for compatibility
    public boolean importModel(Uri modelUri) throws Exception {
        return importModel(modelUri, false);
    }
    public boolean importModel(Uri modelUri, ModelCallback callback) throws Exception {
        return importModel(modelUri, callback, false);
    }

    private boolean importModelInternal(Uri modelUri, String filename, ModelCallback callback) throws Exception {
        File modelFile = new File(context.getFilesDir(), filename);

        try (InputStream is = context.getContentResolver().openInputStream(modelUri)) {
            if (callback != null) {
                long fileSize = context.getContentResolver()
                    .openFileDescriptor(modelUri, "r")
                    .getStatSize();
                copyWithProgress(is, modelFile, fileSize, callback);
            } else {
                copyWithoutProgress(is, modelFile);
            }
        }

        if (!hasActiveModel()) {
            setActiveModel(filename);
        }

        if (callback != null) {
            callback.onSuccess(filename);
        }
        
        Log.d("ModelManager", "Successfully imported model: " + filename);
        return true;
    }

    private void copyWithProgress(InputStream is, File output, long fileSize, ModelCallback callback) throws IOException {
        try (FileOutputStream os = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int read;
            
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                totalBytesRead += read;
                int progress = (int) ((totalBytesRead * 100) / fileSize);
                callback.onProgress(progress);
            }
        }
    }

    private void copyWithoutProgress(InputStream is, File output) throws IOException {
        try (FileOutputStream os = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    public void deleteModel(String modelName, ModelDeleteCallback callback) {
        File modelFile = new File(context.getFilesDir(), modelName);
        if (modelFile.exists()) {
            modelFile.delete();
            vibrationManager.vibrateModelDelete();
            if (callback != null) {
                callback.onModelDeleted(modelName);
            }
        }

        String activeModel = prefs.getString(ACTIVE_MODEL_KEY, null);
        if (modelName.equals(activeModel)) {
            List<String> remaining = getInstalledModels();
            if (!remaining.isEmpty()) {
                setActiveModel(remaining.get(0));
            } else {
                prefs.edit().remove(ACTIVE_MODEL_KEY).apply();
            }
        }
    }

    public void setActiveModel(String modelName) {
        prefs.edit().putString(ACTIVE_MODEL_KEY, modelName).apply();
    }

    public String getActiveModelName() {
        return prefs.getString(ACTIVE_MODEL_KEY, null);
    }

    public boolean isColorModel(String modelName) {
        return modelName != null && modelName.contains("color");
    }

    public interface ModelCallback {
        void onSuccess(String modelName);
        void onError(String error);
        void onProgress(int progress);
    }

    public interface ModelDeleteCallback {
        void onModelDeleted(String modelName);
    }
}
