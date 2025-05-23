package com.je.dejpeg;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
    private final List<String> logs = new ArrayList<>();

    public interface OnLogAddedListener {
        void onLogAdded();
    }
    private OnLogAddedListener logAddedListener;

    public void setOnLogAddedListener(OnLogAddedListener listener) {
        this.logAddedListener = listener;
    }

    public void addLog(String log) {
        addLog(log, false);
    }

    public void addLog(String log, boolean replaceLast) {
        if (replaceLast && !logs.isEmpty()) {
            logs.set(logs.size() - 1, log);
            notifyItemChanged(logs.size() - 1);
        } else {
            logs.add(log);
            notifyItemInserted(logs.size() - 1);
            if (logAddedListener != null) logAddedListener.onLogAdded();
        }
    }

    public void clearLogs() {
        logs.clear();
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.textView.setText(logs.get(position));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        ViewHolder(View view) {
            super(view);
            textView = view.findViewById(android.R.id.text1);
        }
    }
}
