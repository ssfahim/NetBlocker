package com.netblocker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the persistent blocklist of apps whose network access should be restricted.
 * Uses SharedPreferences for storage.
 */
public class BlocklistManager {

    private static final String TAG = "BlocklistManager";
    private static final String PREFS_NAME = "netblocker_prefs";
    private static final String KEY_BLOCKED_APPS = "blocked_apps";
    private static final String KEY_FIREWALL_ENABLED = "firewall_enabled";
    private static final String KEY_BLOCK_WIFI = "block_wifi";
    private static final String KEY_BLOCK_MOBILE = "block_mobile";
    private static final String KEY_SHOW_SYSTEM_APPS = "show_system_apps";
    private static final String KEY_NOTIFY_ON_BLOCK = "notify_on_block";

    private final SharedPreferences prefs;
    private final Gson gson;

    public BlocklistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // ── Blocked apps management ─────────────────────────────────────────

    public Set<String> getBlockedApps() {
        String json = prefs.getString(KEY_BLOCKED_APPS, null);
        if (json == null) return new HashSet<>();
        Type type = new TypeToken<HashSet<String>>() {}.getType();
        try {
            Set<String> result = gson.fromJson(json, type);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public void setBlockedApps(Set<String> blockedApps) {
        prefs.edit()
                .putString(KEY_BLOCKED_APPS, gson.toJson(blockedApps))
                .apply();
    }

    public boolean isAppBlocked(String packageName) {
        return getBlockedApps().contains(packageName);
    }

    public void blockApp(String packageName) {
        Set<String> blocked = getBlockedApps();
        blocked.add(packageName);
        setBlockedApps(blocked);
        Log.i(TAG, "blockApp: " + packageName + " (total: " + blocked.size() + ")");
    }

    public void unblockApp(String packageName) {
        Set<String> blocked = getBlockedApps();
        blocked.remove(packageName);
        setBlockedApps(blocked);
        Log.i(TAG, "unblockApp: " + packageName + " (total: " + blocked.size() + ")");
    }

    public void toggleApp(String packageName) {
        if (isAppBlocked(packageName)) {
            unblockApp(packageName);
        } else {
            blockApp(packageName);
        }
    }

    // ── Firewall state ──────────────────────────────────────────────────

    public boolean isFirewallEnabled() {
        return prefs.getBoolean(KEY_FIREWALL_ENABLED, false);
    }

    public void setFirewallEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FIREWALL_ENABLED, enabled).apply();
        Log.i(TAG, "setFirewallEnabled: " + enabled);
    }

    // ── Settings ────────────────────────────────────────────────────────

    public boolean shouldBlockWifi() {
        return prefs.getBoolean(KEY_BLOCK_WIFI, true);
    }

    public void setBlockWifi(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_WIFI, block).apply();
    }

    public boolean shouldBlockMobile() {
        return prefs.getBoolean(KEY_BLOCK_MOBILE, true);
    }

    public void setBlockMobile(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_MOBILE, block).apply();
    }

    public boolean shouldShowSystemApps() {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false);
    }

    public void setShowSystemApps(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply();
    }

    public boolean shouldNotifyOnBlock() {
        return prefs.getBoolean(KEY_NOTIFY_ON_BLOCK, true);
    }

    public void setNotifyOnBlock(boolean notify) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_BLOCK, notify).apply();
    }

    public int getBlockedAppCount() {
        return getBlockedApps().size();
    }
}
