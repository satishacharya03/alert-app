// ═══════════════════════════════════════════════════════════════
//  AlertNow Android App — Complete Source Code
//  Language: Java  |  Min SDK: 26 (Android 8)  |  Target: 34
// ═══════════════════════════════════════════════════════════════
//
//  FILE STRUCTURE:
//  app/src/main/java/com/alertnow/
//    ├── MainActivity.java          ← Home screen, ID creation
//    ├── AlertReceiver.java         ← FCM message handler
//    ├── AlertFullScreenActivity.java ← Full-screen alarm UI
//    └── MyFirebaseService.java     ← FCM background service
//
//  app/src/main/res/
//    ├── layout/activity_main.xml
//    ├── layout/activity_alert.xml
//    └── raw/alarm.mp3              ← put any alarm sound here
//
//  AndroidManifest.xml             ← shown at bottom
// ═══════════════════════════════════════════════════════════════


// ───────────────────────────────────────────────────────────────
// 1.  app/build.gradle  — dependencies
// ───────────────────────────────────────────────────────────────
/*
plugins {
    id 'com.android.application'
    id 'com.google.gms.google-services'
}

android {
    compileSdk 34
    defaultConfig {
        applicationId "com.alertnow"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }
}

dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-database'
    implementation 'com.google.firebase:firebase-messaging'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
}
*/


// ───────────────────────────────────────────────────────────────
// 2.  MainActivity.java
// ───────────────────────────────────────────────────────────────

