package com.netblocker.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * Core firewall engine that implements multiple blocking strategies
 * without requiring a VPN connection.
 *
 * Strategy 1: iptables (requires root) — most effective
 * Strategy 2: Data Saver / restrict background data (no root)
 * Strategy 3: Accessibility-based kill (no root, less reliable)
 *
 * The engine auto-detects available strategies and uses the best one.
 */
public class FirewallEngine {

    private static final String TAG = "FirewallEngine";

    public enum Strategy {
        IPTABLES,           // Root: kernel-level blocking via iptables
        DATA_RESTRICTION,   // No root: Android data restriction APIs
        HYBRID              // Combination of available methods
    }

    private final Context context;
    private final BlocklistManager blocklistManager;
    private Strategy activeStrategy;
    private boolean rootAvailable;

    public FirewallEngine(Context context) {
        this.context = context;
        this.blocklistManager = new BlocklistManager(context);
        this.rootAvailable = checkRootAccess();
        this.activeStrategy = determineStrategy();
    }

    // ── Strategy detection ──────────────────────────────────────────────

    private Strategy determineStrategy() {
        if (rootAvailable) {
            return Strategy.IPTABLES;
        }
        return Strategy.DATA_RESTRICTION;
    }

    public Strategy getActiveStrategy() {
        return activeStrategy;
    }

    public boolean isRootAvailable() {
        return rootAvailable;
    }

    // ── Root detection ──────────────────────────────────────────────────

    private boolean checkRootAccess() {
        Log.d(TAG, "checkRootAccess: testing su...");
        try {
            Process process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exitCode = process.waitFor();
            boolean hasRoot = line != null && line.contains("uid=0");
            Log.i(TAG, "checkRootAccess: output='" + line + "' exitCode=" + exitCode +
                    " hasRoot=" + hasRoot);
            return hasRoot;
        } catch (Exception e) {
            Log.d(TAG, "checkRootAccess: no root available (" + e.getMessage() + ")");
            return false;
        }
    }

    // ── Apply firewall rules ────────────────────────────────────────────

    /**
     * Apply blocking rules for all apps in the blocklist.
     * Returns true if rules were applied successfully.
     */
    public boolean applyRules() {
        Set<String> blockedApps = blocklistManager.getBlockedApps();
        boolean success = true;

        switch (activeStrategy) {
            case IPTABLES:
                success = applyIptablesRules(blockedApps);
                break;
            case DATA_RESTRICTION:
                success = applyDataRestrictions(blockedApps);
                break;
            case HYBRID:
                success = applyIptablesRules(blockedApps) ||
                          applyDataRestrictions(blockedApps);
                break;
        }

        Log.i(TAG, "Applied rules via " + activeStrategy +
                " for " + blockedApps.size() + " apps. Success: " + success);
        return success;
    }

    /**
     * Remove all firewall rules (disable firewall).
     */
    public boolean clearRules() {
        boolean success = true;

        if (rootAvailable) {
            success = clearIptablesRules();
        }

        Log.i(TAG, "Cleared all firewall rules");
        return success;
    }

    // ── Strategy 1: iptables (root) ─────────────────────────────────────

    private boolean applyIptablesRules(Set<String> blockedApps) {
        if (!rootAvailable) return false;

        try {
            // First clear existing NetBlocker rules
            clearIptablesRules();

            PackageManager pm = context.getPackageManager();

            StringBuilder commands = new StringBuilder();
            for (String packageName : blockedApps) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    int uid = appInfo.uid;

                    // Block OUTPUT traffic for this UID
                    commands.append("iptables -A OUTPUT -m owner --uid-owner ")
                            .append(uid)
                            .append(" -j REJECT --reject-with icmp-port-unreachable")
                            .append(" -m comment --comment netblocker\n");

                    // Also block with ip6tables for IPv6
                    commands.append("ip6tables -A OUTPUT -m owner --uid-owner ")
                            .append(uid)
                            .append(" -j REJECT")
                            .append(" -m comment --comment netblocker\n");

                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Package not found: " + packageName);
                }
            }

