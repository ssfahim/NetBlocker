package com.netblocker.utils;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.RemoteException;

import java.util.Calendar;

/**
 * Utility class for network-related operations.
 */
public class NetworkUtils {

    /**
     * Checks if the device currently has internet connectivity.
     */
    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                     caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                     caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /**
     * Checks if the current connection is Wi-Fi.
     */
    public static boolean isOnWifi(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }

    /**
     * Checks if the current connection is mobile data.
     */
    public static boolean isOnMobileData(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        }
        return false;
    }

    /**
     * Returns data usage for a specific UID in the last 30 days.
     * Requires PACKAGE_USAGE_STATS permission.
     */
    public static long getDataUsageForUid(Context context, int uid) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0;

        NetworkStatsManager statsManager = (NetworkStatsManager)
                context.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (statsManager == null) return 0;

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        long startTime = cal.getTimeInMillis();

        long totalBytes = 0;

        try {
            // Wi-Fi usage
            NetworkStats.Bucket bucket = statsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_WIFI, null, startTime, endTime, uid);
            if (bucket != null) {
                totalBytes += bucket.getRxBytes() + bucket.getTxBytes();
            }
        } catch (RemoteException | SecurityException ignored) {}

        try {
            // Mobile data usage
            NetworkStats.Bucket bucket = statsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_MOBILE, null, startTime, endTime, uid);
            if (bucket != null) {
                totalBytes += bucket.getRxBytes() + bucket.getTxBytes();
            }
        } catch (RemoteException | SecurityException ignored) {}

        return totalBytes;
    }

    /**
     * Format bytes into a human-readable string.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