package com.alertnow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // ── UI ──────────────────────────────────────────────────────
    private TextView tvMyId, tvStatus, tvDndStatus;
    private EditText etName;
    private Button btnCreate, btnCopyId, btnDndPermission;
    private LinearLayout layoutCreated, layoutCreate;

    private SharedPreferences prefs;
    private DatabaseReference dbRef;

    // ── Notification channel ID ─────────────────────────────────
    public static final String CHANNEL_ID      = "alertnow_alerts";
    public static final String CHANNEL_ID_HIGH = "alertnow_high";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("alertnow", MODE_PRIVATE);
        dbRef = FirebaseDatabase.getInstance().getReference();

        // Wire up views
        tvMyId         = findViewById(R.id.tvMyId);
        tvStatus       = findViewById(R.id.tvStatus);
        tvDndStatus    = findViewById(R.id.tvDndStatus);
        etName         = findViewById(R.id.etName);
        btnCreate      = findViewById(R.id.btnCreate);
        btnCopyId      = findViewById(R.id.btnCopyId);
        btnDndPermission = findViewById(R.id.btnDndPermission);
        layoutCreated  = findViewById(R.id.layoutCreated);
        layoutCreate   = findViewById(R.id.layoutCreate);

        createNotificationChannels();

        String savedId = prefs.getString("alertId", null);
        if (savedId != null) {
            showCreatedState(savedId);
            refreshFcmToken(savedId);  // keep token fresh
        }

        btnCreate.setOnClickListener(v -> createAlertId());
        btnCopyId.setOnClickListener(v -> copyId());
        btnDndPermission.setOnClickListener(v -> requestDndPermission());

        checkDndPermission();
    }

    // ── Create a unique AlertID ─────────────────────────────────
    private void createAlertId() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Enter your name first");
            return;
        }

        // Generate ID like "FIRE-2947"
        String[] words = {"FIRE","BOLT","WAVE","STAR","HAWK","LUNA","NOVA","IRIS","APEX","ZEST"};
        String word = words[new Random().nextInt(words.length)];
        String num  = String.valueOf(1000 + new Random().nextInt(9000));
        String newId = word + "-" + num;

        btnCreate.setEnabled(false);
        btnCreate.setText("Creating...");

        // Get FCM token then save to Firebase
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", name);
            userData.put("fcmToken", token);
            userData.put("lastSeen", System.currentTimeMillis());
            userData.put("createdAt", System.currentTimeMillis());

            dbRef.child("users").child(newId).setValue(userData)
                .addOnSuccessListener(unused -> {
                    prefs.edit()
                        .putString("alertId", newId)
                        .putString("name", name)
                        .apply();
                    showCreatedState(newId);

                    // Subscribe to topic for broadcast alerts
                    FirebaseMessaging.getInstance().subscribeToTopic("alerts");
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    btnCreate.setText("Create My Alert ID");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        });
    }

    // ── Show ID created state ───────────────────────────────────
    private void showCreatedState(String id) {
        layoutCreate.setVisibility(View.GONE);
        layoutCreated.setVisibility(View.VISIBLE);
        tvMyId.setText(id);
        tvStatus.setText("Your phone is ready to receive alerts.");
    }

    // ── Keep FCM token fresh in Firebase ───────────────────────
    private void refreshFcmToken(String alertId) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            dbRef.child("users").child(alertId).child("fcmToken").setValue(token);
            dbRef.child("users").child(alertId).child("lastSeen").setValue(System.currentTimeMillis());
        });
    }

    // ── Copy ID to clipboard ────────────────────────────────────
    private void copyId() {
        String id = prefs.getString("alertId", "");
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
            getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("AlertID", id));
        Toast.makeText(this, "AlertID copied! Share it with others.", Toast.LENGTH_SHORT).show();
    }

    // ── DND (Do Not Disturb) permission ────────────────────────
    // This is what allows the alarm to ring even on silent mode.
    // The USER must grant this — Android requires explicit consent.
    private void checkDndPermission() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean granted = nm.isNotificationPolicyAccessGranted();
        if (granted) {
            tvDndStatus.setText("✓ Silent mode bypass: Enabled");
            tvDndStatus.setTextColor(0xFF22C55E);
            btnDndPermission.setVisibility(View.GONE);
        } else {
            tvDndStatus.setText("⚠ Allow silent bypass to ring even on silent mode");
            tvDndStatus.setTextColor(0xFFFF2D2D);
            btnDndPermission.setVisibility(View.VISIBLE);
        }
    }

    private void requestDndPermission() {
        // Takes user to system settings where they can allow DND access.
        // Android enforces this — the app cannot grant itself this permission.
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkDndPermission();  // re-check after returning from settings
    }

    // ── Notification channels ───────────────────────────────────
    private void createNotificationChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Standard channel
        NotificationChannel ch1 = new NotificationChannel(
            CHANNEL_ID, "AlertNow Alerts", NotificationManager.IMPORTANCE_HIGH);
        ch1.setDescription("Incoming alert notifications");
        ch1.enableVibration(true);
        nm.createNotificationChannel(ch1);

        // High-priority / alarm channel — bypasses DND if user granted access
        NotificationChannel ch2 = new NotificationChannel(
            CHANNEL_ID_HIGH, "AlertNow Alarms", NotificationManager.IMPORTANCE_HIGH);
        ch2.setDescription("Emergency alarms — rings on silent");
        ch2.enableVibration(true);
        ch2.setBypassDnd(true);  // works only if DND access permission granted by user
        nm.createNotificationChannel(ch2);
    }
}


// ───────────────────────────────────────────────────────────────
// 3.  MyFirebaseService.java  — receives FCM messages
// ───────────────────────────────────────────────────────────────

