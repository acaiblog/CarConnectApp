package com.carconnect.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.carconnect.app.service.CarConnectService;
import com.carconnect.app.utils.AppLaunchManager;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

/**
 * 开机广播接收器
 * - 启动 CarConnect 后台服务（蓝牙/Wi-Fi自动连接）
 * - 若设置了"开机自启界面"，则延迟 2 秒后打开主界面
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        LogManager.i("BootReceiver", "收到开机广播，启动 CarConnect");

        // 延迟 2 秒等桌面稳定后，先打开主界面，主界面内会自动启动后台服务
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (SharedPrefsManager.isLaunchOnBoot()) {
                LogManager.i("BootReceiver", "开机自启：打开 CarConnect 主界面");
                AppLaunchManager.launchMainActivity(context);
            } else {
                // 未开启主界面自启，仅启动后台服务
                LogManager.i("BootReceiver", "开机自启：仅启动后台服务");
                Intent serviceIntent = new Intent(context, CarConnectService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }, 2000);
    }
}
