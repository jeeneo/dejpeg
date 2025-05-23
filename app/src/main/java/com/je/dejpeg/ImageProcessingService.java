package com.je.dejpeg;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageProcessingService extends IntentService {

    public static final String ACTION_PROCESS_IMAGE = "com.je.dejpeg.action.PROCESS_IMAGE";
    public static final String ACTION_CANCEL_PROCESSING = "com.je.dejpeg.action.CANCEL_PROCESSING";

    public static final String EXTRA_QUEUE_ITEM_ID = "com.je.dejpeg.extra.QUEUE_ITEM_ID";
    public static final String EXTRA_IMAGE_URI = "com.je.dejpeg.extra.IMAGE_URI";
    public static final String EXTRA_MODEL_NAME = "com.je.dejpeg.extra.MODEL_NAME"; // Or path
    public static final String EXTRA_STRENGTH = "com.je.dejpeg.extra.STRENGTH";
    public static final String EXTRA_IS_GREYSCALE = "com.je.dejpeg.extra.IS_GREYSCALE";

    public static final String BROADCAST_PROCESSING_STARTED = "com.je.dejpeg.broadcast.PROCESSING_STARTED";
    public static final String BROADCAST_PROCESSING_PROGRESS = "com.je.dejpeg.broadcast.PROCESSING_PROGRESS"; // For detailed progress if any
    public static final String BROADCAST_PROCESSING_COMPLETE = "com.je.dejpeg.broadcast.PROCESSING_COMPLETE";
    public static final String BROADCAST_PROCESSING_ERROR = "com.je.dejpeg.broadcast.PROCESSING_ERROR";
    public static final String EXTRA_PROCESSED_URI = "com.je.dejpeg.extra.PROCESSED_URI";
    public static final String EXTRA_ERROR_MESSAGE = "com.je.dejpeg.extra.ERROR_MESSAGE";
    public static final String BROADCAST_PROCESSING_CANCELLED = "com.je.dejpeg.broadcast.PROCESSING_CANCELLED";


    private ModelManager modelManager;
    private ImageProcessor imageProcessor;

    public ImageProcessingService() {
        super("ImageProcessingService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        modelManager = new ModelManager(getApplicationContext());
        // Ensure ImageProcessor can be initialized correctly.
        // If it still needs MainActivity context for some reason (e.g. specific UI logs not yet removed),
        // this might need further refactoring in ImageProcessor.
        // Assume for now it can work with ApplicationContext or the Service context.
        imageProcessor = new ImageProcessor(getApplicationContext(), modelManager);
    }

    private void sendProcessingBroadcast(String action, int queueItemId, String processedUri, String errorMessage) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_QUEUE_ITEM_ID, queueItemId);
        if (processedUri != null) intent.putExtra(EXTRA_PROCESSED_URI, processedUri);
        if (errorMessage != null) intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();
        if (ACTION_PROCESS_IMAGE.equals(action)) {
            final int queueItemId = intent.getIntExtra(EXTRA_QUEUE_ITEM_ID, -1);
            final String imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI);
            final String modelName = intent.getStringExtra(EXTRA_MODEL_NAME);
            final float strength = intent.getFloatExtra(EXTRA_STRENGTH, 50f);
            final boolean isGreyscale = intent.getBooleanExtra(EXTRA_IS_GREYSCALE, false);

            if (queueItemId == -1 || imageUriString == null || modelName == null) {
                sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, queueItemId, null, "Invalid processing parameters.");
                return;
            }

            sendProcessingBroadcast(BROADCAST_PROCESSING_STARTED, queueItemId, null, null);

            Uri imageUri = Uri.parse(imageUriString);
            Bitmap inputBitmap;
            try {
                inputBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                // Handle WebP conversion if necessary
                String mimeType = getContentResolver().getType(imageUri);
                if ("image/webp".equals(mimeType)) {
                    // Basic WebP conversion: save as PNG then reload.
                    // This is a simplified approach. A more robust solution might involve a library.
                    File tempFile = new File(getCacheDir(), "temp_webp_conversion.png");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        inputBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    inputBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(tempFile));
                    tempFile.delete();
                }
            } catch (IOException e) {
                sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, queueItemId, null, "Failed to load input image: " + e.getMessage());
                return;
            }

            if (inputBitmap == null) {
                sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, queueItemId, null, "Failed to load input image (bitmap is null).");
                return;
            }

            // Ensure Model is Active
            // Note: `isModelAvailable` is a hypothetical method. `ModelManager` would need to implement it.
            // For now, we assume `setActiveModel` handles loading if the model is valid.
            if (!modelManager.hasActiveModel() || (modelName != null && !modelName.equals(modelManager.getActiveModelName()))) {
                if (modelName != null && modelManager.isModelNameValid(modelName)) { // Assuming isModelNameValid as a placeholder for actual validation
                    modelManager.setActiveModel(modelName); // This might involve loading weights, etc.
                     if (!modelManager.hasActiveModel()) { // Check if setActiveModel was successful
                        sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, queueItemId, null, "Failed to activate model: " + modelName);
                        return;
                    }
                } else {
                    sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, queueItemId, null, "Selected model is not available: " + modelName);
                    return;
                }
            }


            final int currentItemId = queueItemId; // For use in callback
            // The isGreyscale parameter handling would depend on ImageProcessor's capabilities.
            // Assuming ImageProcessor uses the model (which might be color/greyscale specific)
            // or has its own mechanism if isGreyscale needs to be explicitly passed.
            imageProcessor.processImage(
                inputBitmap,
                strength,
                new ImageProcessor.ProcessCallback() {
                    @Override
                    public void onProgress(String message) {
                        // Optionally send BROADCAST_PROCESSING_PROGRESS
                        // sendProcessingBroadcast(BROADCAST_PROCESSING_PROGRESS, currentItemId, message, null);
                    }

                    @Override
                    public void onComplete(Bitmap resultBitmap) {
                        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "processed");
                        if (!outputDir.exists()) {
                            outputDir.mkdirs();
                        }
                        String inputFileName = "unknown_image";
                        try {
                            String path = imageUri.getPath();
                            if (path != null) {
                                inputFileName = new File(path).getName();
                            }
                        } catch (Exception e) {
                            // Could not get name from URI path, use default
                        }

                        String outputFileName = "processed_" + System.currentTimeMillis() + "_" + inputFileName;
                        if (!outputFileName.toLowerCase().endsWith(".png") && !outputFileName.toLowerCase().endsWith(".jpg") && !outputFileName.toLowerCase().endsWith(".jpeg")) {
                            outputFileName += ".png";
                        }

                        File outputFile = new File(outputDir, outputFileName);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            Uri processedUri = Uri.fromFile(outputFile);
                            MediaScannerConnection.scanFile(ImageProcessingService.this,
                                new String[]{outputFile.getAbsolutePath()}, null, null);
                            sendProcessingBroadcast(BROADCAST_PROCESSING_COMPLETE, currentItemId, processedUri.toString(), null);
                        } catch (IOException e) {
                            sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, currentItemId, null, "Failed to save processed image: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        sendProcessingBroadcast(BROADCAST_PROCESSING_ERROR, currentItemId, null, "Processing error: " + error);
                    }
                },
                0, // currentImageIndex (0 for single image processing by service)
                1  // totalImages (1 for single image processing by service)
            );

        } else if (ACTION_CANCEL_PROCESSING.equals(action)) {
            int queueItemIdToCancel = intent.getIntExtra(EXTRA_QUEUE_ITEM_ID, -1);
            if (imageProcessor != null) {
                imageProcessor.cancelProcessing(); // Attempt to cancel
            }
            // Notify UI that cancellation was requested. The actual stop comes via callback's onError/onComplete.
            sendProcessingBroadcast(BROADCAST_PROCESSING_CANCELLED, queueItemIdToCancel, null, "Cancellation requested");
        }
    }
}
