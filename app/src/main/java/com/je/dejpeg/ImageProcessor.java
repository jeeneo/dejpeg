package com.je.dejpeg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtException;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.io.FileOutputStream;

public class ImageProcessor {
    
    public static class ProcessingState {
        private static volatile ProcessingState instance;
        private volatile boolean isProcessing;
        private volatile int currentImageIndex;
        private volatile int totalImages;
        private volatile int currentChunk;
        private volatile int totalChunks;
        private volatile int activeChunks;
        private final List<Integer> activeChunksList = new ArrayList<>();
        
        private ProcessingState() {
            reset();
        }
        
        public static ProcessingState getInstance() {
            if (instance == null) {
                synchronized (ProcessingState.class) {
                    if (instance == null) {
                        instance = new ProcessingState();
                    }
                }
            }
            return instance;
        }
        
        public synchronized void reset() {
            isProcessing = false;
            currentImageIndex = 0;
            totalImages = 0;
            currentChunk = 0;
            totalChunks = 0;
            activeChunks = 0;
            activeChunksList.clear();
        }
        
        public synchronized void setImageProgress(int current, int total) {
            currentImageIndex = current;
            totalImages = total;
            isProcessing = true;
        }
        
        public synchronized void setChunkProgress(int current, int total) {
            currentChunk = current;
            totalChunks = total;
        }
        
        public synchronized int getDisplayImageNumber() {
            return currentImageIndex + 1;
        }
        
        public synchronized void incrementActiveChunks() {
            activeChunks++;
        }
        
        public synchronized void addActiveChunk(int chunk) {
            activeChunksList.add(chunk);
            activeChunks = activeChunksList.size();
        }
        
        public synchronized void removeActiveChunk(int chunk) {
            activeChunksList.remove(Integer.valueOf(chunk));
            activeChunks = activeChunksList.size();
        }
        
        public synchronized int getActiveChunks() {
            return activeChunks;
        }
        
        private String getActiveChunksRange() {
            if (activeChunksList.isEmpty()) return "";
            Collections.sort(activeChunksList);
            StringBuilder ranges = new StringBuilder();
            int start = activeChunksList.get(0);
            int prev = start;
            
            for (int i = 1; i < activeChunksList.size(); i++) {
                int curr = activeChunksList.get(i);
                if (curr > prev + 1) {
                    // End current range
                    if (prev > start) {
                        ranges.append(start).append("-").append(prev).append(",");
                    } else {
                        ranges.append(start).append(",");
                    }
                    start = curr;
                }
                prev = curr;
            }
            
            // Handle last range
            if (prev > start) {
                ranges.append(start).append("-").append(prev);
            } else {
                ranges.append(start);
            }
            
            return ranges.toString();
        }
        
        public synchronized String getProgressString(Context context) {
            if (!isProcessing || context == null) return "";
            
            String chunksRange = getActiveChunksRange();
            if (totalChunks > 1 && totalImages > 1) {
                if (activeChunks > 1) {
                    return context.getString(R.string.processing_image_batch_chunked_concurrent, 
                        getDisplayImageNumber(), totalImages, chunksRange, currentChunk, totalChunks, activeChunks);
                }
                return context.getString(R.string.processing_image_batch_chunked, 
                    getDisplayImageNumber(), totalImages, currentChunk, totalChunks);
            } else if (totalChunks > 1) {
                if (activeChunks > 1) {
                    return context.getString(R.string.processing_image_chunked_concurrent, 
                        currentChunk, totalChunks, activeChunks);
                }
                return context.getString(R.string.processing_image_chunked, 
                    currentChunk, totalChunks);
            } else if (totalImages > 1) {
                return context.getString(R.string.processing_image_batch, 
                    getDisplayImageNumber(), totalImages);
            } else {
                return context.getString(R.string.processing_image_single);
            }
        }
        
        public boolean isProcessing() {
            return isProcessing;
        }
        
        public synchronized void setComplete() {
            isProcessing = false;
        }
        
        public synchronized int getCurrentImage() {
            return currentImageIndex;
        }
        
        public synchronized int getTotalImages() {
            return totalImages;
        }
    }

