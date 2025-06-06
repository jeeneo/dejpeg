package com.je.dejpeg.utils;

import android.content.Context;
import com.je.dejpeg.MainActivity;

import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingState {
    public static final AtomicInteger currentImageIndex = new AtomicInteger(0);
    public static final AtomicInteger totalImages = new AtomicInteger(0);
    public static final AtomicInteger activeChunkStart = new AtomicInteger(0);
    public static final AtomicInteger activeChunkEnd = new AtomicInteger(0);
    public static final AtomicInteger totalChunks = new AtomicInteger(0);
    public static final AtomicInteger completedChunks = new AtomicInteger(0);
    public static long startTime = 0;

    public static void updateImageProgress(int current, int total) {
        startTime = System.currentTimeMillis(); // start timing for current image
        currentImageIndex.set(current);
        totalImages.set(total);
        completedChunks.set(0);
    }

    public static void updateChunkProgress(int start, int end, int total) {
        activeChunkStart.set(start);
        activeChunkEnd.set(end);
        totalChunks.set(total);
    }

    public static String getStatusString(Context context, int threadCount) {
        int currentImage = currentImageIndex.get();
        int totalImage = totalImages.get();
        int completed = completedChunks.get();
        int totalChunk = totalChunks.get();
    
        int totalWork = totalImage * totalChunk;
        int workDone = (currentImage - 1) * totalChunk + completed;
    
        // Time estimate logic with thread count
        String timeEstimate = "";
        if (workDone >= threadCount && workDone < totalWork) {
            long elapsedMillis = System.currentTimeMillis() - startTime;
        
            double avgMillisPerChunk = (double) elapsedMillis / workDone;
            int remainingChunks = totalWork - workDone;
        
            long remainingMillis = (long) (avgMillisPerChunk * remainingChunks);
            long remainingSeconds = remainingMillis / 1000;
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;
        
            if (minutes > 0) {
                timeEstimate = String.format(" (~%d min remaining)", minutes);
            } else {
                timeEstimate = String.format(" (~%d sec remaining)", seconds);
            }
        }
    
        if (MainActivity.isProgressPercentage) {
            int percentage = totalWork > 0 ? (workDone * 100 / totalWork) : 0;
            return String.format("processing %d%% complete...%s", percentage, timeEstimate);
        } else {
            return String.format("processing image %d/%d - chunks %d-%d/%d...%s",
                    currentImage, totalImage,
                    activeChunkStart.get(), activeChunkEnd.get(), totalChunk,
                    timeEstimate);
        }
    }
}