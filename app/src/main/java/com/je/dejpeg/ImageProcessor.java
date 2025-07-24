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
import java.util.concurrent.atomic.AtomicInteger;
import com.je.dejpeg.models.ProcessingState;

public class ImageProcessor {
    public static final int MAX_CHUNK_SIZE = 1200;
    private static final int MIN_OVERLAP = 16;
    private static final int MAX_OVERLAP = 64;
    
    private boolean isCancelled = false;
    private OrtEnvironment ortEnv;
    private OrtSession scunetSession;
    private OrtSession fbcnnSession;
    private boolean isGrayscaleModel;
    private float strength;

    private final Context context;
    private final ModelManager modelManager;
    private final ChunkManager chunkManager;
    private final TensorProcessor tensorProcessor;
    private final BitmapProcessor bitmapProcessor;

    private Map<String, NodeInfo> inputInfoMap;
    private Map<String, NodeInfo> outputInfoMap;

    public interface ProcessCallback {
        void onComplete(Bitmap result);
        void onError(String error);
        void onProgress(String message);
    }

    public ImageProcessor(Context context, ModelManager modelManager) {
        this.context = context;
        this.modelManager = modelManager;
        this.chunkManager = new ChunkManager(context);
        this.tensorProcessor = new TensorProcessor();
        this.bitmapProcessor = new BitmapProcessor();
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

        ChunkConfig chunkConfig = calculateChunkConfig(inputBitmap);
        ProcessingState.Companion.updateChunkProgress(chunkConfig.totalChunks);

        String modelName = modelManager.getActiveModelName();
        if (modelName == null) modelName = "unknown";
        ProcessingState.Companion.initializeTimeEstimation(context, modelName, chunkConfig.totalChunks);

        if (callback != null) {
            callback.onProgress(ProcessingState.Companion.getStatusString(context));
        }

        new Thread(() -> {
            try {
                initializeModelSession();
                Bitmap result = processBitmap(inputBitmap, chunkConfig, callback, index, total);
                if (callback != null) callback.onComplete(result);
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        }).start();
    }

    private void initializeModelSession() throws Exception {
        modelManager.unloadModel();
        OrtSession session = modelManager.loadModel();
        String modelName = modelManager.getActiveModelName();

        inputInfoMap = session.getInputInfo();
        outputInfoMap = session.getOutputInfo();

        boolean isKnown = modelManager.isKnownModel(modelName);

        if (modelName.startsWith("scunet_")) {
            setScunetSession(session, !modelManager.isColorModel(modelName));
        } else if (modelName.startsWith("fbcnn_")) {
            setFbcnnSession(session, !modelManager.isColorModel(modelName));
        } else {
            setScunetSession(session, false);
        }
    }

    private Bitmap processBitmap(Bitmap inputBitmap, ChunkConfig chunkConfig, ProcessCallback callback, int index, int total) throws Exception {
        ProcessingState.Companion.updateImageProgress(index + 1, total);
        
        if (chunkConfig.needsChunking) {
            return processLargeImage(inputBitmap, chunkConfig, callback);
        } else {
            ProcessingState.Companion.updateChunkProgress(1);
            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }
            return processChunk(inputBitmap);
        }
    }

    private Bitmap processLargeImage(Bitmap inputBitmap, ChunkConfig chunkConfig, ProcessCallback callback) throws Exception {
        chunkManager.prepareDirectories();

        List<ChunkInfo> chunks = chunkManager.createChunks(inputBitmap, chunkConfig);
        ProcessingState.Companion.updateChunkProgress(chunks.size());
        ProcessingState.Companion.getCompletedChunks().set(0);

        // Start timing for the first chunk
        if (com.je.dejpeg.models.ProcessingState.Companion.getTimeEstimator() != null) {
            com.je.dejpeg.models.ProcessingState.Companion.getTimeEstimator().startChunk();
        }

        for (ChunkInfo chunk : chunks) {
            if (isCancelled) throw new RuntimeException("Processing cancelled");

            Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.chunkFile.getAbsolutePath());
            Bitmap processed = processChunk(chunkBitmap);

            chunk.processedFile = chunkManager.saveProcessedChunk(processed, chunk);

            chunkBitmap.recycle();
            processed.recycle();

            ProcessingState.Companion.onChunkCompleted(); // Will handle timeEstimator chunk end/start

            if (callback != null) {
                callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }
        }

