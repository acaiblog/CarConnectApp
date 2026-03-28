package com.carconnect.app.model;

/**
 * 蓝牙配置模型
 */
public class BluetoothConfig {
    private String targetDeviceName;     // 目标设备名称
    private String targetDeviceAddress;  // 目标设备MAC地址
    private int maxRetryCount;           // 最大重试次数
    private int connectTimeout;          // 连接超时(秒)
    private int retryInterval;           // 重试间隔(秒)
    private boolean autoConnect;         // 是否自动连接
    private boolean enabled;             // 是否启用

    public BluetoothConfig() {
        maxRetryCount = 5;
        connectTimeout = 30;
        retryInterval = 5;
        autoConnect = true;
        enabled = true;
    }

    public String getTargetDeviceName() { return targetDeviceName; }
    public void setTargetDeviceName(String targetDeviceName) { this.targetDeviceName = targetDeviceName; }

    public String getTargetDeviceAddress() { return targetDeviceAddress; }
    public void setTargetDeviceAddress(String targetDeviceAddress) { this.targetDeviceAddress = targetDeviceAddress; }

    public int getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getRetryInterval() { return retryInterval; }
    public void setRetryInterval(int retryInterval) { this.retryInterval = retryInterval; }

    public boolean isAutoConnect() { return autoConnect; }
    public void setAutoConnect(boolean autoConnect) { this.autoConnect = autoConnect; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