    private static final int MAX_CHUNK_SIZE = 1200;
    private static final int MIN_CHUNK_SIZE = 900;
    private static final int MAX_INPUT_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 32;

    private static final int MAX_CONCURRENT_CHUNKS = Math.max(1, Math.min(8, (int)Math.ceil(Runtime.getRuntime().availableProcessors() / 2.0)));
    private static final long MEMORY_CHUNK_THRESHOLD = 1024 * 1024 * 600;

    private final ModelManager modelManager;
    private final Context context;
    private OrtSession fbcnnSession = null;
    private OrtSession scunetSession = null;
    private OrtEnvironment ortEnv = null;
    public boolean isCancelled = false;
    private Thread processingThread = null;

    private final android.graphics.Bitmap.Config defaultConfig = Bitmap.Config.ARGB_8888;
    private final androidx.collection.LruCache<String, Bitmap> bitmapPool =
        new androidx.collection.LruCache<String, Bitmap>(4) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getAllocationByteCount();
            }
        };

    // Add thread pool for parallel processing
    private ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_CHUNKS);
    
    // Add reusable buffers
    private final ThreadLocal<int[]> pixelBuffer = new ThreadLocal<>();
    private final ThreadLocal<float[]> tensorBuffer = new ThreadLocal<>();

    public interface ProcessCallback {
        void onProgress(String message);
        void onComplete(Bitmap result);
        void onError(String error);
    }

    public ImageProcessor(Context context, ModelManager modelManager) {
        this.context = context;
        this.modelManager = modelManager;
    }

    public void unloadModel() {
        if (fbcnnSession != null) {
            try {
                fbcnnSession.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            fbcnnSession = null;
        }
        
        if (scunetSession != null) {
            try {
                scunetSession.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            scunetSession = null;
        }
        
        if (ortEnv != null) {
            try {
                ortEnv.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ortEnv = null;
        }
        
        System.gc();
    }

    public void cancelProcessing() {
        isCancelled = true;
        ProcessingState.getInstance().reset();
        
        // First cancel all running tasks
        executorService.shutdownNow();
        
        if (processingThread != null) {
            try {
                // Interrupt the main processing thread
                processingThread.interrupt();
                // Wait for a bit to let it clean up
                processingThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                processingThread = null;
                // Create fresh executor service
                executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_CHUNKS);
                // Clear any remaining state
                System.gc();
            }
        }
    }

    private void releaseBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private Bitmap acquireBitmap(int width, int height) {
        String key = width + "x" + height;
        Bitmap bitmap = bitmapPool.get(key);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmapPool.remove(key);
            bitmap.eraseColor(Color.TRANSPARENT);
            return bitmap;
        }
        return Bitmap.createBitmap(width, height, defaultConfig);
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            String key = bitmap.getWidth() + "x" + bitmap.getHeight();
            bitmapPool.put(key, bitmap);
        }
    }

    private float[] getOrCreateTensorBuffer(int size) {
        float[] buffer = tensorBuffer.get();
        if (buffer == null || buffer.length < size) {
            buffer = new float[size];
            tensorBuffer.set(buffer);
        }
        return buffer;
    }

    private int[] getOrCreatePixelBuffer(int size) {
        int[] buffer = pixelBuffer.get();
        if (buffer == null || buffer.length < size) {
            buffer = new int[size];
            pixelBuffer.set(buffer);
        }
        return buffer;
    }

    private boolean validateChunkSize(int width, int height) {
        if (width > MAX_CHUNK_SIZE || height > MAX_CHUNK_SIZE) {
            String error = String.format("Chunk size %dx%d exceeds maximum allowed size of %d", 
                width, height, MAX_CHUNK_SIZE);
            android.util.Log.e("ImageProcessor", error);
            throw new IllegalArgumentException(error);
        }
        long estimatedMemory = width * height * 4L * 2L;
        return estimatedMemory <= MEMORY_CHUNK_THRESHOLD;
    }

    private boolean validateBuffer(int size, int channels, int width, int height) {
        long totalSize = (long)channels * width * height;
        if (totalSize > Integer.MAX_VALUE) {
            String error = String.format("Buffer size %d exceeds maximum array size", totalSize);
            android.util.Log.e("ImageProcessor", error);
            throw new IllegalArgumentException(error);
        }
        return true;
    }

    private Bitmap processImageChunk(Bitmap chunkBitmap, float strength, boolean isGrayscaleModel) {
        if (Thread.interrupted() || isCancelled) {
            throw new RuntimeException("Processing cancelled");
        }
        try {
            int width = chunkBitmap.getWidth();
            int height = chunkBitmap.getHeight();
            boolean hasAlpha = chunkBitmap.getConfig() == Bitmap.Config.ARGB_8888;

            // Validate chunk size
            if (!validateChunkSize(width, height)) {
                throw new IllegalArgumentException("Chunk size exceeds memory limits");
            }

            // Validate buffer sizes
            int tensorChannels = isGrayscaleModel ? 1 : 3;
            validateBuffer(tensorChannels, width, height, 1);

            int[] pixels = getOrCreatePixelBuffer(width * height);
            chunkBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            int tensorSize = tensorChannels * width * height;
            float[] inputArray = getOrCreateTensorBuffer(tensorSize);
            float[] alphaChannel = hasAlpha ? new float[width * height] : null;

            // Fill tensor buffer with correct indexing
            int pixelCount = width * height;
            if (isGrayscaleModel) {
                for (int i = 0; i < pixelCount; i++) {
                    int color = pixels[i];
                    inputArray[i] = ((Color.red(color) + Color.green(color) + Color.blue(color)) / 3) / 255.0f;
                    if (hasAlpha) alphaChannel[i] = Color.alpha(color) / 255.0f;
                }
            } else {
                for (int i = 0; i < pixelCount; i++) {
                    int color = pixels[i];
                    // RGB channels are stored sequentially: RRRGGGBBB
                    inputArray[i] = Color.red(color) / 255.0f;
                    inputArray[i + pixelCount] = Color.green(color) / 255.0f;
                    inputArray[i + (2 * pixelCount)] = Color.blue(color) / 255.0f;
                    if (hasAlpha) alphaChannel[i] = Color.alpha(color) / 255.0f;
                }
            }

            // Process with ONNX model
            OnnxTensor inputTensor = null;
            OnnxTensor qfTensor = null;
            OrtSession.Result result = null;
            try {
                long[] inputShape = new long[]{1, tensorChannels, height, width};
                inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputArray), inputShape);
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input", inputTensor);

                OrtSession session;
                if (scunetSession != null) {
                    session = scunetSession;
                } else {
                    session = fbcnnSession;
                    float qf = strength / 100.0f;
                    qfTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(new float[]{qf}), new long[]{1, 1});
                    inputs.put("qf", qfTensor);
                }

                result = session.run(inputs);
                float[] outputArray = processModelOutput(result.get(0).getValue());

                // Create result bitmap
                return createResultBitmap(outputArray, width, height, alphaChannel, isGrayscaleModel);
            } finally {
                if (inputTensor != null) inputTensor.close();
                if (qfTensor != null) qfTensor.close();
                if (result != null) result.close();
            }
        } catch (Exception e) {
            android.util.Log.e("ImageProcessor", "Error processing chunk", e);
            showErrorDialog(e.getMessage(), e);
            throw new RuntimeException("Error processing chunk: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private float[] processModelOutput(Object outputValue) {
        if (outputValue == null) {
            throw new RuntimeException("ONNX model output is null");
        }
        if (outputValue instanceof float[]) {
            return (float[]) outputValue;
        }
        if (outputValue instanceof float[][][][]) {
            return convertMultiDimensionalOutput((float[][][][]) outputValue);
        }
        throw new RuntimeException("Unexpected ONNX output type: " + outputValue.getClass());
    }

    private float[] convertMultiDimensionalOutput(float[][][][] arr) {
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

    private Bitmap createResultBitmap(float[] outputArray, int width, int height, float[] alphaChannel, boolean isGrayscaleModel) {
        int[] pixels = getOrCreatePixelBuffer(width * height);
        int pixelCount = width * height;

        if (isGrayscaleModel) {
            for (int i = 0; i < pixelCount; i++) {
                int gray = Math.max(0, Math.min(255, (int) (outputArray[i] * 255.0f)));
                int alpha = alphaChannel != null ? Math.max(0, Math.min(255, (int) (alphaChannel[i] * 255.0f))) : 255;
                pixels[i] = Color.argb(alpha, gray, gray, gray);
            }
        } else {
            for (int i = 0; i < pixelCount; i++) {
                int r = Math.max(0, Math.min(255, (int) (outputArray[i] * 255.0f)));
                int g = Math.max(0, Math.min(255, (int) (outputArray[i + pixelCount] * 255.0f)));
                int b = Math.max(0, Math.min(255, (int) (outputArray[i + (2 * pixelCount)] * 255.0f)));
                int alpha = alphaChannel != null ? Math.max(0, Math.min(255, (int) (alphaChannel[i] * 255.0f))) : 255;
                pixels[i] = Color.argb(alpha, r, g, b);
            }
        }

        Bitmap resultBitmap = acquireBitmap(width, height);
        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return resultBitmap;
    }

    private void showErrorDialog(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(message);
        if (throwable != null) {
            sb.append("\n\n").append(android.util.Log.getStackTraceString(throwable));
        }
        String errorText = sb.toString();

        if (Looper.myLooper() != Looper.getMainLooper()) {
            ((AppCompatActivity) context).runOnUiThread(() -> showErrorDialog(message, throwable));
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

    public void processImage(Bitmap inputBitmap, float strength, ProcessCallback callback, int currentIndex, int totalImages) {
        isCancelled = false;
        ProcessingState.getInstance().setImageProgress(currentIndex, totalImages);
        
        processingThread = new Thread(() -> {
            try {
                if (isCancelled) {
                    throw new InterruptedException("Processing cancelled");
                }
                // Create a new environment if null
                if (ortEnv == null) {
                    ortEnv = OrtEnvironment.getEnvironment();
                }
                
                String modelName = modelManager.getActiveModelName();
                boolean isSCUNetModel = modelName != null && modelName.startsWith("scunet_");
                boolean isGrayscaleModel = modelName != null && modelName.contains("gray");
                boolean isSpecialGaussianModel = modelName != null && modelName.equals("scunet_color_real_gan.onnx");

                // Load appropriate model session
                if (isSCUNetModel || isSpecialGaussianModel) {
                    if (fbcnnSession != null) {
                        unloadModel();
                        ortEnv = OrtEnvironment.getEnvironment();
                    }
                    if (scunetSession == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        scunetSession = modelManager.loadModel();
                    }
                } else {
                    if (scunetSession != null) {
                        unloadModel();
                        ortEnv = OrtEnvironment.getEnvironment();
                    }
                    if (fbcnnSession == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        fbcnnSession = modelManager.loadModel();
                    }
                }

                Bitmap workingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true);

                callback.onProgress(totalImages > 1 ?
                    context.getString(R.string.processing_image_batch, currentIndex + 1, totalImages) :
                    context.getString(R.string.processing_image_single));

                Bitmap resultBitmap = null;
                try {
                    if (workingBitmap.getWidth() > MAX_INPUT_SIZE || workingBitmap.getHeight() > MAX_INPUT_SIZE) {
                        resultBitmap = processLargeImage(workingBitmap, strength, isGrayscaleModel, callback, currentIndex, totalImages);
                    } else {
                        resultBitmap = processImageChunk(workingBitmap, strength, isGrayscaleModel);
                    }

                    if (!isCancelled && resultBitmap != null) {
                        ProcessingState.getInstance().setComplete();
                        callback.onComplete(resultBitmap);
                    } else {
                        ProcessingState.getInstance().reset();
                        if (resultBitmap != null) {
                            releaseBitmap(resultBitmap);
                        }
                        callback.onError(context.getString(R.string.processing_cancelled_toast));
                    }
                } catch (Exception e) {
                    ProcessingState.getInstance().reset();
                    if (resultBitmap != null) {
                        releaseBitmap(resultBitmap);
                    }
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                ProcessingState.getInstance().reset();
                e.printStackTrace();
                if (!(e instanceof InterruptedException)) {
                    showErrorDialog(e.getMessage(), e);
                }
                callback.onError(e.getMessage());
            }
        });
        processingThread.start();
    }

    private synchronized void updateProgress(ProcessCallback callback, int currentIndex, int totalImages, 
            int processedChunks, int totalChunks) {
        ProcessingState.getInstance().setImageProgress(currentIndex, totalImages);
        ProcessingState.getInstance().setChunkProgress(processedChunks, totalChunks);
        callback.onProgress(ProcessingState.getInstance().getProgressString(context));
    }

    private static class TempChunkFile implements AutoCloseable {
        final File file;
        final int startX, startY, width, height;

        TempChunkFile(File file, int startX, int startY, int width, int height) {
            this.file = file;
            this.startX = startX;
            this.startY = startY;
            this.width = width;
            this.height = height;
        }

        @Override
        public void close() {
            file.delete();
        }
    }

    private File getTempDir() {
        File tempDir = new File(context.getCacheDir(), "processing_chunks");
        tempDir.mkdirs();
        return tempDir;
    }

    private Bitmap processLargeImage(Bitmap sourceBitmap, float strength, boolean isGrayscaleModel, 
            ProcessCallback callback, int currentIndex, int totalImages) {
        File tempDir = getTempDir();
        List<TempChunkFile> chunkFiles = new ArrayList<>();
        
        try {
            // Add early cancellation check
            if (isCancelled) {
                throw new InterruptedException("Processing cancelled");
            }
            int sourceWidth = sourceBitmap.getWidth();
            int sourceHeight = sourceBitmap.getHeight();
            Bitmap resultBitmap = acquireBitmap(sourceWidth, sourceHeight);

            // Calculate optimal number of chunks while respecting min/max sizes
            int numRows = (int) Math.ceil((double) sourceHeight / MAX_CHUNK_SIZE);
            int numCols = (int) Math.ceil((double) sourceWidth / MAX_CHUNK_SIZE);
            
            // Adjust chunk sizes to be more even
            int chunkHeight = (int) Math.ceil((double) sourceHeight / numRows);
            int chunkWidth = (int) Math.ceil((double) sourceWidth / numCols);
            
            // If chunk sizes are below minimum, reduce number of chunks
            if (chunkHeight < MIN_CHUNK_SIZE) {
                numRows = Math.max(1, (int) Math.ceil((double) sourceHeight / MIN_CHUNK_SIZE));
                chunkHeight = (int) Math.ceil((double) sourceHeight / numRows);
            }
            if (chunkWidth < MIN_CHUNK_SIZE) {
                numCols = Math.max(1, (int) Math.ceil((double) sourceWidth / MIN_CHUNK_SIZE));
                chunkWidth = (int) Math.ceil((double) sourceWidth / numCols);
            }

            int totalChunks = numRows * numCols;

            AtomicInteger processedChunks = new AtomicInteger(0);
            List<Future<Void>> futures = new ArrayList<>();
            java.util.concurrent.Semaphore chunkLimiter = 
                new java.util.concurrent.Semaphore(MAX_CONCURRENT_CHUNKS);

            for (int row = 0; row < numRows && !isCancelled; row++) {
                for (int col = 0; col < numCols && !isCancelled; col++) {
                    final int finalRow = row;
                    final int finalCol = col;
                    
                    // Calculate chunk boundaries with overlap
                    final int startY = Math.max(0, row * chunkHeight - (row > 0 ? CHUNK_OVERLAP : 0));
                    final int startX = Math.max(0, col * chunkWidth - (col > 0 ? CHUNK_OVERLAP : 0));
                    final int endY = Math.min(sourceHeight, (row + 1) * chunkHeight + 
                        (row < numRows - 1 ? CHUNK_OVERLAP : 0));
                    final int endX = Math.min(sourceWidth, (col + 1) * chunkWidth + 
                        (col < numCols - 1 ? CHUNK_OVERLAP : 0));

                    futures.add(executorService.submit(() -> {
                        File chunkFile = null;
                        try {
                            chunkLimiter.acquire();
                            ProcessingState.getInstance().addActiveChunk(processedChunks.get() + 1);
                            if (Thread.interrupted() || isCancelled) {
                                throw new InterruptedException("Processing cancelled");
                            }

                            updateProgress(callback, currentIndex, totalImages, 
                                processedChunks.get() + 1, totalChunks);

                            // Process the chunk
                            Bitmap chunk = Bitmap.createBitmap(sourceBitmap, 
                                startX, startY, endX - startX, endY - startY);
                            Bitmap processedChunk = processImageChunk(chunk, strength, isGrayscaleModel);
                            chunk.recycle();

                            // Calculate effective boundaries excluding overlap
                            final int copyStartY = (startY > 0) ? CHUNK_OVERLAP : 0;
                            final int copyStartX = (startX > 0) ? CHUNK_OVERLAP : 0;
                            final int copyEndY = endY - startY - (endY < sourceHeight ? CHUNK_OVERLAP : 0);
                            final int copyEndX = endX - startX - (endX < sourceWidth ? CHUNK_OVERLAP : 0);
                            final int copyWidth = copyEndX - copyStartX;
                            final int copyHeight = copyEndY - copyStartY;

                            // Create a new bitmap without overlap regions
                            Bitmap trimmedChunk = Bitmap.createBitmap(processedChunk, 
                                copyStartX, copyStartY, copyWidth, copyHeight);
                            processedChunk.recycle();

                            // Save processed chunk to temp file
                            chunkFile = new File(tempDir, String.format("chunk_%d_%d.tmp", finalRow, finalCol));
                            try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                                trimmedChunk.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            }
                            
                            synchronized (chunkFiles) {
                                chunkFiles.add(new TempChunkFile(chunkFile,
                                    startX + copyStartX, startY + copyStartY,
                                    copyWidth, copyHeight));
                            }

                            trimmedChunk.recycle();
                            processedChunks.incrementAndGet();
                            System.gc();
                            return null;
                        } catch (Exception e) {
                            if (chunkFile != null) chunkFile.delete();
                            if (e instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            throw new RuntimeException(e);
                        } finally {
                            ProcessingState.getInstance().removeActiveChunk(processedChunks.get());
                            chunkLimiter.release();
                        }
                    }));
                }
            }

            // Wait for all chunks to be processed
            for (Future<Void> future : futures) {
                if (!isCancelled) {
                    try {
                        future.get();
                    } catch (InterruptedException e) {
                        futures.forEach(f -> f.cancel(true));
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        throw new RuntimeException(e);
                    }
                } else {
                    futures.forEach(f -> f.cancel(true));
                    throw new InterruptedException("Processing cancelled");
                }
            }

            // Combine all chunks
            for (TempChunkFile chunk : chunkFiles) {
                try {
                    Bitmap chunkBitmap = BitmapFactory.decodeFile(chunk.file.getAbsolutePath());
                    int[] pixels = new int[chunk.width * chunk.height];
                    chunkBitmap.getPixels(pixels, 0, chunk.width, 0, 0, chunk.width, chunk.height);
                    resultBitmap.setPixels(pixels, 0, chunk.width, chunk.startX, chunk.startY, 
                        chunk.width, chunk.height);
                    chunkBitmap.recycle();
                } finally {
                    chunk.close();
                }
            }

            return resultBitmap;
        } catch (ExecutionException e) {
            for (TempChunkFile chunk : chunkFiles) {
                chunk.close();
            }
            throw new RuntimeException("ExecutionException occurred", e);
        } catch (Exception e) {
            for (TempChunkFile chunk : chunkFiles) {
                chunk.close();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Processing interrupted", e);
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            File[] remainingFiles = tempDir.listFiles();
            if (remainingFiles != null) {
                for (File f : remainingFiles) f.delete();
            }
            tempDir.delete();
        }
    }

    public boolean isActiveModelSCUNet() {
        String modelName = modelManager.getActiveModelName();
        return modelName != null && (modelName.startsWith("scunet_"));
    }

    // Add cleanup method
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}