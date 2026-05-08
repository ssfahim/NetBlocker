package com.netblocker.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * In-app logger that captures all NetBlocker logs and crashes to a file
 * on device storage. The user can then share the log file for debugging.
 *
 * Log file location: /storage/emulated/0/Documents/NetBlocker/netblocker_log.txt
 * Crash file location: /storage/emulated/0/Documents/NetBlocker/netblocker_crash.txt
 */
public class CrashLogger {

    private static final String TAG = "CrashLogger";
    private static final String LOG_DIR_NAME = "NetBlocker";
    private static final String LOG_FILE_NAME = "netblocker_log.txt";
    private static final String CRASH_FILE_NAME = "netblocker_crash.txt";
    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024; // 5 MB max before rotation

    private static CrashLogger instance;
    private File logDir;
    private File logFile;
    private File crashFile;
    private boolean initialized = false;
    private Context appContext;

    private CrashLogger() {}

    public static synchronized CrashLogger getInstance() {
        if (instance == null) {
            instance = new CrashLogger();
        }
        return instance;
    }

    /**
     * Initialize the logger. Call from Application.onCreate() after storage permission granted.
     */
    public void init(Context context) {
        this.appContext = context.getApplicationContext();

        try {
            // Use Documents directory — visible to the user in file managers
            File documentsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
            logDir = new File(documentsDir, LOG_DIR_NAME);

            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                Log.d(TAG, "init: created log dir: " + created + " path=" + logDir.getAbsolutePath());
            }

            logFile = new File(logDir, LOG_FILE_NAME);
            crashFile = new File(logDir, CRASH_FILE_NAME);

            // Rotate if log file is too large
            rotateIfNeeded();

            // Write session header
            writeLog("========================================");
            writeLog("=== NetBlocker Session Started ===");
            writeLog("Time: " + getTimestamp());
            writeLog("Android: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")");
            writeLog("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            writeLog("App version: " + getAppVersion());
            writeLog("Log file: " + logFile.getAbsolutePath());
            writeLog("========================================");

            // Install global crash handler
            installCrashHandler();

            // Start background logcat capture
            startLogcatCapture();

            initialized = true;
            Log.i(TAG, "CrashLogger initialized. Logs at: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CrashLogger", e);
        }
    }

    /**
     * Check if logger is ready to write
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the log file path for sharing
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * Get the crash file path for sharing
     */
    public File getCrashFile() {
        return crashFile;
    }

    /**
     * Get the log directory
     */
    public File getLogDir() {
        return logDir;
    }

    // ── Manual log writing ──────────────────────────────────────────────