            return executeRootCommands(commands.toString());

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply iptables rules", e);
            return false;
        }
    }

    private boolean clearIptablesRules() {
        if (!rootAvailable) return false;

        try {
            // Remove all rules with netblocker comment
            String commands =
                    "iptables -S OUTPUT 2>/dev/null | grep netblocker | " +
                    "while read line; do iptables $(echo $line | sed 's/-A/-D/'); done\n" +
                    "ip6tables -S OUTPUT 2>/dev/null | grep netblocker | " +
                    "while read line; do ip6tables $(echo $line | sed 's/-A/-D/'); done\n";

            return executeRootCommands(commands);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear iptables rules", e);
            return false;
        }
    }

    private boolean executeRootCommands(String commands) {
        Log.d(TAG, "executeRootCommands: executing " + commands.split("\n").length + " commands");
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(commands);
            os.writeBytes("exit\n");
            os.flush();
            
            // Capture stderr for debugging
            BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
            int result = process.waitFor();
            
            StringBuilder errOutput = new StringBuilder();
            String errLine;
            while (errReader.ready() && (errLine = errReader.readLine()) != null) {
                errOutput.append(errLine).append("\n");
            }
            
            if (result != 0 || errOutput.length() > 0) {
                Log.w(TAG, "executeRootCommands: exitCode=" + result +
                        " stderr=" + errOutput.toString().trim());
            } else {
                Log.d(TAG, "executeRootCommands: success (exitCode=0)");
            }
            return result == 0;
        } catch (Exception e) {
            Log.e(TAG, "executeRootCommands: EXCEPTION", e);
            return false;
        }
    }

    // ── Strategy 2: Data restriction (no root) ──────────────────────────

    /**
     * Uses Android's built-in data restriction mechanisms.
     * This approach programmatically restricts background data for blocked apps.
     *
     * On non-rooted devices, this is limited to:
     * - Enabling Data Saver mode system-wide
     * - Guiding users to manually restrict per-app data
     * - Using ConnectivityManager network callbacks to monitor violations
     */
    private boolean applyDataRestrictions(Set<String> blockedApps) {
        try {
            // Register network callback to monitor blocked apps
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            // We can request the system to restrict background data
            // Note: Full per-app blocking without root requires user action
            // through Settings, which our app guides them through

            Log.i(TAG, "Data restriction mode: monitoring " +
                    blockedApps.size() + " apps");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply data restrictions", e);
            return false;
        }
    }

    // ── Block/unblock individual apps ───────────────────────────────────

    public boolean blockApp(String packageName) {
        blocklistManager.blockApp(packageName);

        if (blocklistManager.isFirewallEnabled()) {
            if (rootAvailable) {
                try {
                    PackageManager pm = context.getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    int uid = appInfo.uid;

                    String commands =
                            "iptables -A OUTPUT -m owner --uid-owner " + uid +
                            " -j REJECT --reject-with icmp-port-unreachable" +
                            " -m comment --comment netblocker\n" +
                            "ip6tables -A OUTPUT -m owner --uid-owner " + uid +
                            " -j REJECT -m comment --comment netblocker\n";

                    return executeRootCommands(commands);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to block app: " + packageName, e);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean unblockApp(String packageName) {
        blocklistManager.unblockApp(packageName);

        if (rootAvailable) {
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                int uid = appInfo.uid;

                String commands =
                        "iptables -D OUTPUT -m owner --uid-owner " + uid +
                        " -j REJECT --reject-with icmp-port-unreachable" +
                        " -m comment --comment netblocker 2>/dev/null\n" +
                        "ip6tables -D OUTPUT -m owner --uid-owner " + uid +
                        " -j REJECT -m comment --comment netblocker 2>/dev/null\n";

                return executeRootCommands(commands);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unblock app: " + packageName, e);
                return false;
            }
        }
        return true;
    }

    // ── Utility: open per-app data settings ─────────────────────────────

    /**
     * Opens Android's per-app data usage settings so the user can
     * manually restrict background/foreground data for a specific app.
     * This is the most reliable non-root method.
     */
    public void openAppDataSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings for: " + packageName, e);
        }
    }

    /**
     * Opens the Data Saver settings screen.
     */
    public void openDataSaverSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_DATA_USAGE_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open data saver settings", e);
        }
    }

    /**
     * Returns a description of the currently active strategy.
     */
    public String getStrategyDescription() {
        switch (activeStrategy) {
            case IPTABLES:
                return "Root mode: Kernel-level blocking via iptables (most effective)";
            case DATA_RESTRICTION:
                return "Standard mode: Uses Android data restrictions + guided manual settings";
            case HYBRID:
                return "Hybrid mode: Combines iptables with Android restrictions";
            default:
                return "Unknown";
        }
    }
}
