package com.carconnect.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.carconnect.app.activity.MainActivity;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

/**
 * 主后台服务 - 统一管理蓝牙和Wi-Fi的后台循环发现
 * 开机自启，持续在后台运行
 */
public class CarConnectService extends Service {

    private static final String TAG = "CarConnectService";
    private static final String CHANNEL_ID = "carconnect_main";
    private static final int NOTIFICATION_ID = 1000;

    // 循环扫描间隔（30秒）
    private static final long SCAN_INTERVAL = 30 * 1000L;

    private Handler handler;
    private boolean btConnected = false;
    private boolean wifiConnected = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case BluetoothService.ACTION_BLUETOOTH_CONNECTED:
                    btConnected = true;
                    String btName = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME);
                    updateNotification("蓝牙已连接: " + btName);
                    LogManager.i(TAG, "主服务: 蓝牙已连接 - " + btName);
                    break;
                case BluetoothService.ACTION_BLUETOOTH_DISCONNECTED:
                    btConnected = false;
                    wifiConnected = false;
                    updateNotification("蓝牙断开，重新扫描中...");
                    break;
                case WifiService.ACTION_WIFI_CONNECTED:
                    wifiConnected = true;
                    String ssid = intent.getStringExtra(WifiService.EXTRA_SSID);
                    String ip = intent.getStringExtra(WifiService.EXTRA_IP);
                    updateNotification("已全连接 | BT✓ WiFi✓ " + ssid + " " + ip);
                    LogManager.i(TAG, "主服务: Wi-Fi已连接 SSID=" + ssid + " IP=" + ip);
                    break;
                case WifiService.ACTION_WIFI_DISCONNECTED:
                    wifiConnected = false;
                    break;
            }
        }
    };

    // 循环扫描任务
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!btConnected) {
                LogManager.d(TAG, "循环检测: 蓝牙未连接，触发扫描");
                startBluetoothServiceScan();
            }
            if (btConnected && !wifiConnected) {
                LogManager.d(TAG, "循环检测: Wi-Fi 未连接，触发扫描");
                startWifiServiceConnect();
            }
            handler.postDelayed(this, SCAN_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothService.ACTION_BLUETOOTH_CONNECTED);
        filter.addAction(BluetoothService.ACTION_BLUETOOTH_DISCONNECTED);
        filter.addAction(WifiService.ACTION_WIFI_CONNECTED);
        filter.addAction(WifiService.ACTION_WIFI_DISCONNECTED);
        registerReceiver(statusReceiver, filter);

        LogManager.i(TAG, "主服务已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("CarConnect 后台运行中"));

        // 启动蓝牙和Wi-Fi服务
        startBluetoothService();
        startWifiService();

        // 开始循环扫描
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, SCAN_INTERVAL);

        LogManager.i(TAG, "主服务 onStartCommand，开始循环后台扫描（间隔" + (SCAN_INTERVAL / 1000) + "秒）");

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
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        LogManager.i(TAG, "主服务已停止，将由系统重启");
    }

    private void startBluetoothService() {
        Intent intent = new Intent(this, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void startWifiService() {
        Intent intent = new Intent(this, WifiService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void startBluetoothServiceScan() {
        Intent intent = new Intent(this, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void startWifiServiceConnect() {
        Intent intent = new Intent(this, WifiService.class);
        intent.setAction(WifiService.ACTION_START_WIFI_CONNECT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "CarConnect 主服务", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("蓝牙和Wi-Fi自动连接主服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CarConnect")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
