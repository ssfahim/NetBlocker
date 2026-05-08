package com.netblocker.models;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean blocked;
    private boolean systemApp;
    private int uid;
    private long dataUsage; // bytes

    public AppInfo(String appName, String packageName, Drawable icon,
                   boolean blocked, boolean systemApp, int uid) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.blocked = blocked;
        this.systemApp = systemApp;
        this.uid = uid;
        this.dataUsage = 0;
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public Drawable getIcon() { return icon; }
    public void setIcon(Drawable icon) { this.icon = icon; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public boolean isSystemApp() { return systemApp; }
    public void setSystemApp(boolean systemApp) { this.systemApp = systemApp; }

    public int getUid() { return uid; }
    public void setUid(int uid) { this.uid = uid; }

    public long getDataUsage() { return dataUsage; }
    public void setDataUsage(long dataUsage) { this.dataUsage = dataUsage; }
}
