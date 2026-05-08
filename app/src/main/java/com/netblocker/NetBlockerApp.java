package com.netblocker;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

import com.netblocker.utils.CrashLogger;

public class NetBlockerApp extends Application {

    private static final String TAG = "NetBlockerApp";
    public static final String CHANNEL_FIREWALL = "firewall_channel";
    public static final String CHANNEL_ALERTS = "alert_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== NetBlocker Application onCreate ===");
        Log.d(TAG, "Android SDK: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        Log.d(TAG, "Package: " + getPackageName());

        // Initialize CrashLogger — captures all logs + crashes to file
        // Note: on Android 10- this needs WRITE_EXTERNAL_STORAGE granted first,
        // but the logger will try regardless and work once permission is granted.
        // On Android 11+ (scoped storage), Documents dir access is free.
        try {
            CrashLogger.getInstance().init(this);
            Log.i(TAG, "CrashLogger initialized");
        } catch (Exception e) {
            Log.e(TAG, "CrashLogger init failed (will retry after permission grant)", e);
        }

        createNotificationChannels();
    }

    /**
     * Re-initialize logger after storage permission is granted at runtime.
     * Called from MainActivity once permissions are approved.
     */
    public void reinitLoggerIfNeeded() {
        if (!CrashLogger.getInstance().isInitialized()) {
            try {
                CrashLogger.getInstance().init(this);
                Log.i(TAG, "CrashLogger re-initialized after permission grant");
            } catch (Exception e) {
                Log.e(TAG, "CrashLogger re-init failed", e);
            }
        }
    }

    private void createNotificationChannels() {
        Log.d(TAG, "createNotificationChannels: SDK=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel firewallChannel = new NotificationChannel(
                        CHANNEL_FIREWALL,
                        "Firewall Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                firewallChannel.setDescription("Shows when the firewall is actively blocking apps");
                firewallChannel.setShowBadge(false);

                NotificationChannel alertChannel = new NotificationChannel(
                        CHANNEL_ALERTS,
                        "Block Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                alertChannel.setDescription("Notifications when an app's network access is blocked");

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(firewallChannel);
                    manager.createNotificationChannel(alertChannel);
                    Log.i(TAG, "Notification channels created successfully");
                } else {
                    Log.e(TAG, "NotificationManager is null — channels NOT created");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create notification channels", e);
            }
        } else {
            Log.d(TAG, "Skipping notification channels (pre-Oreo)");
        }
    }
}
