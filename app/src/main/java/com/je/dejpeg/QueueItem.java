package com.je.dejpeg;

public class QueueItem {

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        ERROR
    }

    private final int id;
    private final String originalImageUri;
    private String processedImageUri; // Can be null initially
    private final String imageName;
    private Status status;
    private final String modelType;
    private final boolean isGreyscale;
    private final float strength;

    public QueueItem(int id, String originalImageUri, String imageName, String modelType, boolean isGreyscale, float strength) {
        this.id = id;
        this.originalImageUri = originalImageUri;
        this.imageName = imageName;
        this.modelType = modelType;
        this.isGreyscale = isGreyscale;
        this.strength = strength;
        this.status = Status.PENDING; // Default status
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getOriginalImageUri() {
        return originalImageUri;
    }

    public String getProcessedImageUri() {
        return processedImageUri;
    }

    public String getImageName() {
        return imageName;
    }

    public Status getStatus() {
        return status;
    }

    public String getModelType() {
        return modelType;
    }

    public boolean isGreyscale() {
        return isGreyscale;
    }

    public float getStrength() {
        return strength;
    }

    // Setters
    public void setProcessedImageUri(String processedImageUri) {
        this.processedImageUri = processedImageUri;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
