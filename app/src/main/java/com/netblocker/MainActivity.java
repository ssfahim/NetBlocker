package com.netblocker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.netblocker.adapters.AppListAdapter;
import com.netblocker.models.AppInfo;
import com.netblocker.services.FirewallService;
import com.netblocker.utils.BlocklistManager;
import com.netblocker.utils.CrashLogger;
import com.netblocker.utils.FirewallEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AppListAdapter.OnAppToggleListener {

    private static final String TAG = "MainActivity";

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private EditText searchBar;
    private ExtendedFloatingActionButton fabFirewall;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView blockedCountText;
    private TextView strategyText;
    private ChipGroup filterChips;
    private LinearLayout emptyState;

    private BlocklistManager blocklistManager;
    private FirewallEngine firewallEngine;
    private List<AppInfo> allApps = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean showSystemApps = false;
    private String currentFilter = "all"; // all, blocked, unblocked

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== MainActivity onCreate ===");
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "setContentView OK");
        } catch (Exception e) {
            Log.e(TAG, "FATAL: setContentView failed", e);
            throw e;
        }

        blocklistManager = new BlocklistManager(this);
        firewallEngine = new FirewallEngine(this);
        Log.d(TAG, "FirewallEngine strategy: " + firewallEngine.getActiveStrategy());
        Log.d(TAG, "Root available: " + firewallEngine.isRootAvailable());

        try {
            initViews();
            Log.d(TAG, "initViews OK");
        } catch (Exception e) {
            Log.e(TAG, "FATAL: initViews failed", e);
            throw e;
        }
        requestPermissions();
        loadApps();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("NetBlocker");
        }

        recyclerView = findViewById(R.id.app_list);
        searchBar = findViewById(R.id.search_bar);
        fabFirewall = findViewById(R.id.fab_firewall);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        blockedCountText = findViewById(R.id.blocked_count);
        strategyText = findViewById(R.id.strategy_text);
        filterChips = findViewById(R.id.filter_chips);
        emptyState = findViewById(R.id.empty_state);

        // RecyclerView setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        // Search
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Firewall toggle FAB
        updateFabState();
        fabFirewall.setOnClickListener(v -> toggleFirewall());

        // Filter chips
        Chip chipAll = findViewById(R.id.chip_all);
        Chip chipBlocked = findViewById(R.id.chip_blocked);
        Chip chipUnblocked = findViewById(R.id.chip_unblocked);

        chipAll.setOnClickListener(v -> { currentFilter = "all"; applyFilter(); });
        chipBlocked.setOnClickListener(v -> { currentFilter = "blocked"; applyFilter(); });
        chipUnblocked.setOnClickListener(v -> { currentFilter = "unblocked"; applyFilter(); });

        // Strategy info
        strategyText.setText(firewallEngine.getStrategyDescription());
    }

    private static final int PERM_REQUEST_NOTIFICATIONS = 100;
    private static final int PERM_REQUEST_STORAGE = 101;

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Storage permission (Android 9 and below need WRITE_EXTERNAL_STORAGE)
        // Android 10 uses requestLegacyExternalStorage in manifest
        // Android 11+ can write to Documents/ without permission
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + needed);
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERM_REQUEST_STORAGE);
        } else {
            // Permissions already granted, make sure logger is running
            ensureLoggerInitialized();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: code=" + requestCode +
                " results=" + java.util.Arrays.toString(grantResults));

        // Re-init logger now that storage permission might be granted
        ensureLoggerInitialized();

        for (int i = 0; i < permissions.length; i++) {
            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission " + permissions[i] + " -> " + (granted ? "GRANTED" : "DENIED"));
        }
    }

    private void ensureLoggerInitialized() {
        if (!CrashLogger.getInstance().isInitialized()) {
            ((NetBlockerApp) getApplication()).reinitLoggerIfNeeded();
        }
        if (CrashLogger.getInstance().isInitialized()) {
            File logFile = CrashLogger.getInstance().getLogFile();
            Log.i(TAG, "Log file location: " + (logFile != null ? logFile.getAbsolutePath() : "null"));
        }
    }

    private void loadApps() {
        Log.d(TAG, "loadApps: starting (showSystemApps=" + showSystemApps + ")");
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> packages = pm.getInstalledApplications(
                        PackageManager.GET_META_DATA);
                Log.d(TAG, "loadApps: found " + packages.size() + " total packages");

                List<AppInfo> apps = new ArrayList<>();
                int skippedSystem = 0;
                for (ApplicationInfo appInfo : packages) {
                    boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                    // Skip self
                    if (appInfo.packageName.equals(getPackageName())) continue;

                    // Skip system apps unless showing them
                    if (isSystemApp && !showSystemApps) {
                        skippedSystem++;
                        continue;
                    }

                    String appName = pm.getApplicationLabel(appInfo).toString();
                    boolean isBlocked = blocklistManager.isAppBlocked(appInfo.packageName);

                    AppInfo app = new AppInfo(
                            appName,
                            appInfo.packageName,
                            pm.getApplicationIcon(appInfo),
                            isBlocked,
                            isSystemApp,
                            appInfo.uid
                    );

                    apps.add(app);
                }
                Log.d(TAG, "loadApps: loaded " + apps.size() + " apps (skipped " + skippedSystem + " system)");

                // Sort: blocked first, then alphabetical
                Collections.sort(apps, (a, b) -> {
                    if (a.isBlocked() != b.isBlocked()) {
                        return a.isBlocked() ? -1 : 1;
                    }
                    return a.getAppName().compareToIgnoreCase(b.getAppName());
                });

                mainHandler.post(() -> {
                    allApps = apps;
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    updateBlockedCount();
                    updateEmptyState();
                    Log.i(TAG, "loadApps: UI updated, showing " + adapter.getItemCount() + " items");
                });
            } catch (Exception e) {
                Log.e(TAG, "loadApps: EXCEPTION loading apps", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Snackbar.make(recyclerView, "Error loading apps: " + e.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyFilter() {
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo app : allApps) {
            switch (currentFilter) {
                case "blocked":
                    if (app.isBlocked()) filtered.add(app);
                    break;
                case "unblocked":
                    if (!app.isBlocked()) filtered.add(app);
                    break;
                default:
                    filtered.add(app);
                    break;
            }
        }
        adapter.updateApps(filtered);
        updateEmptyState();
    }

    private void toggleFirewall() {
        boolean isEnabled = blocklistManager.isFirewallEnabled();
        Log.d(TAG, "toggleFirewall: currently " + (isEnabled ? "ON" : "OFF"));

        if (isEnabled) {
            // Stop firewall
            Log.i(TAG, "toggleFirewall: STOPPING firewall");
            Intent intent = new Intent(this, FirewallService.class);
            intent.setAction(FirewallService.ACTION_STOP);
            startService(intent);
            blocklistManager.setFirewallEnabled(false);

            Snackbar.make(fabFirewall, "Firewall disabled", Snackbar.LENGTH_SHORT).show();
        } else {
            // Start firewall
            int blockedCount = blocklistManager.getBlockedAppCount();
            Log.i(TAG, "toggleFirewall: STARTING firewall (" + blockedCount + " apps blocked)");
            if (blockedCount == 0) {
                Log.w(TAG, "toggleFirewall: no apps blocked, aborting start");
                Snackbar.make(fabFirewall,
                        "Block at least one app first", Snackbar.LENGTH_LONG).show();
                return;
            }

            try {
                Intent intent = new Intent(this, FirewallService.class);
                intent.setAction(FirewallService.ACTION_START);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                blocklistManager.setFirewallEnabled(true);
                Log.i(TAG, "toggleFirewall: service started OK");

                Snackbar.make(fabFirewall,
                        "Firewall enabled — " + blockedCount +
                        " apps blocked", Snackbar.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "toggleFirewall: FAILED to start service", e);
                Snackbar.make(fabFirewall,
                        "Error starting firewall: " + e.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        }

        updateFabState();
    }

    private void updateFabState() {
        boolean enabled = blocklistManager.isFirewallEnabled();
        if (enabled) {
            fabFirewall.setText("Firewall ON");
            fabFirewall.setIconResource(R.drawable.ic_shield);
            fabFirewall.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.firewall_active));
        } else {
            fabFirewall.setText("Firewall OFF");
            fabFirewall.setIconResource(R.drawable.ic_shield_off);
            fabFirewall.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.firewall_inactive));
        }
    }

    private void updateBlockedCount() {
        int count = blocklistManager.getBlockedAppCount();
        blockedCountText.setText(count + " app" + (count != 1 ? "s" : "") + " blocked");
    }

    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // ── AppListAdapter callbacks ────────────────────────────────────────

    @Override
    public void onAppToggled(AppInfo app, boolean blocked) {
        Log.i(TAG, "onAppToggled: " + app.getPackageName() + " -> " + (blocked ? "BLOCK" : "UNBLOCK"));
        if (blocked) {
            firewallEngine.blockApp(app.getPackageName());
        } else {
            firewallEngine.unblockApp(app.getPackageName());
        }

        updateBlockedCount();

        // If firewall is running, refresh rules
        if (blocklistManager.isFirewallEnabled()) {
            Log.d(TAG, "onAppToggled: refreshing firewall rules");
            Intent intent = new Intent(this, FirewallService.class);
            intent.setAction(FirewallService.ACTION_REFRESH);
            startService(intent);
        }

        // If non-root, guide user to system settings
        if (!firewallEngine.isRootAvailable() && blocked) {
            Log.d(TAG, "onAppToggled: non-root, showing settings guidance");
            Snackbar.make(recyclerView,
                    "Tap for best results: disable data in system settings",
                    Snackbar.LENGTH_LONG)
                    .setAction("Open Settings", v ->
                            firewallEngine.openAppDataSettings(app.getPackageName()))
                    .show();
        }
    }

    @Override
    public void onAppClicked(AppInfo app) {
        Log.d(TAG, "onAppClicked: " + app.getPackageName() + " (uid=" + app.getUid() + ")");
        Intent intent = new Intent(this, AppDetailActivity.class);
        intent.putExtra("package_name", app.getPackageName());
        intent.putExtra("app_name", app.getAppName());
        intent.putExtra("is_blocked", app.isBlocked());
        intent.putExtra("is_system", app.isSystemApp());
        intent.putExtra("uid", app.getUid());
        startActivity(intent);
    }

    // ── Menu ────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_show_system) {
            showSystemApps = !showSystemApps;
            item.setChecked(showSystemApps);
            loadApps();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_block_all) {
            blockAllVisible();
            return true;
        } else if (id == R.id.action_unblock_all) {
            unblockAll();
            return true;
        } else if (id == R.id.action_share_logs) {
            shareLogs();
            return true;
        } else if (id == R.id.action_view_log_path) {
            showLogPath();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ── Log sharing ─────────────────────────────────────────────────────

    private void shareLogs() {
        CrashLogger logger = CrashLogger.getInstance();
        if (!logger.isInitialized() || logger.getLogFile() == null ||
                !logger.getLogFile().exists()) {
            Toast.makeText(this, "No log file found yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File logFile = logger.getLogFile();
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", logFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NetBlocker Logs");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "NetBlocker debug logs attached.\n" +
                    "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                    "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share NetBlocker Logs"));
            Log.i(TAG, "shareLogs: share intent launched");

        } catch (Exception e) {
            Log.e(TAG, "shareLogs: FAILED", e);

            // Fallback: just copy the log path to clipboard
            showLogPath();
            Toast.makeText(this,
                    "Share failed. Log path copied — use a file manager to find it.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showLogPath() {
        CrashLogger logger = CrashLogger.getInstance();
        String path;
        if (logger.isInitialized() && logger.getLogFile() != null) {
            path = logger.getLogFile().getAbsolutePath();

            // Also show crash file if it exists
            File crashFile = logger.getCrashFile();
            if (crashFile != null && crashFile.exists() && crashFile.length() > 0) {
                path += "\n\nCrash log: " + crashFile.getAbsolutePath();
            }
        } else {
            path = "Logger not initialized — grant storage permission and restart.";
        }

        // Copy to clipboard
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Log Path", path));
        }

        Snackbar.make(recyclerView, "Log path copied:\n" + path, Snackbar.LENGTH_LONG).show();
        Log.i(TAG, "showLogPath: " + path);
    }

    private void blockAllVisible() {
        for (AppInfo app : allApps) {
            if (!app.isBlocked()) {
                app.setBlocked(true);
                firewallEngine.blockApp(app.getPackageName());
            }
        }
        adapter.notifyDataSetChanged();
        updateBlockedCount();

        Snackbar.make(recyclerView, "All visible apps blocked", Snackbar.LENGTH_SHORT).show();
    }

    private void unblockAll() {
        for (AppInfo app : allApps) {
            if (app.isBlocked()) {
                app.setBlocked(false);
                firewallEngine.unblockApp(app.getPackageName());
            }
        }
        adapter.notifyDataSetChanged();
        updateBlockedCount();

        Snackbar.make(recyclerView, "All apps unblocked", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        updateFabState();
        updateBlockedCount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        executor.shutdown();
    }
}
