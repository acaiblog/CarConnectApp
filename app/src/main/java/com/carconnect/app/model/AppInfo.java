package com.carconnect.app.model;

/**
 * 已安装应用信息模型
 */
public class AppInfo {
    private String appName;
    private String packageName;
    private android.graphics.drawable.Drawable icon;
    private boolean isSystemApp;

    public AppInfo(String appName, String packageName, android.graphics.drawable.Drawable icon, boolean isSystemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public android.graphics.drawable.Drawable getIcon() { return icon; }
    public boolean isSystemApp() { return isSystemApp; }
}
