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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.je.dejpeg.models.ProcessingState;

public class ImageProcessor {
    public static final int DEFAULT_CHUNK_SIZE = 1200; // normal?
    public static final int SCUNET_CHUNK_SIZE = 640; // + overlap = no more than ~720ish should help prevent crashing with scunet
    public static final int OVERLAP = 32;
    public static final int SCUNET_OVERLAP = 128; // needs larger overlap for better blending, doesn't handle edge artifacts well

    private static final int TILE_MEMORY_THRESHOLD = 4096 * 4096; // Added missing constant

    private volatile boolean isCancelled = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Context context;
    private ModelManager modelManager;
    private ChunkProcessor chunkProcessor;
    private ModelInfo modelInfo;

    /***
     * TODO:
     * - Allow user-configurable chunk size and overlap
     */

    public ImageProcessor(Context context, ModelManager modelManager) {
        this.context = context;
        this.modelManager = modelManager;
        chunkProcessor = new ChunkProcessor(context);
    }

    public void cancelProcessing() {
        isCancelled = true;
        executor.shutdownNow();
    }

    public void processImage(Bitmap inputBitmap, float strength, ProcessCallback callback, int index, int total) {
        isCancelled = false;
        // Move everything to the executor thread
        executor.submit(() -> {
            try {
                String modelName = modelManager.getActiveModelName();
                modelInfo = new ModelInfo(modelManager, modelName, strength);

                ProcessingState.Companion.updateImageProgress(index + 1, total);
                ProcessingState.Companion.updateImageDimensions(inputBitmap.getWidth(), inputBitmap.getHeight());

                int width = inputBitmap.getWidth();
                int height = inputBitmap.getHeight();
                int effectiveChunkSize = modelInfo.chunkSize;
                int overlap = modelInfo.overlap;
                int totalChunks = (width > effectiveChunkSize || height > effectiveChunkSize) ?
                    ((int)Math.ceil((double)width / (effectiveChunkSize - overlap))) *
                    ((int)Math.ceil((double)height / (effectiveChunkSize - overlap))) : 1;
                ProcessingState.Companion.updateChunkProgress(totalChunks);
                ProcessingState.Companion.initializeTimeEstimation(context, modelName != null ? modelName : "unknown", totalChunks);

                if (callback != null) {
                    callback.onProgress(ProcessingState.Companion.getStatusString(context));
                }

                OrtSession session = modelManager.loadModel();
                Bitmap result = processBitmapUnified(session, inputBitmap, callback, modelInfo);
                if (callback != null) callback.onComplete(result);
            } catch (Exception e) {
                if (callback != null) callback.onError(formatError(e));
            }
        });
    }

