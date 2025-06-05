package com.je.dejpeg.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingState {

    // Number of CPU cores used for processing
    public static int coresUsed = Runtime.getRuntime().availableProcessors();

    // Chunk progress state
    public static final AtomicInteger currentChunkStart = new AtomicInteger(0);
    public static final AtomicInteger currentChunkEnd = new AtomicInteger(0);
    public static final AtomicInteger totalChunks = new AtomicInteger(0);

    // Image queue state, with chunk substate
    public static final AtomicInteger currentImageIndex = new AtomicInteger(0);
    public static final AtomicInteger totalImages = new AtomicInteger(0);
    public static final AtomicInteger currentImageChunkStart = new AtomicInteger(0);
    public static final AtomicInteger currentImageChunkEnd = new AtomicInteger(0);
    public static final AtomicInteger currentImageTotalChunks = new AtomicInteger(0);

    // New active chunk counter for currently running tasks
    public static final AtomicInteger activeChunks = new AtomicInteger(0);

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
        activeChunks.set(0);
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
        String activeInfo = "";
        if (activeChunks.get() > 0) {
            activeInfo = String.format(", chunks in progress: %d (using %d cores)", 
                activeChunks.get(), coresUsed);
        }

        if (totalImages.get() > 0) {
            return "Processing image " + currentImageIndex.get() + " of " + totalImages.get() +
                    ", chunks " + currentImageChunkStart.get() + "-" + currentImageChunkEnd.get() +
                    " of " + currentImageTotalChunks.get() + activeInfo;
        } else {
            return "Processing chunks " + currentChunkStart.get() + "-" + currentChunkEnd.get() +
                    " of " + totalChunks.get() + activeInfo;
        }
    }
}