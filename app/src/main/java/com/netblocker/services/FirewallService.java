package com.netblocker.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.netblocker.MainActivity;
import com.netblocker.NetBlockerApp;
import com.netblocker.R;
import com.netblocker.utils.BlocklistManager;
import com.netblocker.utils.FirewallEngine;

/**
 * Foreground service that maintains the firewall state.
 * Keeps the firewall rules active and monitors for changes.
 */
public class FirewallService extends Service {

    private static final String TAG = "FirewallService";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.netblocker.START";
    public static final String ACTION_STOP = "com.netblocker.STOP";
    public static final String ACTION_REFRESH = "com.netblocker.REFRESH";

    private FirewallEngine engine;
    private BlocklistManager blocklistManager;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new FirewallEngine(this);
        blocklistManager = new BlocklistManager(this);
        Log.i(TAG, "FirewallService created. Strategy: " + engine.getActiveStrategy());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        Log.i(TAG, "onStartCommand: action=" + action + " flags=" + flags + " startId=" + startId);

        try {
            switch (action != null ? action : ACTION_START) {
                case ACTION_STOP:
                    stopFirewall();
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;

                case ACTION_REFRESH:
                    refreshRules();
                    updateNotification();
                    return START_STICKY;

                case ACTION_START:
                default:
                    startFirewall();
                    return START_STICKY;
            }
        } catch (Exception e) {
            Log.e(TAG, "onStartCommand: EXCEPTION handling action=" + action, e);
            return START_NOT_STICKY;
        }
    }

    private void startFirewall() {
        Log.i(TAG, "startFirewall: beginning...");
        blocklistManager.setFirewallEnabled(true);
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            Log.d(TAG, "startFirewall: foreground notification posted");
        } catch (Exception e) {
            Log.e(TAG, "startFirewall: FAILED to start foreground", e);
        }
        boolean result = engine.applyRules();
        Log.i(TAG, "startFirewall: applyRules result=" + result +
                ", blockedApps=" + blocklistManager.getBlockedAppCount());
    }

    private void stopFirewall() {
        Log.i(TAG, "stopFirewall: clearing rules...");
        blocklistManager.setFirewallEnabled(false);
        boolean result = engine.clearRules();
        Log.i(TAG, "stopFirewall: clearRules result=" + result);
    }

    private void refreshRules() {
        boolean result = engine.applyRules();
        Log.i(TAG, "refreshRules: applyRules result=" + result);
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop action
        Intent stopIntent = new Intent(this, FirewallService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int blockedCount = blocklistManager.getBlockedAppCount();
        String strategy = engine.isRootAvailable() ? "Root" : "Standard";

        return new NotificationCompat.Builder(this, NetBlockerApp.CHANNEL_FIREWALL)
                .setContentTitle("NetBlocker Active")
                .setContentText(blockedCount + " apps blocked • " + strategy + " mode")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        try {
            Notification notification = buildNotification();
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update notification", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "FirewallService destroyed");
    }
}
