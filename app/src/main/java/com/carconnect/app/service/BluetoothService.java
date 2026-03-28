package com.carconnect.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.carconnect.app.activity.MainActivity;
import com.carconnect.app.model.BluetoothConfig;
import com.carconnect.app.utils.AppLaunchManager;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.Set;

/**
 * 蓝牙自动连接服务
 * - 通过广播发现蓝牙设备
 * - 自动配对并连接
 * - 支持重试次数和超时设置
 */
public class BluetoothService extends Service {

    private static final String TAG = "BluetoothService";
    private static final String CHANNEL_ID = "bluetooth_channel";
    private static final int NOTIFICATION_ID = 1001;

    // 广播动作
    public static final String ACTION_BLUETOOTH_CONNECTED = "com.carconnect.app.BT_CONNECTED";
    public static final String ACTION_BLUETOOTH_DISCONNECTED = "com.carconnect.app.BT_DISCONNECTED";
    public static final String ACTION_BLUETOOTH_CONNECTING = "com.carconnect.app.BT_CONNECTING";
    public static final String ACTION_BLUETOOTH_FAILED = "com.carconnect.app.BT_FAILED";
    public static final String ACTION_DEVICE_FOUND = "com.carconnect.app.BT_DEVICE_FOUND";
    public static final String EXTRA_DEVICE_NAME = "device_name";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_MESSAGE = "message";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothConfig config;
    private Handler handler;

    private int currentRetryCount = 0;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private boolean isDiscovering = false;
    private BluetoothDevice targetDevice;

    private BluetoothA2dp bluetoothA2dp;
    private BluetoothHeadset bluetoothHeadset;

    // 发现设备的广播接收器
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String name = device.getName();
                    String address = device.getAddress();
                    LogManager.d(TAG, "发现蓝牙设备: " + name + " [" + address + "]");

                    // 通知界面
                    Intent foundIntent = new Intent(ACTION_DEVICE_FOUND);
                    foundIntent.putExtra(EXTRA_DEVICE_NAME, name != null ? name : "未知设备");
                    foundIntent.putExtra(EXTRA_DEVICE_ADDRESS, address);
                    sendBroadcast(foundIntent);

