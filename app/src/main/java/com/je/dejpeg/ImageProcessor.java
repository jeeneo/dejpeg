package com.je.dejpeg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageProcessor {
    
    public static class ProcessingState {
        private static volatile ProcessingState instance;
        private volatile boolean isProcessing;
        private volatile int currentImageIndex;
        private volatile int totalImages;
        private volatile int currentChunk;
        private volatile int totalChunks;
        
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
        
        public synchronized String getProgressString(Context context) {
            if (!isProcessing) return "";
            
            if (totalChunks > 1 && totalImages > 1) {
                return context.getString(R.string.processing_image_batch_chunked, 
                    getDisplayImageNumber(), totalImages, currentChunk, totalChunks);
            } else if (totalChunks > 1) {
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
    private static final int MAX_INPUT_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 32;

    // Add max concurrent chunks based on available memory
    private static final int MAX_CONCURRENT_CHUNKS = 2;
    private static final long MEMORY_CHUNK_THRESHOLD = 1024 * 1024 * 100; // 100MB threshold

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
    private ExecutorService executorService = Executors.newFixedThreadPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
    );
    
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
        if (processingThread != null) {
            try {
                executorService.shutdownNow(); // Interrupt all running tasks
                processingThread.interrupt();
                processingThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                processingThread = null;
                // Recreate the executor service for future use
                executorService = Executors.newFixedThreadPool(
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
                );
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
        // Check if estimated memory usage is within bounds
        long estimatedMemory = width * height * 4L * 2L; // RGB + working buffer
        return estimatedMemory <= MEMORY_CHUNK_THRESHOLD;
    }

    private Bitmap processImageChunk(Bitmap chunkBitmap, float strength, boolean isGrayscaleModel) {
        try {
            int width = chunkBitmap.getWidth();
            int height = chunkBitmap.getHeight();
            boolean hasAlpha = chunkBitmap.getConfig() == Bitmap.Config.ARGB_8888;

            // Validate chunk size
            if (!validateChunkSize(width, height)) {
                throw new IllegalArgumentException("Chunk size exceeds memory limits");
            }

            int[] pixels = getOrCreatePixelBuffer(width * height);
            chunkBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // Fix tensor buffer size calculation
            int tensorSize = (isGrayscaleModel ? 1 : 3) * height * width;
            float[] inputArray = getOrCreateTensorBuffer(tensorSize);
            FloatBuffer inputBuffer = FloatBuffer.wrap(inputArray);
            inputBuffer.limit(tensorSize); // Ensure buffer size matches tensor requirements

            float[] alphaChannel = hasAlpha ? new float[width * height] : null;

            if (isGrayscaleModel) {
                for (int i = 0; i < pixels.length; i++) {
                    int color = pixels[i];
                    inputArray[i] = ((Color.red(color) + Color.green(color) + Color.blue(color)) / 3) / 255.0f;
                    if (hasAlpha) alphaChannel[i] = Color.alpha(color) / 255.0f;
                }
            } else {
                for (int i = 0; i < pixels.length; i++) {
                    int color = pixels[i];
                    inputArray[i] = Color.red(color) / 255.0f;
                    inputArray[i + height * width] = Color.green(color) / 255.0f;
                    inputArray[i + 2 * height * width] = Color.blue(color) / 255.0f;
                    if (hasAlpha) alphaChannel[i] = Color.alpha(color) / 255.0f;
                }
            }

            long[] inputShape = new long[]{1, isGrayscaleModel ? 1 : 3, height, width};
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
                int h = arr[0][0].length;
                int w = arr[0][0][0].length;
                outputArray = new float[c * h * w];
                for (int ch = 0; ch < c; ch++) {
                    for (int i = 0; i < h; i++) {
                        for (int j = 0; j < w; j++) {
                            outputArray[ch * h * w + i * w + j] = arr[0][ch][i][j];
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

            // Optimize result bitmap creation
            int[] outPixels = pixels; // Reuse input pixel array
            if (isGrayscaleModel) {
                for (int i = 0; i < pixels.length; i++) {
                    int gray = Math.max(0, Math.min(255, (int) (outputArray[i] * 255.0f)));
                    int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[i] * 255.0f))) : 255;
                    outPixels[i] = Color.argb(alpha, gray, gray, gray);
                }
            } else {
                for (int i = 0; i < pixels.length; i++) {
                    int r = Math.max(0, Math.min(255, (int) (outputArray[i] * 255.0f)));
                    int g = Math.max(0, Math.min(255, (int) (outputArray[i + height * width] * 255.0f)));
                    int b = Math.max(0, Math.min(255, (int) (outputArray[i + 2 * height * width] * 255.0f)));
                    int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[i] * 255.0f))) : 255;
                    outPixels[i] = Color.argb(alpha, r, g, b);
                }
            }

            Bitmap resultBitmap = acquireBitmap(width, height);
            resultBitmap.setPixels(outPixels, 0, width, 0, 0, width, height);
            return resultBitmap;
        } catch (Exception e) {
            android.util.Log.e("ImageProcessor", "Error processing chunk", e);
            showErrorDialog(e.getMessage(), e);
            throw new RuntimeException("Error processing chunk: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
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
                    throw e;
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

    private Bitmap processLargeImage(Bitmap sourceBitmap, float strength, boolean isGrayscaleModel, 
            ProcessCallback callback, int currentIndex, int totalImages) {
        try {
            int sourceWidth = sourceBitmap.getWidth();
            int sourceHeight = sourceBitmap.getHeight();
            Bitmap resultBitmap = acquireBitmap(sourceWidth, sourceHeight);

            // Calculate effective chunk size (accounting for overlap)
            int effectiveChunkSize = MAX_CHUNK_SIZE - CHUNK_OVERLAP * 2;
            int numRows = (int) Math.ceil((double) sourceHeight / effectiveChunkSize);
            int numCols = (int) Math.ceil((double) sourceWidth / effectiveChunkSize);
            int totalChunks = numRows * numCols;

            AtomicInteger processedChunks = new AtomicInteger(0);
            List<Future<Void>> futures = new ArrayList<>();
            java.util.concurrent.Semaphore chunkLimiter = 
                new java.util.concurrent.Semaphore(MAX_CONCURRENT_CHUNKS);

            for (int row = 0; row < numRows && !isCancelled; row++) {
                for (int col = 0; col < numCols && !isCancelled; col++) {
                    final int finalRow = row;
                    final int finalCol = col;

                    // Calculate chunk boundaries with proper overlap
                    final int startY = Math.max(0, finalRow * effectiveChunkSize - CHUNK_OVERLAP);
                    final int startX = Math.max(0, finalCol * effectiveChunkSize - CHUNK_OVERLAP);
                    final int endY = Math.min(sourceHeight, (finalRow + 1) * effectiveChunkSize + CHUNK_OVERLAP);
                    final int endX = Math.min(sourceWidth, (finalCol + 1) * effectiveChunkSize + CHUNK_OVERLAP);

                    futures.add(executorService.submit(() -> {
                        try {
                            chunkLimiter.acquire();
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

                            synchronized (resultBitmap) {
                                // Calculate copy boundaries excluding overlap
                                final int copyStartY = (startY > 0) ? CHUNK_OVERLAP : 0;
                                final int copyStartX = (startX > 0) ? CHUNK_OVERLAP : 0;
                                final int copyEndY = endY - startY - 
                                    (endY < sourceHeight ? CHUNK_OVERLAP : 0);
                                final int copyEndX = endX - startX - 
                                    (endX < sourceWidth ? CHUNK_OVERLAP : 0);
                                final int copyWidth = copyEndX - copyStartX;
                                final int copyHeight = copyEndY - copyStartY;

                                int[] chunkPixels = new int[copyWidth * copyHeight];
                                processedChunk.getPixels(chunkPixels, 0, copyWidth, 
                                    copyStartX, copyStartY, copyWidth, copyHeight);
                                resultBitmap.setPixels(chunkPixels, 0, copyWidth,
                                    startX + copyStartX, startY + copyStartY, 
                                    copyWidth, copyHeight);
                            }
                            processedChunk.recycle();

                            processedChunks.incrementAndGet();
                            return null;
                        } catch (InterruptedException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            chunkLimiter.release();
                            System.gc();
                        }
                    }));
                }
            }

            // Wait for completion or cancellation
            try {
                for (Future<Void> future : futures) {
                    if (!isCancelled) {
                        try {
                            future.get();
                        } catch (InterruptedException e) {
                            futures.forEach(f -> f.cancel(true));
                            throw e;
                        }
                    } else {
                        futures.forEach(f -> f.cancel(true));
                        throw new InterruptedException("Processing cancelled");
                    }
                }
                return resultBitmap;
            } catch (InterruptedException e) {
                releaseBitmap(resultBitmap);
                throw e;
            } catch (Exception e) {
                releaseBitmap(resultBitmap);
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isActiveModelSCUNet() {
        String modelName = modelManager.getActiveModelName();
        return modelName != null && (modelName.startsWith("scunet_") || modelName.equals("scunet_color_real_gan.onnx"));
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