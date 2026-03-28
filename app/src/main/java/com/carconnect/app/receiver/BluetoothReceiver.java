package com.carconnect.app.receiver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.carconnect.app.service.BluetoothService;
import com.carconnect.app.utils.LogManager;

/**
 * 系统蓝牙广播接收器 - 监听蓝牙状态变化（可选，Service内部也有处理）
 */
public class BluetoothReceiver extends BroadcastReceiver {

    private static final String TAG = "BluetoothReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    LogManager.i(TAG, "蓝牙已开启，启动蓝牙服务");
                    // 蓝牙开启后确保服务运行
                    Intent serviceIntent = new Intent(context, BluetoothService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    LogManager.w(TAG, "蓝牙已关闭");
                }
                break;
            case BluetoothDevice.ACTION_FOUND:
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    LogManager.d(TAG, "[广播] 发现设备: " + device.getName() + " " + device.getAddress());
                }
                break;
        }
    }
}
