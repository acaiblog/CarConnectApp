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
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.carconnect.app.activity.MainActivity;
import com.carconnect.app.utils.AppLaunchManager;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

/**
 * Wi-Fi 热点服务
 * 功能：蓝牙连接后自动关闭 Wi-Fi，打开本机热点，让车机连接手机热点上网
 */
public class WifiService extends Service {

    private static final String TAG = "WifiService";
    private static final String CHANNEL_ID = "wifi_channel";
    private static final int NOTIFICATION_ID = 1002;

    public static final String ACTION_START_WIFI_CONNECT    = "com.carconnect.app.START_WIFI";
    public static final String ACTION_WIFI_CONNECTED        = "com.carconnect.app.WIFI_CONNECTED";
    public static final String ACTION_WIFI_DISCONNECTED     = "com.carconnect.app.WIFI_DISCONNECTED";
    public static final String ACTION_WIFI_CONNECTING       = "com.carconnect.app.WIFI_CONNECTING";
    public static final String ACTION_WIFI_FAILED           = "com.carconnect.app.WIFI_FAILED";
    public static final String ACTION_WIFI_INFO             = "com.carconnect.app.WIFI_INFO";
    public static final String ACTION_HOTSPOT_STARTED       = "com.carconnect.app.HOTSPOT_STARTED";
    public static final String ACTION_HOTSPOT_STOPPED       = "com.carconnect.app.HOTSPOT_STOPPED";
    public static final String ACTION_HOTSPOT_CLIENTS_UPDATED = "com.carconnect.app.HOTSPOT_CLIENTS";

    public static final String EXTRA_SSID          = "ssid";
    public static final String EXTRA_IP            = "ip";
    public static final String EXTRA_SIGNAL        = "signal";
    public static final String EXTRA_MAC           = "mac";
    public static final String EXTRA_MESSAGE       = "message";
    public static final String EXTRA_HOTSPOT_SSID  = "hotspot_ssid";
    public static final String EXTRA_CLIENT_COUNT  = "client_count";
    public static final String EXTRA_CLIENTS_INFO  = "clients_info";

    private WifiManager wifiManager;
    private Handler handler;

    // 热点相关状态
    private Object localOnlyHotspotReservation = null; // WifiManager.LocalOnlyHotspotReservation
    private boolean isHotspotStarted  = false;
    private boolean isHotspotStarting = false; // 防重复触发
    private Runnable clientCheckRunnable = null;

