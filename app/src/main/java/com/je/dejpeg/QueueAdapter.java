package com.je.dejpeg;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.ViewHolder> {

    private List<QueueItem> queueItems = new ArrayList<>();
    private final OnQueueItemInteractionListener listener;
    private final Context context;

    public interface OnQueueItemInteractionListener {
        void onCancelClicked(int itemId);
        void onViewResultClicked(int itemId, String originalImageUri, String processedImageUri);
    }

    public QueueAdapter(Context context, OnQueueItemInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_queue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QueueItem item = queueItems.get(position);

        holder.textImageName.setText(item.getImageName());
        holder.textProcessingStatus.setText(String.format(Locale.getDefault(), "Status: %s", item.getStatus().name()));

        // Placeholder for thumbnail loading
        holder.imageThumbnail.setImageResource(R.mipmap.ic_launcher); // Replace with actual image loading logic

        switch (item.getStatus()) {
            case PENDING:
                holder.progressBarImage.setVisibility(View.GONE);
                holder.buttonCancelProcessing.setVisibility(View.GONE);
                holder.buttonViewResult.setVisibility(View.GONE);
                break;
            case PROCESSING:
                holder.progressBarImage.setVisibility(View.VISIBLE);
                holder.progressBarImage.setIndeterminate(true);
                holder.buttonCancelProcessing.setVisibility(View.VISIBLE);
                holder.buttonViewResult.setVisibility(View.GONE);
                break;
            case COMPLETED:
                holder.progressBarImage.setVisibility(View.GONE);
                holder.buttonCancelProcessing.setVisibility(View.GONE);
                holder.buttonViewResult.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                holder.progressBarImage.setVisibility(View.GONE);
                holder.buttonCancelProcessing.setVisibility(View.GONE);
                holder.buttonViewResult.setVisibility(View.GONE);
                holder.textProcessingStatus.setText(String.format(Locale.getDefault(), "Status: %s - Error", item.getStatus().name()));
                break;
        }

        holder.buttonCancelProcessing.setOnClickListener(v -> listener.onCancelClicked(item.getId()));
        holder.buttonViewResult.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewResultClicked(item.getId(), item.getOriginalImageUri(), item.getProcessedImageUri());
            }
        });
    }

    @Override
    public int getItemCount() {
        return queueItems.size();
    }

    public void setQueueItems(List<QueueItem> items) {
        this.queueItems = new ArrayList<>(items); // Create a new list to avoid reference issues
        notifyDataSetChanged(); // Consider using DiffUtil for better performance
    }
    
    public void updateItemStatus(int itemId, QueueItem.Status newStatus) {
        for (int i = 0; i < queueItems.size(); i++) {
            if (queueItems.get(i).getId() == itemId) {
                queueItems.get(i).setStatus(newStatus);
                notifyItemChanged(i);
                return;
            }
        }
    }


    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageThumbnail;
        TextView textImageName;
        TextView textProcessingStatus;
        ProgressBar progressBarImage;
        Button buttonCancelProcessing;
        Button buttonViewResult;

        ViewHolder(View itemView) {
            super(itemView);
            imageThumbnail = itemView.findViewById(R.id.image_thumbnail);
            textImageName = itemView.findViewById(R.id.text_image_name);
            textProcessingStatus = itemView.findViewById(R.id.text_processing_status);
            progressBarImage = itemView.findViewById(R.id.progress_bar_image);
            buttonCancelProcessing = itemView.findViewById(R.id.button_cancel_processing);
            buttonViewResult = itemView.findViewById(R.id.button_view_result);
        }
    }
}
