package com.je.dejpeg.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingState {

    // Number of CPU cores used for processing
    public static int coresUsed = Runtime.getRuntime().availableProcessors();

    // Current chunk range being processed (e.g., 1-4 out of 12)
    public static final AtomicInteger currentChunkStart = new AtomicInteger(0);
    public static final AtomicInteger currentChunkEnd = new AtomicInteger(0);
    public static final AtomicInteger totalChunks = new AtomicInteger(0);

    // Image queue state (e.g., image 3 of 10), with chunk substate
    public static final AtomicInteger currentImageIndex = new AtomicInteger(0);
    public static final AtomicInteger totalImages = new AtomicInteger(0);
    public static final AtomicInteger currentImageChunkStart = new AtomicInteger(0);
    public static final AtomicInteger currentImageChunkEnd = new AtomicInteger(0);
    public static final AtomicInteger currentImageTotalChunks = new AtomicInteger(0);

    /**
     * Resets all values to default (should be called at the end of processing)
     */
    public static void reset() {
        currentChunkStart.set(0);
        currentChunkEnd.set(0);
        totalChunks.set(0);
        currentImageIndex.set(0);
        totalImages.set(0);
        currentImageChunkStart.set(0);
        currentImageChunkEnd.set(0);
        currentImageTotalChunks.set(0);
    }

    /**
     * Updates current global chunk processing progress.
     */
    public static void updateChunkProgress(int start, int end, int total) {
        currentChunkStart.set(start);
        currentChunkEnd.set(end);
        totalChunks.set(total);
    }

    /**
     * Updates current image queue processing status.
     */
    public static void updateImageProgress(int imageIndex, int totalImagesCount,
                                           int chunkStart, int chunkEnd, int totalChunksForImage) {
        currentImageIndex.set(imageIndex);
        totalImages.set(totalImagesCount);
        currentImageChunkStart.set(chunkStart);
        currentImageChunkEnd.set(chunkEnd);
        currentImageTotalChunks.set(totalChunksForImage);
    }

    /**
     * Human-readable representation of the current progress.
     */
    public static String getStatusString() {
        if (totalImages.get() > 0) {
            return "Processing image " + currentImageIndex.get() + " of " + totalImages.get() +
                    ", chunks " + currentImageChunkStart.get() + "-" + currentImageChunkEnd.get() +
                    " of " + currentImageTotalChunks.get();
        } else {
            return "Processing chunks " + currentChunkStart.get() + "-" + currentChunkEnd.get() +
                    " of " + totalChunks.get();
        }
    }
}