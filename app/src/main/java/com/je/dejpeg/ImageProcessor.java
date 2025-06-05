package com.je.dejpeg;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import ai.onnxruntime.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

public class ImageProcessor {
    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 32;
    private boolean isCancelled = false;
    private OrtEnvironment ortEnv;
    private OrtSession scunetSession;
    private OrtSession fbcnnSession;
    private boolean isGrayscaleModel;
    private float strength;

    private File chunkDir;
    private File processedDir;
    private Context context;
    private ModelManager modelManager;

    public interface ProcessCallback {
        void onComplete(Bitmap result);
        void onError(String error);
        void onProgress(String message);
    }

    public ImageProcessor(Context context, ModelManager modelManager) {
        this.context = context;
        this.modelManager = modelManager;
        this.chunkDir = new File(context.getCacheDir(), "chunkAccumulator");
        this.processedDir = new File(context.getCacheDir(), "processedChunkDir");
    }

    public void cancelProcessing() {
        isCancelled = true;
    }

    public void processImage(Bitmap inputBitmap, float strength, ProcessCallback callback, int index, int total) {
        isCancelled = false;
        this.strength = strength;
        new Thread(() -> {
            try {
                if (callback != null) callback.onProgress("Loading model...");
                
                OrtSession session = modelManager.loadModel();
                String modelName = modelManager.getActiveModelName();
                
                if (modelName.startsWith("scunet_")) {
                    setScunetSession(session, !modelManager.isColorModel(modelName));
                } else if (modelName.startsWith("fbcnn_")) {
                    setFbcnnSession(session, !modelManager.isColorModel(modelName));
                } else {
                    throw new Exception("Unknown model type: " + modelName);
                }

                if (callback != null) callback.onProgress("Processing image " + (index + 1) + " of " + total + "...");
                Bitmap result = processBitmap(inputBitmap, callback, index, total);
                if (callback != null) callback.onComplete(result);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        }).start();
    }

    // Add this overloaded method for internal use
    private Bitmap processBitmap(Bitmap inputBitmap) throws Exception {
        return processBitmap(inputBitmap, null, 0, 0);
    }
    
    // Main universal function
    public Bitmap processBitmap(Bitmap inputBitmap, ProcessCallback callback, int index, int total) throws Exception {
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();

        if (width > CHUNK_SIZE || height > CHUNK_SIZE) {
            // Ensure directories exist and are empty
            if (!chunkDir.exists()) chunkDir.mkdirs();
            if (!processedDir.exists()) processedDir.mkdirs();
            clearDirectory(chunkDir);
            clearDirectory(processedDir);

            // Chunking required
            List<ChunkInfo> chunks = chunkBitmapToDisk(inputBitmap);

            int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() >= 4 ?
                Runtime.getRuntime().availableProcessors() / 2 : 1);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<File>> futures = new ArrayList<>();

            // Process each chunk in parallel, save to processedDir
            for (int i = 0; i < chunks.size(); i++) {
                final int chunkIndex = i;
                ChunkInfo chunk = chunks.get(i);
                futures.add(executor.submit(() -> {
                    if (callback != null) callback.onProgress(String.format("Processing image %d/%d - chunk %d/%d...", 
                        index + 1, total, chunkIndex + 1, chunks.size()));
                    // Load chunk from disk
                    Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.chunkFile.getAbsolutePath());
                    Bitmap processed = processBitmap(chunkBitmap);
                    File outFile = new File(processedDir, "chunk_" + chunk.x + "_" + chunk.y + ".png");
                    saveBitmapToFile(processed, outFile);
                    chunkBitmap.recycle();
                    processed.recycle(); // Free memory, but keep file
                    chunk.processedFile = outFile;
                    return outFile;
                }));
            }
            // Wait for all chunks to finish
            for (Future<File> f : futures) f.get();
            executor.shutdown();

            // Merge processed chunks, removing overlaps
            Bitmap result = Bitmap.createBitmap(width, height, inputBitmap.getConfig());
            for (ChunkInfo chunk : chunks) {
                File processedFile = chunk.processedFile;
                Bitmap processed = BitmapFactory.decodeFile(processedFile.getAbsolutePath());
                int chunkW = processed.getWidth();
                int chunkH = processed.getHeight();

                // Calculate region to copy (remove overlap except at borders)
                int left = chunk.x == 0 ? 0 : OVERLAP / 2;
                int top = chunk.y == 0 ? 0 : OVERLAP / 2;
                int right = (chunk.x + chunkW >= width) ? chunkW : chunkW - OVERLAP / 2;
                int bottom = (chunk.y + chunkH >= height) ? chunkH : chunkH - OVERLAP / 2;

                int copyW = right - left;
                int copyH = bottom - top;
                int[] pixels = new int[copyW * copyH];
                processed.getPixels(pixels, 0, copyW, left, top, copyW, copyH);
                result.setPixels(pixels, 0, copyW, chunk.x + left, chunk.y + top, copyW, copyH);
                processed.recycle();
            }

            // Clean up chunk files
            clearDirectory(chunkDir);
            clearDirectory(processedDir);

            return result;
        }

