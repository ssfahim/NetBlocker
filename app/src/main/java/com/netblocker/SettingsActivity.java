package com.netblocker;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.netblocker.utils.BlocklistManager;
import com.netblocker.utils.FirewallEngine;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private BlocklistManager blocklistManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== SettingsActivity onCreate ===");
        setContentView(R.layout.activity_settings);

        blocklistManager = new BlocklistManager(this);
        FirewallEngine engine = new FirewallEngine(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        // Switches
        MaterialSwitch switchWifi = findViewById(R.id.switch_block_wifi);
        MaterialSwitch switchMobile = findViewById(R.id.switch_block_mobile);
        MaterialSwitch switchNotify = findViewById(R.id.switch_notifications);
        MaterialSwitch switchSystem = findViewById(R.id.switch_show_system);

        TextView strategyInfo = findViewById(R.id.strategy_info);
        TextView rootStatus = findViewById(R.id.root_status);
        TextView blockedCount = findViewById(R.id.settings_blocked_count);

        // Set current values
        switchWifi.setChecked(blocklistManager.shouldBlockWifi());
        switchMobile.setChecked(blocklistManager.shouldBlockMobile());
        switchNotify.setChecked(blocklistManager.shouldNotifyOnBlock());
        switchSystem.setChecked(blocklistManager.shouldShowSystemApps());

        strategyInfo.setText(engine.getStrategyDescription());
        rootStatus.setText(engine.isRootAvailable() ?
                "Root: Available ✓" : "Root: Not available — using standard mode");
        blockedCount.setText("Currently blocking " +
                blocklistManager.getBlockedAppCount() + " apps");

        // Listeners
        switchWifi.setOnCheckedChangeListener((v, checked) -> {
            Log.d(TAG, "Block Wi-Fi: " + checked);
            blocklistManager.setBlockWifi(checked);
        });
        switchMobile.setOnCheckedChangeListener((v, checked) -> {
            Log.d(TAG, "Block Mobile: " + checked);
            blocklistManager.setBlockMobile(checked);
        });
        switchNotify.setOnCheckedChangeListener((v, checked) -> {
            Log.d(TAG, "Notifications: " + checked);
            blocklistManager.setNotifyOnBlock(checked);
        });
        switchSystem.setOnCheckedChangeListener((v, checked) -> {
            Log.d(TAG, "Show system apps: " + checked);
            blocklistManager.setShowSystemApps(checked);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
