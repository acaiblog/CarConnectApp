package com.carconnect.app.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carconnect.app.model.LogEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

    private final List<LogEntry> logs = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setTextSize(11f);
        tv.setPadding(12, 4, 12, 4);
        tv.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogEntry entry = logs.get(position);
        String time = sdf.format(new Date(entry.getTimestamp()));
        String text = time + " [" + entry.getLevelStr() + "/" + entry.getTag() + "] " + entry.getMessage();
        holder.textView.setText(text);

        switch (entry.getLevel()) {
            case LogEntry.LEVEL_ERROR:
                holder.textView.setTextColor(Color.parseColor("#FF5252"));
                break;
            case LogEntry.LEVEL_WARN:
                holder.textView.setTextColor(Color.parseColor("#FFB74D"));
                break;
            case LogEntry.LEVEL_INFO:
                holder.textView.setTextColor(Color.parseColor("#81C784"));
                break;
            default:
                holder.textView.setTextColor(Color.parseColor("#B0BEC5"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void setLogs(List<LogEntry> newLogs) {
        logs.clear();
        if (newLogs != null) logs.addAll(newLogs);
        notifyDataSetChanged();
    }

    public void addLog(LogEntry entry) {
        if (entry == null) {
            logs.clear();
            notifyDataSetChanged();
            return;
        }
        logs.add(entry);
        notifyItemInserted(logs.size() - 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View v) {
            super(v);
            textView = (TextView) v;
        }
    }
}
