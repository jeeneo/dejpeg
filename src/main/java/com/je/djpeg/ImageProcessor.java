package com.je.djpeg;

import android.content.Context;
import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

public class ImageProcessor {
    private static final int MAX_CHUNK_SIZE = 1200;
    private static final int MAX_INPUT_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 32;

    private final ModelManager modelManager;
    private final Context context;
    private Module fbcnnModule = null;
    private Module scunetModule = null;
    public boolean isCancelled = false;
    private Thread processingThread = null;

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
        fbcnnModule = null;
        scunetModule = null;
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

    private void releaseMat(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    private void releaseBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private Bitmap processImageChunk(Mat chunk, float strength, boolean isGrayscaleModel) {
        try {
            chunk.convertTo(chunk, CvType.CV_32F, 1.0 / 255.0);

            int height = chunk.rows();
            int width = chunk.cols();
            float[] inputArray;

            if (isGrayscaleModel) {
                inputArray = new float[height * width];
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        inputArray[i * width + j] = (float) chunk.get(i, j)[0];
                    }
                }
            } else {
                inputArray = new float[3 * height * width];
                for (int c = 0; c < 3; c++) {
                    for (int h = 0; h < height; h++) {
                        for (int w = 0; w < width; w++) {
                            double[] pixel = chunk.get(h, w);
                            inputArray[c * height * width + h * width + w] = (float) (pixel[2 - c]);
                        }
                    }
                }
            }

            Tensor inputTensor = Tensor.fromBlob(inputArray, new long[]{1, isGrayscaleModel ? 1 : 3, height, width});
            Tensor outputTensor;

            if (scunetModule != null) {
                outputTensor = scunetModule.forward(IValue.from(inputTensor)).toTensor();
            } else {
                float qf = strength / 100.0f;
                Tensor qfTensor = Tensor.fromBlob(new float[]{qf}, new long[]{1, 1});
                outputTensor = fbcnnModule.forward(IValue.from(inputTensor), IValue.from(qfTensor)).toTuple()[0].toTensor();
            }

            float[] outputArray = outputTensor.getDataAsFloatArray();
            Mat outputMat = new Mat(height, width, isGrayscaleModel ? CvType.CV_32FC1 : CvType.CV_32FC3);

            if (isGrayscaleModel) {
                for (int i = 0; i < height; i++) {
                    for (int j = 0; j < width; j++) {
                        outputMat.put(i, j, outputArray[i * width + j]);
                    }
                }
                outputMat.convertTo(outputMat, CvType.CV_8UC1, 255.0);
            } else {
                for (int h = 0; h < height; h++) {
                    for (int w = 0; w < width; w++) {
                        float[] pixel = new float[]{
                            outputArray[2 * height * width + h * width + w],
                            outputArray[1 * height * width + h * width + w],
                            outputArray[0 * height * width + h * width + w]
                        };
                        outputMat.put(h, w, pixel);
                    }
                }
                outputMat.convertTo(outputMat, CvType.CV_8UC3, 255.0);
            }

            Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(outputMat, resultBitmap);
            releaseMat(outputMat);
            return resultBitmap;

        } catch (Exception e) {
            throw new RuntimeException("Error processing chunk: " + e.getMessage());
        }
    }

    public void processImage(Bitmap inputBitmap, float strength, ProcessCallback callback, int currentIndex, int totalImages) {
        isCancelled = false;
        processingThread = new Thread(() -> {
            Mat imageMat = null;
            try {
                if (Thread.interrupted()) {
                    callback.onError(context.getString(R.string.processing_cancelled));
                    return;
                }

                // Determine the active model and its type
                String modelName = modelManager.getActiveModelName();
                boolean isSCUNetModel = modelName != null && modelName.startsWith("scunet_");
                boolean isGrayscaleModel = modelName != null && modelName.contains("gray");
                boolean isSpecialGaussianModel = modelName != null && modelName.equals("scunet_color_real_gan.ptl");

                // Load the appropriate model if needed
                if (isSCUNetModel || isSpecialGaussianModel) {
                    if (fbcnnModule != null) unloadModel();
                    if (scunetModule == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        scunetModule = modelManager.loadModel();
                    }
                } else {
                    if (scunetModule != null) unloadModel();
                    if (fbcnnModule == null) {
                        callback.onProgress(context.getString(R.string.loading_model));
                        fbcnnModule = modelManager.loadModel();
                    }
                }

                imageMat = new Mat();
                Utils.bitmapToMat(inputBitmap, imageMat);

                if (isGrayscaleModel) {
                    Mat grayMat = new Mat();
                    org.opencv.imgproc.Imgproc.cvtColor(imageMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
                    releaseMat(imageMat);
                    imageMat = grayMat;
                }

                Bitmap resultBitmap;
                if (imageMat.width() > MAX_INPUT_SIZE || imageMat.height() > MAX_INPUT_SIZE) {
                    resultBitmap = processLargeImage(imageMat, strength, isGrayscaleModel, callback);
                } else {
                    callback.onProgress(totalImages > 1 ? 
                        context.getString(R.string.processing_batch, currentIndex + 1, totalImages) :
                        context.getString(R.string.processing_single));
                    resultBitmap = processImageChunk(imageMat, strength, isGrayscaleModel);
                }

                if (!isCancelled) {
                    callback.onComplete(resultBitmap);
                } else {
                    releaseBitmap(resultBitmap);
                    callback.onError(context.getString(R.string.processing_cancelled));
                }

            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            } finally {
                releaseMat(imageMat);
            }
        });
        processingThread.start();
    }

    private Bitmap processLargeImage(Mat sourceMat, float strength, boolean isGrayscaleModel, ProcessCallback callback) {
        int sourceWidth = sourceMat.width();
        int sourceHeight = sourceMat.height();
        
        Mat resultMat = new Mat(sourceHeight, sourceWidth, sourceMat.type());
        
        int numRows = (int) Math.ceil((double) sourceHeight / MAX_CHUNK_SIZE);
        int numCols = (int) Math.ceil((double) sourceWidth / MAX_CHUNK_SIZE);
        int totalChunks = numRows * numCols;
        int processedChunks = 0;

        try {
            for (int row = 0; row < numRows && !isCancelled; row++) {
                for (int col = 0; col < numCols && !isCancelled; col++) {
                    int startY = row * MAX_CHUNK_SIZE - (row > 0 ? CHUNK_OVERLAP : 0);
                    int startX = col * MAX_CHUNK_SIZE - (col > 0 ? CHUNK_OVERLAP : 0);
                    int endY = Math.min((row + 1) * MAX_CHUNK_SIZE + (row < numRows - 1 ? CHUNK_OVERLAP : 0), sourceHeight);
                    int endX = Math.min((col + 1) * MAX_CHUNK_SIZE + (col < numCols - 1 ? CHUNK_OVERLAP : 0), sourceWidth);

                    Mat chunk = new Mat(sourceMat, new org.opencv.core.Rect(startX, startY, endX - startX, endY - startY));
                    callback.onProgress(context.getString(R.string.processing_chunk, ++processedChunks, totalChunks));

                    Bitmap processedChunk = processImageChunk(chunk, strength, isGrayscaleModel);
                    releaseMat(chunk);

                    Mat processedMat = new Mat();
                    Utils.bitmapToMat(processedChunk, processedMat);
                    releaseBitmap(processedChunk);

                    // Calculate the region to copy (excluding overlap)
                    int copyStartY = row > 0 ? CHUNK_OVERLAP : 0;
                    int copyStartX = col > 0 ? CHUNK_OVERLAP : 0;
                    int copyWidth = endX - startX - (col < numCols - 1 ? CHUNK_OVERLAP : 0) - copyStartX;
                    int copyHeight = endY - startY - (row < numRows - 1 ? CHUNK_OVERLAP : 0) - copyStartY;

                    Mat copyRegion = new Mat(processedMat, new org.opencv.core.Rect(copyStartX, copyStartY, copyWidth, copyHeight));
                    copyRegion.copyTo(new Mat(resultMat, new org.opencv.core.Rect(
                        startX + copyStartX,
                        startY + copyStartY,
                        copyWidth,
                        copyHeight
                    )));

                    releaseMat(processedMat);
                    releaseMat(copyRegion);
                }
            }

            if (isCancelled) {
                releaseMat(resultMat);
                return null;
            }

            Bitmap finalBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultMat, finalBitmap);
            
            if (!isCancelled) {
                callback.onProgress(context.getString(R.string.processing_complete_single));
            }
            
            return finalBitmap;

        } finally {
            releaseMat(resultMat);
        }
    }

    public boolean isActiveModelSCUNet() {
        String modelName = modelManager.getActiveModelName();
        return modelName != null && (modelName.startsWith("scunet_") || modelName.equals("scunet_color_real_gan.ptl"));
    }
}