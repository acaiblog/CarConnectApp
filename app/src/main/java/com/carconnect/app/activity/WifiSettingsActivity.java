package com.carconnect.app.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.carconnect.app.R;
import com.carconnect.app.model.WifiConfig;
import com.carconnect.app.service.WifiService;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WifiSettingsActivity extends AppCompatActivity {

    private static final String TAG = "WifiSettings";

    private EditText etSsid, etPassword, etMaxRetry, etTimeout;
    private Button btnSave, btnScan, btnConnect;
    private CheckBox cbShowPwd, cbHotspotMode;
    private ListView lvNetworks;
    private TextView tvCurrentWifi, tvScanStatus;

    private WifiManager wifiManager;
    private final List<String> networkList = new ArrayList<>();
    private ArrayAdapter<String> networkAdapter;

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                updateWifiList();
            }
        }
    };

    private final BroadcastReceiver wifiStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiService.ACTION_WIFI_CONNECTED.equals(intent.getAction())) {
                String ssid = intent.getStringExtra(WifiService.EXTRA_SSID);
                String ip = intent.getStringExtra(WifiService.EXTRA_IP);
                int signal = intent.getIntExtra(WifiService.EXTRA_SIGNAL, 0);
                String mac = intent.getStringExtra(WifiService.EXTRA_MAC);
                runOnUiThread(() -> {
                    tvCurrentWifi.setText("当前连接: " + ssid
                            + "\nIP: " + ip
                            + "  信号: " + signal + "dBm"
                            + "\nMAC: " + mac);
                    tvCurrentWifi.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
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

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        etSsid = findViewById(R.id.et_ssid);
        etPassword = findViewById(R.id.et_password);
        etMaxRetry = findViewById(R.id.et_wifi_max_retry);
        etTimeout = findViewById(R.id.et_wifi_timeout);
        btnSave = findViewById(R.id.btn_save_wifi);
        btnScan = findViewById(R.id.btn_scan_wifi);
        btnConnect = findViewById(R.id.btn_connect_wifi);
        cbShowPwd = findViewById(R.id.cb_show_password);
        cbHotspotMode = findViewById(R.id.cb_hotspot_mode);
        lvNetworks = findViewById(R.id.lv_wifi_networks);
        tvCurrentWifi = findViewById(R.id.tv_current_wifi);
        tvScanStatus = findViewById(R.id.tv_wifi_scan_status);

        networkAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, networkList);
        lvNetworks.setAdapter(networkAdapter);

        // 加载当前 Wi-Fi 信息
        loadCurrentWifiInfo();

        // 加载已保存配置
        WifiConfig config = SharedPrefsManager.loadWifiConfig();
        etSsid.setText(config.getTargetSsid());
        etPassword.setText(config.getPassword());
        etMaxRetry.setText(String.valueOf(config.getMaxRetryCount()));
        etTimeout.setText(String.valueOf(config.getConnectTimeout()));
        cbHotspotMode.setChecked(config.isHotspotMode());

        cbShowPwd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });

        lvNetworks.setOnItemClickListener((parent, view, position, id) -> {
            String entry = networkList.get(position);
            // 分隔线行不可点击
            if (entry.startsWith("── ")) return;
            // 去掉图标和信号强度等后缀，提取纯 SSID
            String ssid = entry;
            if (ssid.startsWith("✅ ") || ssid.startsWith("📶 ") || ssid.startsWith("🔒 ") || ssid.startsWith("🔓 ")) {
                ssid = ssid.substring(3);
            }
            // 去掉后面的 "  信号XX%" 部分
            if (ssid.contains("  ")) {
                ssid = ssid.substring(0, ssid.indexOf("  ")).trim();
            }
            etSsid.setText(ssid);
            LogManager.i(TAG, "[选择网络] " + ssid);
            Toast.makeText(this, "已选择: " + ssid, Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            saveConfig();
            LogManager.i(TAG, "[按钮] 保存Wi-Fi配置");
        });

        btnScan.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 扫描Wi-Fi");
            startWifiScan();
        });

        btnConnect.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 手动连接Wi-Fi");
            saveConfig();
            Intent intent = new Intent(this, WifiService.class);
            intent.setAction(WifiService.ACTION_START_WIFI_CONNECT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(this, "开始连接 Wi-Fi", Toast.LENGTH_SHORT).show();
        });

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, filter);

        IntentFilter statusFilter = new IntentFilter(WifiService.ACTION_WIFI_CONNECTED);
        registerReceiver(wifiStatusReceiver, statusFilter);

        // 页面打开时立即加载保存的网络
        showSavedNetworks();
    }

    /** 加载当前Wi-Fi连接信息 */
    private void loadCurrentWifiInfo() {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null && info.getSSID() != null && !info.getSSID().equals("<unknown ssid>")) {
                String ssid = info.getSSID().replace("\"", "");
                String ip = intToIp(info.getIpAddress());
                tvCurrentWifi.setText("当前连接: " + ssid + "\nIP: " + ip + "  信号: " + info.getRssi() + "dBm");
                tvCurrentWifi.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvCurrentWifi.setText("当前未连接 Wi-Fi");
            }
        } else {
            tvCurrentWifi.setText("Wi-Fi 未开启");
        }
    }

    /** 显示已保存的Wi-Fi（手机记住的网络） */
    private void showSavedNetworks() {
        networkList.clear();
        Set<String> savedSsids = getSavedSsids();
        if (!savedSsids.isEmpty()) {
            networkList.add("── 已保存的网络 ──────────────");
            for (String ssid : savedSsids) {
                networkList.add("✅ " + ssid);
            }
            tvScanStatus.setText("已保存 " + savedSsids.size() + " 个网络，点击【扫描】搜索更多");
        } else {
            // Android 10+ 系统限制，无法获取已保存列表
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tvScanStatus.setText("Android 10+ 系统限制无法读取已保存列表，请点击【扫描】");
            } else {
                tvScanStatus.setText("未找到已保存的网络，请点击【扫描】");
            }
        }
        networkAdapter.notifyDataSetChanged();
    }

    /** 获取手机已保存（记住）的Wi-Fi SSID列表 */
    @SuppressWarnings("deprecation")
    private Set<String> getSavedSsids() {
        Set<String> result = new HashSet<>();
        // Android 10+ getConfiguredNetworks 系统做了限制，只在 Android 9 及以下可用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LogManager.w(TAG, "Android 10+ 系统限制，无法通过 getConfiguredNetworks 获取已保存网络");
            return result;
        }
        // 需要定位权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            LogManager.w(TAG, "缺少定位权限，无法获取已保存网络");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 200);
            return result;
        }
        try {
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs != null) {
                for (WifiConfiguration c : configs) {
                    if (c.SSID != null && !c.SSID.isEmpty()) {
                        result.add(c.SSID.replace("\"", ""));
                    }
                }
            }
            LogManager.i(TAG, "已保存Wi-Fi网络数: " + result.size());
        } catch (Exception e) {
            LogManager.w(TAG, "获取已保存网络失败: " + e.getMessage());
        }
        return result;
    }

    /** 扫描完成后合并显示：已保存 + 可用 */
    private void updateWifiList() {
        List<ScanResult> results = wifiManager.getScanResults();
        Set<String> savedSsids = getSavedSsids();

        // 获取当前连接的Wi-Fi
        String currentSsid = "";
        if (wifiManager.isWifiEnabled()) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null && info.getSSID() != null && !info.getSSID().equals("<unknown ssid>")) {
                currentSsid = info.getSSID().replace("\"", "");
            }
        }

        // 按信号强度分类
        List<String> savedAvailable = new ArrayList<>();
        List<String> otherAvailable = new ArrayList<>();

        Set<String> addedSsids = new HashSet<>();
        for (ScanResult result : results) {
            if (result.SSID == null || result.SSID.isEmpty()) continue;
            if (addedSsids.contains(result.SSID)) continue;
            addedSsids.add(result.SSID);

            int level = WifiManager.calculateSignalLevel(result.level, 5);
            String signal = level * 20 + "%";
            boolean isOpen = result.capabilities == null || !result.capabilities.contains("WPA");
            String lockIcon = isOpen ? "🔓" : "🔒";
            // 当前连接标记
            String connMark = result.SSID.equals(currentSsid) ? " 📶已连接" : "";
            String entry = lockIcon + " " + result.SSID + "  " + signal + connMark;

            if (savedSsids.contains(result.SSID)) {
                savedAvailable.add("✅ " + result.SSID + "  " + signal + connMark);
            } else if (result.SSID.equals(currentSsid)) {
                // 当前连接但不在保存列表（Android 10+）
                savedAvailable.add("✅ " + result.SSID + "  " + signal + " 📶已连接");
            } else {
                otherAvailable.add(entry);
            }
        }

        networkList.clear();
        // 已保存且可用
        if (!savedAvailable.isEmpty()) {
            networkList.add("── 已保存的网络 ──────────────");
            networkList.addAll(savedAvailable);
        }
        // 其他已保存但不在扫描结果中的
        Set<String> offlineSaved = new HashSet<>(savedSsids);
        offlineSaved.removeAll(addedSsids);
        if (!offlineSaved.isEmpty()) {
            if (savedAvailable.isEmpty()) {
                networkList.add("── 已保存的网络 ──────────────");
            }
            for (String ssid : offlineSaved) {
                networkList.add("✅ " + ssid + "  (不在范围内)");
            }
        }
        // 可用Wi-Fi
        if (!otherAvailable.isEmpty()) {
            networkList.add("── 可用网络 ──────────────────");
            networkList.addAll(otherAvailable);
        }

        networkAdapter.notifyDataSetChanged();
        tvScanStatus.setText("扫描完成：" + (savedAvailable.size() + offlineSaved.size()) + " 个已保存，"
                + otherAvailable.size() + " 个其他网络");
        LogManager.i(TAG, "Wi-Fi 扫描完成，已保存可用: " + savedAvailable.size()
                + "，其他: " + otherAvailable.size());
    }

    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        networkList.clear();
        networkList.add("── 正在扫描... ───────────────");
        networkAdapter.notifyDataSetChanged();
        wifiManager.startScan();
        tvScanStatus.setText("正在扫描...");
    }

    private void saveConfig() {
        WifiConfig config = new WifiConfig();
        config.setTargetSsid(etSsid.getText().toString().trim());
        config.setPassword(etPassword.getText().toString());
        try {
            config.setMaxRetryCount(Integer.parseInt(etMaxRetry.getText().toString().trim()));
        } catch (Exception e) { config.setMaxRetryCount(5); }
        try {
            config.setConnectTimeout(Integer.parseInt(etTimeout.getText().toString().trim()));
        } catch (Exception e) { config.setConnectTimeout(30); }
        config.setAutoConnect(true);
        config.setEnabled(true);
        config.setHotspotMode(cbHotspotMode.isChecked());

        SharedPrefsManager.saveWifiConfig(config);
        Toast.makeText(this, "Wi-Fi 配置已保存", Toast.LENGTH_SHORT).show();
        LogManager.i(TAG, "Wi-Fi 配置已保存: SSID=" + config.getTargetSsid());
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(wifiScanReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(wifiStatusReceiver); } catch (Exception ignored) {}
    }
}
