package com.alertnow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // ── View references ────────────────────────────────────────────
    private TextView  tvMyId, tvStatus, tvDndStatus;
    private EditText  etName;
    private Button    btnCreate, btnCopyId, btnDndPermission;
    private LinearLayout layoutCreate, layoutCreated;

    // ── Firebase ───────────────────────────────────────────────────
    private SharedPreferences prefs;
    private DatabaseReference dbRef;

    // ── Notification channel IDs (must match MyFirebaseService) ───
    public static final String CHANNEL_ID      = "alertnow_alerts";
    public static final String CHANNEL_ID_HIGH = "alertnow_high";

    // ── Words used to generate the random AlertID ──────────────────
    private static final String[] ID_WORDS = {
        "FIRE","BOLT","WAVE","STAR","HAWK",
        "LUNA","NOVA","IRIS","APEX","ZEST"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        tvMyId           = findViewById(R.id.tvMyId);
        tvStatus         = findViewById(R.id.tvStatus);
        tvDndStatus      = findViewById(R.id.tvDndStatus);
        etName           = findViewById(R.id.etName);
        btnCreate        = findViewById(R.id.btnCreate);
        btnCopyId        = findViewById(R.id.btnCopyId);
        btnDndPermission = findViewById(R.id.btnDndPermission);
        layoutCreate     = findViewById(R.id.layoutCreate);
        layoutCreated    = findViewById(R.id.layoutCreated);

        prefs = getSharedPreferences("alertnow", MODE_PRIVATE);
        dbRef = FirebaseDatabase.getInstance().getReference();

        // Create notification channels once
        createNotificationChannels();

        // If user already registered, skip straight to the "your ID" screen
        String savedId = prefs.getString("alertId", null);
        if (savedId != null) {
            showCreatedState(savedId);
            refreshFcmToken(savedId);   // keep token fresh on every launch
        }

        btnCreate.setOnClickListener(v        -> createAlertId());
        btnCopyId.setOnClickListener(v        -> copyId());
        btnDndPermission.setOnClickListener(v -> requestDndPermission());

        checkDndPermission();
    }

    // ── Re-check DND whenever the user returns from Settings ───────
    @Override
    protected void onResume() {
        super.onResume();
        checkDndPermission();
    }

    // ──────────────────────────────────────────────────────────────
    //  Create AlertID
    // ──────────────────────────────────────────────────────────────
    private void createAlertId() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Please enter your name");
            return;
        }

        // Build ID like  HAWK-3821
        String word  = ID_WORDS[new Random().nextInt(ID_WORDS.length)];
        String num   = String.valueOf(1000 + new Random().nextInt(9000));
        String newId = word + "-" + num;

        btnCreate.setEnabled(false);
        btnCreate.setText("Creating…");

        // Fetch FCM token, then write everything to Firebase
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name",      name);
            userData.put("fcmToken",  token);
            userData.put("lastSeen",  System.currentTimeMillis());
            userData.put("createdAt", System.currentTimeMillis());

            dbRef.child("users").child(newId).setValue(userData)
                .addOnSuccessListener(unused -> {
                    prefs.edit()
                         .putString("alertId", newId)
                         .putString("name", name)
                         .apply();
                    showCreatedState(newId);

                    // Subscribe to "alerts" topic → enables admin broadcast
                    FirebaseMessaging.getInstance().subscribeToTopic("alerts");
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    btnCreate.setText("Create My Alert ID");
                    Toast.makeText(this,
                        "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        }).addOnFailureListener(e -> {
            btnCreate.setEnabled(true);
            btnCreate.setText("Create My Alert ID");
            Toast.makeText(this,
                "Could not get FCM token: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  UI State helpers
    // ──────────────────────────────────────────────────────────────
    private void showCreatedState(String id) {
        layoutCreate.setVisibility(View.GONE);
        layoutCreated.setVisibility(View.VISIBLE);
        tvMyId.setText(id);
        tvStatus.setText("📡  Your phone is ready to receive alerts");
    }

    private void refreshFcmToken(String alertId) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            dbRef.child("users").child(alertId).child("fcmToken").setValue(token);
            dbRef.child("users").child(alertId).child("lastSeen")
                 .setValue(System.currentTimeMillis());
        });
    }

    private void copyId() {
        String id = prefs.getString("alertId", "");
        ClipboardManager cm =
            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AlertID", id));
        Toast.makeText(this, "ID copied — share it with whoever needs to alert you!",
            Toast.LENGTH_SHORT).show();
    }

    // ──────────────────────────────────────────────────────────────
    //  DND / Silent-mode bypass permission
    // ──────────────────────────────────────────────────────────────
    private void checkDndPermission() {
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean granted = nm.isNotificationPolicyAccessGranted();

        if (granted) {
            tvDndStatus.setText("✅  Silent mode bypass: Enabled");
            tvDndStatus.setTextColor(0xFF22C55E);
            btnDndPermission.setVisibility(View.GONE);
        } else {
            tvDndStatus.setText("⚠️  Grant permission so we can ring on silent mode");
            tvDndStatus.setTextColor(0xFFFF2D2D);
            btnDndPermission.setVisibility(View.VISIBLE);
        }
    }

    private void requestDndPermission() {
        // Takes the user to the system "Do Not Disturb access" settings page.
        // Android enforces that the user must enable this manually — the app
        // cannot do it on its own, which is by design for privacy/security.
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    // ──────────────────────────────────────────────────────────────
    //  Notification channels (created once, idempotent)
    // ──────────────────────────────────────────────────────────────
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Standard channel
        NotificationChannel ch1 = new NotificationChannel(
            CHANNEL_ID,
            "AlertNow Alerts",
            NotificationManager.IMPORTANCE_HIGH);
        ch1.setDescription("Incoming alert notifications");
        ch1.enableVibration(true);
        nm.createNotificationChannel(ch1);

        // High-priority ALARM channel — bypasses DND when permission granted
        NotificationChannel ch2 = new NotificationChannel(
            CHANNEL_ID_HIGH,
            "AlertNow Alarms",
            NotificationManager.IMPORTANCE_HIGH);
        ch2.setDescription("Emergency alarms — rings even on silent");
        ch2.enableVibration(true);
        ch2.setBypassDnd(true);     // effective only after DND access is granted
        nm.createNotificationChannel(ch2);
    }
}
