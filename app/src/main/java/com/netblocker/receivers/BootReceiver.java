package com.netblocker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.netblocker.services.FirewallService;
import com.netblocker.utils.BlocklistManager;

/**
 * Receives BOOT_COMPLETED broadcast to restart the firewall service
 * if it was enabled before the device rebooted.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive called");
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "onReceive: null intent or action");
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "onReceive: action=" + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            BlocklistManager manager = new BlocklistManager(context);
            boolean fwEnabled = manager.isFirewallEnabled();
            int blockedCount = manager.getBlockedAppCount();
            Log.i(TAG, "Boot: firewallEnabled=" + fwEnabled + " blockedCount=" + blockedCount);

            if (fwEnabled && blockedCount > 0) {
                Log.i(TAG, "Boot: restarting firewall service with " + blockedCount + " blocked apps");
                try {
                    Intent serviceIntent = new Intent(context, FirewallService.class);
                    serviceIntent.setAction(FirewallService.ACTION_START);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.i(TAG, "Boot: firewall service start requested OK");
                } catch (Exception e) {
                    Log.e(TAG, "Boot: FAILED to restart firewall service", e);
                }
            } else {
                Log.i(TAG, "Boot: not restarting (disabled or no blocked apps)");
            }
        }
    }
}
