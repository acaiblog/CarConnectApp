package com.carconnect.app.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carconnect.app.R;
import com.carconnect.app.model.BluetoothConfig;
import com.carconnect.app.service.BluetoothService;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothSettingsActivity extends AppCompatActivity {

    private static final String TAG = "BTSettings";

    private EditText etDeviceName, etDeviceAddress, etMaxRetry, etTimeout, etRetryInterval;
    private Button btnSave, btnScan, btnClearBound;
    private ListView lvDevices;
    private TextView tvScanStatus;

    private BluetoothAdapter bluetoothAdapter;
    // 存设备显示名（纯蓝牙名称）和地址
    private final List<String> deviceList = new ArrayList<>();
    private final List<String> deviceAddressList = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String name = device.getName() != null ? device.getName() : "未知设备";
                    String address = device.getAddress();
                    // 只显示蓝牙名称，不加任何前缀
                    String entry = name + "\n" + address;
                    if (!deviceAddressList.contains(address)) {
                        deviceList.add(entry);
                        deviceAddressList.add(address);
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                tvScanStatus.setText("扫描完成，共发现 " + deviceList.size() + " 台设备");
                btnScan.setEnabled(true);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                tvScanStatus.setText("正在扫描蓝牙设备...");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 根据主题设置
        applyTheme();
        setContentView(R.layout.activity_bluetooth_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("蓝牙设置");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        etDeviceName = findViewById(R.id.et_device_name);
        etDeviceAddress = findViewById(R.id.et_device_address);
        etMaxRetry = findViewById(R.id.et_max_retry);
        etTimeout = findViewById(R.id.et_timeout);
        etRetryInterval = findViewById(R.id.et_retry_interval);
        btnSave = findViewById(R.id.btn_save_bt);
        btnScan = findViewById(R.id.btn_scan_bt);
        btnClearBound = findViewById(R.id.btn_clear_bound);
        lvDevices = findViewById(R.id.lv_bt_devices);
        tvScanStatus = findViewById(R.id.tv_scan_status);

        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, deviceList);
        lvDevices.setAdapter(deviceAdapter);

        // 加载已保存配置
        BluetoothConfig config = SharedPrefsManager.loadBluetoothConfig();
        etDeviceName.setText(config.getTargetDeviceName());
        etDeviceAddress.setText(config.getTargetDeviceAddress());
        etMaxRetry.setText(String.valueOf(config.getMaxRetryCount()));
        etTimeout.setText(String.valueOf(config.getConnectTimeout()));
        etRetryInterval.setText(String.valueOf(config.getRetryInterval()));

        // 显示已配对设备（只显示蓝牙名称）
        loadBondedDevices();

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            String entry = deviceList.get(position);
            String address = deviceAddressList.get(position);
            String name = entry.split("\n")[0];
            etDeviceName.setText(name);
            etDeviceAddress.setText(address);
            LogManager.i(TAG, "[选择设备] " + name + " " + address);
            Toast.makeText(this, "已选择: " + name, Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            saveConfig();
            LogManager.i(TAG, "[按钮] 保存蓝牙配置");
        });

        btnScan.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 开始扫描蓝牙");
            startScan();
        });

        btnClearBound.setOnClickListener(v -> {
            LogManager.i(TAG, "[按钮] 刷新设备列表");
            deviceList.clear();
            deviceAddressList.clear();
            loadBondedDevices();
            deviceAdapter.notifyDataSetChanged();
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(scanReceiver, filter);
    }

    private void applyTheme() {
        if (SharedPrefsManager.isWhiteTheme()) {
            setTheme(R.style.AppThemeWhite);
        } else {
            setTheme(R.style.AppTheme);
        }
    }

    /** 加载已配对设备，只显示蓝牙名称，不加[已配对]前缀 */
    private void loadBondedDevices() {
        if (bluetoothAdapter == null) return;
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) {
            for (BluetoothDevice d : bonded) {
                String name = d.getName() != null ? d.getName() : "未知";
                String address = d.getAddress();
                if (!deviceAddressList.contains(address)) {
                    // 只显示蓝牙名称，不加"[已配对]"前缀
                    deviceList.add(name + "\n" + address);
                    deviceAddressList.add(address);
                }
            }
            deviceAdapter.notifyDataSetChanged();
            tvScanStatus.setText("已配对设备: " + bonded.size() + " 台，可点击扫描发现更多");
        }
    }

    private void startScan() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
            Toast.makeText(this, "正在开启蓝牙...", Toast.LENGTH_SHORT).show();
            return;
        }
        deviceList.clear();
        deviceAddressList.clear();
        loadBondedDevices();
        btnScan.setEnabled(false);
        if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();
    }

    private void saveConfig() {
        BluetoothConfig config = new BluetoothConfig();
        config.setTargetDeviceName(etDeviceName.getText().toString().trim());
        config.setTargetDeviceAddress(etDeviceAddress.getText().toString().trim());
        try {
            config.setMaxRetryCount(Integer.parseInt(etMaxRetry.getText().toString().trim()));
        } catch (Exception e) { config.setMaxRetryCount(5); }
        try {
            config.setConnectTimeout(Integer.parseInt(etTimeout.getText().toString().trim()));
        } catch (Exception e) { config.setConnectTimeout(30); }
        try {
            config.setRetryInterval(Integer.parseInt(etRetryInterval.getText().toString().trim()));
        } catch (Exception e) { config.setRetryInterval(5); }
        config.setAutoConnect(true);
        config.setEnabled(true);

        SharedPrefsManager.saveBluetoothConfig(config);
        Toast.makeText(this, "蓝牙配置已保存", Toast.LENGTH_SHORT).show();
        LogManager.i(TAG, "蓝牙配置已保存: " + config.getTargetDeviceName()
                + " 重试间隔=" + config.getRetryInterval() + "s");

        // 重启蓝牙服务
        Intent intent = new Intent(this, BluetoothService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
    }
}
