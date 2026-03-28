package com.carconnect.app.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * vivo 手机无感连接车机工具类
 * 
 * 适配 vivo 系统特性，实现：
 * 1. 蓝牙免提（HFP）+ 音频（A2DP）连接亿联车机
 * 2. 自动连接车机热点
 * 3. 绕过部分 vivo 系统限制
 */
public class VivoCarConnectHelper {

    private static final String TAG = "VivoHelper";

    /**
     * 判断是否是 vivo 设备
     */
    public static boolean isVivoDevice() {
        return "vivo".equalsIgnoreCase(Build.MANUFACTURER)
                || Build.BRAND.toLowerCase().contains("vivo");
    }

    /**
     * vivo 设备上保持 Wi-Fi 始终活跃（防止 vivo 省电策略断开 Wi-Fi）
     * 需要 WAKE_LOCK 权限
     */
    public static void keepWifiActive(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                // 创建 WifiLock 阻止系统关闭 Wi-Fi
                WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CarConnect:WifiLock");
                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                    LogManager.i(TAG, "vivo Wi-Fi Lock 已获取，防止系统断开 Wi-Fi");
                }
            }
        } catch (Exception e) {
            LogManager.w(TAG, "获取 Wi-Fi Lock 失败: " + e.getMessage());
        }
    }

    /**
     * 在 vivo 设备上通过反射连接蓝牙 A2DP 配置文件
     */
    public static boolean connectBluetoothA2dp(Object a2dpProxy, BluetoothDevice device) {
        try {
            Method connect = a2dpProxy.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            connect.setAccessible(true);
            boolean result = (boolean) connect.invoke(a2dpProxy, device);
            LogManager.i(TAG, "vivo A2DP 连接结果: " + result + " -> " + device.getName());
            return result;
        } catch (Exception e) {
            LogManager.e(TAG, "vivo A2DP 连接异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 在 vivo 设备上通过反射连接蓝牙 HFP/HSP 配置文件（免提通话）
     */
    public static boolean connectBluetoothHeadset(Object headsetProxy, BluetoothDevice device) {
        try {
            Method connect = headsetProxy.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            connect.setAccessible(true);
            boolean result = (boolean) connect.invoke(headsetProxy, device);
            LogManager.i(TAG, "vivo HFP 连接结果: " + result + " -> " + device.getName());
            return result;
        } catch (Exception e) {
            LogManager.e(TAG, "vivo HFP 连接异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 强制设置蓝牙设备优先级（提高自动连接成功率）
     * vivo FUNTOUCH OS 上尤其需要
     */
    public static void setPriorityHighest(Object profileProxy, BluetoothDevice device) {
        try {
            // BluetoothProfile.PRIORITY_AUTO_CONNECT = 1000 (API 30 deprecated, use CONNECTION_POLICY)
            Method setPriority = profileProxy.getClass()
                    .getDeclaredMethod("setPriority", BluetoothDevice.class, int.class);
            setPriority.setAccessible(true);
            setPriority.invoke(profileProxy, device, 1000); // PRIORITY_AUTO_CONNECT
            LogManager.i(TAG, "设备优先级已设置为 AUTO_CONNECT: " + device.getName());
        } catch (Exception e) {
            // Android 11+ 使用 setConnectionPolicy
            try {
                Method setPolicy = profileProxy.getClass()
                        .getDeclaredMethod("setConnectionPolicy", BluetoothDevice.class, int.class);
                setPolicy.setAccessible(true);
                setPolicy.invoke(profileProxy, device, 100); // CONNECTION_POLICY_ALLOWED
                LogManager.i(TAG, "设备 Connection Policy 已设置: " + device.getName());
            } catch (Exception ex) {
                LogManager.w(TAG, "设置优先级失败: " + ex.getMessage());
            }
        }
    }

    /**
     * vivo 专用: 检查是否已开启"自动连接蓝牙"系统设置
     * 提示用户关闭 vivo 电池优化
     */
    public static void checkVivoSettings(Context context) {
        if (!isVivoDevice()) return;
        try {
            // 检查是否有 vivo 特定设置 - 蓝牙后台运行权限
            LogManager.i(TAG, "当前设备是 vivo，建议：");
            LogManager.i(TAG, "  1. 在【设置 → 电池 → 后台高耗电】中允许 CarConnect");
            LogManager.i(TAG, "  2. 在【设置 → 更多设置 → 应用权限】中开启自启动");
            LogManager.i(TAG, "  3. 确保【蓝牙自动连接】开关已开启");
        } catch (Exception e) {
            LogManager.w(TAG, "检查 vivo 设置失败: " + e.getMessage());
        }
    }

    /**
     * 亿联车机蓝牙特性说明
     * 亿联(YEALINK) UC 系列车机蓝牙设备名通常包含 "YEALINK" 或 "YEA"
     * 亿联车机热点 SSID 通常为 "YEALINK_XXXXXX" 格式
     */
    public static boolean isYealinkDevice(BluetoothDevice device) {
        if (device == null) return false;
        String name = device.getName();
        if (name == null) return false;
        return name.toUpperCase().contains("YEALINK")
                || name.toUpperCase().contains("YEA-")
                || name.toUpperCase().startsWith("YEA");
    }

    /**
     * 生成亿联车机热点 SSID 候选列表
     * 根据蓝牙设备名推断热点名称
     */
    public static String[] getYealinkHotspotCandidates(String bluetoothName) {
        if (bluetoothName == null) return new String[]{};
        return new String[]{
                bluetoothName + "_HOTSPOT",
                bluetoothName + "_WiFi",
                bluetoothName,
                bluetoothName.replace(" ", "_"),
                "YEALINK_HOTSPOT",
                "Yealink",
                "YeaLink"
        };
    }
}
