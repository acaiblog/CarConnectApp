package com.carconnect.app.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carconnect.app.R;
import com.carconnect.app.model.WifiConfig;
import com.carconnect.app.service.WifiService;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

/**
 * Wi-Fi / 热点设置界面
 * 只保留热点模式开关，删除 SSID/密码/扫描/连接等自动连 Wi-Fi 功能
 */
public class WifiSettingsActivity extends AppCompatActivity {

    private static final String TAG = "WifiSettings";

    private CheckBox cbHotspotMode;
    private TextView tvHotspotStatus;
    private Button btnSave;

    private final BroadcastReceiver hotspotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiService.ACTION_HOTSPOT_STARTED.equals(action)) {
                String ssid = intent.getStringExtra(WifiService.EXTRA_HOTSPOT_SSID);
                runOnUiThread(() -> {
                    tvHotspotStatus.setText("✅ 热点已开启: " + (ssid != null ? ssid : ""));
                    tvHotspotStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                });
            } else if (WifiService.ACTION_HOTSPOT_STOPPED.equals(action)) {
                runOnUiThread(() -> {
                    tvHotspotStatus.setText("热点已关闭");
                    tvHotspotStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                });
            } else if (WifiService.ACTION_HOTSPOT_CLIENTS_UPDATED.equals(action)) {
                int count = intent.getIntExtra(WifiService.EXTRA_CLIENT_COUNT, 0);
                String info = intent.getStringExtra(WifiService.EXTRA_CLIENTS_INFO);
                runOnUiThread(() -> {
                    String base = tvHotspotStatus.getText().toString();
                    if (base.startsWith("✅ 热点已开启")) {
                        tvHotspotStatus.setText(base.split("\n")[0]
                                + "\n已连接设备: " + count + " 台"
                                + (info != null && !info.isEmpty() ? "\n" + info : ""));
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SharedPrefsManager.isWhiteTheme()) setTheme(R.style.AppThemeWhite);
        setContentView(R.layout.activity_wifi_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Wi-Fi 设置");

        cbHotspotMode = findViewById(R.id.cb_hotspot_mode);
        tvHotspotStatus = findViewById(R.id.tv_hotspot_status);
        btnSave = findViewById(R.id.btn_save_wifi);

        // 加载已保存配置
        WifiConfig config = SharedPrefsManager.loadWifiConfig();
        cbHotspotMode.setChecked(config.isHotspotMode());

        btnSave.setOnClickListener(v -> {
            saveConfig();
            LogManager.i(TAG, "[按钮] 保存Wi-Fi配置, 热点模式=" + cbHotspotMode.isChecked());
            if (cbHotspotMode.isChecked()) {
                // 立即启动热点服务
                Intent serviceIntent = new Intent(this, WifiService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "已保存，正在启动热点...", Toast.LENGTH_SHORT).show();
                tvHotspotStatus.setText("正在启动热点...");
                tvHotspotStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                // 停止热点服务
                stopService(new Intent(this, WifiService.class));
                Toast.makeText(this, "已保存，热点已关闭", Toast.LENGTH_SHORT).show();
                tvHotspotStatus.setText("热点未开启");
                tvHotspotStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });

        // 注册热点状态广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiService.ACTION_HOTSPOT_STARTED);
        filter.addAction(WifiService.ACTION_HOTSPOT_STOPPED);
        filter.addAction(WifiService.ACTION_HOTSPOT_CLIENTS_UPDATED);
        registerReceiver(hotspotReceiver, filter);
    }

    private void saveConfig() {
        WifiConfig config = SharedPrefsManager.loadWifiConfig();
        config.setHotspotMode(cbHotspotMode.isChecked());
        config.setEnabled(cbHotspotMode.isChecked());
        SharedPrefsManager.saveWifiConfig(config);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(hotspotReceiver); } catch (Exception ignored) {}
    }
}
