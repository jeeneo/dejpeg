package com.je.dejpeg;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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

/***
 * TODO:
 * - Allow user-configurable chunk size and overlap
 */

public class ImageProcessor {
    public static final int DEFAULT_CHUNK_SIZE = 1200; // normal?
    public static final int SCUNET_CHUNK_SIZE = 640; // + overlap = no more than ~720ish should help prevent crashing with scunet
    public static final int OVERLAP = 32;
    public static final int SCUNET_OVERLAP = 128; // needs larger overlap for better blending, doesn't handle edge artifacts well
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

        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();
        String modelName = modelManager.getActiveModelName();
        int effectiveChunkSize = getChunkSizeForModel(modelName);
        int overlap = getOverlapForModel(modelName);
        int totalChunks = (width > effectiveChunkSize || height > effectiveChunkSize) ? 
            ((int)Math.ceil((double)width / (effectiveChunkSize - overlap))) * 
            ((int)Math.ceil((double)height / (effectiveChunkSize - overlap))) : 1;
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
                }
            } catch (Exception e) {
                android.util.Log.e("ImageProcessor", "Error processing image", e);
                String errorMsg = "Error: " + e.getClass().getSimpleName();
                if (e.getMessage() != null) {
                    errorMsg += ": " + e.getMessage();
                }
                if (callback != null) callback.onError(errorMsg);
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
        int overlap = getOverlapForModel(modelName);
        int totalChunks = (width > effectiveChunkSize || height > effectiveChunkSize) ? 
            ((int)Math.ceil((double)width / (effectiveChunkSize - overlap))) * 
            ((int)Math.ceil((double)height / (effectiveChunkSize - overlap))) : 1;
        ProcessingState.Companion.updateChunkProgress(totalChunks);
        ProcessingState.Companion.initializeTimeEstimation(context, modelName != null ? modelName : "unknown", totalChunks);

        boolean hasTransparency = false;
        if (inputBitmap.hasAlpha()) {
            int sampleWidth = Math.min(width, 64);
            int sampleHeight = Math.min(height, 64);
            int[] pixels = new int[sampleWidth * sampleHeight];
            inputBitmap.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
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

            Bitmap result = reassembleChunksWithFeathering(chunks, width, height, processingConfig);
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

            Bitmap result = reassembleChunksWithFeathering(chunks, width, height, processingConfig);
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

    private Bitmap reassembleChunksWithFeathering(List<ChunkInfo> chunks, int width, int height, Bitmap.Config config) {
        Bitmap result = Bitmap.createBitmap(width, height, config);
        Canvas canvas = new Canvas(result);
        
        for (ChunkInfo chunk : chunks) {
            Bitmap processed = BitmapFactory.decodeFile(chunk.processedFile.getAbsolutePath());
            int chunkW = processed.getWidth();
            int chunkH = processed.getHeight();
            
            // Create alpha mask for feathered blending
            Bitmap blendedChunk = createFeatheredChunk(processed, chunk, width, height);
            
            // Draw with proper blending
            Paint paint = new Paint();
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
            canvas.drawBitmap(blendedChunk, chunk.x, chunk.y, paint);
            
            processed.recycle();
            blendedChunk.recycle();
        }
        
        return result;
    }

    private Bitmap createFeatheredChunk(Bitmap chunk, ChunkInfo chunkInfo, int totalWidth, int totalHeight) {
        int chunkW = chunk.getWidth();
        int chunkH = chunk.getHeight();
        
        // Create copy with alpha channel for blending
        Bitmap feathered = chunk.copy(Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[chunkW * chunkH];
        feathered.getPixels(pixels, 0, chunkW, 0, 0, chunkW, chunkH);
        
        int featherSize = getOverlapForModel(modelManager.getActiveModelName()) / 2;
        
        for (int y = 0; y < chunkH; y++) {
            for (int x = 0; x < chunkW; x++) {
                int idx = y * chunkW + x;
                int pixel = pixels[idx];
                
                // Calculate alpha based on distance from edges
                float alpha = 1.0f;
                
                // Left edge feathering (except for leftmost chunks)
                if (chunkInfo.x > 0 && x < featherSize) {
                    alpha = Math.min(alpha, (float) x / featherSize);
                }
                
                // Top edge feathering (except for topmost chunks)
                if (chunkInfo.y > 0 && y < featherSize) {
                    alpha = Math.min(alpha, (float) y / featherSize);
                }
                
                // Right edge feathering (except for rightmost chunks)
                if (chunkInfo.x + chunkW < totalWidth && x >= chunkW - featherSize) {
                    alpha = Math.min(alpha, (float) (chunkW - x) / featherSize);
                }
                
                // Bottom edge feathering (except for bottommost chunks)
                if (chunkInfo.y + chunkH < totalHeight && y >= chunkH - featherSize) {
                    alpha = Math.min(alpha, (float) (chunkH - y) / featherSize);
                }
                
                // Apply alpha to pixel
                int newAlpha = (int) (alpha * 255);
                pixels[idx] = (pixel & 0x00FFFFFF) | (newAlpha << 24);
            }
        }
        
        feathered.setPixels(pixels, 0, chunkW, 0, 0, chunkW, chunkH);
        return feathered;
    }

    // Alternative: Simple linear blend approach
    private void blendChunkLinear(Bitmap result, ChunkInfo chunk, Bitmap processed) {
        int chunkW = processed.getWidth();
        int chunkH = processed.getHeight();
        int resultW = result.getWidth();
        int resultH = result.getHeight();
        
        int[] chunkPixels = new int[chunkW * chunkH];
        processed.getPixels(chunkPixels, 0, chunkW, 0, 0, chunkW, chunkH);
        
        for (int y = 0; y < chunkH; y++) {
            for (int x = 0; x < chunkW; x++) {
                int globalX = chunk.x + x;
                int globalY = chunk.y + y;
                
                if (globalX >= resultW || globalY >= resultH) continue;
                
                int chunkPixel = chunkPixels[y * chunkW + x];
                
                // Check if this pixel is in an overlap region
                boolean inOverlap = false;
                float blendWeight = 1.0f;
                
                // Calculate blend weight based on position in overlap
                if (chunk.x > 0 && x < OVERLAP / 2) {
                    inOverlap = true;
                    blendWeight = (float) x / (OVERLAP / 2);
                }
                if (chunk.y > 0 && y < OVERLAP / 2) {
                    inOverlap = true;
                    blendWeight = Math.min(blendWeight, (float) y / (OVERLAP / 2));
                }
                
                if (inOverlap) {
                    // Blend with existing pixel
                    int existingPixel = result.getPixel(globalX, globalY);
                    int blendedPixel = blendPixels(existingPixel, chunkPixel, blendWeight);
                    result.setPixel(globalX, globalY, blendedPixel);
                } else {
                    // Direct copy
                    result.setPixel(globalX, globalY, chunkPixel);
                }
            }
        }
    }

    private int blendPixels(int pixel1, int pixel2, float weight) {
        int a1 = (pixel1 >>> 24) & 0xFF;
        int r1 = (pixel1 >>> 16) & 0xFF;
        int g1 = (pixel1 >>> 8) & 0xFF;
        int b1 = pixel1 & 0xFF;
        
        int a2 = (pixel2 >>> 24) & 0xFF;
        int r2 = (pixel2 >>> 16) & 0xFF;
        int g2 = (pixel2 >>> 8) & 0xFF;
        int b2 = pixel2 & 0xFF;
        
        int a = (int) (a1 * (1 - weight) + a2 * weight);
        int r = (int) (r1 * (1 - weight) + r2 * weight);
        int g = (int) (g1 * (1 - weight) + g2 * weight);
        int b = (int) (b1 * (1 - weight) + b2 * weight);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
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
        int overlap = getOverlapForModel(modelName);
        
        // Calculate grid dimensions
        int cols = (int) Math.ceil((double) width / (effectiveChunkSize - overlap));
        int rows = (int) Math.ceil((double) height / (effectiveChunkSize - overlap));
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                // Calculate chunk position with symmetric overlap
                int startX = col * (effectiveChunkSize - overlap);
                int startY = row * (effectiveChunkSize - overlap);
                
                // Extend into overlap regions
                int chunkX = Math.max(0, startX - (col > 0 ? overlap / 2 : 0));
                int chunkY = Math.max(0, startY - (row > 0 ? overlap / 2 : 0));
                
                // Calculate actual chunk dimensions
                int chunkWidth = Math.min(effectiveChunkSize + (col > 0 ? overlap / 2 : 0), width - chunkX);
                int chunkHeight = Math.min(effectiveChunkSize + (row > 0 ? overlap / 2 : 0), height - chunkY);
                
                // Ensure we don't exceed image boundaries
                chunkWidth = Math.min(chunkWidth, width - chunkX);
                chunkHeight = Math.min(chunkHeight, height - chunkY);
                
                if (chunkWidth <= 0 || chunkHeight <= 0) continue;
                
                Bitmap chunk = Bitmap.createBitmap(input, chunkX, chunkY, chunkWidth, chunkHeight);
                File chunkFile = new File(chunkDir, "chunk_" + chunkX + "_" + chunkY + ".png");
                saveBitmapToFile(chunk, chunkFile);
                chunk.recycle();
                
                ChunkInfo chunkInfo = new ChunkInfo(chunkX, chunkY, chunkFile);
                chunkInfo.originalStartX = startX;
                chunkInfo.originalStartY = startY;
                chunkInfo.row = row;
                chunkInfo.col = col;
                chunks.add(chunkInfo);
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

    private int getOverlapForModel(String modelName) {
        return modelName != null && modelName.startsWith("scunet_") ? SCUNET_OVERLAP : OVERLAP;
    }
    private static class ChunkInfo {
        final int x, y;
        final File chunkFile;
        File processedFile;
        
        // Additional fields for symmetric blending
        int originalStartX, originalStartY;
        int row, col;

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