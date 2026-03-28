package com.carconnect.app.utils;

import android.content.Context;
import android.util.Log;

import com.carconnect.app.model.LogEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 全局日志管理器，支持内存缓存+通知界面更新
 */
public class LogManager {

    private static final String TAG = "CarConnect";
    private static final int MAX_LOG_COUNT = 500;

    private static LogManager instance;
    private final List<LogEntry> logList = new ArrayList<>();
    private final List<LogListener> listeners = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public interface LogListener {
        void onNewLog(LogEntry entry);
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new LogManager();
        }
    }

    public static LogManager getInstance() {
        if (instance == null) instance = new LogManager();
        return instance;
    }

    public static void d(String tag, String msg) {
        getInstance().addLog(LogEntry.LEVEL_DEBUG, tag, msg);
        Log.d(TAG + "/" + tag, msg);
    }

    public static void i(String tag, String msg) {
        getInstance().addLog(LogEntry.LEVEL_INFO, tag, msg);
        Log.i(TAG + "/" + tag, msg);
    }

    public static void w(String tag, String msg) {
        getInstance().addLog(LogEntry.LEVEL_WARN, tag, msg);
        Log.w(TAG + "/" + tag, msg);
    }

    public static void e(String tag, String msg) {
        getInstance().addLog(LogEntry.LEVEL_ERROR, tag, msg);
        Log.e(TAG + "/" + tag, msg);
    }

    private synchronized void addLog(int level, String tag, String msg) {
        LogEntry entry = new LogEntry(level, tag, msg);
        logList.add(entry);
        if (logList.size() > MAX_LOG_COUNT) {
            logList.remove(0);
        }
        for (LogListener listener : listeners) {
            listener.onNewLog(entry);
        }
    }

    public synchronized List<LogEntry> getLogs() {
        return new ArrayList<>(logList);
    }

    public synchronized void clearLogs() {
        logList.clear();
        for (LogListener listener : listeners) {
            listener.onNewLog(null);
        }
    }

    public void addListener(LogListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public String formatTime(long timestamp) {
        return sdf.format(new Date(timestamp));
    }
}
