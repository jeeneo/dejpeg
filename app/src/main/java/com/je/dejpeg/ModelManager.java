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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.provider.OpenableColumns;

// ONNX Runtime imports
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;

public class ModelManager {
    private static final String PREFS_NAME = "ModelPrefs";
    private static final String ACTIVE_MODEL_KEY = "activeModel";
    private static final List<String> VALID_MODELS = Arrays.asList(
            "fbcnn_color.onnx",
            "fbcnn_gray.onnx",
            "fbcnn_gray_double.onnx",
            "scunet_color_real_psnr.onnx", // SCUNet color model
            "scunet_gray_15.onnx",        // SCUNet grayscale models
            "scunet_gray_25.onnx",
            "scunet_gray_50.onnx",
            "scunet_color_real_gan.onnx"      // SCUNet gaussian model
    );

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
                // ignore
            }
            currentSession = null;
        }
        if (ortEnv != null) {
            try {
                ortEnv.close();
            } catch (Exception e) {
                // ignore
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

    private String resolveFilename(Uri modelUri) throws Exception {
        String filename = null;
        
        // Try cursor method first
        try (Cursor cursor = context.getContentResolver().query(modelUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex);
                    Log.d("ModelManager", "Got filename from cursor: " + filename);
                }
            }
        }

        // Fallback methods
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

        // Clean up filename
        int queryIndex = filename.indexOf('?');
        if (queryIndex > 0) {
            filename = filename.substring(0, queryIndex);
        }
        
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex > 0) {
            filename = filename.substring(slashIndex + 1);
        }
        
        final String cleanFilename = filename.trim();

        // Validate against valid models
        String matchedModel = VALID_MODELS.stream()
            .filter(model -> model.equalsIgnoreCase(cleanFilename))
            .findFirst()
            .orElseThrow(() -> new Exception(
                "Invalid model filename: " + cleanFilename + 
                "\nExpected one of: " + String.join(", ", VALID_MODELS)
            ));

        return matchedModel;
    }

    public boolean importModel(Uri modelUri) throws Exception {
        String filename = resolveFilename(modelUri);
        return importModelInternal(modelUri, filename, null);
    }

    public boolean importModel(Uri modelUri, ModelCallback callback) throws Exception {
        String filename = resolveFilename(modelUri);
        return importModelInternal(modelUri, filename, callback);
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
