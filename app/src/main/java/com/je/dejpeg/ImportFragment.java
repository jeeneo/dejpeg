package com.je.dejpeg;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ImportFragment extends Fragment {

    private ImageView imagePreview;
    private Button buttonSelectImage;
    private Spinner spinnerModelType;
    private SwitchMaterial switchColorGreyscale;
    private Slider strengthSlider;
    private Button buttonStartProcessing;
    private Uri selectedImageUri; // To store the selected image URI

    private ActivityResultLauncher<String> mGetContent;


    public ImportFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imagePreview = view.findViewById(R.id.image_preview);
        buttonSelectImage = view.findViewById(R.id.button_select_image);
        spinnerModelType = view.findViewById(R.id.spinner_model_type);
        switchColorGreyscale = view.findViewById(R.id.switch_color_greyscale);
        strengthSlider = view.findViewById(R.id.strengthSlider);
        buttonStartProcessing = view.findViewById(R.id.button_start_processing);

        // Setup spinner_model_type (placeholder)
        // Later, this will be populated from ModelManager
        String[] models = {"FBCNN_Color", "FBCNN_Gray", "FBCNN_Gray_Double"}; // Example models
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModelType.setAdapter(adapter);

        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imagePreview.setImageURI(selectedImageUri); // Show preview
                        buttonStartProcessing.setEnabled(true); // Enable button
                        Toast.makeText(getContext(), "Image selected: " + selectedImageUri.toString(), Toast.LENGTH_SHORT).show();
                    }
                });

        buttonSelectImage.setOnClickListener(v -> {
            mGetContent.launch("image/*");
        });

        buttonStartProcessing.setOnClickListener(v -> {
            if (selectedImageUri == null) {
                Toast.makeText(getContext(), "Please select an image first.", Toast.LENGTH_SHORT).show();
                return;
            }

            String modelName = spinnerModelType.getSelectedItem().toString();
            boolean isGreyscale = switchColorGreyscale.isChecked();
            float strength = strengthSlider.getValue();
            int queueItemId = (int) System.currentTimeMillis();

            Intent serviceIntent = new Intent(getActivity(), ImageProcessingService.class);
            serviceIntent.setAction(ImageProcessingService.ACTION_PROCESS_IMAGE);
            serviceIntent.putExtra(ImageProcessingService.EXTRA_QUEUE_ITEM_ID, queueItemId);
            serviceIntent.putExtra(ImageProcessingService.EXTRA_IMAGE_URI, selectedImageUri.toString());
            serviceIntent.putExtra(ImageProcessingService.EXTRA_MODEL_NAME, modelName);
            serviceIntent.putExtra(ImageProcessingService.EXTRA_STRENGTH, strength);
            serviceIntent.putExtra(ImageProcessingService.EXTRA_IS_GREYSCALE, isGreyscale);

            if (getActivity() != null) {
                getActivity().startService(serviceIntent);

                // Notify QueueFragment about the new item
                Intent newItemIntent = new Intent("com.je.dejpeg.broadcast.NEW_QUEUE_ITEM");
                newItemIntent.putExtra(ImageProcessingService.EXTRA_QUEUE_ITEM_ID, queueItemId);
                newItemIntent.putExtra(ImageProcessingService.EXTRA_IMAGE_URI, selectedImageUri.toString());
                newItemIntent.putExtra(ImageProcessingService.EXTRA_MODEL_NAME, modelName);
                newItemIntent.putExtra(ImageProcessingService.EXTRA_STRENGTH, strength);
                newItemIntent.putExtra(ImageProcessingService.EXTRA_IS_GREYSCALE, isGreyscale);
                LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(newItemIntent);

                Toast.makeText(getContext(), "Processing started!", Toast.LENGTH_SHORT).show();

                // Optionally clear preview and disable button
                // imagePreview.setImageURI(null);
                // selectedImageUri = null;
                // buttonStartProcessing.setEnabled(false);
            }
        });

        strengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            // Optionally update a TextView to show the current strength value
        });

        // Initially, the process button is disabled until an image is selected
        buttonStartProcessing.setEnabled(false);
    }
}
