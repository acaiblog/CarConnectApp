package com.carconnect.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.carconnect.app.model.BluetoothConfig;
import com.carconnect.app.model.WifiConfig;

public class SharedPrefsManager {

    private static final String PREFS_NAME = "CarConnectPrefs";

    // Bluetooth keys
    private static final String KEY_BT_DEVICE_NAME = "bt_device_name";
    private static final String KEY_BT_DEVICE_ADDRESS = "bt_device_address";
    private static final String KEY_BT_MAX_RETRY = "bt_max_retry";
    private static final String KEY_BT_TIMEOUT = "bt_timeout";
    private static final String KEY_BT_RETRY_INTERVAL = "bt_retry_interval";
    private static final String KEY_BT_AUTO_CONNECT = "bt_auto_connect";
    private static final String KEY_BT_ENABLED = "bt_enabled";
    // Theme key
    private static final String KEY_THEME_WHITE = "theme_white";

    // WiFi keys
    private static final String KEY_WIFI_SSID = "wifi_ssid";
    private static final String KEY_WIFI_PASSWORD = "wifi_password";
    private static final String KEY_WIFI_MAX_RETRY = "wifi_max_retry";
    private static final String KEY_WIFI_TIMEOUT = "wifi_timeout";
    private static final String KEY_WIFI_AUTO_CONNECT = "wifi_auto_connect";
    private static final String KEY_WIFI_ENABLED = "wifi_enabled";
    private static final String KEY_WIFI_HOTSPOT_MODE = "wifi_hotspot_mode";

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // -------- Bluetooth --------

    public static void saveBluetoothConfig(BluetoothConfig config) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_BT_DEVICE_NAME, config.getTargetDeviceName());
        editor.putString(KEY_BT_DEVICE_ADDRESS, config.getTargetDeviceAddress());
        editor.putInt(KEY_BT_MAX_RETRY, config.getMaxRetryCount());
        editor.putInt(KEY_BT_TIMEOUT, config.getConnectTimeout());
        editor.putInt(KEY_BT_RETRY_INTERVAL, config.getRetryInterval());
        editor.putBoolean(KEY_BT_AUTO_CONNECT, config.isAutoConnect());
        editor.putBoolean(KEY_BT_ENABLED, config.isEnabled());
        editor.apply();
    }

    public static BluetoothConfig loadBluetoothConfig() {
        BluetoothConfig config = new BluetoothConfig();
        config.setTargetDeviceName(prefs.getString(KEY_BT_DEVICE_NAME, ""));
        config.setTargetDeviceAddress(prefs.getString(KEY_BT_DEVICE_ADDRESS, ""));
        config.setMaxRetryCount(prefs.getInt(KEY_BT_MAX_RETRY, 5));
        config.setConnectTimeout(prefs.getInt(KEY_BT_TIMEOUT, 30));
        config.setRetryInterval(prefs.getInt(KEY_BT_RETRY_INTERVAL, 5));
        config.setAutoConnect(prefs.getBoolean(KEY_BT_AUTO_CONNECT, true));
        config.setEnabled(prefs.getBoolean(KEY_BT_ENABLED, true));
        return config;
    }

    public static boolean hasBluetoothTarget() {
        String name = prefs.getString(KEY_BT_DEVICE_NAME, "");
        String address = prefs.getString(KEY_BT_DEVICE_ADDRESS, "");
        return !TextUtils.isEmpty(name) || !TextUtils.isEmpty(address);
    }

    // -------- WiFi --------

    public static void saveWifiConfig(WifiConfig config) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_WIFI_SSID, config.getTargetSsid());
        editor.putString(KEY_WIFI_PASSWORD, config.getPassword());
        editor.putInt(KEY_WIFI_MAX_RETRY, config.getMaxRetryCount());
        editor.putInt(KEY_WIFI_TIMEOUT, config.getConnectTimeout());
        editor.putBoolean(KEY_WIFI_AUTO_CONNECT, config.isAutoConnect());
        editor.putBoolean(KEY_WIFI_ENABLED, config.isEnabled());
        editor.putBoolean(KEY_WIFI_HOTSPOT_MODE, config.isHotspotMode());
        editor.apply();
    }

    public static WifiConfig loadWifiConfig() {
        WifiConfig config = new WifiConfig();
        config.setTargetSsid(prefs.getString(KEY_WIFI_SSID, ""));
        config.setPassword(prefs.getString(KEY_WIFI_PASSWORD, ""));
        config.setMaxRetryCount(prefs.getInt(KEY_WIFI_MAX_RETRY, 5));
        config.setConnectTimeout(prefs.getInt(KEY_WIFI_TIMEOUT, 30));
        config.setAutoConnect(prefs.getBoolean(KEY_WIFI_AUTO_CONNECT, true));
        config.setEnabled(prefs.getBoolean(KEY_WIFI_ENABLED, true));
        config.setHotspotMode(prefs.getBoolean(KEY_WIFI_HOTSPOT_MODE, false));
        return config;
    }

    public static boolean hasWifiTarget() {
        String ssid = prefs.getString(KEY_WIFI_SSID, "");
        return !TextUtils.isEmpty(ssid);
    }

    // -------- Theme --------
    public static void setWhiteTheme(boolean white) {
        prefs.edit().putBoolean(KEY_THEME_WHITE, white).apply();
    }

    public static boolean isWhiteTheme() {
        return prefs.getBoolean(KEY_THEME_WHITE, false);
    }

    // -------- Bound App --------
    private static final String KEY_BOUND_APP_PACKAGE = "bound_app_package";
    private static final String KEY_BOUND_APP_NAME    = "bound_app_name";
    private static final String KEY_AUTO_LAUNCH_APP   = "auto_launch_app";
    private static final String KEY_LAUNCH_ON_BOOT    = "launch_on_boot";

    public static void saveBoundApp(String packageName, String appName) {
        prefs.edit()
                .putString(KEY_BOUND_APP_PACKAGE, packageName)
                .putString(KEY_BOUND_APP_NAME, appName)
                .apply();
    }

    public static String getBoundAppPackage() {
        return prefs.getString(KEY_BOUND_APP_PACKAGE, "");
    }

    public static String getBoundAppName() {
        return prefs.getString(KEY_BOUND_APP_NAME, "");
    }

    public static boolean hasBoundApp() {
        String pkg = prefs.getString(KEY_BOUND_APP_PACKAGE, "");
        return pkg != null && !pkg.isEmpty();
    }

    public static void clearBoundApp() {
        prefs.edit()
                .remove(KEY_BOUND_APP_PACKAGE)
                .remove(KEY_BOUND_APP_NAME)
                .apply();
    }

    /** 蓝牙+Wi-Fi连接后是否自动启动绑定应用（默认开启） */
    public static void setAutoLaunchApp(boolean enable) {
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH_APP, enable).apply();
    }

    public static boolean isAutoLaunchApp() {
        return prefs.getBoolean(KEY_AUTO_LAUNCH_APP, true);
    }

    /** 开机后是否自动打开软件主界面（默认开启） */
    public static void setLaunchOnBoot(boolean enable) {
        prefs.edit().putBoolean(KEY_LAUNCH_ON_BOOT, enable).apply();
    }

    public static boolean isLaunchOnBoot() {
        return prefs.getBoolean(KEY_LAUNCH_ON_BOOT, true);
    }
}
