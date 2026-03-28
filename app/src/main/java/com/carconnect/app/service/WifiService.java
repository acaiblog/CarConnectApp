package com.carconnect.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.carconnect.app.activity.MainActivity;
import com.carconnect.app.model.WifiConfig;
import com.carconnect.app.utils.AppLaunchManager;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.List;

/**
 * Wi-Fi 自动连接服务
 * - 在蓝牙连接后自动连接车机热点
 * - 显示连接信息
 */
public class WifiService extends Service {

    private static final String TAG = "WifiService";
    private static final String CHANNEL_ID = "wifi_channel";
    private static final int NOTIFICATION_ID = 1002;

    public static final String ACTION_START_WIFI_CONNECT = "com.carconnect.app.START_WIFI";
    public static final String ACTION_WIFI_CONNECTED = "com.carconnect.app.WIFI_CONNECTED";
    public static final String ACTION_WIFI_DISCONNECTED = "com.carconnect.app.WIFI_DISCONNECTED";
    public static final String ACTION_WIFI_CONNECTING = "com.carconnect.app.WIFI_CONNECTING";
    public static final String ACTION_WIFI_FAILED = "com.carconnect.app.WIFI_FAILED";
    public static final String ACTION_WIFI_INFO = "com.carconnect.app.WIFI_INFO";
    public static final String EXTRA_SSID = "ssid";
    public static final String EXTRA_IP = "ip";
    public static final String EXTRA_SIGNAL = "signal";
    public static final String EXTRA_MAC = "mac";
    public static final String EXTRA_MESSAGE = "message";

    private WifiManager wifiManager;
    private WifiConfig config;
    private Handler handler;
    private int currentRetryCount = 0;
    private boolean isConnecting = false;
    private boolean isConnected = false;

