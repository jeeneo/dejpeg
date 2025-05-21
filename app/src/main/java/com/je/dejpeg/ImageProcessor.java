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
    private final ModelManager modelManager;
    private final Context context;
    private Module fbcnnModule = null;
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

    public void processImage(Bitmap inputBitmap, float strength, ProcessCallback callback, int currentIndex, int totalImages) {
        isCancelled = false;
        processingThread = new Thread(() -> {
            try {
                if (Thread.interrupted()) {
                    callback.onError(context.getString(R.string.processing_cancelled));
                    return;
                }

                // Unload model if active model type changed
                String modelName = modelManager.getActiveModelName();
                if (fbcnnModule != null) {
                    boolean wasColorModel = modelManager.isColorModel(modelName);
                    boolean isColorModel = modelName != null && modelName.contains("color");
                    if (wasColorModel != isColorModel) {
                        unloadModel();
                    }
                }

                if (fbcnnModule == null) {
                    callback.onProgress(context.getString(R.string.loading_model));
                    fbcnnModule = modelManager.loadModel();
                }

                if (Thread.interrupted()) {
                    callback.onError(context.getString(R.string.processing_cancelled));
                    return;
                }

                // Update progress message for batch processing
                if (totalImages > 1) {
                    callback.onProgress(context.getString(R.string.processing_batch, currentIndex + 1, totalImages));
                } else {
                    callback.onProgress(context.getString(R.string.processing_single));
                }

                boolean isGrayscaleModel = modelName != null && modelName.contains("gray");

                Mat imageMat = new Mat();
                Utils.bitmapToMat(inputBitmap, imageMat);
                
                if (isGrayscaleModel) {
                    Mat grayMat = new Mat();
                    org.opencv.imgproc.Imgproc.cvtColor(imageMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
                    imageMat = grayMat;
                }

                imageMat.convertTo(imageMat, CvType.CV_32F, 1.0/255.0);

                if (Thread.interrupted()) {
                    callback.onError("Processing cancelled");
                    return;
                }

                int height = imageMat.rows();
                int width = imageMat.cols();
                float[] inputArray;
                
                if (isGrayscaleModel) {
                    inputArray = new float[height * width];
                    for (int i = 0; i < height; i++) {
                        for (int j = 0; j < width; j++) {
                            inputArray[i * width + j] = (float)imageMat.get(i, j)[0];
                        }
                    }
                } else {
                    inputArray = new float[3 * height * width];
                    for (int c = 0; c < 3; c++) {
                        for (int i = 0; i < height; i++) {
                            for (int j = 0; j < width; j++) {
                                double[] pixel = imageMat.get(i, j);
                                inputArray[c * height * width + i * width + j] = (float)(pixel[2 - c]);
                            }
                        }
                    }
                }

                Tensor inputTensor = Tensor.fromBlob(inputArray,
                        new long[]{1, isGrayscaleModel ? 1 : 3, height, width});

                float qf = strength / 100.0f;
                Tensor qfTensor = Tensor.fromBlob(new float[]{qf}, new long[]{1, 1});

                IValue[] outputTuple = fbcnnModule.forward(
                        IValue.from(inputTensor),
                        IValue.from(qfTensor)
                ).toTuple();

                Tensor outputTensor = outputTuple[0].toTensor();
                float[] outputArray = outputTensor.getDataAsFloatArray();

                Mat outputMat = new Mat(height, width, isGrayscaleModel ? CvType.CV_32FC1 : CvType.CV_32FC3);
                
                if (isGrayscaleModel) {
                    for (int i = 0; i < height; i++) {
                        for (int j = 0; j < width; j++) {
                            float value = outputArray[i * width + j];
                            outputMat.put(i, j, value);
                        }
                    }
                    outputMat.convertTo(outputMat, CvType.CV_8UC1, 255.0);
                    Mat colorMat = new Mat();
                    org.opencv.imgproc.Imgproc.cvtColor(outputMat, colorMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2BGR);
                    outputMat = colorMat;
                } else {
                    for (int i = 0; i < height; i++) {
                        for (int j = 0; j < width; j++) {
                            float b = outputArray[2 * height * width + i * width + j];
                            float g = outputArray[1 * height * width + i * width + j];
                            float r = outputArray[0 * height * width + i * width + j];
                            outputMat.put(i, j, new float[]{b, g, r});
                        }
                    }
                    outputMat.convertTo(outputMat, CvType.CV_8UC3, 255.0);
                }

                if (!isCancelled) {
                    Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(outputMat, outputBitmap);
                    callback.onComplete(outputBitmap);
                } else {
                    callback.onError(context.getString(R.string.processing_cancelled));
                }

            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            } finally {
                if (isCancelled) {
                }
            }
        });
        processingThread.start();
    }

    public void unloadModel() {
        fbcnnModule = null;
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
}