    /**
     * 监听 Wi-Fi 状态 + 系统热点状态
     * - WIFI_STATE_DISABLED：Wi-Fi 关闭后触发开热点
     * - WIFI_AP_STATE_CHANGED：监听系统热点开关状态
     */
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                LogManager.d(TAG, "Wi-Fi 状态变化: " + state);
                if (state == WifiManager.WIFI_STATE_DISABLED) {
                    if (isHotspotStarting && !isHotspotStarted) {
                        LogManager.i(TAG, "Wi-Fi 已关闭，自动触发开热点");
                        handler.postDelayed(() -> doStartHotspot(), 500);
                    }
                }
            } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                // 13 = WIFI_AP_STATE_ENABLED, 11 = WIFI_AP_STATE_DISABLED
                int apState = intent.getIntExtra("wifi_state", -1);
                LogManager.d(TAG, "热点状态变化: apState=" + apState);
                if (apState == 13 && !isHotspotStarted) {
                    String ssid = getSystemHotspotSsid();
                    LogManager.i(TAG, "系统热点已开启，SSID: " + ssid);
                    isHotspotStarted  = true;
                    isHotspotStarting = false;
                    updateNotification("热点已开启: " + ssid);
                    Intent bi = new Intent(ACTION_HOTSPOT_STARTED);
                    bi.putExtra(EXTRA_HOTSPOT_SSID, ssid);
                    sendBroadcast(bi);
                    AppLaunchManager.onWifiConnected(WifiService.this);
                    startClientCheckLoop();
                } else if (apState == 11 && isHotspotStarted) {
                    isHotspotStarted  = false;
                    isHotspotStarting = false;
                    LogManager.i(TAG, "系统热点已关闭");
                    sendBroadcast(new Intent(ACTION_HOTSPOT_STOPPED));
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        createNotificationChannel();

        IntentFilter filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        registerReceiver(wifiStateReceiver, filter);
        LogManager.i(TAG, "Wi-Fi 热点服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("热点服务运行中"));
        LogManager.i(TAG, "收到启动指令，开始热点流程");
        startLocalHotspot();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(wifiStateReceiver); } catch (Exception ignored) {}
        stopLocalHotspot();
        LogManager.i(TAG, "Wi-Fi 热点服务已停止");
    }

    // ──────────────────────────────────────────────────────────────
    // 热点流程
    // ──────────────────────────────────────────────────────────────

    /**
     * 启动热点入口：
     * Android 12+ 普通 App 不能强制关闭 Wi-Fi（setWifiEnabled 被静默忽略）
     * vivo Android 15 不允许通过 TetheringManager/LocalOnlyHotspot 开热点（系统限制）
     * 策略：
     *   若 Wi-Fi 关着 → 直接调 startTethering/LocalOnlyHotspot
     *   若 Wi-Fi 开着 → 跳到系统热点设置页，引导用户开热点，并通过 WIFI_AP_STATE_CHANGED 广播感知
     */
    @SuppressWarnings("deprecation")
    private void startLocalHotspot() {
        if (isHotspotStarted) {
            LogManager.i(TAG, "热点已在运行，跳过");
            return;
        }
        if (isHotspotStarting) {
            LogManager.i(TAG, "热点正在启动中，跳过");
            return;
        }
        isHotspotStarting = true;

        if (wifiManager.isWifiEnabled()) {
            LogManager.i(TAG, "Wi-Fi 开启中，跳转热点设置页引导用户开热点...");
            updateNotification("请开启「个人热点」，App 将自动检测 ▶");
            // 跳转到系统热点设置页
            try {
                Intent hotspotIntent = new Intent("android.settings.TETHER_SETTINGS");
                hotspotIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(hotspotIntent);
            } catch (Exception e1) {
                try {
                    Intent hotspotIntent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                    hotspotIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(hotspotIntent);
                } catch (Exception e2) {
                    LogManager.w(TAG, "无法打开热点设置: " + e2.getMessage());
                }
            }
            // isHotspotStarting 保持 true，等 WIFI_AP_STATE_CHANGED 广播感知热点开启
        } else {
            LogManager.i(TAG, "Wi-Fi 已关闭，直接启动热点");
            handler.postDelayed(() -> doStartHotspot(), 300);
        }
    }

    /**
     * 获取系统当前热点 SSID
     */
    @SuppressWarnings("deprecation")
    private String getSystemHotspotSsid() {
        try {
            java.lang.reflect.Method method = wifiManager.getClass()
                    .getMethod("getWifiApConfiguration");
            WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
            if (config != null && config.SSID != null) return config.SSID;
        } catch (Exception ignored) {}
        return "CarConnect_AP";
    }

    /**
     * 真正调用 API 开热点
     * 优先用 ConnectivityManager.startTethering()（需 TETHER_PRIVILEGED，可用 adb 授予）
     * 回退：startLocalOnlyHotspot → 反射 setWifiApEnabled
     */
    @SuppressWarnings("MissingPermission")
    private void doStartHotspot() {
        if (isHotspotStarted) return;
        LogManager.i(TAG, "正在启动本机热点...");
        updateNotification("热点模式：正在启动热点...");

        // 方案1：ConnectivityManager.startTethering 反射（需 TETHER_PRIVILEGED）
        if (startTetheringViaConnectivityManager()) {
            return;
        }

        // 方案2：startLocalOnlyHotspot → 反射 setWifiApEnabled
        startLocalOnlyHotspotFallback();
    }

    /**
     * 反射方式兼容 Android 7 及部分旧机型
     */
    @SuppressWarnings("deprecation")
    private void startHotspotViaReflection() {
        try {
            java.lang.reflect.Method method = wifiManager.getClass()
                    .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = "CarConnect_AP";
            boolean result = (boolean) method.invoke(wifiManager, config, true);
            if (result) {
                isHotspotStarted  = true;
                isHotspotStarting = false;
                LogManager.i(TAG, "反射方式热点已启动");
                updateNotification("热点已开启: CarConnect_AP");
                Intent i = new Intent(ACTION_HOTSPOT_STARTED);
                i.putExtra(EXTRA_HOTSPOT_SSID, "CarConnect_AP");
                sendBroadcast(i);
                AppLaunchManager.onWifiConnected(this);
            } else {
                isHotspotStarting = false;
                LogManager.e(TAG, "反射方式热点启动失败");
                updateNotification("热点启动失败，请手动开启热点");
            }
        } catch (Exception e) {
            isHotspotStarting = false;
            LogManager.e(TAG, "反射开热点异常: " + e.getMessage());
            updateNotification("热点启动失败: " + e.getMessage());
        }
    }

    /**
     * 通过 ConnectivityManager.startTethering() 反射开热点
     * 需要 TETHER_PRIVILEGED 权限（adb pm grant 授予）
     * @return true 表示调用成功（不代表热点开启成功）
     */
    private boolean startTetheringViaConnectivityManager() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            // Android 12+ 用 TetheringManager.startTethering（反射）
            try {
                Object tetheringManager = getSystemService("tethering");
                if (tetheringManager != null) {
                    return startTetheringViaTetheringManager(tetheringManager);
                }
            } catch (Exception e) {
                LogManager.w(TAG, "TetheringManager 不可用: " + e.getMessage());
            }

            // Android 7-11 用 ConnectivityManager.startTethering 反射
            // OnStartTetheringCallback 是抽象类，动态生成子类
            Class<?> callbackClass = Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
            Object callback = new Object() {
                public void onTetheringStarted() {
                    LogManager.i(TAG, "startTethering: 热点已启动");
                    isHotspotStarted  = true;
                    isHotspotStarting = false;
                    updateNotification("热点已开启");
                    sendBroadcast(new Intent(ACTION_HOTSPOT_STARTED));
                    AppLaunchManager.onWifiConnected(WifiService.this);
                    startClientCheckLoop();
                }
                public void onTetheringFailed() {
                    LogManager.e(TAG, "startTethering: 热点启动失败");
                    isHotspotStarting = false;
                    handler.post(() -> startLocalOnlyHotspotFallback());
                }
            };

            java.lang.reflect.Method startTethering = cm.getClass().getMethod(
                    "startTethering", int.class, boolean.class, callbackClass, android.os.Handler.class);
            startTethering.invoke(cm, 0, false, callback, handler);
            LogManager.i(TAG, "startTethering(CM) 调用成功，等待回调...");
            return true;
        } catch (Exception e) {
            LogManager.w(TAG, "startTethering(CM) 不可用: " + e.getMessage() + "，回退其他方式");
            return false;
        }
    }

    /**
     * Android 12+ TetheringManager.startTethering 反射
     */
    private boolean startTetheringViaTetheringManager(Object tetheringManager) {
        try {
            Class<?> callbackClass = Class.forName("android.net.TetheringManager$StartTetheringCallback");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class[]{callbackClass},
                    (proxy, method1, args) -> {
                        String mn = method1.getName();
                        if ("onTetheringStarted".equals(mn)) {
                            LogManager.i(TAG, "TetheringManager: 热点已启动");
                            isHotspotStarted  = true;
                            isHotspotStarting = false;
                            updateNotification("热点已开启");
                            sendBroadcast(new Intent(ACTION_HOTSPOT_STARTED));
                            AppLaunchManager.onWifiConnected(WifiService.this);
                            startClientCheckLoop();
                        } else if ("onTetheringFailed".equals(mn)) {
                            int error = (args != null && args.length > 0) ? (int) args[0] : -1;
                            LogManager.e(TAG, "TetheringManager: 热点启动失败 error=" + error);
                            isHotspotStarting = false;
                            handler.post(() -> startLocalOnlyHotspotFallback());
                        }
                        return null;
                    });

            java.util.concurrent.Executor executor = command -> handler.post(command);

            // 尝试不同 API 签名
            // 方式1: startTethering(int type, Executor, StartTetheringCallback)
            try {
                tetheringManager.getClass().getMethod("startTethering",
                        int.class,
                        java.util.concurrent.Executor.class, callbackClass)
                        .invoke(tetheringManager, 0 /*TETHERING_WIFI*/, executor, callback);
                LogManager.i(TAG, "TetheringManager.startTethering(int) 调用成功");
                return true;
            } catch (NoSuchMethodException ignored) {}

            // 方式2: startTethering(TetheringRequest, Executor, StartTetheringCallback)
            // TetheringRequest 用 int 构造
            Class<?> requestClass = Class.forName("android.net.TetheringManager$TetheringRequest");
            Object request = null;
            try {
                // 尝试 Builder(int) 构造
                Class<?> builderClass = Class.forName("android.net.TetheringManager$TetheringRequest$Builder");
                Object builder = builderClass.getConstructor(int.class).newInstance(0);
                request = builderClass.getMethod("build").invoke(builder);
            } catch (Exception e2) {
                LogManager.w(TAG, "TetheringRequest.Builder(int) 失败: " + e2.getMessage());
            }
            if (request != null) {
                tetheringManager.getClass().getMethod("startTethering",
                        requestClass, java.util.concurrent.Executor.class, callbackClass)
                        .invoke(tetheringManager, request, executor, callback);
                LogManager.i(TAG, "TetheringManager.startTethering(request) 调用成功");
                return true;
            }

            LogManager.w(TAG, "TetheringManager 所有方式均不可用");
            return false;
        } catch (Exception e) {
            LogManager.w(TAG, "TetheringManager.startTethering 异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 回退方案：startLocalOnlyHotspot
     */
    @SuppressWarnings("MissingPermission")
    private void startLocalOnlyHotspotFallback() {
        if (isHotspotStarted || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) startHotspotViaReflection();
            return;
        }
        try {
            isHotspotStarting = true;
            wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    localOnlyHotspotReservation = reservation;
                    isHotspotStarted  = true;
                    isHotspotStarting = false;
                    String ssid = reservation.getWifiConfiguration() != null
                            ? reservation.getWifiConfiguration().SSID : "";
                    LogManager.i(TAG, "LocalOnlyHotspot 已启动，SSID: " + ssid);
                    updateNotification("热点已开启: " + ssid);
                    Intent bi = new Intent(ACTION_HOTSPOT_STARTED);
                    bi.putExtra(EXTRA_HOTSPOT_SSID, ssid);
                    sendBroadcast(bi);
                    AppLaunchManager.onWifiConnected(WifiService.this);
                    startClientCheckLoop();
                }
                @Override public void onStopped() {
                    isHotspotStarted = false; isHotspotStarting = false;
                    localOnlyHotspotReservation = null;
                    sendBroadcast(new Intent(ACTION_HOTSPOT_STOPPED));
                }
                @Override public void onFailed(int reason) {
                    isHotspotStarted = false; isHotspotStarting = false;
                    LogManager.e(TAG, "LocalOnlyHotspot 失败 reason=" + reason + "，最终回退反射");
                    startHotspotViaReflection();
                }
            }, null);
        } catch (Exception e) {
            isHotspotStarting = false;
            LogManager.e(TAG, "LocalOnlyHotspot 异常: " + e.getMessage());
            startHotspotViaReflection();
        }
    }

    /**
     * 关闭热点
     */
    private void stopLocalHotspot() {
        if (clientCheckRunnable != null) {
            handler.removeCallbacks(clientCheckRunnable);
            clientCheckRunnable = null;
        }
        if (localOnlyHotspotReservation != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ((WifiManager.LocalOnlyHotspotReservation) localOnlyHotspotReservation).close();
                }
            } catch (Exception e) {
                LogManager.w(TAG, "关闭热点异常: " + e.getMessage());
            }
            localOnlyHotspotReservation = null;
        }
        isHotspotStarted  = false;
        isHotspotStarting = false;
    }

    // ──────────────────────────────────────────────────────────────
    // 客户端检测
    // ──────────────────────────────────────────────────────────────

    private void startClientCheckLoop() {
        clientCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isHotspotStarted) return;
                checkHotspotClients();
                handler.postDelayed(this, 10000);
            }
        };
        handler.postDelayed(clientCheckRunnable, 3000);
    }

    private void checkHotspotClients() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/net/arp"));
            String line;
            java.util.List<String> clients = new java.util.ArrayList<>();
            reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4) {
                    String ip  = parts[0];
                    String mac = parts[3];
                    if (!mac.equals("00:00:00:00:00:00")) {
                        clients.add(ip + " (" + mac + ")");
                    }
                }
            }
            reader.close();
            Intent intent = new Intent(ACTION_HOTSPOT_CLIENTS_UPDATED);
            intent.putExtra(EXTRA_CLIENT_COUNT, clients.size());
            intent.putExtra(EXTRA_CLIENTS_INFO, android.text.TextUtils.join("\n", clients));
            sendBroadcast(intent);
        } catch (Exception e) {
            LogManager.w(TAG, "读取ARP表失败: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 通知
    // ──────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Wi-Fi 热点服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CarConnect 热点")
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
