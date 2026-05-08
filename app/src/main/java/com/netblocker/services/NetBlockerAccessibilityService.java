package com.netblocker.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.netblocker.utils.BlocklistManager;

/**
 * Accessibility service that monitors app launches.
 * When a blocked app comes to the foreground, it can:
 * - Send a notification warning
 * - Log the access attempt
 * - (Optionally) force-stop the app's network connection
 *
 * This is a supplementary strategy for non-rooted devices.
 */
public class NetBlockerAccessibilityService extends AccessibilityService {

    private static final String TAG = "NetBlockerA11y";
    private BlocklistManager blocklistManager;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        blocklistManager = new BlocklistManager(this);

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();

        if (blocklistManager.isFirewallEnabled() &&
                blocklistManager.isAppBlocked(packageName)) {
            Log.i(TAG, "Blocked app detected in foreground: " + packageName);
            // Log the event - the actual blocking is handled by
            // FirewallEngine (iptables on root, data restriction otherwise)
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Accessibility service destroyed");
    }
}
