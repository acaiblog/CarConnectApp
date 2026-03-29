package com.carconnect.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 绑定应用启动管理器
 * - 蓝牙 + Wi-Fi 同时连接成功后调用 tryLaunchBoundApp()
 * - 开机自启时也会被调用（仅启动 CarConnect 主界面）
 */
public class AppLaunchManager {

    private static final String TAG = "AppLaunchManager";

    private static volatile boolean bluetoothConnected = false;
    private static volatile boolean wifiConnected = false;
    private static volatile boolean appLaunched = false;

    /** 蓝牙连接成功通知 */
    public static synchronized void onBluetoothConnected(Context context) {
        bluetoothConnected = true;
        LogManager.i(TAG, "蓝牙已连接，appLaunched=" + appLaunched);
        tryLaunchBoundApp(context);
    }

    /** Wi-Fi 连接成功通知 */
    public static synchronized void onWifiConnected(Context context) {
        wifiConnected = true;
        LogManager.i(TAG, "Wi-Fi 已连接，appLaunched=" + appLaunched);
        tryLaunchBoundApp(context);
    }

    /** 蓝牙断开：重置状态，下次需要再次同时连接才启动 */
    public static synchronized void onBluetoothDisconnected() {
        bluetoothConnected = false;
        appLaunched = false;
        LogManager.i(TAG, "蓝牙断开，重置启动状态");
    }

    /** Wi-Fi 断开 */
    public static synchronized void onWifiDisconnected() {
        wifiConnected = false;
        appLaunched = false;
        LogManager.i(TAG, "Wi-Fi 断开，重置启动状态");
    }

    /**
     * 尝试启动绑定应用：
     * - 普通模式：需蓝牙 + Wi-Fi 同时已连接
     * - 热点模式：仅需蓝牙已连接（Wi-Fi 侧视为已通过热点建立连接）
     */
    private static void tryLaunchBoundApp(Context context) {
        boolean hotspotMode = SharedPrefsManager.loadWifiConfig().isHotspotMode();
        if (!bluetoothConnected) return;
        if (!hotspotMode && !wifiConnected) return;
        if (appLaunched) return;
        if (!SharedPrefsManager.isAutoLaunchApp()) return;
        if (!SharedPrefsManager.hasBoundApp()) {
            LogManager.i(TAG, "蓝牙+连接均已就绪，但未绑定任何应用");
            return;
        }

        String pkg = SharedPrefsManager.getBoundAppPackage();
        String name = SharedPrefsManager.getBoundAppName();
        LogManager.i(TAG, "连接完成，启动绑定应用: " + name + " (" + pkg + ")");

        try {
            PackageManager pm = context.getApplicationContext().getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.getApplicationContext().startActivity(launchIntent);
                appLaunched = true;
                LogManager.i(TAG, "成功启动绑定应用: " + pkg);
            } else {
                LogManager.w(TAG, "无法获取启动 Intent，包名: " + pkg);
            }
        } catch (Exception e) {
            LogManager.e(TAG, "启动绑定应用失败: " + e.getMessage());
        }
    }

    /**
     * 启动 CarConnect 主界面（开机自启时调用）
     */
    public static void launchMainActivity(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
                LogManager.i(TAG, "已启动 CarConnect 主界面");
            }
        } catch (Exception e) {
            LogManager.e(TAG, "启动主界面失败: " + e.getMessage());
        }
    }
}