    private Bitmap processBitmapUnified(OrtSession session, Bitmap inputBitmap, ProcessCallback callback, ModelInfo info) throws Exception {
        int width = inputBitmap.getWidth();
        int height = inputBitmap.getHeight();
        boolean hasTransparency = detectTransparency(inputBitmap);

        Bitmap.Config processingConfig = Bitmap.Config.ARGB_8888;
        boolean mustTile = (width > info.chunkSize || height > info.chunkSize) || (width * height) > TILE_MEMORY_THRESHOLD;

        if (mustTile) {
            chunkProcessor.prepareDirs();
            List<ChunkProcessor.ChunkInfo> chunks = chunkProcessor.chunkBitmapToDisk(inputBitmap, info.chunkSize, info.overlap);
            ProcessingState.Companion.updateChunkProgress(chunks.size());
            ProcessingState.Companion.getCompletedChunks().set(0);

            if (callback != null) callback.onProgress(ProcessingState.Companion.getStatusString(context));

            for (ChunkProcessor.ChunkInfo chunk : chunks) {
                if (isCancelled) throw new RuntimeException("Processing cancelled");
                Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.chunkFile.getAbsolutePath());
                if (chunkBitmap.getConfig() != processingConfig) {
                    Bitmap converted = chunkBitmap.copy(processingConfig, true);
                    chunkBitmap.recycle();
                    chunkBitmap = converted;
                }
                Bitmap processed = processChunkUnified(session, chunkBitmap, processingConfig, hasTransparency, info);
                chunkProcessor.saveBitmapToFile(processed, chunk.processedFile = chunkProcessor.getProcessedFile(chunk));
                chunkBitmap.recycle();
                processed.recycle();
                ProcessingState.Companion.onChunkCompleted();
                if (callback != null) callback.onProgress(ProcessingState.Companion.getStatusString(context));
            }
            Bitmap result = chunkProcessor.reassembleChunksWithFeathering(chunks, width, height, processingConfig, info.overlap);
            chunkProcessor.clearDirs();
            return result;
        } else {
            Bitmap bitmapToProcess = inputBitmap.getConfig() != processingConfig ?
                inputBitmap.copy(processingConfig, true) : inputBitmap;
            ProcessingState.Companion.updateChunkProgress(1);
            if (callback != null) callback.onProgress(ProcessingState.Companion.getStatusString(context));
            Bitmap result = processChunkUnified(session, bitmapToProcess, processingConfig, hasTransparency, info);
            ProcessingState.Companion.onChunkCompleted();
            return result;
        }
    }

    private Bitmap processChunkUnified(OrtSession session, Bitmap chunk, Bitmap.Config config, boolean hasAlpha, ModelInfo info) throws Exception {
        int w = chunk.getWidth(), h = chunk.getHeight();
        int channels = info.isGrayscale ? 1 : 3;
        int[] pixels = new int[w * h]; // allocate new buffer
        chunk.getPixels(pixels, 0, w, 0, 0, w, h);

        float[] inputArray = new float[channels * w * h]; // allocate new buffer
        float[] alphaChannel = hasAlpha ? new float[w * h] : null; // allocate new buffer if needed

        for (int i = 0; i < w * h; i++) {
            int color = pixels[i];
            if (channels == 1) {
                int gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                inputArray[i] = gray / 255f;
            } else {
                inputArray[i] = Color.red(color) / 255f;
                inputArray[w * h + i] = Color.green(color) / 255f;
                inputArray[2 * w * h + i] = Color.blue(color) / 255f;
            }
            if (hasAlpha) alphaChannel[i] = Color.alpha(color) / 255f;
        }

        if (info.env == null) info.env = OrtEnvironment.getEnvironment();
        long[] inputShape = new long[]{1, channels, h, w};
        Map<String, OnnxTensor> inputs = new HashMap<>();
        OnnxTensor inputTensor = OnnxTensor.createTensor(info.env, FloatBuffer.wrap(inputArray), inputShape);
        inputs.put(info.inputName, inputTensor);

        // Attach extra float inputs if present
        for (Map.Entry<String, NodeInfo> entry : info.inputInfoMap.entrySet()) {
            if (entry.getKey().equals(info.inputName)) continue;
            if (entry.getValue().getInfo() instanceof TensorInfo tinfo && tinfo.type == OnnxJavaType.FLOAT) {
                long[] shape = tinfo.getShape().clone();
                for (int i = 0; i < shape.length; i++) if (shape[i] == -1) shape[i] = 1;
                if (shape.length == 2 && shape[0] == 1 && shape[1] == 1) {
                    inputs.put(entry.getKey(), OnnxTensor.createTensor(info.env, FloatBuffer.wrap(new float[]{info.strength / 100f}), shape));
                }
            }
        }

        try (inputTensor) {
            try (OrtSession.Result result = session.run(inputs)) {
                float[] outputArray = extractOutputArray(result.get(0).getValue(), channels, h, w);
                Bitmap resultBitmap = Bitmap.createBitmap(w, h, config);
                int[] outPixels = new int[w * h]; // allocate new buffer
                for (int i = 0; i < w * h; i++) {
                    int r, g, b, gray, alpha;
                    if (channels == 1) {
                        gray = clamp255(outputArray[i] * 255f);
                        alpha = hasAlpha ? clamp255(alphaChannel[i] * 255f) : 255;
                        outPixels[i] = Color.argb(alpha, gray, gray, gray);
                    } else {
                        r = clamp255(outputArray[i] * 255f);
                        g = clamp255(outputArray[w * h + i] * 255f);
                        b = clamp255(outputArray[2 * w * h + i] * 255f);
                        alpha = hasAlpha ? clamp255(alphaChannel[i] * 255f) : 255;
                        outPixels[i] = Color.argb(alpha, r, g, b);
                    }
                }
                resultBitmap.setPixels(outPixels, 0, w, 0, 0, w, h);
                return resultBitmap;
            }
        } finally {
            for (OnnxTensor t : inputs.values()) t.close();
        }
    }

    private float[] extractOutputArray(Object outputValue, int channels, int h, int w) {
        if (outputValue instanceof float[]) {
            return (float[]) outputValue;
        } else if (outputValue instanceof float[][][][] arr) {
            float[][][][] a = (float[][][][]) outputValue;
            float[] out = new float[channels * h * w];
            for (int ch = 0; ch < channels; ch++)
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        out[ch * h * w + y * w + x] = a[0][ch][y][x];
            return out;
        } else {
            throw new RuntimeException("Unexpected ONNX output type: " + outputValue.getClass());
        }
    }

    private boolean detectTransparency(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (!bitmap.hasAlpha()) return false;
        int sampleW = Math.min(w, 64), sampleH = Math.min(h, 64);
        int[] pixels = new int[sampleW * sampleH]; // allocate new buffer
        bitmap.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH);
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) != 0xFF) return true;
        }
        return false;
    }

    private int clamp255(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    private String formatError(Exception e) {
        String msg = "Error: " + e.getClass().getSimpleName();
        if (e.getMessage() != null) msg += ": " + e.getMessage();
        return msg;
    }

    // --- ModelInfo for session/model metadata ---
    private static class ModelInfo {
        OrtEnvironment env;
        String inputName;
        Map<String, NodeInfo> inputInfoMap;
        boolean isGrayscale;
        int chunkSize, overlap;
        float strength;

        ModelInfo(ModelManager manager, String modelName, float strength) {
            this.strength = strength;
            this.chunkSize = modelName != null && modelName.startsWith("scunet_") ? SCUNET_CHUNK_SIZE : DEFAULT_CHUNK_SIZE;
            this.overlap = modelName != null && modelName.startsWith("scunet_") ? SCUNET_OVERLAP : OVERLAP;
            OrtSession session = null;
            try {
                session = manager.loadModel();
                inputInfoMap = session.getInputInfo();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load model or get input info", e);
            }
            inputName = null;
            isGrayscale = false;
            for (Map.Entry<String, NodeInfo> entry : inputInfoMap.entrySet()) {
                NodeInfo info = entry.getValue();
                if (info.getInfo() instanceof TensorInfo tinfo) {
                    long[] shape = tinfo.getShape();
                    if (tinfo.type == OnnxJavaType.FLOAT && shape.length == 4) {
                        inputName = entry.getKey();
                        isGrayscale = (shape[1] == 1 || shape[1] == -1);
                        break;
                    }
                }
            }
        }
    }

    // --- ChunkProcessor for chunk/tiling logic ---
    private static class ChunkProcessor {
        private File chunkDir, processedDir;
        private Context context;

        ChunkProcessor(Context context) {
            this.context = context;
            chunkDir = new File(context.getCacheDir(), "chunkAccumulator");
            processedDir = new File(context.getCacheDir(), "processedChunkDir");
        }

        void prepareDirs() {
            if (!chunkDir.exists()) chunkDir.mkdirs();
            if (!processedDir.exists()) processedDir.mkdirs();
            clearDir(chunkDir);
            clearDir(processedDir);
        }

        void clearDirs() {
            clearDir(chunkDir);
            clearDir(processedDir);
        }

        void clearDir(File dir) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) f.delete();
            }
        }

        List<ChunkInfo> chunkBitmapToDisk(Bitmap input, int chunkSize, int overlap) throws IOException {
            List<ChunkInfo> chunks = new ArrayList<>();
            int width = input.getWidth(), height = input.getHeight();
            int cols = (int) Math.ceil((double) width / (chunkSize - overlap));
            int rows = (int) Math.ceil((double) height / (chunkSize - overlap));
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int startX = col * (chunkSize - overlap);
                    int startY = row * (chunkSize - overlap);
                    int chunkX = Math.max(0, startX - (col > 0 ? overlap / 2 : 0));
                    int chunkY = Math.max(0, startY - (row > 0 ? overlap / 2 : 0));
                    int chunkW = Math.min(chunkSize + (col > 0 ? overlap / 2 : 0), width - chunkX);
                    int chunkH = Math.min(chunkSize + (row > 0 ? overlap / 2 : 0), height - chunkY);
                    chunkW = Math.min(chunkW, width - chunkX);
                    chunkH = Math.min(chunkH, height - chunkY);
                    if (chunkW <= 0 || chunkH <= 0) continue;
                    Bitmap chunk = Bitmap.createBitmap(input, chunkX, chunkY, chunkW, chunkH);
                    File chunkFile = new File(chunkDir, "chunk_" + chunkX + "_" + chunkY + ".png");
                    saveBitmapToFile(chunk, chunkFile);
                    chunk.recycle();
                    ChunkInfo info = new ChunkInfo(chunkX, chunkY, chunkFile);
                    info.row = row; info.col = col;
                    chunks.add(info);
                }
            }
            return chunks;
        }

        void saveBitmapToFile(Bitmap bitmap, File file) throws IOException {
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }

        File getProcessedFile(ChunkInfo chunk) {
            return new File(processedDir, "chunk_" + chunk.x + "_" + chunk.y + ".png");
        }

        Bitmap reassembleChunksWithFeathering(List<ChunkInfo> chunks, int width, int height, Bitmap.Config config, int overlap) {
            Bitmap result = Bitmap.createBitmap(width, height, config);
            Canvas canvas = new Canvas(result);
            for (ChunkInfo chunk : chunks) {
                Bitmap processed = BitmapFactory.decodeFile(chunk.processedFile.getAbsolutePath());
                Bitmap blendedChunk = createFeatheredChunk(processed, chunk, width, height, overlap);
                Paint paint = new Paint();
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
                canvas.drawBitmap(blendedChunk, chunk.x, chunk.y, paint);
                processed.recycle();
                blendedChunk.recycle();
            }
            return result;
        }

        Bitmap createFeatheredChunk(Bitmap chunk, ChunkInfo chunkInfo, int totalWidth, int totalHeight, int overlap) {
            int chunkW = chunk.getWidth(), chunkH = chunk.getHeight();
            Bitmap feathered = chunk.copy(Bitmap.Config.ARGB_8888, true);
            int[] pixels = new int[chunkW * chunkH];
            feathered.getPixels(pixels, 0, chunkW, 0, 0, chunkW, chunkH);
            int featherSize = overlap / 2;
            for (int y = 0; y < chunkH; y++) {
                for (int x = 0; x < chunkW; x++) {
                    int idx = y * chunkW + x;
                    int pixel = pixels[idx];
                    float alpha = 1.0f;
                    if (chunkInfo.x > 0 && x < featherSize) alpha = Math.min(alpha, (float) x / featherSize);
                    if (chunkInfo.y > 0 && y < featherSize) alpha = Math.min(alpha, (float) y / featherSize);
                    if (chunkInfo.x + chunkW < totalWidth && x >= chunkW - featherSize) alpha = Math.min(alpha, (float) (chunkW - x) / featherSize);
                    if (chunkInfo.y + chunkH < totalHeight && y >= chunkH - featherSize) alpha = Math.min(alpha, (float) (chunkH - y) / featherSize);
                    int newAlpha = (int) (alpha * 255);
                    pixels[idx] = (pixel & 0x00FFFFFF) | (newAlpha << 24);
                }
            }
            feathered.setPixels(pixels, 0, chunkW, 0, 0, chunkW, chunkH);
            return feathered;
        }

        static class ChunkInfo {
            final int x, y;
            final File chunkFile;
            File processedFile;
            int row, col;
            ChunkInfo(int x, int y, File chunkFile) {
                this.x = x; this.y = y; this.chunkFile = chunkFile;
            }
        }
    }

    public interface ProcessCallback {
        void onComplete(Bitmap result);
        void onError(String error);
        void onProgress(String message);
    }
}