        Bitmap result = bitmapProcessor.reassembleChunks(inputBitmap, chunks, chunkConfig);
        chunkManager.cleanup();

        return result;
    }

    private Bitmap processChunk(Bitmap chunk) throws Exception {
        checkCancellation();

        ImageData imageData = bitmapProcessor.extractImageData(chunk);
        String modelName = modelManager.getActiveModelName();
        boolean isKnown = modelManager.isKnownModel(modelName);

        if (!isKnown) {
            // nothing
        }

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
                    if (imageInputShape[2] == -1) imageInputShape[2] = imageData.height;
                    if (imageInputShape[3] == -1) imageInputShape[3] = imageData.width;
                    isImageInputGrayscale = (imageInputShape[1] == 1);
                    break;
                }
            }
        }
        if (imageInputName == null) throw new Exception("Could not find image input tensor");

        float[] inputArray = tensorProcessor.prepareInputTensor(imageData, isImageInputGrayscale);
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment();
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        OnnxTensor imageTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputArray), imageInputShape);
        inputs.put(imageInputName, imageTensor);

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

        checkCancellation();
        System.gc();

        try (OrtSession.Result result = getCurrentSession().run(inputs)) {
            // Dynamically extract output tensor
            float[] outputArray = tensorProcessor.extractDynamicOutputArray(result, outputInfoMap);
            return bitmapProcessor.createResultBitmap(imageData, outputArray, isImageInputGrayscale);
        } finally {
            for (OnnxTensor t : inputs.values()) t.close();
        }
    }

    private ChunkConfig calculateChunkConfig(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= MAX_CHUNK_SIZE && height <= MAX_CHUNK_SIZE) {
            return new ChunkConfig(false, 0, 0, 0, 1, 0, 0);
        }

        // Calculate how many chunks we need in each dimension
        int xChunks = (int) Math.ceil((double) width / MAX_CHUNK_SIZE);
        int yChunks = (int) Math.ceil((double) height / MAX_CHUNK_SIZE);
        
        // Calculate optimal overlap to ensure smooth transitions
        int overlap = Math.max(MIN_OVERLAP, Math.min(MAX_OVERLAP, 32));
        
        // Calculate effective chunk size (the step size between chunks)
        int effectiveChunkWidth = (width - overlap) / xChunks + overlap;
        int effectiveChunkHeight = (height - overlap) / yChunks + overlap;
        
        // Ensure chunks are not larger than MAX_CHUNK_SIZE
        effectiveChunkWidth = Math.min(effectiveChunkWidth, MAX_CHUNK_SIZE);
        effectiveChunkHeight = Math.min(effectiveChunkHeight, MAX_CHUNK_SIZE);
        
        return new ChunkConfig(true, effectiveChunkWidth, effectiveChunkHeight, overlap, xChunks * yChunks, xChunks, yChunks);
    }

    private OnnxTensor createQualityFactorTensor() throws OrtException {
        return scunetSession == null ?
            OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(new float[]{strength / 100.0f}), new long[]{1, 1}) :
            null;
    }

    private Map<String, OnnxTensor> buildInputMap(OnnxTensor inputTensor, OnnxTensor qfTensor) {
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input", inputTensor);
        if (qfTensor != null) {
            inputs.put("qf", qfTensor);
        }
        return inputs;
    }

    private OrtSession getCurrentSession() {
        return scunetSession != null ? scunetSession : fbcnnSession;
    }

    private void checkCancellation() {
        if (isCancelled || Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Processing cancelled");
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
            .setTitle(context.getString(R.string.error_dialog_title))
            .setMessage(errorText)
            .setPositiveButton(context.getString(R.string.ok_button), null)
            .setNeutralButton(context.getString(R.string.copy_button), (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Error", errorText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, context.getString(R.string.error_copied_to_clipboard_toast), Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    // Configuration class for chunking parameters
    private static class ChunkConfig {
        final boolean needsChunking;
        final int chunkWidth;
        final int chunkHeight;
        final int overlap;
        final int totalChunks;
        final int xChunks;
        final int yChunks;

        ChunkConfig(boolean needsChunking, int chunkWidth, int chunkHeight, int overlap, int totalChunks, int xChunks, int yChunks) {
            this.needsChunking = needsChunking;
            this.chunkWidth = chunkWidth;
            this.chunkHeight = chunkHeight;
            this.overlap = overlap;
            this.totalChunks = totalChunks;
            this.xChunks = xChunks;
            this.yChunks = yChunks;
        }
    }

    public static class ChunkInfo {
        final int x, y;
        final int width, height;
        final File chunkFile;
        final int gridX, gridY;
        File processedFile;

        ChunkInfo(int x, int y, int width, int height, File chunkFile, int gridX, int gridY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.chunkFile = chunkFile;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }

    // Data class for image pixel data
    public static class ImageData {
        final int width, height;
        final int[] pixels;
        final boolean hasAlpha;
        final float[] alphaChannel;

        ImageData(int width, int height, int[] pixels, boolean hasAlpha, float[] alphaChannel) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.hasAlpha = hasAlpha;
            this.alphaChannel = alphaChannel;
        }
    }

    // Chunk management class
    private static class ChunkManager {
        private final File chunkDir;
        private final File processedDir;

        ChunkManager(Context context) {
            this.chunkDir = new File(context.getCacheDir(), "chunkAccumulator");
            this.processedDir = new File(context.getCacheDir(), "processedChunkDir");
        }

        void prepareDirectories() {
            if (!chunkDir.exists()) chunkDir.mkdirs();
            if (!processedDir.exists()) processedDir.mkdirs();
            clearDirectory(chunkDir);
            clearDirectory(processedDir);
        }

        List<ChunkInfo> createChunks(Bitmap bitmap, ChunkConfig config) throws IOException {
            List<ChunkInfo> chunks = new ArrayList<>();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Calculate the step size (distance between chunk origins)
            int stepX = (width - config.overlap) / config.xChunks;
            int stepY = (height - config.overlap) / config.yChunks;

            for (int yi = 0; yi < config.yChunks; yi++) {
                for (int xi = 0; xi < config.xChunks; xi++) {
                    // Calculate chunk position
                    int x = xi * stepX;
                    int y = yi * stepY;
                    
                    // For the last chunk in each dimension, align to the edge
                    if (xi == config.xChunks - 1) {
                        x = width - config.chunkWidth;
                    }
                    if (yi == config.yChunks - 1) {
                        y = height - config.chunkHeight;
                    }
                    
                    // Ensure we don't go beyond boundaries
                    x = Math.max(0, Math.min(x, width - config.chunkWidth));
                    y = Math.max(0, Math.min(y, height - config.chunkHeight));
                    
                    // Calculate actual chunk dimensions
                    int chunkWidth = Math.min(config.chunkWidth, width - x);
                    int chunkHeight = Math.min(config.chunkHeight, height - y);

                    // Skip if chunk would be too small
                    if (chunkWidth < 32 || chunkHeight < 32) {
                        continue;
                    }

                    Bitmap chunk = Bitmap.createBitmap(bitmap, x, y, chunkWidth, chunkHeight);
                    File chunkFile = new File(chunkDir, "chunk_" + xi + "_" + yi + ".png");
                    
                    saveBitmapToFile(chunk, chunkFile);
                    chunk.recycle();
                    
                    chunks.add(new ChunkInfo(x, y, chunkWidth, chunkHeight, chunkFile, xi, yi));
                }
            }
            
            return chunks;
        }

        File saveProcessedChunk(Bitmap processed, ChunkInfo chunk) throws IOException {
            File outFile = new File(processedDir, "processed_" + chunk.gridX + "_" + chunk.gridY + ".png");
            saveBitmapToFile(processed, outFile);
            return outFile;
        }

        void cleanup() {
            clearDirectory(chunkDir);
            clearDirectory(processedDir);
        }

        private void saveBitmapToFile(Bitmap bitmap, File file) throws IOException {
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }

        private void clearDirectory(File dir) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
        }
    }

    // Tensor processing utilities
    private static class TensorProcessor {
        
        float[] prepareInputTensor(ImageData imageData, boolean isGrayscale) {
            int w = imageData.width;
            int h = imageData.height;
            
            if (isGrayscale) {
                return prepareGrayscaleTensor(imageData.pixels, w, h);
            } else {
                return prepareColorTensor(imageData.pixels, w, h);
            }
        }

        private float[] prepareGrayscaleTensor(int[] pixels, int w, int h) {
            float[] inputArray = new float[h * w];
            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int color = pixels[idx];
                    int gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                    inputArray[idx] = gray / 255.0f;
                }
            }
            return inputArray;
        }

        private float[] prepareColorTensor(int[] pixels, int w, int h) {
            float[] inputArray = new float[3 * h * w];
            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int color = pixels[idx];
                    inputArray[0 * h * w + idx] = Color.red(color) / 255.0f;
                    inputArray[1 * h * w + idx] = Color.green(color) / 255.0f;
                    inputArray[2 * h * w + idx] = Color.blue(color) / 255.0f;
                }
            }
            return inputArray;
        }

        float[] extractDynamicOutputArray(OrtSession.Result result, Map<String, NodeInfo> outputInfoMap) throws OrtException {
            // Try all outputs in order, return the first float[] or float[][][][] found
            for (int i = 0; i < result.size(); i++) {
                OnnxValue val = result.get(i);
                if (val == null) continue;
                Object outputValue = val.getValue();
                if (outputValue == null) continue;
                if (outputValue instanceof float[]) {
                    return (float[]) outputValue;
                } else if (outputValue instanceof float[][][][]) {
                    return flatten4DArray((float[][][][]) outputValue);
                }
            }
            throw new RuntimeException("Unexpected ONNX output type");
        }

        private float[] flatten4DArray(float[][][][] arr) {
            int c = arr[0].length;
            int h = arr[0][0].length;
            int w = arr[0][0][0].length;
            float[] outputArray = new float[c * h * w];
            
            for (int ch = 0; ch < c; ch++) {
                for (int i = 0; i < h; i++) {
                    for (int j = 0; j < w; j++) {
                        outputArray[ch * h * w + i * w + j] = arr[0][ch][i][j];
                    }
                }
            }
            return outputArray;
        }
    }

    // Bitmap processing utilities
    private static class BitmapProcessor {
        
        ImageData extractImageData(Bitmap bitmap) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            boolean hasAlpha = bitmap.getConfig() == Bitmap.Config.ARGB_8888;

            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            float[] alphaChannel = null;
            if (hasAlpha) {
                alphaChannel = extractAlphaChannel(pixels);
            }

            return new ImageData(w, h, pixels, hasAlpha, alphaChannel);
        }

        private float[] extractAlphaChannel(int[] pixels) {
            float[] alphaChannel = new float[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                alphaChannel[i] = Color.alpha(pixels[i]) / 255.0f;
            }
            return alphaChannel;
        }

        Bitmap createResultBitmap(ImageData imageData, float[] outputArray, boolean isGrayscale) {
            int w = imageData.width;
            int h = imageData.height;
            boolean hasAlpha = imageData.hasAlpha;
            
            Bitmap resultBitmap = Bitmap.createBitmap(w, h, hasAlpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            int[] outPixels = new int[w * h];
            
            if (isGrayscale) {
                fillGrayscalePixels(outPixels, outputArray, imageData.alphaChannel, w, h, hasAlpha);
            } else {
                fillColorPixels(outPixels, outputArray, imageData.alphaChannel, w, h, hasAlpha);
            }
            
            resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h);
            return resultBitmap;
        }

        private void fillGrayscalePixels(int[] outPixels, float[] outputArray, float[] alphaChannel, int w, int h, boolean hasAlpha) {
            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    int idx = i * w + j;
                    int gray = Math.max(0, Math.min(255, (int) (outputArray[idx] * 255.0f)));
                    int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                    outPixels[idx] = Color.argb(alpha, gray, gray, gray);
                }
            }
        }

        private void fillColorPixels(int[] outPixels, float[] outputArray, float[] alphaChannel, int w, int h, boolean hasAlpha) {
            for (int i = 0; i < h; i++) {
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

        Bitmap reassembleChunks(Bitmap originalBitmap, List<ChunkInfo> chunks, ChunkConfig config) {
            int originalWidth = originalBitmap.getWidth();
            int originalHeight = originalBitmap.getHeight();
            Bitmap result = Bitmap.createBitmap(originalWidth, originalHeight, originalBitmap.getConfig());
            
            // Create a 2D array to track which chunks go where
            ChunkInfo[][] chunkGrid = new ChunkInfo[config.yChunks][config.xChunks];
            for (ChunkInfo chunk : chunks) {
                if (chunk.gridY < config.yChunks && chunk.gridX < config.xChunks) {
                    chunkGrid[chunk.gridY][chunk.gridX] = chunk;
                }
            }
            
            // Process chunks in grid order
            for (int yi = 0; yi < config.yChunks; yi++) {
                for (int xi = 0; xi < config.xChunks; xi++) {
                    ChunkInfo chunk = chunkGrid[yi][xi];
                    if (chunk == null) continue;
                    
                    Bitmap processed = BitmapFactory.decodeFile(chunk.processedFile.getAbsolutePath());
                    if (processed == null) continue;
                    
                    // Calculate the region to copy from the processed chunk
                    int srcLeft = 0;
                    int srcTop = 0;
                    int srcRight = processed.getWidth();
                    int srcBottom = processed.getHeight();
                    
                    // Calculate destination position in the result image
                    int dstLeft = chunk.x;
                    int dstTop = chunk.y;
                    
                    // Adjust for overlaps (skip overlap regions except for edge chunks)
                    if (xi > 0) { // Not leftmost chunk
                        srcLeft = config.overlap / 2;
                        dstLeft += config.overlap / 2;
                    }
                    if (yi > 0) { // Not topmost chunk
                        srcTop = config.overlap / 2;
                        dstTop += config.overlap / 2;
                    }
                    if (xi < config.xChunks - 1) { // Not rightmost chunk
                        srcRight -= config.overlap / 2;
                    }
                    if (yi < config.yChunks - 1) { // Not bottommost chunk
                        srcBottom -= config.overlap / 2;
                    }
                    
                    int copyWidth = Math.min(srcRight - srcLeft, originalWidth - dstLeft);
                    int copyHeight = Math.min(srcBottom - srcTop, originalHeight - dstTop);
                    
                    if (copyWidth > 0 && copyHeight > 0) {
                        int[] pixels = new int[copyWidth * copyHeight];
                        processed.getPixels(pixels, 0, copyWidth, srcLeft, srcTop, copyWidth, copyHeight);
                        result.setPixels(pixels, 0, copyWidth, dstLeft, dstTop, copyWidth, copyHeight);
                    }
                    processed.recycle();
                }
            }
            return result;
        }
    }
}
