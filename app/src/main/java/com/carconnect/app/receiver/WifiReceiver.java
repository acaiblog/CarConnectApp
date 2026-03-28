package com.carconnect.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.carconnect.app.service.WifiService;
import com.carconnect.app.utils.LogManager;

/**
 * 系统 Wi-Fi 广播接收器（可选）
 */
public class WifiReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                LogManager.i(TAG, "Wi-Fi 已开启（系统广播）");
                // 启动/唤醒 Wi-Fi 服务
                Intent serviceIntent = new Intent(context, WifiService.class);
                serviceIntent.setAction(WifiService.ACTION_START_WIFI_CONNECT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