        int w = inputBitmap.getWidth();
        int h = inputBitmap.getHeight();
        boolean hasAlpha = inputBitmap.getConfig() == Bitmap.Config.ARGB_8888;

        int[] pixels = new int[w * h];
        inputBitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        float[] inputArray;
        float[] alphaChannel = null;

        if (hasAlpha) {
            alphaChannel = new float[w * h];
        }

        if (isGrayscaleModel) {
            inputArray = new float[h * w];
            for (int i = 0; i < h; i++) {
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Processing cancelled");
                }
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int color = pixels[idx];
                    int gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                    inputArray[idx] = gray / 255.0f;
                    if (hasAlpha) alphaChannel[idx] = Color.alpha(color) / 255.0f;
                }
            }
        } else {
            inputArray = new float[3 * h * w];
            for (int i = 0; i < h; i++) {
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Processing cancelled");
                }
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int color = pixels[idx];
                    inputArray[0 * h * w + idx] = Color.red(color) / 255.0f;
                    inputArray[1 * h * w + idx] = Color.green(color) / 255.0f;
                    inputArray[2 * h * w + idx] = Color.blue(color) / 255.0f;
                    if (hasAlpha) alphaChannel[idx] = Color.alpha(color) / 255.0f;
                }
            }
        }

        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment();
        }

        long[] inputShape = new long[]{1, isGrayscaleModel ? 1 : 3, h, w};
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputArray), inputShape);
        OnnxTensor qfTensor = null;
        Map<String, OnnxTensor> inputs = new HashMap<>();

        OrtSession session;
        if (scunetSession != null) {
            session = scunetSession;
            inputs.put("input", inputTensor);
        } else {
            session = fbcnnSession;
            float qf = strength / 100.0f;
            FloatBuffer qfBuffer = FloatBuffer.wrap(new float[]{qf});
            qfTensor = OnnxTensor.createTensor(ortEnv, qfBuffer, new long[]{1, 1});
            inputs.put("input", inputTensor);
            inputs.put("qf", qfTensor);
        }

        if (isCancelled || Thread.currentThread().isInterrupted()) {
            inputTensor.close();
            if (qfTensor != null) qfTensor.close();
            throw new RuntimeException("Processing cancelled");
        }

        OrtSession.Result result = session.run(inputs);
        float[] outputArray = null;
        Object outputValue = result.get(0).getValue();
        if (outputValue == null) {
            android.util.Log.e("ImageProcessor", "ONNX output is null");
            showErrorDialog("ONNX model output is null", null);
            throw new RuntimeException("ONNX model output is null");
        }
        if (outputValue instanceof float[]) {
            outputArray = (float[]) outputValue;
        } else if (outputValue instanceof float[][][][]) {
            float[][][][] arr = (float[][][][]) outputValue;
            int c = arr[0].length;
            int hh = arr[0][0].length;
            int ww = arr[0][0][0].length;
            outputArray = new float[c * hh * ww];
            for (int ch = 0; ch < c; ch++) {
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    inputTensor.close();
                    if (qfTensor != null) qfTensor.close();
                    result.close();
                    throw new RuntimeException("Processing cancelled");
                }
                for (int i = 0; i < hh; i++) {
                    for (int j = 0; j < ww; j++) {
                        outputArray[ch * hh * ww + i * ww + j] = arr[0][ch][i][j];
                    }
                }
            }
        } else {
            android.util.Log.e("ImageProcessor", "Unexpected ONNX output type: " + outputValue.getClass());
            showErrorDialog("Unexpected ONNX output type: " + outputValue.getClass(), null);
            throw new RuntimeException("Unexpected ONNX output type: " + outputValue.getClass());
        }
        inputTensor.close();
        if (qfTensor != null) qfTensor.close();
        result.close();

        Bitmap resultBitmap = Bitmap.createBitmap(w, h, hasAlpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        int[] outPixels = new int[w * h];
        if (isGrayscaleModel) {
            for (int i = 0; i < h; i++) {
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    resultBitmap.recycle();
                    throw new RuntimeException("Processing cancelled");
                }
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int gray = Math.max(0, Math.min(255, (int) (outputArray[idx] * 255.0f)));
                    int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                    outPixels[idx] = Color.argb(alpha, gray, gray, gray);
                }
            }
        } else {
            for (int i = 0; i < h; i++) {
                if (isCancelled || Thread.currentThread().isInterrupted()) {
                    resultBitmap.recycle();
                    throw new RuntimeException("Processing cancelled");
                }
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int r = Math.max(0, Math.min(255, (int) (outputArray[0 * h * w + idx] * 255.0f)));
                    int g = Math.max(0, Math.min(255, (int) (outputArray[1 * h * w + idx] * 255.0f)));
                    int b = Math.max(0, Math.min(255, (int) (outputArray[2 * h * w + idx] * 255.0f)));
                    int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                    outPixels[idx] = Color.argb(alpha, r, g, b);
                }
            }
        }
        resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h);
        return resultBitmap;
    }

    // Helper for chunking bitmap and saving to disk
    private List<ChunkInfo> chunkBitmapToDisk(Bitmap input) throws IOException {
        List<ChunkInfo> chunks = new ArrayList<>();
        int width = input.getWidth();
        int height = input.getHeight();

        for (int y = 0; y < height; y += CHUNK_SIZE - OVERLAP) {
            for (int x = 0; x < width; x += CHUNK_SIZE - OVERLAP) {
                int chunkWidth = Math.min(CHUNK_SIZE, width - x);
                int chunkHeight = Math.min(CHUNK_SIZE, height - y);
                Bitmap chunk = Bitmap.createBitmap(input, x, y, chunkWidth, chunkHeight);
                File chunkFile = new File(chunkDir, "chunk_" + x + "_" + y + ".png");
                saveBitmapToFile(chunk, chunkFile);
                chunk.recycle();
                chunks.add(new ChunkInfo(x, y, chunkFile));
            }
        }
        return chunks;
    }

    // Save bitmap to file as PNG
    private void saveBitmapToFile(Bitmap bitmap, File file) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) out.close();
        }
    }

    // Add these public setters
    public void setScunetSession(OrtSession session, boolean isGrayscale) {
        this.scunetSession = session;
        this.fbcnnSession = null;
        this.isGrayscaleModel = isGrayscale;
    }

    public void setFbcnnSession(OrtSession session, boolean isGrayscale) {
        this.fbcnnSession = session;
        this.scunetSession = null;
        this.isGrayscaleModel = isGrayscale;
    }

    // Helper class for chunk info
    private static class ChunkInfo {
        final int x, y;
        final File chunkFile;
        File processedFile;

        ChunkInfo(int x, int y, File chunkFile) {
            this.x = x;
            this.y = y;
            this.chunkFile = chunkFile;
        }
    }

    // Utility to clear a directory
    private void clearDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    private void showErrorDialog(String message, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(message != null ? message : "An error occurred when processing an error message");
        if (e != null) {
            sb.append("\n\n").append(android.util.Log.getStackTraceString(e));
        }
        String errorText = sb.toString();

        if (Looper.myLooper() != Looper.getMainLooper()) {
            ((AppCompatActivity) context).runOnUiThread(() -> showErrorDialog(message, e));
            return;
        }

        // Prevent window leak by checking activity state
        AppCompatActivity activity = (AppCompatActivity) context;
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle("Error")
            .setMessage(errorText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Error", errorText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}