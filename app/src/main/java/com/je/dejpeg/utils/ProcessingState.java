package com.je.dejpeg.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.je.dejpeg.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessingState {
    private static volatile ProcessingState instance;
    private static final String PROGRESS_CALLBACK_TYPE = "progress_callback_type";
    private static final String FORMAT_PLAINTEXT = "PLAINTEXT";
    private static final String FORMAT_PERCENTAGE = "PERCENTAGE";

    // Add multi-threading capability detection
    public static final boolean SUPPORTS_PARALLEL_PROCESSING = Runtime.getRuntime().availableProcessors() > 1;
    public static final int MAX_PARALLEL_TASKS = Math.max(1, Math.min(8, (int)Math.ceil(Runtime.getRuntime().availableProcessors() / 2.0)));

    private volatile boolean isProcessing;
    private volatile int currentImageIndex;
    private volatile int totalImages;
    private volatile int currentChunk;
    private volatile int totalChunks;
    private volatile int activeChunks;
    private final List<Integer> activeChunksList = new ArrayList<>();

    // Add tracking for parallel image processing
    private final CopyOnWriteArrayList<ProcessingItem> activeItems = new CopyOnWriteArrayList<>();

    // Add progress tracking for chunked processing
    private volatile int totalProgress;
    private final AtomicInteger activeChunksCounter = new AtomicInteger(0);
    private volatile int completedChunks;
    private final CopyOnWriteArrayList<ChunkRange> activeRanges = new CopyOnWriteArrayList<>();

    private static class ChunkRange {
        final int start;
        final int end;

        ChunkRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class ProcessingItem {
        public final int imageIndex;
        public final boolean requiresChunking;
        public final int width;
        public final int height;
        public volatile int currentChunk;
        public final int totalChunks;

        public ProcessingItem(int imageIndex, boolean requiresChunking, int width, int height, int totalChunks) {
            this.imageIndex = imageIndex;
            this.requiresChunking = requiresChunking;
            this.width = width;
            this.height = height;
            this.totalChunks = totalChunks;
            this.currentChunk = 0;
        }
    }

    private ProcessingState(Context context) {
        loadProgressFormat(context);
        reset();
    }

    public static ProcessingState getInstance(Context context) {
        if (instance == null) {
            synchronized (ProcessingState.class) {
                if (instance == null) {
                    instance = new ProcessingState(context);
                }
            }
        }
        return instance;
    }

    public void loadProgressFormat(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        progressFormat = prefs.getString(PROGRESS_CALLBACK_TYPE, FORMAT_PLAINTEXT);
    }

    public synchronized void reset() {
        isProcessing = false;
        currentImageIndex = 0;
        totalImages = 0;
        currentChunk = 0;
        totalChunks = 0;
        activeChunks = 0;
        totalProgress = 0;
        activeChunksList.clear();
        activeItems.clear();
        activeRanges.clear();
    }

    public synchronized void setImageProgress(int current, int total) {
        currentImageIndex = current;
        totalImages = total;
        isProcessing = true;
    }

    public synchronized void addProcessingItem(int imageIndex, boolean requiresChunking, int width, int height, int totalChunks) {
        activeItems.add(new ProcessingItem(imageIndex, requiresChunking, width, height, totalChunks));
    }

    public synchronized void updateItemProgress(int imageIndex, int currentChunk) {
        for (ProcessingItem item : activeItems) {
            if (item.imageIndex == imageIndex) {
                item.currentChunk = currentChunk;
                break;
            }
        }
    }

    public synchronized void removeProcessingItem(int imageIndex) {
        activeItems.removeIf(item -> item.imageIndex == imageIndex);
    }

    public synchronized void updateChunkProgress(int start, int end) {
        activeRanges.clear();
        activeRanges.add(new ChunkRange(start, end));
    }

    public synchronized void setCompletedChunks(int completed) {
        this.completedChunks = completed;
    }

    private String progressFormat;

    public void setProgressFormat(String format) {
        this.progressFormat = format;
    }

    public synchronized String getProgressString(Context context) {
        if (!isProcessing || (activeItems.isEmpty() && totalImages == 0)) {
            return context.getString(R.string.processing_preparing);
        }

        switch (progressFormat) {
            case FORMAT_PERCENTAGE:
                return getPercentageProgressString(context);
            case FORMAT_PLAINTEXT:
                return getPlainTextProgressString(context);
            default:
                return ("Unknown progress format: " + progressFormat);
        }
    }

    private synchronized String getPlainTextProgressString(Context context) {
        StringBuilder progress = new StringBuilder();
        int activeCount = activeItems.size();

        if (activeCount > 1) {
            progress.append(context.getString(R.string.processing_multiple_images, activeCount, totalImages));
            for (ProcessingItem item : activeItems) {
                progress.append("\n").append(getItemProgressString(context, item));
            }
        } else if (activeCount == 1) {
            ProcessingItem item = activeItems.get(0);
            progress.append(getItemProgressString(context, item));
        }

        return progress.toString();
    }

    private synchronized String getPercentageProgressString(Context context) {
        StringBuilder progress = new StringBuilder();
        int activeCount = activeItems.size();

        if (activeCount > 1) {
            int batchProgress = (currentImageIndex * 100) / Math.max(1, totalImages);
            progress.append(String.format("Batch: %d%% (%d/%d images)", batchProgress, activeCount, totalImages));
            for (ProcessingItem item : activeItems) {
                progress.append("\n").append(getItemPercentageProgressString(item));
            }
        } else if (activeCount == 1) {
            ProcessingItem item = activeItems.get(0);
            progress.append(getItemPercentageProgressString(item));
        }

        return progress.toString();
    }

    private String getItemProgressString(Context context, ProcessingItem item) {
        if (item.requiresChunking && SUPPORTS_PARALLEL_PROCESSING && !activeRanges.isEmpty()) {
            ChunkRange range = activeRanges.get(0);
            if (totalImages > 1) {
                return String.format("Image %d/%d: processing chunks %d-%d out of %d, %d completed",
                    item.imageIndex + 1, totalImages, range.start, range.end, item.totalChunks, completedChunks);
            }
            return String.format("Processing chunks %d-%d out of %d, %d completed",
                range.start, range.end, item.totalChunks, completedChunks);
        }

        if (totalImages > 1) {
            return context.getString(R.string.processing_image_batch, item.imageIndex + 1, totalImages);
        }
        return context.getString(R.string.processing_image_single);
    }

    private String getItemPercentageProgressString(ProcessingItem item) {
        if (item.requiresChunking && SUPPORTS_PARALLEL_PROCESSING && !activeRanges.isEmpty()) {
            ChunkRange range = activeRanges.get(0);
            int chunkProgress = (completedChunks * 100) / Math.max(1, item.totalChunks);
            if (totalImages > 1) {
                return String.format("Image %d/%d: %d%% (chunks %d-%d/%d)",
                    item.imageIndex + 1, totalImages, chunkProgress, range.start, range.end, item.totalChunks);
            }
            return String.format("Progress: %d%% (chunks %d-%d/%d)", chunkProgress, range.start, range.end, item.totalChunks);
        }

        int imageProgress = getTotalProgress();
        if (totalImages > 1) {
            return String.format("Image %d/%d: %d%%", item.imageIndex + 1, totalImages, imageProgress);
        }
        return String.format("Progress: %d%%", imageProgress);
    }

    public boolean isParallelProcessingAvailable() {
        return SUPPORTS_PARALLEL_PROCESSING;
    }

    public int getMaxParallelTasks() {
        return MAX_PARALLEL_TASKS;
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

    public synchronized void addActiveChunk(int chunkIndex) {
        activeChunksCounter.incrementAndGet();
        if (totalChunks > 0) {
            totalProgress = Math.min(100, (chunkIndex * 100) / totalChunks);
        } else {
            totalProgress = 0;
        }
    }

    // Add getter for progress
    public synchronized int getTotalProgress() {
        return totalProgress;
    }

    // Add method to set total chunks
    public synchronized void setTotalChunks(int chunks) {
        this.totalChunks = Math.max(1, chunks); // Ensure minimum of 1 chunk
    }

    public void removeActiveChunk(int chunkIndex) {
        activeChunksCounter.decrementAndGet();
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
                if (prev > start) {
                    ranges.append(start).append("-").append(prev).append(",");
                } else {
                    ranges.append(start).append(",");
                }
                start = curr;
            }
            prev = curr;
        }

        if (prev > start) {
            ranges.append(start).append("-").append(prev);
        } else {
            ranges.append(start);
        }

        return ranges.toString();
    }

    // public synchronized void reloadProgressFormat(Context context) {
        // loadProgressFormat(context);
    // }
}