    // Wi-Fi 状态广播接收器
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.isConnected()) {
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        String ssid = wifiInfo != null ? wifiInfo.getSSID().replace("\"", "") : "";
                        if (isTargetWifi(ssid)) {
                            onWifiConnected(wifiInfo);
                        }
                    } else if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                        if (isConnected) {
                            isConnected = false;
                            LogManager.w(TAG, "Wi-Fi 已断开");
                            sendBroadcast(new Intent(ACTION_WIFI_DISCONNECTED));
                            AppLaunchManager.onWifiDisconnected();
                            // 重新尝试连接
                            handler.postDelayed(() -> {
                                currentRetryCount = 0;
                                startWifiConnect();
                            }, 5000);
                        }
                    }
                }
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                handleScanResults();
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    LogManager.i(TAG, "Wi-Fi 已开启");
                    startWifiConnect();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        config = SharedPrefsManager.loadWifiConfig();

        createNotificationChannel();
        registerReceivers();
        LogManager.i(TAG, "Wi-Fi 服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Wi-Fi 服务运行中"));
        config = SharedPrefsManager.loadWifiConfig();

        if (intent != null && ACTION_START_WIFI_CONNECT.equals(intent.getAction())) {
            LogManager.i(TAG, "收到蓝牙连接通知，开始 Wi-Fi 连接");
            currentRetryCount = 0;
            startWifiConnect();
        } else if (config.isEnabled() && config.isAutoConnect()) {
            startWifiConnect();
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
        try { unregisterReceiver(wifiStateReceiver); } catch (Exception ignored) {}
        LogManager.i(TAG, "Wi-Fi 服务已停止");
    }

    /**
     * 开始 Wi-Fi 连接流程
     */
    public void startWifiConnect() {
        if (wifiManager == null) {
            LogManager.e(TAG, "设备不支持 Wi-Fi");
            return;
        }
        if (isConnecting || isConnected) return;
        if (!config.isEnabled() || config.getTargetSsid() == null || config.getTargetSsid().isEmpty()) {
            LogManager.w(TAG, "Wi-Fi 未配置目标 SSID");
            return;
        }

        // 开启 Wi-Fi
        if (!wifiManager.isWifiEnabled()) {
            LogManager.i(TAG, "正在开启 Wi-Fi...");
            wifiManager.setWifiEnabled(true);
            return; // 等待 Wi-Fi 开启广播
        }

        // 检查是否已经连接到目标 Wi-Fi
        WifiInfo currentWifi = wifiManager.getConnectionInfo();
        if (currentWifi != null) {
            String currentSsid = currentWifi.getSSID().replace("\"", "");
            if (isTargetWifi(currentSsid)) {
                LogManager.i(TAG, "已连接到目标 Wi-Fi: " + currentSsid);
                onWifiConnected(currentWifi);
                return;
            }
        }

        sendBroadcast(new Intent(ACTION_WIFI_CONNECTING)
                .putExtra(EXTRA_SSID, config.getTargetSsid()));

        // 先在已保存的网络中找
        if (connectToSavedNetwork()) return;

        // 开始扫描
        LogManager.i(TAG, "扫描 Wi-Fi 网络...");
        wifiManager.startScan();
    }

    /**
     * 连接已保存的 Wi-Fi 配置
     */
    private boolean connectToSavedNetwork() {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks == null) return false;

        for (WifiConfiguration network : configuredNetworks) {
            String savedSsid = network.SSID != null ? network.SSID.replace("\"", "") : "";
            if (isTargetWifi(savedSsid)) {
                LogManager.i(TAG, "使用已保存配置连接: " + savedSsid);
                wifiManager.disconnect();
                wifiManager.enableNetwork(network.networkId, true);
                wifiManager.reconnect();
                isConnecting = true;

                // 超时处理
                handler.postDelayed(() -> {
                    if (isConnecting && !isConnected) {
                        isConnecting = false;
                        LogManager.w(TAG, "Wi-Fi 连接超时");
                        scheduleRetry();
                    }
                }, config.getConnectTimeout() * 1000L);

                return true;
            }
        }
        return false;
    }

    /**
     * 处理扫描结果
     */
    private void handleScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null) return;

        for (ScanResult result : results) {
            if (isTargetWifi(result.SSID)) {
                LogManager.i(TAG, "扫描到目标热点: " + result.SSID + " 信号: " + result.level + "dBm");
                connectToNewNetwork(result.SSID, config.getPassword());
                return;
            }
        }

        LogManager.w(TAG, "未扫描到目标 Wi-Fi: " + config.getTargetSsid());
        scheduleRetry();
    }

    /**
     * 连接新的 Wi-Fi 网络
     */
    private void connectToNewNetwork(String ssid, String password) {
        WifiConfiguration wifiConf = new WifiConfiguration();
        wifiConf.SSID = "\"" + ssid + "\"";

        if (password == null || password.isEmpty()) {
            // 开放网络
            wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            // WPA/WPA2
            wifiConf.preSharedKey = "\"" + password + "\"";
            wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        }

        int networkId = wifiManager.addNetwork(wifiConf);
        if (networkId == -1) {
            // 尝试更新现有配置
            List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
            if (existingConfigs != null) {
                for (WifiConfiguration conf : existingConfigs) {
                    if (("\"" + ssid + "\"").equals(conf.SSID)) {
                        networkId = conf.networkId;
                        break;
                    }
                }
            }
        }

        if (networkId != -1) {
            isConnecting = true;
            wifiManager.disconnect();
            final int finalNetworkId = networkId;
            handler.postDelayed(() -> {
                wifiManager.enableNetwork(finalNetworkId, true);
                wifiManager.reconnect();
                LogManager.i(TAG, "正在连接 Wi-Fi: " + ssid);

                handler.postDelayed(() -> {
                    if (isConnecting && !isConnected) {
                        isConnecting = false;
                        LogManager.w(TAG, "Wi-Fi 连接超时");
                        scheduleRetry();
                    }
                }, config.getConnectTimeout() * 1000L);
            }, 1000);
        } else {
            LogManager.e(TAG, "无法添加 Wi-Fi 配置");
            scheduleRetry();
        }
    }

    /**
     * Wi-Fi 连接成功后处理
     */
    private void onWifiConnected(WifiInfo wifiInfo) {
        isConnected = true;
        isConnecting = false;
        currentRetryCount = 0;
        handler.removeCallbacksAndMessages(null);

        String ssid = wifiInfo.getSSID().replace("\"", "");
        String ipAddress = intToIp(wifiInfo.getIpAddress());
        int signal = wifiInfo.getRssi();
        String mac = wifiInfo.getMacAddress();
        int linkSpeed = wifiInfo.getLinkSpeed();

        LogManager.i(TAG, "Wi-Fi 连接成功!");
        LogManager.i(TAG, "  SSID: " + ssid);
        LogManager.i(TAG, "  IP地址: " + ipAddress);
        LogManager.i(TAG, "  信号强度: " + signal + " dBm");
        LogManager.i(TAG, "  MAC地址: " + mac);
        LogManager.i(TAG, "  链接速率: " + linkSpeed + " Mbps");

        updateNotification("已连接 Wi-Fi: " + ssid);

        Intent intent = new Intent(ACTION_WIFI_CONNECTED);
        intent.putExtra(EXTRA_SSID, ssid);
        intent.putExtra(EXTRA_IP, ipAddress);
        intent.putExtra(EXTRA_SIGNAL, signal);
        intent.putExtra(EXTRA_MAC, mac);
        intent.putExtra("link_speed", linkSpeed);
        sendBroadcast(intent);

        // 通知绑定应用管理器（需蓝牙也已连接才会启动绑定应用）
        AppLaunchManager.onWifiConnected(this);
    }

    /**
     * 判断是否是目标 Wi-Fi
     */
    private boolean isTargetWifi(String ssid) {
        if (ssid == null || ssid.isEmpty()) return false;
        String target = config.getTargetSsid();
        if (target == null || target.isEmpty()) return false;
        return ssid.equalsIgnoreCase(target) || ssid.toLowerCase().contains(target.toLowerCase());
    }

    /**
     * 安排重试；超过最大次数则重启 Wi-Fi 模块后重新尝试
     */
    private void scheduleRetry() {
        isConnecting = false;
        if (currentRetryCount >= config.getMaxRetryCount()) {
            LogManager.w(TAG, "Wi-Fi 已达最大重试次数 " + config.getMaxRetryCount() + "，重启 Wi-Fi 模块后再试");
            sendBroadcast(new Intent(ACTION_WIFI_FAILED)
                    .putExtra(EXTRA_MESSAGE, "已达最大重试次数，正在重启 Wi-Fi..."));
            updateNotification("重试失败，重启 Wi-Fi 模块...");
            restartWifiAndRetry();
            return;
        }
        currentRetryCount++;
        long delay = 8000;
        LogManager.i(TAG, "Wi-Fi 将在 " + (delay / 1000) + " 秒后重试 (" + currentRetryCount + "/" + config.getMaxRetryCount() + ")");
        updateNotification("Wi-Fi 连接重试中 " + currentRetryCount + "/" + config.getMaxRetryCount());
        handler.postDelayed(this::startWifiConnect, delay);
    }

    /**
     * 重启 Wi-Fi：先关闭，等 3 秒后重新开启，Wi-Fi 开启广播会自动触发重连
     */
    @SuppressWarnings("deprecation")
    private void restartWifiAndRetry() {
        handler.removeCallbacksAndMessages(null);
        currentRetryCount = 0;
        isConnecting = false;
        isConnected = false;

        LogManager.i(TAG, "正在关闭 Wi-Fi...");
        wifiManager.setWifiEnabled(false);

        // 等待 3 秒后重新开启
        handler.postDelayed(() -> {
            LogManager.i(TAG, "正在开启 Wi-Fi...");
            wifiManager.setWifiEnabled(true);
            // setWifiEnabled(true) 后 WIFI_STATE_CHANGED_ACTION 广播会触发 startWifiConnect()
            // 但保险起见，5 秒后若仍未触发，则手动尝试
            handler.postDelayed(() -> {
                if (!isConnecting && !isConnected) {
                    LogManager.i(TAG, "Wi-Fi 重启后手动触发连接");
                    startWifiConnect();
                }
            }, 5000);
        }, 3000);
    }

    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiStateReceiver, filter);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Wi-Fi 连接服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CarConnect Wi-Fi")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