    /**
     * Write a timestamped line to the log file.
     * Call this from anywhere in the app for custom log entries.
     */
    public synchronized void writeLog(String message) {
        if (logFile == null) return;
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(getTimestamp() + " " + message + "\n");
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "writeLog failed", e);
        }
    }

    /**
     * Write a log entry with a tag (similar to Log.d/i/e)
     */
    public void log(String level, String tag, String message) {
        writeLog("[" + level + "] " + tag + ": " + message);
    }

    public void d(String tag, String message) { log("D", tag, message); }
    public void i(String tag, String message) { log("I", tag, message); }
    public void w(String tag, String message) { log("W", tag, message); }

    public void e(String tag, String message) { log("E", tag, message); }

    public void e(String tag, String message, Throwable t) {
        log("E", tag, message);
        writeLog(getStackTrace(t));
    }

    // ── Crash handler ───────────────────────────────────────────────────

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String crash = buildCrashReport(thread, throwable);

                // Write to crash file
                try (FileWriter writer = new FileWriter(crashFile, true)) {
                    writer.write(crash);
                    writer.flush();
                }

                // Also append to main log
                writeLog("!!! CRASH !!!");
                writeLog(crash);

                Log.e(TAG, "CRASH captured to file: " + crashFile.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "Failed to write crash report", e);
            }

            // Pass to default handler so the system crash dialog still shows
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        Log.d(TAG, "Crash handler installed");
    }

    private String buildCrashReport(Thread thread, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════\n");
        sb.append("║ NETBLOCKER CRASH REPORT\n");
        sb.append("╠══════════════════════════════════════════\n");
        sb.append("║ Time: ").append(getTimestamp()).append("\n");
        sb.append("║ Thread: ").append(thread.getName()).append(" (id=")
                .append(thread.getId()).append(")\n");
        sb.append("║ Android: ").append(Build.VERSION.SDK_INT)
                .append(" (").append(Build.VERSION.RELEASE).append(")\n");
        sb.append("║ Device: ").append(Build.MANUFACTURER).append(" ")
                .append(Build.MODEL).append("\n");
        sb.append("║ App version: ").append(getAppVersion()).append("\n");
        sb.append("╠══════════════════════════════════════════\n");
        sb.append("║ Exception: ").append(throwable.getClass().getName()).append("\n");
        sb.append("║ Message: ").append(throwable.getMessage()).append("\n");
        sb.append("╠══════════════════════════════════════════\n");
        sb.append("║ Stack Trace:\n");
        sb.append(getStackTrace(throwable));
        sb.append("╚══════════════════════════════════════════\n\n");
        return sb.toString();
    }

    // ── Logcat capture ──────────────────────────────────────────────────

    /**
     * Starts a background thread that reads logcat output filtered for
     * NetBlocker tags and writes it to our log file.
     */
    private void startLogcatCapture() {
        Thread logcatThread = new Thread(() -> {
            Process process = null;
            try {
                // Clear old logcat buffer first
                Runtime.getRuntime().exec("logcat -c").waitFor();

                // Start capturing with NetBlocker-related tags
                String[] cmd = {
                        "logcat",
                        "-v", "threadtime",     // verbose timestamp format
                        "NetBlockerApp:V",
                        "MainActivity:V",
                        "AppDetailActivity:V",
                        "SettingsActivity:V",
                        "FirewallEngine:V",
                        "FirewallService:V",
                        "BlocklistManager:V",
                        "CrashLogger:V",
                        "BootReceiver:V",
                        "NetBlockerA11y:V",
                        "NetworkUtils:V",
                        "AndroidRuntime:E",     // catch system crash messages too
                        "*:S"                   // silence everything else
                };

                process = Runtime.getRuntime().exec(cmd);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Write each logcat line directly to our file
                    synchronized (this) {
                        try (FileWriter writer = new FileWriter(logFile, true)) {
                            writer.write(line + "\n");
                        }
                    }

                    // Check if rotation needed periodically
                    if (logFile.length() > MAX_LOG_SIZE) {
                        rotateIfNeeded();
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Logcat capture thread error", e);
                writeLog("[CrashLogger] Logcat capture failed: " + e.getMessage());
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }, "NetBlocker-LogCapture");

        logcatThread.setDaemon(true);
        logcatThread.setPriority(Thread.MIN_PRIORITY);
        logcatThread.start();

        Log.d(TAG, "Logcat capture thread started");
    }

    // ── Utilities ───────────────────────────────────────────────────────

    private void rotateIfNeeded() {
        if (logFile != null && logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            File oldLog = new File(logDir, "netblocker_log_old.txt");
            if (oldLog.exists()) {
                oldLog.delete();
            }
            logFile.renameTo(oldLog);
            logFile = new File(logDir, LOG_FILE_NAME);
            writeLog("[CrashLogger] Log rotated (previous log saved as _old)");
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }

    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private String getAppVersion() {
        try {
            if (appContext != null) {
                return appContext.getPackageManager()
                        .getPackageInfo(appContext.getPackageName(), 0).versionName;
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /**
     * Get the full log content as a string (for sharing via intent).
     * Returns the last ~200KB if the file is very large.
     */
    public String getLogContent() {
        if (logFile == null || !logFile.exists()) return "No log file found.";

        try {
            long fileSize = logFile.length();
            StringBuilder sb = new StringBuilder();

            if (fileSize > 200 * 1024) {
                sb.append("[... truncated, showing last 200KB of ")
                        .append(fileSize / 1024).append("KB ...]\n\n");
            }

            BufferedReader reader = new BufferedReader(
                    new java.io.FileReader(logFile));

            // If file is large, skip to last ~200KB
            if (fileSize > 200 * 1024) {
                reader.skip(fileSize - 200 * 1024);
                reader.readLine(); // skip partial line
            }

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();

        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
    }
}
