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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.je.dejpeg.models.ProcessingState;

public class ImageProcessor {
    public static final int DEFAULT_CHUNK_SIZE = 1200;
    public static final int SCUNET_CHUNK_SIZE = 800;
    public static final int OVERLAP = 32;
    private boolean isCancelled = false;
    private OrtEnvironment ortEnv;
    private OrtSession scunetSession;
    private OrtSession fbcnnSession;
    private boolean isGrayscaleModel;
    private float strength;

    public static File chunkDir;
    public static File processedDir;
    private Context context;
    private ModelManager modelManager;
    private final AtomicInteger runningChunks = new AtomicInteger(0);

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
        ProcessingState.Companion.updateImageProgress(index + 1, total);
        ProcessingState.lastImageWidth = inputBitmap.getWidth();
        ProcessingState.lastImageHeight = inputBitmap.getHeight();

        String modelName = modelManager.getActiveModelName();
        int effectiveChunkSize = getChunkSizeForModel(modelName);
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();
        int totalChunks = (width > effectiveChunkSize || height > effectiveChunkSize) ? ((int)Math.ceil((double)width / (effectiveChunkSize - OVERLAP))) * ((int)Math.ceil((double)height / (effectiveChunkSize - OVERLAP))): 1;
        ProcessingState.Companion.updateChunkProgress(totalChunks);
        ProcessingState.Companion.initializeTimeEstimation(context, modelName != null ? modelName : "unknown", totalChunks);

        if (callback != null) {
            callback.onProgress(ProcessingState.Companion.getStatusString(context));
        }

