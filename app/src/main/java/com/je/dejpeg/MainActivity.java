package com.je.dejpeg;

import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private Button selectButton;
    private Button processButton;
    private Slider strengthSlider;
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;
    private TextView pageIndicator;
    private Button cancelButton;
    private ImageView imageViewOriginal;
    private ImageView imageViewProcessed;

    // Core components
    private ModelManager modelManager;
    private ImageProcessor imageProcessor;

    // Progress dialog
    private Dialog importProgressDialog;
    private ProgressBar importProgressBar;
    private TextView importProgressText;

    // Activity launchers
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> modelPickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Constants
    private static final String PREFS_NAME = "AppPrefs";
    private static final String OUTPUT_FORMAT_KEY = "outputFormat";
    private static final String STRENGTH_FACTOR_KEY = "strengthFactor";
    private static final int FORMAT_PNG = 0;
    private static final int FORMAT_BMP = 1;

    // State
    private boolean isProcessing = false;
    private List<ProcessingImage> images = new ArrayList<>();
    private int currentPage = 0;
    private float lastStrengthValue = 0.5f;
    private boolean showPreviews = true;
    private boolean showFilmstrip = false;
    private List<Float> perImageStrengthFactors = new ArrayList<>();
    private boolean applyStrengthToAll = true;

    // Inner class for processing images
    private static class ProcessingImage {
        public Bitmap inputBitmap;
        public Bitmap outputBitmap;

        public ProcessingImage(Bitmap inputBitmap) {
            this.inputBitmap = inputBitmap;
            this.outputBitmap = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load OpenCV
        try {
            System.loadLibrary("opencv_java4");
        } catch (UnsatisfiedLinkError e) {
            Toast.makeText(this, getString(R.string.opencv_error), Toast.LENGTH_LONG).show();
        }

        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        initializeLaunchers();
        setupEventListeners();
        loadPreferences();
        handleIntent(getIntent());

        if (!modelManager.hasActiveModel()) {
            promptModelSelection();
        }

        setupNotificationPermission();
        updateStrengthSliderVisibility();
    }

    private void initializeViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        selectButton = findViewById(R.id.selectButton);
        processButton = findViewById(R.id.processButton);
        strengthSlider = findViewById(R.id.strengthSlider);
        logRecyclerView = findViewById(R.id.logRecyclerView);
        pageIndicator = findViewById(R.id.pageIndicator);
        cancelButton = findViewById(R.id.cancelButton);
        imageViewOriginal = findViewById(R.id.imageViewOriginal);
        imageViewProcessed = findViewById(R.id.imageViewProcessed);

        // Setup RecyclerView
        logAdapter = new LogAdapter();
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);

        logAdapter.setOnLogAddedListener(() -> {
            logRecyclerView.post(() ->
                logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1)
            );
        });

        processButton.setEnabled(false);
    }

    private void initializeComponents() {
        modelManager = new ModelManager(this);
        imageProcessor = new ImageProcessor(this, modelManager);
    }

    private void initializeLaunchers() {
        // Image picker launcher
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        handleMultipleImages(data.getClipData());
                    } else {
                        Uri uri = data.getData();
                        if (uri != null) {
                            onImageSelected(uri);
                        }
                    }
                }
            }
        );

        // Model picker launcher
        modelPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri modelUri = result.getData().getData();
                    if (modelUri != null) {
                        copyAndLoadModel(modelUri);
                    }
                } else {
                    if (!modelManager.hasActiveModel()) {
                        promptModelSelection();
                    }
                }
            }
        );

        // Notification permission launcher
        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    setupNotificationChannel();
                } else {
                    Toast.makeText(this,
                        "Notification permission denied. Background updates will not be shown.",
                        Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void setupEventListeners() {
        selectButton.setOnClickListener(v -> showImageSelectionDialog());

        processButton.setOnClickListener(v -> {
            if (!images.isEmpty()) {
                processWithModel();
            }
        });

        cancelButton.setOnClickListener(v -> cancelProcessing());

        strengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            float snapped = ((int)(value / 5)) * 5f;
            slider.setValue(snapped);
            lastStrengthValue = snapped / 100f;

            if (images.size() > 1 && !applyStrengthToAll &&
                currentPage < perImageStrengthFactors.size()) {
                perImageStrengthFactors.set(currentPage, lastStrengthValue);
            }

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(STRENGTH_FACTOR_KEY, lastStrengthValue)
                .apply();
        });
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        lastStrengthValue = prefs.getFloat(STRENGTH_FACTOR_KEY, 0.5f);
        strengthSlider.setValue(lastStrengthValue * 100f);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) &&
            intent.getType() != null && intent.getType().startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                onImageSelected(imageUri);
            }
        }
    }

    private void setupNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            setupNotificationChannel();
        }
    }

    private void updateStrengthSliderVisibility() {
        strengthSlider.setVisibility(modelManager.hasActiveModel() ? View.VISIBLE : View.GONE);
    }

    private void showImageSelectionDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_image)
            .setItems(new String[]{
                getString(R.string.single_image),
                getString(R.string.multiple_images)
            }, (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                if (which == 1) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                imagePickerLauncher.launch(intent);
            })
            .show();
    }

    private void promptModelSelection() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setMessage(R.string.no_models)
            .setPositiveButton(R.string.import_model, (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                modelPickerLauncher.launch(intent);
            })
            .setCancelable(false)
            .show();
    }

    private void showModelManagementDialog() {
        List<String> models = modelManager.getInstalledModels();
        String activeModel = modelManager.getActiveModelName();
        String[] items = models.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_model)
            .setSingleChoiceItems(items, models.indexOf(activeModel),
                (DialogInterface dialog, int which) -> {
                    modelManager.unloadModel();
                    imageProcessor.unloadModel();
                    modelManager.setActiveModel(models.get(which));
                    logAdapter.addLog(getString(R.string.model_switched, models.get(which)));
                    Toast.makeText(this,
                        getString(R.string.model_switched, models.get(which)),
                        Toast.LENGTH_SHORT).show();
                    processButton.setEnabled(!images.isEmpty());
                    updateStrengthSliderVisibility();
                    dialog.dismiss();
                })
            .setPositiveButton(R.string.import_button, (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                modelPickerLauncher.launch(intent);
            })
            .setNeutralButton(R.string.delete_button, (dialog, which) ->
                showDeleteModelDialog(models))
            .show();
    }

    private void showDeleteModelDialog(List<String> models) {
        String[] items = models.toArray(new String[0]);
        boolean[] checkedItems = new boolean[models.size()];

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_models)
            .setMultiChoiceItems(items, checkedItems,
                (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton(getString(R.string.delete_button), (dialog, which) -> {
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) {
                        modelManager.deleteModel(models.get(i));
                        logAdapter.addLog(getString(R.string.model_deleted, models.get(i)));
                    }
                }
                if (!modelManager.hasActiveModel()) {
                    promptModelSelection();
                }
            })
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show();
    }

    private void copyAndLoadModel(Uri modelUri) {
        runOnUiThread(this::showImportProgressDialog);
        logAdapter.addLog(getString(R.string.importing_model_message));

        new Thread(() -> {
            try {
                ModelManager.ModelCallback callback = new ModelManager.ModelCallback() {
                    @Override
                    public void onSuccess(String modelName) {
                        runOnUiThread(() -> {
                            dismissImportProgressDialog();
                            modelManager.setActiveModel(modelName);
                            Toast.makeText(MainActivity.this,
                                getString(R.string.model_imported_toast),
                                Toast.LENGTH_SHORT).show();
                            logAdapter.addLog(getString(R.string.model_imported, modelName));
                            logAdapter.addLog(getString(R.string.model_switched, modelName));
                            processButton.setEnabled(!images.isEmpty());
                            updateStrengthSliderVisibility();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            dismissImportProgressDialog();
                            Toast.makeText(MainActivity.this,
                                getString(R.string.error_importing_model),
                                Toast.LENGTH_LONG).show();
                            logAdapter.addLog(getString(R.string.error_importing_model, error));
                            promptModelSelection();
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        runOnUiThread(() -> updateImportProgressDialog(progress));
                    }
                };

                modelManager.importModel(modelUri, callback);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissImportProgressDialog();
                    Toast.makeText(MainActivity.this,
                        getString(R.string.invalid_model),
                        Toast.LENGTH_LONG).show();
                    logAdapter.addLog(getString(R.string.invalid_model));
                    promptModelSelection();
                });
            }
        }).start();
    }

    private void showImportProgressDialog() {
        if (importProgressDialog != null && importProgressDialog.isShowing()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_progress_material3, null);
        importProgressBar = dialogView.findViewById(R.id.progressBar);
        importProgressText = dialogView.findViewById(R.id.progressText);

        importProgressDialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.importing_model)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        importProgressDialog.show();
    }

    private void updateImportProgressDialog(int progress) {
        if (importProgressDialog != null && importProgressDialog.isShowing()) {
            if (importProgressBar != null) {
                importProgressBar.setProgress(progress);
            }
            if (importProgressText != null) {
                importProgressText.setText(progress + "%");
            }
        }
    }

    private void dismissImportProgressDialog() {
        if (importProgressDialog != null) {
            importProgressDialog.dismiss();
            importProgressDialog = null;
        }
    }

    private void onImageSelected(Uri selectedImage) {
        try {
            String mimeType = getContentResolver().getType(selectedImage);
            if (mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(selectedImage.toString())
                );
            }

            Bitmap bitmap;
            if ("image/webp".equals(mimeType)) {
                bitmap = convertWebpToBitmap(selectedImage);
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
            }

            updateImageViews(bitmap);
        } catch (IOException e) {
            logAdapter.addLog(getString(R.string.error_loading_image, e.getMessage()));
            Toast.makeText(this,
                getString(R.string.error_loading_image, e.getMessage()),
                Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap convertWebpToBitmap(Uri webpUri) throws IOException {
        Bitmap webpBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), webpUri);
        File bmpFile = new File(getCacheDir(), "input_temp.bmp");
        try (FileOutputStream fos = new FileOutputStream(bmpFile)) {
            webpBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        return BitmapFactory.decodeFile(bmpFile.getAbsolutePath());
    }

    private void handleMultipleImages(ClipData clipData) {
        try {
            images.clear();
            perImageStrengthFactors.clear();
            int loadedCount = 0;

            for (int i = 0; i < clipData.getItemCount(); i++) {
                try {
                    Uri uri = clipData.getItemAt(i).getUri();
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    images.add(new ProcessingImage(bitmap));
                    perImageStrengthFactors.add(lastStrengthValue);
                    loadedCount++;
                } catch (Exception e) {
                    logAdapter.addLog(getString(R.string.error_loading_image_n, i + 1, e.getMessage()));
                }
            }

            if (loadedCount > 0) {
                currentPage = 0;
                updatePageIndicator();
                processButton.setEnabled(modelManager.hasActiveModel());
                Toast.makeText(this, getString(R.string.images_loaded, loadedCount),
                    Toast.LENGTH_SHORT).show();
                showPreviews = true;
                showFilmstrip = false;
                updateImageView();
            } else {
                logAdapter.addLog(getString(R.string.no_images_loaded));
                Toast.makeText(this, getString(R.string.no_images_loaded),
                    Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            logAdapter.addLog(getString(R.string.error_loading_images, e.getMessage()));
            Toast.makeText(this, getString(R.string.error_loading_images, e.getMessage()),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void updateImageViews(Bitmap bitmap) {
        images.clear();
        images.add(new ProcessingImage(bitmap));
        perImageStrengthFactors.clear();
        perImageStrengthFactors.add(lastStrengthValue);
        currentPage = 0;
        updatePageIndicator();
        processButton.setEnabled(modelManager.hasActiveModel());
        Toast.makeText(this, getString(R.string.image_loaded), Toast.LENGTH_SHORT).show();
        showPreviews = true;
        showFilmstrip = false;
        updateStrengthSliderVisibility();
        updateImageView();
    }

    private void processWithModel() {
        isProcessing = true;
        updateButtonVisibility();
        updatePageIndicator();
        updateImageView();

        boolean isBatch = images.size() > 1;
        final int[] completedCount = {0};
        showProcessingNotification(0, images.size());

        processNext(0, isBatch, completedCount);
    }

    private void processNext(int index, boolean isBatch, int[] completedCount) {
        if (!isProcessing || index >= images.size()) {
            isProcessing = false;
            if (completedCount[0] > 0) {
                showCompletionNotification(completedCount[0]);
            }
            dismissProcessingNotification();
            updateButtonVisibility();
            updatePageIndicator();
            showFilmstrip = true;
            updateImageView();
            return;
        }

        ProcessingImage image = images.get(index);
        float strength = applyStrengthToAll ?
            lastStrengthValue * 100f :
            perImageStrengthFactors.get(index) * 100f;

        showProcessingNotification(index + 1, images.size());

        imageProcessor.processImage(
            image.inputBitmap,
            strength,
            new ImageProcessor.ProcessCallback() {
                @Override
                public void onProgress(String message) {
                    runOnUiThread(() -> {
                        if (isProcessing) {
                            // Log only meaningful messages
                            if (message.contains("Loading model")) {
                                logAdapter.addLog(message);
                            } else if (!message.contains("Processing chunk") &&
                                      !message.contains("Processing complete")) {
                                logAdapter.addLog(message);
                            }
                        }
                    });
                }

                @Override
                public void onComplete(Bitmap result) {
                    runOnUiThread(() -> {
                        if (isProcessing) {
                            image.outputBitmap = result;
                            completedCount[0]++;
                            if (!isBatch || index == images.size() - 1) {
                                logAdapter.addLog(getString(R.string.processing_complete_single));
                            }
                            updateImageView();
                            processNext(index + 1, isBatch, completedCount);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (isProcessing) {
                            logAdapter.addLog(isBatch ?
                                getString(R.string.error_loading_image_n, index + 1, error) :
                                getString(R.string.error_loading_image, error));
                            processNext(index + 1, isBatch, completedCount);
                        }
                    });
                }
            },
            index,
            images.size()
        );
    }

    private void cancelProcessing() {
        if (isProcessing) {
            imageProcessor.cancelProcessing();
            isProcessing = false;
            dismissProcessingNotification();
            updateButtonVisibility();
            updatePageIndicator();
            updateImageView();
            Toast.makeText(this, getString(R.string.processing_cancelled),
                Toast.LENGTH_SHORT).show();
            logAdapter.addLog(getString(R.string.processing_cancelled));
        }
    }

    private void updateButtonVisibility() {
        runOnUiThread(() -> {
            processButton.setVisibility(isProcessing ? View.GONE : View.VISIBLE);
            cancelButton.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
            selectButton.setEnabled(!isProcessing);
            processButton.setEnabled(!images.isEmpty() && modelManager.hasActiveModel());
        });
    }

    private void updatePageIndicator() {
        if (images.size() > 1 && !isProcessing) {
            pageIndicator.setVisibility(View.GONE);
        } else {
            pageIndicator.setVisibility(View.GONE);
        }
    }

    private void updateImageView() {
        if (images.isEmpty()) {
            imageViewOriginal.setImageResource(R.drawable.placeholder);
            imageViewProcessed.setImageDrawable(null);
            return;
        }

        ProcessingImage image = images.get(currentPage);
        imageViewOriginal.setImageBitmap(image.inputBitmap);
        
        if (image.outputBitmap != null && !isProcessing) {
            imageViewProcessed.setImageBitmap(image.outputBitmap);
        } else {
            imageViewProcessed.setImageDrawable(null);
        }
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "processing_channel",
                getString(R.string.processing_updates),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.processing_updates_description));
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showCompletionNotification(int totalImages) {
        String notificationTitle = totalImages > 1 ?
            getString(R.string.processing_complete_batch, totalImages) :
            getString(R.string.processing_complete_single);

        Intent intent = new Intent(this, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setContentText(getString(R.string.processing_complete_description))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify(2, builder.build());
    }

    private void showProcessingNotification(int currentImage, int totalImages) {
        String notificationTitle = totalImages > 1 ?
            getString(R.string.processing_batch, currentImage, totalImages) :
            getString(R.string.processing_single);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "processing_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notificationTitle)
            .setProgress(0, 0, true)
            .setOngoing(true);

        NotificationManagerCompat.from(this).notify(1, builder.build());
    }

    private void dismissProcessingNotification() {
        NotificationManagerCompat.from(this).cancel(1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_config) {
            showModelManagementDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}