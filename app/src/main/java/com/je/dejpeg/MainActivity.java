package com.je.dejpeg;

import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ModelManager modelManager;

    // Progress dialog
    private Dialog importProgressDialog;
    private ProgressBar importProgressBar;
    private TextView importProgressText;

    // Activity launchers
    private ActivityResultLauncher<Intent> modelPickerLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Constants
    private static final String PREFS_NAME = "AppPrefs";
    private static final String STRENGTH_FACTOR_KEY = "strengthFactor";

    // State
    private float lastStrengthValue = 0.5f;
    private boolean applyStrengthToAll = true;

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

        // Initialize toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize core components
        modelManager = new ModelManager(this);

        // Initialize launchers before using them
        initializeLaunchers();

        // Set up BottomNavigationView
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_import) {
                loadFragment(new ImportFragment());
                return true;
            } else if (itemId == R.id.nav_queue) {
                loadFragment(new QueueFragment());
                return true;
            }
            return false;
        });

        // Load default fragment
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_import);
        }

        // Setup notification permission
        setupNotificationPermission();

        if (!modelManager.hasActiveModel()) {
            promptModelSelection();
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
    }

    private void setupNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void initializeLaunchers() {
        // Initialize model picker launcher
        modelPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.getData() != null) {
                        copyAndLoadModel(data.getData());
                    }
                }
            }
        );

        // Initialize notification permission launcher
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
                    modelManager.setActiveModel(models.get(which));
                    Toast.makeText(this,
                        getString(R.string.model_switched, models.get(which)),
                        Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, getString(R.string.model_deleted, models.get(i)),
                            Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.importing_model_message), Toast.LENGTH_SHORT).show();

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
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            dismissImportProgressDialog();
                            Toast.makeText(MainActivity.this,
                                getString(R.string.error_importing_model),
                                Toast.LENGTH_LONG).show();
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