package com.carconnect.app.activity;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carconnect.app.R;
import com.carconnect.app.adapter.LogAdapter;
import com.carconnect.app.model.LogEntry;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogActivity extends AppCompatActivity implements LogManager.LogListener {

    private static final String TAG = "LogActivity";

    private RecyclerView rvLogs;
    private LogAdapter logAdapter;
    private Button btnClearLog, btnScrollBottom, btnDownloadLog;
    private boolean autoScroll = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SharedPrefsManager.isWhiteTheme()) setTheme(R.style.AppThemeWhite);
        setContentView(R.layout.activity_log);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("运行日志");

        rvLogs = findViewById(R.id.rv_logs);
        btnClearLog = findViewById(R.id.btn_clear_log);
        btnScrollBottom = findViewById(R.id.btn_scroll_bottom);
        btnDownloadLog = findViewById(R.id.btn_download_log);

        logAdapter = new LogAdapter();
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvLogs.setLayoutManager(llm);
        rvLogs.setAdapter(logAdapter);

        // 加载历史日志
        logAdapter.setLogs(LogManager.getInstance().getLogs());
        scrollToBottom();

        LogManager.getInstance().addListener(this);

        btnClearLog.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 清除日志");
            LogManager.getInstance().clearLogs();
        });

        btnScrollBottom.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 滚动到底部");
            scrollToBottom();
        });

        btnDownloadLog.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 下载日志");
            exportLogs();
        });
    }

    /**
     * 将日志导出到 Downloads 目录
     */
    private void exportLogs() {
        List<LogEntry> logs = LogManager.getInstance().getLogs();
        if (logs.isEmpty()) {
            Toast.makeText(this, "没有可导出的日志", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "CarConnect_log_" + timeStr + ".txt";

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
        sb.append("CarConnect 运行日志 导出时间: ").append(timeStr).append("\n");
        for (int i = 0; i < 60; i++) sb.append("=");
        sb.append("\n");
        for (LogEntry entry : logs) {
            sb.append(sdf.format(new Date(entry.getTimestamp())))
                    .append(" [").append(entry.getLevelStr())
                    .append("/").append(entry.getTag()).append("] ")
                    .append(entry.getMessage()).append("\n");
        }

        try {
            boolean success = false;
            String savedPath = "";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(sb.toString().getBytes("UTF-8"));
                            success = true;
                            savedPath = "下载/Downloads/" + fileName;
                        }
                    }
                }
            } else {
                // Android 9 及以下直接写文件
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(sb.toString().getBytes("UTF-8"));
                    success = true;
                    savedPath = file.getAbsolutePath();
                }
            }

            if (success) {
                Toast.makeText(this, "日志已保存到: " + savedPath, Toast.LENGTH_LONG).show();
                LogManager.i(TAG, "日志已导出到: " + savedPath);
            } else {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            LogManager.e(TAG, "日志导出失败: " + e.getMessage());
        }
    }

    @Override
    public void onNewLog(final LogEntry entry) {
        runOnUiThread(() -> {
            logAdapter.addLog(entry);
            if (autoScroll) {
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        if (logAdapter.getItemCount() > 0) {
            rvLogs.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogManager.getInstance().removeListener(this);
    }
}
