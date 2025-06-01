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

public class ImageProcessor {
    private static final int MAX_CHUNK_SIZE = 1200;
    private static final int MAX_INPUT_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 32;

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
        fbcnnSession = null;
        scunetSession = null;
        ortEnv = null;
        modelManager.unloadModel();
    }

    public void cancelProcessing() {
        isCancelled = true;
        if (processingThread != null) {
            try {
                processingThread.interrupt();
                processingThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                processingThread = null;
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

    private Bitmap processImageChunk(Bitmap chunkBitmap, float strength, boolean isGrayscaleModel) {
        try {
            int width = chunkBitmap.getWidth();
            int height = chunkBitmap.getHeight();
            boolean hasAlpha = chunkBitmap.getConfig() == Bitmap.Config.ARGB_8888;

            int[] pixels = new int[width * height];
            chunkBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            float[] inputArray;
            float[] alphaChannel = null;

            if (hasAlpha) {
                alphaChannel = new float[width * height];
            }

            if (isGrayscaleModel) {
                inputArray = new float[height * width];
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        int idx = i * width + j;
                        int color = pixels[idx];
                        int gray = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                        inputArray[idx] = gray / 255.0f;
                        if (hasAlpha) alphaChannel[idx] = Color.alpha(color) / 255.0f;
                    }
                }
            } else {
                inputArray = new float[3 * height * width];
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        int idx = i * width + j;
                        int color = pixels[idx];
                        inputArray[0 * height * width + idx] = Color.red(color) / 255.0f;
                        inputArray[1 * height * width + idx] = Color.green(color) / 255.0f;
                        inputArray[2 * height * width + idx] = Color.blue(color) / 255.0f;
                        if (hasAlpha) alphaChannel[idx] = Color.alpha(color) / 255.0f;
                    }
                }
            }

            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment();
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

            Bitmap resultBitmap = Bitmap.createBitmap(width, height, hasAlpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            int[] outPixels = new int[width * height];
            if (isGrayscaleModel) {
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        int idx = i * width + j;
                        int gray = Math.max(0, Math.min(255, (int) (outputArray[idx] * 255.0f)));
                        int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                        outPixels[idx] = Color.argb(alpha, gray, gray, gray);
                    }
                }
            } else {
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        int idx = i * width + j;
                        int r = Math.max(0, Math.min(255, (int) (outputArray[0 * height * width + idx] * 255.0f)));
                        int g = Math.max(0, Math.min(255, (int) (outputArray[1 * height * width + idx] * 255.0f)));
                        int b = Math.max(0, Math.min(255, (int) (outputArray[2 * height * width + idx] * 255.0f)));
                        int alpha = hasAlpha ? Math.max(0, Math.min(255, (int) (alphaChannel[idx] * 255.0f))) : 255;
                        outPixels[idx] = Color.argb(alpha, r, g, b);
                    }
                }
            }
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
        processingThread = new Thread(() -> {
            try {
                String modelName = modelManager.getActiveModelName();
                boolean isSCUNetModel = modelName != null && modelName.startsWith("scunet_");
                boolean isGrayscaleModel = modelName != null && modelName.contains("gray");
                boolean isSpecialGaussianModel = modelName != null && modelName.equals("scunet_color_real_gan.onnx");

                if (ortEnv == null) {
                    ortEnv = OrtEnvironment.getEnvironment();
                }

                if (isSCUNetModel || isSpecialGaussianModel) {
                    if (fbcnnSession != null) unloadModel();
                    if (scunetSession == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        scunetSession = modelManager.loadModel();
                    }
                } else {
                    if (scunetSession != null) unloadModel();
                    if (fbcnnSession == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        fbcnnSession = modelManager.loadModel();
                    }
                }

                Bitmap workingBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true);

                callback.onProgress(totalImages > 1 ?
                    context.getString(R.string.processing_image_n_of_m, currentIndex + 1, totalImages) :
                    context.getString(R.string.processing_single));

                Bitmap resultBitmap;
                if (workingBitmap.getWidth() > MAX_INPUT_SIZE || workingBitmap.getHeight() > MAX_INPUT_SIZE) {
                    resultBitmap = processLargeImage(workingBitmap, strength, isGrayscaleModel, callback, currentIndex, totalImages);
                } else {
                    resultBitmap = processImageChunk(workingBitmap, strength, isGrayscaleModel);
                }

                if (!isCancelled) {
                    callback.onComplete(resultBitmap);
                } else {
                    releaseBitmap(resultBitmap);
                    callback.onError(context.getString(R.string.processing_cancelled));
                }
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog(e.getMessage(), e);
                callback.onError(e.getMessage());
            }
        });
        processingThread.start();
    }

    private Bitmap processLargeImage(Bitmap sourceBitmap, float strength, boolean isGrayscaleModel, 
            ProcessCallback callback, int currentIndex, int totalImages) {
        int sourceWidth = sourceBitmap.getWidth();
        int sourceHeight = sourceBitmap.getHeight();
        Bitmap resultBitmap = acquireBitmap(sourceWidth, sourceHeight);

        int numRows = (int) Math.ceil((double) sourceHeight / MAX_CHUNK_SIZE);
        int numCols = (int) Math.ceil((double) sourceWidth / MAX_CHUNK_SIZE);
        int totalChunks = numRows * numCols;
        int processedChunks = 0;

        for (int row = 0; row < numRows && !isCancelled; row++) {
            for (int col = 0; col < numCols && !isCancelled; col++) {
                int startY = row * MAX_CHUNK_SIZE - (row > 0 ? CHUNK_OVERLAP : 0);
                int startX = col * MAX_CHUNK_SIZE - (col > 0 ? CHUNK_OVERLAP : 0);
                int endY = Math.min((row + 1) * MAX_CHUNK_SIZE + (row < numRows - 1 ? CHUNK_OVERLAP : 0), sourceHeight);
                int endX = Math.min((col + 1) * MAX_CHUNK_SIZE + (col < numCols - 1 ? CHUNK_OVERLAP : 0), sourceWidth);

                Bitmap chunk = Bitmap.createBitmap(sourceBitmap, startX, startY, endX - startX, endY - startY);
                
                String progress = totalImages > 1 ?
                    context.getString(R.string.processing_image_n_of_m_chunk, currentIndex + 1, totalImages, ++processedChunks, totalChunks) :
                    context.getString(R.string.processing_chunk, processedChunks, totalChunks);
                callback.onProgress(progress);

                Bitmap processedChunk = processImageChunk(chunk, strength, isGrayscaleModel);
                chunk.recycle();

                int copyStartY = row > 0 ? CHUNK_OVERLAP : 0;
                int copyStartX = col > 0 ? CHUNK_OVERLAP : 0;
                int copyWidth = endX - startX - (col < numCols - 1 ? CHUNK_OVERLAP : 0) - copyStartX;
                int copyHeight = endY - startY - (row < numRows - 1 ? CHUNK_OVERLAP : 0) - copyStartY;

                int[] chunkPixels = new int[copyWidth * copyHeight];
                processedChunk.getPixels(chunkPixels, 0, copyWidth, copyStartX, copyStartY, copyWidth, copyHeight);
                resultBitmap.setPixels(chunkPixels, 0, copyWidth, startX + copyStartX, startY + copyStartY, copyWidth, copyHeight);
                processedChunk.recycle();
            }
        }

        return resultBitmap;
    }

    public boolean isActiveModelSCUNet() {
        String modelName = modelManager.getActiveModelName();
        return modelName != null && (modelName.startsWith("scunet_") || modelName.equals("scunet_color_real_gan.onnx"));
    }
}