package com.alertnow;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();

        if (data.isEmpty()) return;

        String type    = data.getOrDefault("type", "");
        String from    = data.getOrDefault("from", "Someone");
        String message = data.getOrDefault("message", "You have an alert!");
        String sound   = data.getOrDefault("sound", "alarm");

        if ("ALERT".equals(type)) {
            // Launch full-screen alert activity
            Intent fullScreen = new Intent(this, AlertFullScreenActivity.class);
            fullScreen.putExtra("from", from);
            fullScreen.putExtra("message", message);
            fullScreen.putExtra("sound", sound);
            fullScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(fullScreen);

            // Also show a notification (fallback if activity can't show)
            showNotification(from, message, sound);

            // Ring the alarm
            ringAlarm(sound);
            vibratePhone();
        }
    }

    @Override
    public void onNewToken(String token) {
        // Update token in Firebase when it refreshes
        String alertId = getSharedPreferences("alertnow", MODE_PRIVATE)
            .getString("alertId", null);
        if (alertId != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users").child(alertId).child("fcmToken").setValue(token);
        }
    }

    private void showNotification(String from, String message, String sound) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        PendingIntent pi = PendingIntent.getActivity(
            this, 0,
            new Intent(this, AlertFullScreenActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID_HIGH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Alert from " + from)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)  // shows on lock screen
            .setAutoCancel(true)
            .setContentIntent(pi);

        nm.notify(1001, builder.build());
    }

    private void ringAlarm(String soundType) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)  // ALARM stream bypasses silent
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());

            // Use built-in alarm ringtone (or use res/raw/alarm.mp3 for custom)
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            // Uncomment to use your own sound file:
            // Uri alarmUri = Uri.parse("android.resource://" + getPackageName() + "/raw/alarm");

            mp.setDataSource(this, alarmUri);
            mp.setLooping(true);
            mp.prepare();

            // Max volume on ALARM stream (not affected by media/ringer volume)
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);

            mp.start();

            // Stop after 30 seconds automatically
            mp.setOnCompletionListener(MediaPlayer::release);
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (mp.isPlaying()) { mp.stop(); mp.release(); }
            }, 30000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        // Pattern: wait 0ms, vibrate 500ms, pause 200ms, vibrate 500ms — repeat
        long[] pattern = {0, 500, 200, 500, 200, 500};
        v.vibrate(VibrationEffect.createWaveform(pattern, 0)); // 0 = repeat from start
    }
}


// ───────────────────────────────────────────────────────────────
// 4.  AlertFullScreenActivity.java  — the big red alert screen
// ───────────────────────────────────────────────────────────────

package com.alertnow;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AlertFullScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen and turn screen on
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_alert);

        String from    = getIntent().getStringExtra("from");
        String message = getIntent().getStringExtra("message");

        TextView tvFrom = findViewById(R.id.tvAlertFrom);
        TextView tvMsg  = findViewById(R.id.tvAlertMessage);
        Button   btnDismiss = findViewById(R.id.btnDismiss);

        tvFrom.setText("From: " + (from != null ? from : "Someone"));
        tvMsg.setText(message != null ? message : "You have an alert!");

        btnDismiss.setOnClickListener(v -> {
            // Stop vibration
            android.os.Vibrator vib = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null) vib.cancel();
            finish();
        });
    }
}


