# NetBlocker — Per-App Firewall (No VPN Required)

Block specific apps from accessing the internet **without using a VPN slot**. Works alongside Surfshark, Tailscale, or any other VPN.

## How It Works

NetBlocker uses two strategies depending on your device:

### Rooted Devices (Best)
Uses `iptables` to block outgoing network traffic at the kernel level, per-app by UID. This is the most effective method — apps are completely cut off from the internet while iptables rules are active.

### Non-Rooted Devices
Guides you to Android's **built-in per-app data restriction** settings. For each app you block, NetBlocker opens the system settings where you can disable both "Mobile data" and "Wi-Fi" access. Android itself then enforces the block — no VPN needed.

The app auto-detects whether root is available and uses the best strategy.

## Features

- **Per-app blocking** — choose exactly which apps lose internet access
- **No VPN conflict** — works alongside Surfshark, Tailscale, WireGuard, etc.
- **Search & filter** — quickly find apps, filter by blocked/unblocked
- **Persistent** — survives reboots (foreground service + boot receiver)
- **Dark theme** — clean dark UI
- **Block all / unblock all** — bulk operations
- **Data usage display** — see how much data each app has used (30 days)
- **System app support** — optionally show and block system apps

## Building

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android SDK 34
- JDK 11+

### Steps
1. Open Android Studio
2. File → Open → select the `NetBlocker` folder
3. Let Gradle sync
4. Run → Run 'app' (or Build → Build APK)

### Direct APK build (command line)
```bash
cd NetBlocker
chmod +x gradlew
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Setup on Your Phone

### After installing:

1. **Grant permissions** when prompted:
   - Notification permission (for the persistent service notification)

2. **Enable Usage Access** (optional, for data usage stats):
   - Settings → Apps → Special app access → Usage access → NetBlocker → Enable

3. **Disable battery optimization** (recommended):
   - Settings → Apps → NetBlocker → Battery → Unrestricted
   - This prevents Android from killing the service

### Using the app:

1. Launch NetBlocker
2. Browse or search for apps you want to block
3. Toggle the switch on each app to block it
4. Tap the **"Firewall OFF"** button at the bottom to activate

### On rooted devices:
- Grant root access when prompted
- Rules take effect immediately via iptables

### On non-rooted devices:
- When you toggle an app to "blocked", a snackbar appears with **"Open Settings"**
- Tap it to open that app's system settings
- Go to **"Mobile data & Wi-Fi"** and disable both toggles
- Android will enforce the block natively

## Project Structure

```
NetBlocker/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/netblocker/
│   │   ├── NetBlockerApp.java          — Application class, notification channels
│   │   ├── MainActivity.java           — Main screen, app list, firewall toggle
│   │   ├── AppDetailActivity.java      — Per-app details, data usage, settings
│   │   ├── SettingsActivity.java       — App settings
│   │   ├── adapters/
│   │   │   └── AppListAdapter.java     — RecyclerView adapter with filtering
│   │   ├── models/
│   │   │   └── AppInfo.java            — App data model
│   │   ├── services/
│   │   │   ├── FirewallService.java    — Foreground service maintaining rules
│   │   │   └── NetBlockerAccessibilityService.java — Monitors foreground app
│   │   ├── receivers/
│   │   │   └── BootReceiver.java       — Restarts firewall after reboot
│   │   └── utils/
│   │       ├── BlocklistManager.java   — Persists blocked app list
│   │       ├── FirewallEngine.java     — Core blocking logic (iptables / data restriction)
│   │       └── NetworkUtils.java       — Connectivity & data usage helpers
│   └── res/
│       ├── layout/                     — 4 layouts (main, item, detail, settings)
│       ├── drawable/                   — Vector icons & shape drawables
│       ├── menu/                       — Options menu
│       ├── values/                     — Colors, strings, styles
│       └── xml/                        — Accessibility service config
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Why Not VPN-Based?

Apps like NetGuard and TrackerControl use Android's VpnService API to intercept traffic. This works well, but Android only allows **one VPN at a time**. If you use Surfshark, Tailscale, or any other VPN, those apps can't coexist.

NetBlocker avoids the VPN slot entirely:
- **Root**: iptables rules operate at the kernel level, completely independent of VPN
- **Non-root**: Android's own per-app data settings are also VPN-independent

## License

Personal use. Built for you.
