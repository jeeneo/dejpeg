package com.je.dejpeg;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class QueueFragment extends Fragment implements QueueAdapter.OnQueueItemInteractionListener {

    private RecyclerView recyclerViewQueue;
    private QueueAdapter queueAdapter;
    private List<QueueItem> queueItems = new ArrayList<>();

    private BroadcastReceiver processingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            int queueItemId = intent.getIntExtra(ImageProcessingService.EXTRA_QUEUE_ITEM_ID, -1);
            if (queueItemId == -1) return;

            // Find item by ID
            QueueItem itemToUpdate = null;
            int itemIndex = -1;
            for (int i = 0; i < queueItems.size(); i++) {
                if (queueItems.get(i).getId() == queueItemId) {
                    itemToUpdate = queueItems.get(i);
                    itemIndex = i;
                    break;
                }
            }

            switch (action) {
                case "com.je.dejpeg.broadcast.NEW_QUEUE_ITEM":
                    if (itemToUpdate == null) { // Ensure it's not already added
                        String imageUri = intent.getStringExtra(ImageProcessingService.EXTRA_IMAGE_URI);
                        String modelName = intent.getStringExtra(ImageProcessingService.EXTRA_MODEL_NAME);
                        float strength = intent.getFloatExtra(ImageProcessingService.EXTRA_STRENGTH, 50f); // Default strength
                        boolean isGreyscale = intent.getBooleanExtra(ImageProcessingService.EXTRA_IS_GREYSCALE, false);
                        String imageName = imageUri != null ? new File(Uri.parse(imageUri).getPath()).getName() : "Unknown Image";

                        QueueItem newItem = new QueueItem(queueItemId, imageUri, imageName, modelName, isGreyscale, strength);
                        // newItem.setStatus(QueueItem.Status.PENDING); // Already default in constructor
                        queueItems.add(0, newItem);
                        if (queueAdapter != null) {
                             queueAdapter.notifyItemInserted(0);
                             // Consider scrolling to top: recyclerViewQueue.scrollToPosition(0);
                        }
                    }
                    break;
                case ImageProcessingService.BROADCAST_PROCESSING_STARTED:
                    if (itemToUpdate != null) {
                        itemToUpdate.setStatus(QueueItem.Status.PROCESSING);
                        if (itemIndex != -1 && queueAdapter != null) queueAdapter.notifyItemChanged(itemIndex);
                    }
                    break;
                case ImageProcessingService.BROADCAST_PROCESSING_COMPLETE:
                    if (itemToUpdate != null) {
                        itemToUpdate.setStatus(QueueItem.Status.COMPLETED);
                        String processedUri = intent.getStringExtra(ImageProcessingService.EXTRA_PROCESSED_URI);
                        itemToUpdate.setProcessedImageUri(processedUri);
                        if (itemIndex != -1 && queueAdapter != null) queueAdapter.notifyItemChanged(itemIndex);
                    }
                    break;
                case ImageProcessingService.BROADCAST_PROCESSING_ERROR:
                    if (itemToUpdate != null) {
                        itemToUpdate.setStatus(QueueItem.Status.ERROR);
                        // String errorMessage = intent.getStringExtra(ImageProcessingService.EXTRA_ERROR_MESSAGE);
                        // Optionally store and display error message
                        if (itemIndex != -1 && queueAdapter != null) queueAdapter.notifyItemChanged(itemIndex);
                    }
                    break;
            }
        }
    };


    public QueueFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerViewQueue = view.findViewById(R.id.recycler_view_queue);
        recyclerViewQueue.setLayoutManager(new LinearLayoutManager(getContext()));

        queueAdapter = new QueueAdapter(getContext(), this);
        recyclerViewQueue.setAdapter(queueAdapter);
        queueAdapter.setQueueItems(queueItems); // Initialize with current list (likely empty at first)

        // loadSampleData(); // Sample data loading removed
    }

    // loadSampleData() method removed or commented out
    /*
    private void loadSampleData() {
        sampleQueueItems = new ArrayList<>();
        sampleQueueItems.add(new QueueItem(1, "uri_1", "Image_Alpha.jpg", "FBCNN_Color", false, 75f));
        sampleQueueItems.add(new QueueItem(2, "uri_2", "Image_Beta_Long_Name_With_Underscores.png", "FBCNN_Gray", true, 50f));
        sampleQueueItems.add(new QueueItem(3, "uri_3", "Image_Gamma.bmp", "FBCNN_Color", false, 25f));

        // Simulate different statuses
        sampleQueueItems.get(0).setStatus(QueueItem.Status.PENDING);
        sampleQueueItems.get(1).setStatus(QueueItem.Status.PROCESSING);
        sampleQueueItems.get(2).setStatus(QueueItem.Status.COMPLETED);
        
        // Add one more item to show error state
        QueueItem errorItem = new QueueItem(4, "uri_4", "Image_Delta_Error.tiff", "FBCNN_Gray", true, 60f);
        errorItem.setStatus(QueueItem.Status.ERROR);
        sampleQueueItems.add(errorItem);


        queueAdapter.setQueueItems(sampleQueueItems);
    }
    */

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.je.dejpeg.broadcast.NEW_QUEUE_ITEM");
        filter.addAction(ImageProcessingService.BROADCAST_PROCESSING_STARTED);
        filter.addAction(ImageProcessingService.BROADCAST_PROCESSING_COMPLETE);
        filter.addAction(ImageProcessingService.BROADCAST_PROCESSING_ERROR);
        lbm.registerReceiver(processingBroadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(processingBroadcastReceiver);
    }

    @Override
    public void onCancelClicked(int itemId) {
        Toast.makeText(getContext(), "Cancel clicked for item ID: " + itemId, Toast.LENGTH_SHORT).show();
        // Placeholder for actual cancellation. Service doesn't currently support it.
        // For now, just changing status locally for UI feedback.
        // In a real app, this would also try to cancel the service task.
        boolean itemFound = false;
        for (int i = 0; i < queueItems.size(); i++) {
            if (queueItems.get(i).getId() == itemId) {
                // queueItems.get(i).setStatus(QueueItem.Status.ERROR); // Or a new CANCELED status
                // queueAdapter.notifyItemChanged(i);
                itemFound = true;
                break;
            }
        }
        if (itemFound) {
             // Start service with ACTION_CANCEL_PROCESSING
            Intent serviceIntent = new Intent(getActivity(), ImageProcessingService.class);
            serviceIntent.setAction(ImageProcessingService.ACTION_CANCEL_PROCESSING);
            serviceIntent.putExtra(ImageProcessingService.EXTRA_QUEUE_ITEM_ID, itemId);
            if (getActivity() != null) {
                getActivity().startService(serviceIntent);
            }
            // For now, we'll just reflect a pending state or remove, as actual cancellation isn't implemented in service
             queueAdapter.updateItemStatus(itemId, QueueItem.Status.PENDING); // Or remove
        }
    }

    @Override
    public void onViewResultClicked(int itemId, String originalImageUri, String processedImageUri) {
        QueueItem itemToView = null;
        for (QueueItem item : queueItems) {
            if (item.getId() == itemId) {
                itemToView = item;
                break;
            }
        }

        if (itemToView == null) {
            Toast.makeText(getContext(), "Error: Could not find item " + itemId, Toast.LENGTH_SHORT).show();
            return;
        }

        // Use URIs from the itemToView which should be updated by the broadcast receiver
        originalImageUri = itemToView.getOriginalImageUri();
        processedImageUri = itemToView.getProcessedImageUri();

        if (originalImageUri == null || processedImageUri == null) {
             Toast.makeText(getContext(), "Image URIs are missing for item ID: " + itemId, Toast.LENGTH_SHORT).show();
             return;
        }

        Intent intent = new Intent(getActivity(), ImageDetailActivity.class);
        intent.putExtra(ImageDetailActivity.EXTRA_ORIGINAL_URI, originalImageUri);
        intent.putExtra(ImageDetailActivity.EXTRA_PROCESSED_URI, processedImageUri);
        startActivity(intent);
    }
}