// ───────────────────────────────────────────────────────────────
// 5.  activity_main.xml  — Home screen layout
// ───────────────────────────────────────────────────────────────
/*
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="#080808">

    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="28dp" android:gravity="center">

        <!-- Logo / Title -->
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="AlertNow" android:textSize="36sp" android:textStyle="bold"
            android:textColor="#FF2D2D" android:layout_marginTop="40dp"/>

        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Get alerted anywhere, anytime."
            android:textSize="14sp" android:textColor="#666666"
            android:layout_marginBottom="48dp"/>

        <!-- CREATE SECTION (hidden after ID created) -->
        <LinearLayout android:id="@+id/layoutCreate"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:background="#111111"
            android:padding="24dp" android:layout_marginBottom="16dp">

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="Create your AlertID" android:textSize="18sp"
                android:textStyle="bold" android:textColor="#F0EDE8"
                android:layout_marginBottom="16dp"/>

            <EditText android:id="@+id/etName"
                android:layout_width="match_parent" android:layout_height="wrap_content"
                android:hint="Your name" android:textColorHint="#555555"
                android:textColor="#F0EDE8" android:background="#181818"
                android:padding="14dp" android:layout_marginBottom="16dp"/>

            <Button android:id="@+id/btnCreate"
                android:layout_width="match_parent" android:layout_height="56dp"
                android:text="Create My Alert ID"
                android:backgroundTint="#FF2D2D" android:textColor="#FFFFFF"
                android:textSize="15sp" android:textStyle="bold"/>
        </LinearLayout>

        <!-- CREATED SECTION (shown after ID created) -->
        <LinearLayout android:id="@+id/layoutCreated"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical" android:visibility="gone"
            android:background="#111111" android:padding="24dp"
            android:layout_marginBottom="16dp">

            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="YOUR ALERT ID" android:textSize="11sp"
                android:textColor="#666666" android:letterSpacing="0.15"
                android:layout_marginBottom="10dp"/>

            <TextView android:id="@+id/tvMyId"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:textSize="42sp" android:textStyle="bold"
                android:textColor="#FF2D2D" android:fontFamily="monospace"
                android:layout_marginBottom="12dp"/>

            <TextView android:id="@+id/tvStatus"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:textSize="13sp" android:textColor="#666666"
                android:layout_marginBottom="20dp"/>

            <Button android:id="@+id/btnCopyId"
                android:layout_width="match_parent" android:layout_height="48dp"
                android:text="Copy ID &amp; Share"
                android:backgroundTint="#1A1A1A" android:textColor="#F0EDE8"
                android:layout_marginBottom="12dp"/>

            <!-- DND / Silent bypass section -->
            <TextView android:id="@+id/tvDndStatus"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:textSize="13sp" android:textColor="#FF2D2D"
                android:layout_marginBottom="12dp"/>

            <Button android:id="@+id/btnDndPermission"
                android:layout_width="match_parent" android:layout_height="48dp"
                android:text="Enable Silent Mode Bypass"
                android:backgroundTint="#FF2D2D" android:textColor="#FFFFFF"
                android:visibility="gone"/>
        </LinearLayout>

    </LinearLayout>
</ScrollView>
*/


// ───────────────────────────────────────────────────────────────
// 6.  activity_alert.xml  — Full-screen alarm UI
// ───────────────────────────────────────────────────────────────
/*
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:gravity="center"
    android:background="#CC0000" android:padding="32dp">

    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="🚨" android:textSize="72sp" android:layout_marginBottom="20dp"/>

    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="ALERT" android:textSize="36sp" android:textStyle="bold"
        android:textColor="#FFFFFF" android:letterSpacing="0.2"
        android:layout_marginBottom="12dp"/>

    <TextView android:id="@+id/tvAlertFrom"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:textSize="16sp" android:textColor="#FFCCCC"
        android:layout_marginBottom="24dp"/>

    <TextView android:id="@+id/tvAlertMessage"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textSize="22sp" android:textColor="#FFFFFF"
        android:textAlignment="center" android:lineSpacingMultiplier="1.4"
        android:background="#99000000" android:padding="20dp"
        android:layout_marginBottom="48dp"/>

    <Button android:id="@+id/btnDismiss"
        android:layout_width="200dp" android:layout_height="56dp"
        android:text="DISMISS" android:textSize="16sp"
        android:textStyle="bold" android:textColor="#CC0000"
        android:backgroundTint="#FFFFFF"/>
</LinearLayout>
*/


// ───────────────────────────────────────────────────────────────
// 7.  AndroidManifest.xml  — required permissions
// ───────────────────────────────────────────────────────────────
/*
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <!-- Vibration -->
    <uses-permission android:name="android.permission.VIBRATE"/>
    <!-- Show on lock screen -->
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT"/>
    <!-- Post notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <!-- DND access — user must grant this manually in settings -->
    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY"/>
    <!-- Keep processor alive to receive alerts in background -->
    <uses-permission android:name="android.permission.WAKE_LOCK"/>

    <application
        android:name=".AlertNowApp"
        android:allowBackup="true"
        android:label="AlertNow"
        android:theme="@style/Theme.AppCompat.DayNight">

        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <!-- Show OVER lock screen when alert arrives -->
        <activity android:name=".AlertFullScreenActivity"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:exported="false"/>

        <!-- FCM Service -->
        <service android:name=".MyFirebaseService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
        </service>

    </application>
</manifest>
*/