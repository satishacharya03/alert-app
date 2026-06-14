package com.alertnow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * SosAccessibilityService
 *
 * The ONLY reliable way to auto-enable Wi-Fi, Mobile Data and GPS on Android 10+
 * without root. The user grants this service ONCE in:
 *    Settings → Accessibility → Installed Services → AlertNow Super App → Enable
 *
 * When an SOS SMS arrives, SmsReceiver calls triggerSos(context).
 * This service then runs a 3-strategy cascade:
 *
 *   Strategy 1 – Direct API  (works Android 8/9, no root needed)
 *   Strategy 2 – LocationManager hidden method (works on some OEM ROMs)
 *   Strategy 3 – Quick Settings automation (works Android 10+ via Accessibility)
 */
public class SosAccessibilityService extends AccessibilityService {

    private static final String TAG = "SosAccessibility";

    // Singleton kept alive as long as the service is running
    private static SosAccessibilityService instance;

    // ── Public API ──────────────────────────────────────────────────────────────

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Called by SmsReceiver. Triggers all three enablement strategies.
     * Safe to call from a background thread.
     */
    public static void triggerSos(Context context) {
        // Strategy 1 & 2: Direct API calls (work instantly where supported)
        enableWifiDirect(context);
        enableMobileDataDirect(context);
        boolean gpsEnabled = enableGpsDirect(context);

        // Strategy 3: Quick Settings UI automation (guaranteed on Android 10+)
        new Handler(Looper.getMainLooper()).post(() -> {
            if (instance != null) {
                instance.performQuickSettingsAutomation(gpsEnabled);
            } else {
                Log.w(TAG, "Accessibility service not running – only direct API tried.");
            }
        });
    }

    // ── Strategy 1 & 2: Direct API ─────────────────────────────────────────────

    /** Try to enable Wi-Fi via WifiManager (works on Android ≤ 9). */
    private static void enableWifiDirect(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    wm.setWifiEnabled(true);
                    Log.d(TAG, "Wi-Fi enabled via WifiManager (API ≤ 28)");
                }
                // On API 29+ we fall through to Quick Settings automation
            }
        } catch (Exception e) {
            Log.e(TAG, "Wi-Fi direct enable failed: " + e.getMessage());
        }
    }

    /** Try to enable Mobile Data via reflection on ConnectivityManager. */
    private static void enableMobileDataDirect(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Method setMobileDataEnabled = cm.getClass().getDeclaredMethod("setMobileDataEnabled", boolean.class);
                setMobileDataEnabled.setAccessible(true);
                setMobileDataEnabled.invoke(cm, true);
                Log.d(TAG, "Mobile data enabled via reflection");
            }
        } catch (Exception e) {
            Log.e(TAG, "Mobile data direct enable failed: " + e.getMessage());
        }
    }

    /**
     * Try to enable GPS directly.
     * Returns true if GPS is already on or was turned on successfully.
     *
     * Methods tried in order:
     *   1. Check if already enabled → done
     *   2. Settings.Secure LOCATION_MODE write (requires WRITE_SECURE_SETTINGS — granted via ADB)
     *   3. LocationManager hidden setLocationEnabledForUser (some OEM ROMs)
     */
    private static boolean enableGpsDirect(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;

        // Already on?
        if (lm.isLocationEnabled()) {
            Log.d(TAG, "GPS already enabled.");
            return true;
        }

        // Method A: Settings.Secure (needs WRITE_SECURE_SETTINGS ADB permission)
        try {
            ContentResolver cr = context.getContentResolver();
            Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            if (lm.isLocationEnabled()) {
                Log.d(TAG, "GPS enabled via Settings.Secure.LOCATION_MODE");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Settings.Secure GPS write failed: " + e.getMessage());
        }

        // Method B: Reflection on LocationManager (works on some OEM builds)
        try {
            Method enableMethod = lm.getClass().getDeclaredMethod("setLocationEnabledForUser",
                    boolean.class, android.os.UserHandle.class);
            enableMethod.setAccessible(true);
            enableMethod.invoke(lm, true, android.os.Process.myUserHandle());
            if (lm.isLocationEnabled()) {
                Log.d(TAG, "GPS enabled via LocationManager reflection");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "LocationManager reflection failed: " + e.getMessage());
        }

        Log.d(TAG, "GPS direct enable failed — will try Quick Settings tile.");
        return false;
    }

    // ── Strategy 3: Quick Settings Automation ──────────────────────────────────

    /**
     * Opens the Quick Settings panel and taps the Wi-Fi, Data, and Location tiles.
     * @param gpsAlreadyEnabled skip the Location tile if GPS was already turned on
     */
    private void performQuickSettingsAutomation(boolean gpsAlreadyEnabled) {
        Log.d(TAG, "Starting Quick Settings automation...");

        // Open Quick Settings (two-step pull-down)
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);

            // Wait for the panel to fully expand
            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                // ① Wi-Fi tile
                tapTile("Wi-Fi", "wifi");

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    // ② Mobile data tile
                    tapTile("Mobile data", "data", "cellular", "internet");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        // ③ Location/GPS tile — only if not already enabled
                        if (!gpsAlreadyEnabled) {
                            tapTile("Location", "GPS", "location");
                        }

                        // Dismiss Quick Settings after a short pause
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> performGlobalAction(GLOBAL_ACTION_BACK), 1000);
                    }, 700);
                }, 700);
            }, 1000);
        }, 400);
    }

    /**
     * Tap a Quick Settings tile by searching for any of the given keywords
     * in both node text AND content description (handles all OEM labels).
     */
    private void tapTile(String... keywords) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "Root window null — cannot tap tile " + Arrays.toString(keywords));
            return;
        }

        for (String keyword : keywords) {
            // Search by text
            List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText(keyword);
            if (byText != null) {
                for (AccessibilityNodeInfo node : byText) {
                    if (clickNodeOrParent(node, keyword)) { root.recycle(); return; }
                }
            }

            // Search by content description (many OEMs use icons without text)
            List<AccessibilityNodeInfo> byDesc = root.findAccessibilityNodeInfosByViewId(
                    "com.android.systemui:id/tile_label");
            if (byDesc != null) {
                for (AccessibilityNodeInfo node : byDesc) {
                    CharSequence text = node.getText();
                    CharSequence desc = node.getContentDescription();
                    if (containsIgnoreCase(text, keyword) || containsIgnoreCase(desc, keyword)) {
                        if (clickNodeOrParent(node, keyword)) { root.recycle(); return; }
                    }
                }
            }
        }
        Log.w(TAG, "Could not find Quick Settings tile for: " + Arrays.toString(keywords));
        root.recycle();
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node, String label) {
        AccessibilityNodeInfo clickable = findClickableAncestor(node);
        if (clickable != null) {
            boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Tapped tile '" + label + "': " + ok);
            clickable.recycle();
            return ok;
        }
        return false;
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isClickable()) return node;
        AccessibilityNodeInfo parent = node.getParent();
        if (parent == null) return null;
        AccessibilityNodeInfo result = findClickableAncestor(parent);
        parent.recycle();
        return result;
    }

    private boolean containsIgnoreCase(CharSequence haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toString().toLowerCase().contains(needle.toLowerCase());
    }

    // ── AccessibilityService lifecycle ──────────────────────────────────────────

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "SosAccessibilityService connected ✓");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "SosAccessibilityService disconnected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* passive — no action */ }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "SosAccessibilityService interrupted");
    }
}
