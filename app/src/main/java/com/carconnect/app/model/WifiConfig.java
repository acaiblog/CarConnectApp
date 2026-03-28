package com.carconnect.app.model;

/**
 * Wi-Fi配置模型
 */
public class WifiConfig {
    private String targetSsid;     // 目标SSID
    private String password;       // Wi-Fi密码
    private int maxRetryCount;     // 最大重试次数
    private int connectTimeout;    // 连接超时(秒)
    private boolean autoConnect;   // 是否自动连接
    private boolean enabled;       // 是否启用
    private boolean hotspotMode;   // 是否热点模式（车机热点）

    public WifiConfig() {
        maxRetryCount = 5;
        connectTimeout = 30;
        autoConnect = true;
        enabled = true;
        hotspotMode = false;
    }

    public String getTargetSsid() { return targetSsid; }
    public void setTargetSsid(String targetSsid) { this.targetSsid = targetSsid; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public boolean isAutoConnect() { return autoConnect; }
    public void setAutoConnect(boolean autoConnect) { this.autoConnect = autoConnect; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isHotspotMode() { return hotspotMode; }
    public void setHotspotMode(boolean hotspotMode) { this.hotspotMode = hotspotMode; }
}
