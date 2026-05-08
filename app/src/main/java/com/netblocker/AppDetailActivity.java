package com.netblocker;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.netblocker.utils.BlocklistManager;
import com.netblocker.utils.FirewallEngine;
import com.netblocker.utils.NetworkUtils;

public class AppDetailActivity extends AppCompatActivity {

    private static final String TAG = "AppDetailActivity";
    private BlocklistManager blocklistManager;
    private FirewallEngine firewallEngine;
    private String packageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== AppDetailActivity onCreate ===");
        setContentView(R.layout.activity_app_detail);

        blocklistManager = new BlocklistManager(this);
        firewallEngine = new FirewallEngine(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        packageName = getIntent().getStringExtra("package_name");
        String appName = getIntent().getStringExtra("app_name");
        boolean isBlocked = getIntent().getBooleanExtra("is_blocked", false);
        boolean isSystem = getIntent().getBooleanExtra("is_system", false);
        int uid = getIntent().getIntExtra("uid", 0);

        Log.d(TAG, "Viewing: " + packageName + " name=" + appName +
                " blocked=" + isBlocked + " system=" + isSystem + " uid=" + uid);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName);
        }

        // Views
        ImageView iconView = findViewById(R.id.detail_app_icon);
        TextView nameView = findViewById(R.id.detail_app_name);
        TextView pkgView = findViewById(R.id.detail_package_name);
        TextView uidView = findViewById(R.id.detail_uid);
        TextView typeView = findViewById(R.id.detail_app_type);
        TextView dataView = findViewById(R.id.detail_data_usage);
        TextView strategyView = findViewById(R.id.detail_strategy);
        SwitchMaterial blockSwitch = findViewById(R.id.detail_block_switch);
        Button openSettingsBtn = findViewById(R.id.btn_open_settings);
        Button openDataSaverBtn = findViewById(R.id.btn_open_data_saver);

        // Populate
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            iconView.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            iconView.setImageResource(R.drawable.ic_app_default);
        }

        nameView.setText(appName);
        pkgView.setText(packageName);
        uidView.setText("UID: " + uid);
        typeView.setText(isSystem ? "System App" : "User App");

        long dataUsage = NetworkUtils.getDataUsageForUid(this, uid);
        dataView.setText("Data usage (30 days): " + NetworkUtils.formatBytes(dataUsage));

        strategyView.setText("Blocking: " + firewallEngine.getStrategyDescription());

        // Block toggle
        blockSwitch.setChecked(isBlocked);
        blockSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            Log.i(TAG, "Block toggle changed: " + packageName + " -> " + (checked ? "BLOCK" : "UNBLOCK"));
            if (checked) {
                firewallEngine.blockApp(packageName);
            } else {
                firewallEngine.unblockApp(packageName);
            }
        });

        // System settings buttons
        openSettingsBtn.setOnClickListener(v -> {
            Log.d(TAG, "Opening app data settings for: " + packageName);
            firewallEngine.openAppDataSettings(packageName);
        });

        openDataSaverBtn.setOnClickListener(v -> {
            Log.d(TAG, "Opening data saver settings");
            firewallEngine.openDataSaverSettings();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
