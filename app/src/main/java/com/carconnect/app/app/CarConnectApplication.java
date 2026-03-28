package com.carconnect.app.app;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.multidex.MultiDex;

import com.carconnect.app.service.CarConnectService;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

public class CarConnectApplication extends Application {

    private static CarConnectApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        MultiDex.install(this);

        // 初始化日志管理器
        LogManager.init(this);

        // 初始化配置管理器
        SharedPrefsManager.init(this);

        LogManager.i("APP", "CarConnect 应用启动");

        // 启动后台服务
        startCarConnectService();
    }

    private void startCarConnectService() {
        Intent serviceIntent = new Intent(this, CarConnectService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public static CarConnectApplication getInstance() {
        return instance;
    }
}
