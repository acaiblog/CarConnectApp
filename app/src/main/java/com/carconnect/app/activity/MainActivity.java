package com.carconnect.app.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.carconnect.app.R;
import com.carconnect.app.service.BluetoothService;
import com.carconnect.app.service.CarConnectService;
import com.carconnect.app.service.WifiService;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 100;

    private TextView tvStatus, tvBtStatus, tvWifiStatus, tvWifiInfo;
    private Button btnBluetoothSettings, btnWifiSettings, btnAppList, btnLog,
            btnStartService, btnStopScan, btnManualScan, btnToggleTheme;

    private final BroadcastReceiver mainReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            runOnUiThread(() -> {
                switch (action) {
                    case BluetoothService.ACTION_BLUETOOTH_CONNECTED:
                        String btName = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME);
                        tvBtStatus.setText("蓝牙: 已连接 ✓  " + btName);
                        tvBtStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        tvStatus.setText("状态: 蓝牙已连接 " + btName);
                        LogManager.i(TAG, "[按钮事件] 蓝牙连接成功: " + btName);
                        break;
                    case BluetoothService.ACTION_BLUETOOTH_DISCONNECTED:
                        tvBtStatus.setText("蓝牙: 已断开");
                        tvBtStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        tvWifiStatus.setText("Wi-Fi: 等待蓝牙...");
                        tvWifiInfo.setText("");
                        tvStatus.setText("状态: 蓝牙已断开，等待重连");
                        break;
                    case BluetoothService.ACTION_BLUETOOTH_CONNECTING:
                        String connectingName = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME);
                        String connectingAddr = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_ADDRESS);
                        String connectingDisplay = (connectingName != null && !connectingName.isEmpty())
                                ? connectingName : connectingAddr;
                        tvBtStatus.setText("蓝牙: 连接中... " + connectingDisplay);
                        tvBtStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        break;
                    case BluetoothService.ACTION_DEVICE_FOUND:
                        String devName = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME);
                        String devAddr = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_ADDRESS);
                        LogManager.d(TAG, "发现设备: " + devName + " " + devAddr);
                        break;
                    case WifiService.ACTION_WIFI_CONNECTED:
                        String ssid = intent.getStringExtra(WifiService.EXTRA_SSID);
                        String ip = intent.getStringExtra(WifiService.EXTRA_IP);
                        int signal = intent.getIntExtra(WifiService.EXTRA_SIGNAL, 0);
                        String mac = intent.getStringExtra(WifiService.EXTRA_MAC);
                        tvWifiStatus.setText("Wi-Fi: 已连接 ✓  " + ssid);
                        tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        tvWifiInfo.setText("IP: " + ip + "  信号: " + signal + "dBm  MAC: " + mac);
                        tvStatus.setText("状态: 全部已连接 BT✓ WiFi✓");
                        break;
                    case WifiService.ACTION_WIFI_DISCONNECTED:
                        tvWifiStatus.setText("Wi-Fi: 已断开");
                        tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        tvWifiInfo.setText("");
                        break;
                    case WifiService.ACTION_WIFI_CONNECTING:
                        tvWifiStatus.setText("Wi-Fi: 连接中...");
                        tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        break;
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 主题必须在 setContentView 前设置
        if (SharedPrefsManager.isWhiteTheme()) {
            setTheme(R.style.AppThemeWhite);
        } else {
            setTheme(R.style.AppTheme);
        }
        setContentView(R.layout.activity_main);
        initViews();
        requestNecessaryPermissions();
        registerBroadcastReceivers();
        startCarConnectService();
        LogManager.i(TAG, "主界面启动");
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvBtStatus = findViewById(R.id.tv_bt_status);
        tvWifiStatus = findViewById(R.id.tv_wifi_status);
        tvWifiInfo = findViewById(R.id.tv_wifi_info);

        btnBluetoothSettings = findViewById(R.id.btn_bluetooth_settings);
        btnWifiSettings = findViewById(R.id.btn_wifi_settings);
        btnAppList = findViewById(R.id.btn_app_list);
        btnLog = findViewById(R.id.btn_log);
        btnStartService = findViewById(R.id.btn_start_service);
        btnStopScan = findViewById(R.id.btn_stop_scan);
        btnManualScan = findViewById(R.id.btn_manual_scan);
        btnToggleTheme = findViewById(R.id.btn_toggle_theme);

        // 更新主题按钮显示
        updateThemeButton();

        btnToggleTheme.setOnClickListener(v -> {
            boolean isWhite = SharedPrefsManager.isWhiteTheme();
            SharedPrefsManager.setWhiteTheme(!isWhite);
            LogManager.i(TAG, "[按钮] 切换主题 -> " + (!isWhite ? "白色" : "深色"));
            // 重启 Activity 使主题生效
            recreate();
        });

        btnBluetoothSettings.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 打开蓝牙设置");
            startActivity(new Intent(this, BluetoothSettingsActivity.class));
        });

        btnWifiSettings.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 打开Wi-Fi设置");
            startActivity(new Intent(this, WifiSettingsActivity.class));
        });

        btnAppList.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 打开应用列表");
            startActivity(new Intent(this, AppListActivity.class));
        });

        btnLog.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 打开日志界面");
            startActivity(new Intent(this, LogActivity.class));
        });

        btnStartService.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 手动启动服务");
            startCarConnectService();
            Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show();
        });

        btnManualScan.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 手动触发蓝牙扫描");
            Intent btIntent = new Intent(this, BluetoothService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(btIntent);
            } else {
                startService(btIntent);
            }
            Toast.makeText(this, "开始蓝牙扫描", Toast.LENGTH_SHORT).show();
        });

        btnStopScan.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 停止服务");
            stopService(new Intent(this, CarConnectService.class));
            stopService(new Intent(this, BluetoothService.class));
            stopService(new Intent(this, WifiService.class));
            Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateThemeButton() {
        if (btnToggleTheme == null) return;
        if (SharedPrefsManager.isWhiteTheme()) {
            btnToggleTheme.setText("🌙 深色");
            btnToggleTheme.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF555566));
        } else {
            btnToggleTheme.setText("☀ 白色");
            btnToggleTheme.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF334499));
        }
    }

    private void startCarConnectService() {
        Intent intent = new Intent(this, CarConnectService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void registerBroadcastReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothService.ACTION_BLUETOOTH_CONNECTED);
        filter.addAction(BluetoothService.ACTION_BLUETOOTH_DISCONNECTED);
        filter.addAction(BluetoothService.ACTION_BLUETOOTH_CONNECTING);
        filter.addAction(BluetoothService.ACTION_DEVICE_FOUND);
        filter.addAction(WifiService.ACTION_WIFI_CONNECTED);
        filter.addAction(WifiService.ACTION_WIFI_DISCONNECTED);
        filter.addAction(WifiService.ACTION_WIFI_CONNECTING);
        registerReceiver(mainReceiver, filter);
    }

    private void requestNecessaryPermissions() {
        List<String> permissions = new ArrayList<>();
        String[] required = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
        };
        for (String perm : required) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(perm);
            }
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            LogManager.i(TAG, "权限请求结果已处理");
            startCarConnectService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(mainReceiver); } catch (Exception ignored) {}
    }
}
