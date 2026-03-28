package com.carconnect.app.activity;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carconnect.app.R;
import com.carconnect.app.adapter.LogAdapter;
import com.carconnect.app.model.LogEntry;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

public class LogActivity extends AppCompatActivity implements LogManager.LogListener {

    private static final String TAG = "LogActivity";

    private RecyclerView rvLogs;
    private LogAdapter logAdapter;
    private Button btnClearLog, btnScrollBottom;
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