                    // 仅当发现目标设备时才发起连接请求
                    if (!isConnected && !isConnecting && isTargetDevice(device)) {
                        LogManager.i(TAG, "✅ 发现目标蓝牙设备，发送连接请求: " + name + " [" + address + "]");
                        targetDevice = device;
                        bluetoothAdapter.cancelDiscovery();
                        connectToDevice(device);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                isDiscovering = true;
                LogManager.i(TAG, "蓝牙扫描已开始");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                isDiscovering = false;
                LogManager.i(TAG, "蓝牙扫描结束");
                if (!isConnected && !isConnecting && currentRetryCount < config.getMaxRetryCount()) {
                    // 未找到目标，继续重试
                    scheduleRetry();
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (device != null) {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        LogManager.i(TAG, "设备配对成功: " + device.getName());
                        connectProfiles(device);
                    } else if (bondState == BluetoothDevice.BOND_BONDING) {
                        LogManager.i(TAG, "正在配对: " + device.getName());
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        LogManager.w(TAG, "配对失败/取消: " + device.getName());
                    }
                }
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleConnectionStateChange(state, device);
            }
        }
    };

    // A2DP profile 监听器（音频）
    private final BluetoothProfile.ServiceListener a2dpListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            bluetoothA2dp = (BluetoothA2dp) proxy;
            LogManager.i(TAG, "A2DP Profile 已连接");
            if (targetDevice != null) {
                connectA2dp(targetDevice);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            bluetoothA2dp = null;
        }
    };

    // HFP profile 监听器（通话）
    private final BluetoothProfile.ServiceListener headsetListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            bluetoothHeadset = (BluetoothHeadset) proxy;
            LogManager.i(TAG, "Headset Profile 已连接");
            if (targetDevice != null) {
                connectHeadset(targetDevice);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            bluetoothHeadset = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        config = SharedPrefsManager.loadBluetoothConfig();

        createNotificationChannel();
        registerReceivers();

        // 获取蓝牙 Profile 代理
        if (bluetoothAdapter != null) {
            bluetoothAdapter.getProfileProxy(this, a2dpListener, BluetoothProfile.A2DP);
            bluetoothAdapter.getProfileProxy(this, headsetListener, BluetoothProfile.HEADSET);
        }

        LogManager.i(TAG, "蓝牙服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("蓝牙服务运行中"));
        config = SharedPrefsManager.loadBluetoothConfig();

        if (config.isEnabled() && config.isAutoConnect()) {
            LogManager.i(TAG, "开始蓝牙自动连接流程");
            currentRetryCount = 0;
            startBluetoothDiscovery();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        unregisterReceivers();

        if (bluetoothAdapter != null && isDiscovering) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (bluetoothA2dp != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp);
        }
        if (bluetoothHeadset != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
        }
        LogManager.i(TAG, "蓝牙服务已停止");
    }

    /**
     * 开始蓝牙扫描发现
     */
    public void startBluetoothDiscovery() {
        if (bluetoothAdapter == null) {
            LogManager.e(TAG, "设备不支持蓝牙");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            LogManager.w(TAG, "蓝牙未开启，等待蓝牙开启...");
            return;
        }
        if (isConnected) {
            LogManager.i(TAG, "蓝牙已连接，跳过扫描");
            return;
        }

        // 先检查已配对设备
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null) {
            for (BluetoothDevice device : pairedDevices) {
                if (isTargetDevice(device)) {
                    LogManager.i(TAG, "在已配对列表中找到目标设备: " + device.getName());
                    targetDevice = device;
                    connectToDevice(device);
                    return;
                }
            }
        }

        // 开始扫描
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        boolean started = bluetoothAdapter.startDiscovery();
        if (started) {
            LogManager.i(TAG, "开始扫描蓝牙设备...(第 " + (currentRetryCount + 1) + " 次)");
        } else {
            LogManager.e(TAG, "启动蓝牙扫描失败");
            scheduleRetry();
        }
    }

    /**
     * 判断是否是目标蓝牙设备
     */
    private boolean isTargetDevice(BluetoothDevice device) {
        String targetName = config.getTargetDeviceName();
        String targetAddr = config.getTargetDeviceAddress();

        if (device == null) return false;

        // 通过MAC地址精确匹配
        if (targetAddr != null && !targetAddr.isEmpty()) {
            if (targetAddr.equalsIgnoreCase(device.getAddress())) return true;
        }

        // 通过名称模糊匹配
        if (targetName != null && !targetName.isEmpty()) {
            String deviceName = device.getName();
            if (deviceName != null && deviceName.toLowerCase().contains(targetName.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 连接到蓝牙设备
     */
    private void connectToDevice(BluetoothDevice device) {
        if (isConnecting) return;
        isConnecting = true;

        LogManager.i(TAG, "尝试连接蓝牙设备: " + device.getName() + " [" + device.getAddress() + "]");
        sendBroadcast(new Intent(ACTION_BLUETOOTH_CONNECTING)
                .putExtra(EXTRA_DEVICE_NAME, device.getName())
                .putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress()));

        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDED) {
            // 已配对，直接连接 Profile
            connectProfiles(device);
        } else if (bondState == BluetoothDevice.BOND_NONE) {
            // 未配对，先配对
            LogManager.i(TAG, "设备未配对，开始配对...");
            device.createBond();
        } else {
            LogManager.i(TAG, "正在配对中，等待配对完成...");
        }

        // 超时处理
        handler.postDelayed(() -> {
            if (isConnecting && !isConnected) {
                LogManager.w(TAG, "连接超时");
                isConnecting = false;
                currentRetryCount++;
                scheduleRetry();
            }
        }, config.getConnectTimeout() * 1000L);
    }

    /**
     * 连接蓝牙 Profile（A2DP + Headset）
     */
    private void connectProfiles(BluetoothDevice device) {
        if (bluetoothA2dp != null) connectA2dp(device);
        if (bluetoothHeadset != null) connectHeadset(device);
    }

    private void connectA2dp(BluetoothDevice device) {
        try {
            java.lang.reflect.Method connect = bluetoothA2dp.getClass().getMethod("connect", BluetoothDevice.class);
            boolean result = (boolean) connect.invoke(bluetoothA2dp, device);
            LogManager.i(TAG, "A2DP 连接请求: " + result);
        } catch (Exception e) {
            LogManager.e(TAG, "A2DP 连接失败: " + e.getMessage());
        }
    }

    private void connectHeadset(BluetoothDevice device) {
        try {
            java.lang.reflect.Method connect = bluetoothHeadset.getClass().getMethod("connect", BluetoothDevice.class);
            boolean result = (boolean) connect.invoke(bluetoothHeadset, device);
            LogManager.i(TAG, "Headset 连接请求: " + result);
        } catch (Exception e) {
            LogManager.e(TAG, "Headset 连接失败: " + e.getMessage());
        }
    }

    /**
     * 处理连接状态变化
     */
    private void handleConnectionStateChange(int state, BluetoothDevice device) {
        if (device == null) return;
        String name = device.getName();
        if (state == BluetoothAdapter.STATE_CONNECTED) {
            isConnected = true;
            isConnecting = false;
            currentRetryCount = 0;
            handler.removeCallbacksAndMessages(null);
            LogManager.i(TAG, "蓝牙已连接: " + name);
            updateNotification("已连接: " + name);
            sendBroadcast(new Intent(ACTION_BLUETOOTH_CONNECTED)
                    .putExtra(EXTRA_DEVICE_NAME, name)
                    .putExtra(EXTRA_DEVICE_ADDRESS, device.getAddress()));

            // 通知绑定应用管理器（需要同时连接Wi-Fi才会启动应用）
            AppLaunchManager.onBluetoothConnected(this);

            // 蓝牙连接成功后，触发Wi-Fi连接
            Intent wifiIntent = new Intent(this, WifiService.class);
            wifiIntent.setAction(WifiService.ACTION_START_WIFI_CONNECT);
            startService(wifiIntent);

        } else if (state == BluetoothAdapter.STATE_DISCONNECTED) {
            if (isConnected) {
                isConnected = false;
                isConnecting = false;
                LogManager.w(TAG, "蓝牙断开: " + name);
                updateNotification("蓝牙已断开，等待重连...");
                sendBroadcast(new Intent(ACTION_BLUETOOTH_DISCONNECTED)
                        .putExtra(EXTRA_DEVICE_NAME, name));
                // 通知绑定应用管理器重置状态
                AppLaunchManager.onBluetoothDisconnected();
                // 断开后重新开始扫描
                handler.postDelayed(() -> {
                    currentRetryCount = 0;
                    startBluetoothDiscovery();
                }, 3000);
            }
        }
    }

    /**
     * 安排重试
     */
    private void scheduleRetry() {
        if (currentRetryCount >= config.getMaxRetryCount()) {
            LogManager.w(TAG, "已达到最大重试次数 " + config.getMaxRetryCount() + "，停止重试");
            sendBroadcast(new Intent(ACTION_BLUETOOTH_FAILED)
                    .putExtra(EXTRA_MESSAGE, "达到最大重试次数"));
            updateNotification("连接失败，等待下次扫描");
            // 5分钟后重新开始
            handler.postDelayed(() -> {
                currentRetryCount = 0;
                startBluetoothDiscovery();
            }, 5 * 60 * 1000);
            return;
        }
        currentRetryCount++;
        long delay = config.getRetryInterval() * 1000L;
        LogManager.i(TAG, "将在 " + config.getRetryInterval() + " 秒后重试 (第 " + currentRetryCount + "/" + config.getMaxRetryCount() + " 次)");
        handler.postDelayed(this::startBluetoothDiscovery, delay);
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(discoveryReceiver, filter);
    }

    private void unregisterReceivers() {
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "蓝牙连接服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("蓝牙自动连接后台服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CarConnect")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    /**
     * 供外部调用：刷新配置并重新扫描
     */
    public static void restartScan(Context context) {
        Intent intent = new Intent(context, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