        new Thread(() -> {
            try {
                OrtSession session = modelManager.loadModel();
                String modelNameInner = modelManager.getActiveModelName();
                boolean isKnown = modelManager.isKnownModel(modelNameInner);
                if (isKnown) {
                    if (modelNameInner.startsWith("scunet_")) {
                        setScunetSession(session, !modelManager.isColorModel(modelNameInner));
                    } else if (modelNameInner.startsWith("fbcnn_")) {
                        setFbcnnSession(session, !modelManager.isColorModel(modelNameInner));
                    } else {
                        throw new Exception("Unknown model type: " + modelNameInner);
                    }
                    Bitmap result = processBitmap(inputBitmap, callback, index, total);
                    if (callback != null) callback.onComplete(result);
                } else {
                    // dynamic tensor detection for unknown models
                    Bitmap result = processBitmapDynamic(session, inputBitmap, callback, index, total);
                    if (callback != null) callback.onComplete(result);
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        }).start();
    }

    private Bitmap processBitmap(Bitmap inputBitmap) throws Exception {
        return processBitmap(inputBitmap, null, 0, 0);
    }

    public Bitmap processBitmap(Bitmap inputBitmap, ProcessCallback callback, int index, int total) throws Exception {
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();
        String modelName = modelManager.getActiveModelName();
        int effectiveChunkSize = getChunkSizeForModel(modelName);

        // Detect if any transparency exists in the input image
        boolean hasTransparency = false;
        if (inputBitmap.hasAlpha()) {
            int[] pixels = new int[Math.min(width * height, 4096)];
            inputBitmap.getPixels(pixels, 0, width, 0, 0, Math.min(width, 64), Math.min(height, 64));
            for (int i = 0; i < pixels.length; i++) {
                if ((pixels[i] >>> 24) != 0xFF) {
                    hasTransparency = true;
                    break;
                }
            }
        }

        Bitmap.Config processingConfig = Bitmap.Config.ARGB_8888;

        if (width > effectiveChunkSize || height > effectiveChunkSize) {
            // Ensure directories exist and are empty
            if (!chunkDir.exists()) chunkDir.mkdirs();
            if (!processedDir.exists()) processedDir.mkdirs();
            clearCacheDirs(chunkDir);
            clearCacheDirs(processedDir);

            // Split into chunks but process sequentially with ONNX's internal threading
            List<ChunkInfo> chunks = chunkBitmapToDisk(inputBitmap);
            ProcessingState.Companion.updateChunkProgress(chunks.size());
            ProcessingState.Companion.getCompletedChunks().set(0);

            // Show initial chunk progress and time estimation before processing any chunk
            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }

            for (int i = 0; i < chunks.size(); i++) {
                if (isCancelled) throw new RuntimeException("Processing cancelled");

                ChunkInfo chunk = chunks.get(i);

                Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.chunkFile.getAbsolutePath());
                // Convert chunk to correct config if needed
                if (chunkBitmap.getConfig() != processingConfig) {
                    Bitmap converted = chunkBitmap.copy(processingConfig, true);
                    chunkBitmap.recycle();
                    chunkBitmap = converted;
                }
                Bitmap processed = processChunk(chunkBitmap, processingConfig, hasTransparency);

                File outFile = new File(processedDir, "chunk_" + chunk.x + "_" + chunk.y + ".png");
                saveBitmapToFile(processed, outFile);

                chunkBitmap.recycle();
                processed.recycle();
                chunk.processedFile = outFile;

                ProcessingState.Companion.onChunkCompleted();
                if (callback != null) {
                    callback.onProgress(ProcessingState.Companion.getStatusString(context));
                }
            }

            Bitmap result = Bitmap.createBitmap(width, height, processingConfig);
            for (ChunkInfo chunk : chunks) {
                File processedFile = chunk.processedFile;
                Bitmap processed = BitmapFactory.decodeFile(processedFile.getAbsolutePath());
                int chunkW = processed.getWidth();
                int chunkH = processed.getHeight();

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
            clearCacheDirs(chunkDir);
            clearCacheDirs(processedDir);
            return result;
        } else {
            Bitmap bitmapToProcess = inputBitmap;
            if (inputBitmap.getConfig() != processingConfig) {
                bitmapToProcess = inputBitmap.copy(processingConfig, true);
            }
            ProcessingState.Companion.updateChunkProgress(1);
            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }
            Bitmap result = processChunk(bitmapToProcess, processingConfig, hasTransparency);
            ProcessingState.Companion.onChunkCompleted();
            return result;
        }
    }

    private Bitmap processChunk(Bitmap chunk, Bitmap.Config processingConfig, boolean processAlpha) throws Exception {
        int w = chunk.getWidth();
        int h = chunk.getHeight();
        boolean hasAlpha = processAlpha;

        int[] pixels = new int[w * h];
        chunk.getPixels(pixels, 0, w, 0, 0, w, h);

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
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputArray), inputShape);
             OnnxTensor qfTensor = scunetSession == null ? 
                OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(new float[]{strength / 100.0f}), new long[]{1, 1}) : 
                null) {

            if (isCancelled || Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Processing cancelled");
            }

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input", inputTensor);
            if (qfTensor != null) {
                inputs.put("qf", qfTensor);
            }

            System.gc();

            try (OrtSession.Result result = (scunetSession != null ? scunetSession : fbcnnSession).run(inputs)) {
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

                Bitmap resultBitmap = Bitmap.createBitmap(w, h, processingConfig);
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
                            outPixels[idx] = hasAlpha
                                ? Color.argb(alpha, gray, gray, gray)
                                : Color.rgb(gray, gray, gray);
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
                            outPixels[idx] = hasAlpha
                                ? Color.argb(alpha, r, g, b)
                                : Color.rgb(r, g, b);
                        }
                    }
                }
                resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h);
                return resultBitmap;
            }
        }
    }

    // Dynamic tensor detection for unknown models
    private Bitmap processBitmapDynamic(OrtSession session, Bitmap inputBitmap, ProcessCallback callback, int index, int total) throws Exception {
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();
        int effectiveChunkSize = getChunkSizeForModel("unknown"); // fallback

        // Detect if any transparency exists in the input image
        boolean hasTransparency = false;
        if (inputBitmap.hasAlpha()) {
            int[] pixels = new int[Math.min(width * height, 4096)];
            inputBitmap.getPixels(pixels, 0, width, 0, 0, Math.min(width, 64), Math.min(height, 64));
            for (int i = 0; i < pixels.length; i++) {
                if ((pixels[i] >>> 24) != 0xFF) {
                    hasTransparency = true;
                    break;
                }
            }
        }

        Bitmap.Config processingConfig = Bitmap.Config.ARGB_8888;

        if (width > effectiveChunkSize || height > effectiveChunkSize) {
            if (!chunkDir.exists()) chunkDir.mkdirs();
            if (!processedDir.exists()) processedDir.mkdirs();
            clearCacheDirs(chunkDir);
            clearCacheDirs(processedDir);

            List<ChunkInfo> chunks = chunkBitmapToDisk(inputBitmap);
            ProcessingState.Companion.updateChunkProgress(chunks.size());
            ProcessingState.Companion.getCompletedChunks().set(0);

            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }

            for (int i = 0; i < chunks.size(); i++) {
                if (isCancelled) throw new RuntimeException("Processing cancelled");

                ChunkInfo chunk = chunks.get(i);

                Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.chunkFile.getAbsolutePath());
                if (chunkBitmap.getConfig() != processingConfig) {
                    Bitmap converted = chunkBitmap.copy(processingConfig, true);
                    chunkBitmap.recycle();
                    chunkBitmap = converted;
                }
                Bitmap processed = processChunkDynamic(session, chunkBitmap, processingConfig, hasTransparency);

                File outFile = new File(processedDir, "chunk_" + chunk.x + "_" + chunk.y + ".png");
                saveBitmapToFile(processed, outFile);

                chunkBitmap.recycle();
                processed.recycle();
                chunk.processedFile = outFile;

                ProcessingState.Companion.onChunkCompleted();
                if (callback != null) {
                    callback.onProgress(ProcessingState.Companion.getStatusString(context));
                }
            }

            Bitmap result = Bitmap.createBitmap(width, height, processingConfig);
            for (ChunkInfo chunk : chunks) {
                File processedFile = chunk.processedFile;
                Bitmap processed = BitmapFactory.decodeFile(processedFile.getAbsolutePath());
                int chunkW = processed.getWidth();
                int chunkH = processed.getHeight();

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
            clearCacheDirs(chunkDir);
            clearCacheDirs(processedDir);
            return result;
        } else {
            Bitmap bitmapToProcess = inputBitmap;
            if (inputBitmap.getConfig() != processingConfig) {
                bitmapToProcess = inputBitmap.copy(processingConfig, true);
            }
            ProcessingState.Companion.updateChunkProgress(1);
            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }
            Bitmap result = processChunkDynamic(session, bitmapToProcess, processingConfig, hasTransparency);
            ProcessingState.Companion.onChunkCompleted();
            return result;
        }
    }

    // Dynamic tensor detection for unknown models
    private Bitmap processChunkDynamic(OrtSession session, Bitmap chunk, Bitmap.Config processingConfig, boolean processAlpha) throws Exception {
        int w = chunk.getWidth();
        int h = chunk.getHeight();
        boolean hasAlpha = processAlpha;

        int[] pixels = new int[w * h];
        chunk.getPixels(pixels, 0, w, 0, 0, w, h);

        // Introspect input tensor
        Map<String, NodeInfo> inputInfoMap = session.getInputInfo();
        String imageInputName = null;
        long[] imageInputShape = null;
        boolean isImageInputGrayscale = false;
        for (Map.Entry<String, NodeInfo> entry : inputInfoMap.entrySet()) {
            NodeInfo info = entry.getValue();
            if (info.getInfo() instanceof TensorInfo) {
                TensorInfo tinfo = (TensorInfo) info.getInfo();
                long[] shape = tinfo.getShape();
                if (tinfo.type == OnnxJavaType.FLOAT && shape.length == 4) {
                    imageInputName = entry.getKey();
                    imageInputShape = shape.clone();
                    if (imageInputShape[0] == -1) imageInputShape[0] = 1;
                    if (imageInputShape[1] == -1) imageInputShape[1] = (shape[1] == 1) ? 1 : 3;
                    if (imageInputShape[2] == -1) imageInputShape[2] = h;
                    if (imageInputShape[3] == -1) imageInputShape[3] = w;
                    isImageInputGrayscale = (imageInputShape[1] == 1);
                    break;
                }
            }
        }
        if (imageInputName == null) throw new Exception("Could not find image input tensor");

        float[] inputArray;
        float[] alphaChannel = null;
        if (hasAlpha) {
            alphaChannel = new float[w * h];
        }
        if (isImageInputGrayscale) {
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

        Map<String, OnnxTensor> inputs = new HashMap<>();
        OnnxTensor imageTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputArray), imageInputShape);
        inputs.put(imageInputName, imageTensor);

        // Add any other float input (e.g. qf) if present and shape is [1,1]
        for (Map.Entry<String, NodeInfo> entry : inputInfoMap.entrySet()) {
            String name = entry.getKey();
            if (name.equals(imageInputName)) continue;
            NodeInfo info = entry.getValue();
            if (info.getInfo() instanceof TensorInfo) {
                TensorInfo tinfo = (TensorInfo) info.getInfo();
                long[] shape = tinfo.getShape();
                long[] fixedShape = shape.clone();
                for (int i = 0; i < fixedShape.length; i++) {
                    if (fixedShape[i] == -1) fixedShape[i] = 1;
                }
                if (tinfo.type == OnnxJavaType.FLOAT && fixedShape.length == 2) {
                    float[] qf = new float[]{strength / 100.0f};
                    OnnxTensor qfTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(qf), fixedShape);
                    inputs.put(name, qfTensor);
                }
            }
        }

        if (isCancelled || Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Processing cancelled");
        }

        System.gc();

        try (OrtSession.Result result = session.run(inputs)) {
            // Dynamically extract output tensor
            float[] outputArray = null;
            for (int i = 0; i < result.size(); i++) {
                ai.onnxruntime.OnnxValue val = result.get(i);
                if (val == null) continue;
                Object outputValue = val.getValue();
                if (outputValue == null) continue;
                if (outputValue instanceof float[]) {
                    outputArray = (float[]) outputValue;
                    break;
                } else if (outputValue instanceof float[][][][]) {
                    float[][][][] arr = (float[][][][]) outputValue;
                    int c = arr[0].length;
                    int hh = arr[0][0].length;
                    int ww = arr[0][0][0].length;
                    outputArray = new float[c * hh * ww];
                    for (int ch = 0; ch < c; ch++) {
                        if (isCancelled || Thread.currentThread().isInterrupted()) {
                            throw new RuntimeException("Processing cancelled");
                        }
                        for (int i2 = 0; i2 < hh; i2++) {
                            for (int j2 = 0; j2 < ww; j2++) {
                                outputArray[ch * hh * ww + i2 * ww + j2] = arr[0][ch][i2][j2];
                            }
                        }
                    }
                    break;
                }
            }
            if (outputArray == null) throw new RuntimeException("Unexpected ONNX output type");

            Bitmap resultBitmap = Bitmap.createBitmap(w, h, processingConfig);
            int[] outPixels = new int[w * h];
            if (isImageInputGrayscale) {
                for (int i = 0; i < h; i++) {
                    if (isCancelled || Thread.currentThread().isInterrupted()) {
                        resultBitmap.recycle();
                        throw new RuntimeException("Processing cancelled");
                    }
                    for (int j = 0; j < w; j++) {
                        int idx = i * w + j;
                        int gray = Math.max(0, Math.min(255, (int) (outputArray[idx] * 255.0f)));
                        int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                        outPixels[idx] = hasAlpha
                            ? Color.argb(alpha, gray, gray, gray)
                            : Color.rgb(gray, gray, gray);
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
                        outPixels[idx] = hasAlpha
                            ? Color.argb(alpha, r, g, b)
                            : Color.rgb(r, g, b);
                    }
                }
            }
            resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h);
            return resultBitmap;
        } finally {
            for (OnnxTensor t : inputs.values()) t.close();
        }
    }

    // Helper methods for fallback chunk size if needed
    private int chunkWidth(ChunkInfo chunk) {
        return Math.min(DEFAULT_CHUNK_SIZE, ProcessingState.lastImageWidth - chunk.x);
    }
    private int chunkHeight(ChunkInfo chunk) {
        return Math.min(DEFAULT_CHUNK_SIZE, ProcessingState.lastImageHeight - chunk.y);
    }

    private List<ChunkInfo> chunkBitmapToDisk(Bitmap input) throws IOException {
        List<ChunkInfo> chunks = new ArrayList<>();
        int width = input.getWidth();
        int height = input.getHeight();
        String modelName = modelManager.getActiveModelName();
        int effectiveChunkSize = getChunkSizeForModel(modelName);

        for (int y = 0; y < height; y += effectiveChunkSize - OVERLAP) {
            for (int x = 0; x < width; x += effectiveChunkSize - OVERLAP) {
                int chunkWidth = Math.min(effectiveChunkSize, width - x);
                int chunkHeight = Math.min(effectiveChunkSize, height - y);
                Bitmap chunk = Bitmap.createBitmap(input, x, y, chunkWidth, chunkHeight);
                File chunkFile = new File(chunkDir, "chunk_" + x + "_" + y + ".png");
                saveBitmapToFile(chunk, chunkFile);
                chunk.recycle();
                chunks.add(new ChunkInfo(x, y, chunkFile));
            }
        }
        return chunks;
    }

    private void saveBitmapToFile(Bitmap bitmap, File file) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) out.close();
        }
    }

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

    private int getChunkSizeForModel(String modelName) {
        return modelName != null && modelName.startsWith("scunet_") ? SCUNET_CHUNK_SIZE : DEFAULT_CHUNK_SIZE;
    }

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

    public static void clearCacheDirs(File dir) {
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