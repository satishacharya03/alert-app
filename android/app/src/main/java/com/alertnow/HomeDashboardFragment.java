package com.alertnow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class HomeDashboardFragment extends Fragment {

    private TextView  tvMyId, tvDndStatus;
    private TextView  tvSosAccessibilityStatus, tvSosLocationStatus, tvSosSmsStatus;
    private EditText  etName;
    private Button    btnCreate, btnCopyId, btnDndPermission, btnStopAlarm;
    private LinearLayout layoutCreate, layoutCreated;

    private SharedPreferences prefs;
    private DatabaseReference dbRef;

    private static final String[] ID_WORDS = {"FIRE","BOLT","WAVE","STAR","HAWK","LUNA","NOVA","IRIS","APEX","ZEST"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        tvMyId                   = view.findViewById(R.id.tvMyId);
        tvDndStatus              = view.findViewById(R.id.tvDndStatus);
        tvSosAccessibilityStatus = view.findViewById(R.id.tvSosAccessibilityStatus);
        tvSosLocationStatus      = view.findViewById(R.id.tvSosLocationStatus);
        tvSosSmsStatus           = view.findViewById(R.id.tvSosSmsStatus);
        etName           = view.findViewById(R.id.etName);
        btnCreate        = view.findViewById(R.id.btnCreate);
        btnCopyId        = view.findViewById(R.id.btnCopyId);
        btnDndPermission = view.findViewById(R.id.btnDndPermission);
        btnStopAlarm     = view.findViewById(R.id.btnStopAlarm);
        layoutCreate     = view.findViewById(R.id.layoutCreate);
        layoutCreated    = view.findViewById(R.id.layoutCreated);

        prefs = requireActivity().getSharedPreferences("alertnow", Context.MODE_PRIVATE);
        dbRef = FirebaseDatabase.getInstance().getReference();

        createNotificationChannels();

        String savedId = prefs.getString("alertId", null);
        if (savedId != null) {
            showCreatedState(savedId);
            refreshFcmToken(savedId);
        }

        btnCreate.setOnClickListener(v -> createAlertId());
        btnCopyId.setOnClickListener(v -> copyId());
        btnDndPermission.setOnClickListener(v -> requestDndPermission());
        btnStopAlarm.setOnClickListener(v -> {
            MyFirebaseService.stopAlarm();
            Toast.makeText(getContext(), "Alarm stopped", Toast.LENGTH_SHORT).show();
        });

        checkDndPermission();
        refreshSosReadiness();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkDndPermission();
        refreshSosReadiness();
    }

    private void createAlertId() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Required");
            return;
        }

        String newId = ID_WORDS[new Random().nextInt(ID_WORDS.length)] + "-" + (1000 + new Random().nextInt(9000));
        btnCreate.setEnabled(false);
        btnCreate.setText("Creating…");

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", name);
            userData.put("fcmToken", token);
            dbRef.child("users").child(newId).setValue(userData).addOnSuccessListener(unused -> {
                prefs.edit().putString("alertId", newId).putString("name", name).apply();
                showCreatedState(newId);
                FirebaseMessaging.getInstance().subscribeToTopic("alerts");
            });
        }).addOnFailureListener(e -> {
            btnCreate.setEnabled(true);
            btnCreate.setText("Create My Alert ID");
        });
    }

    private void showCreatedState(String id) {
        layoutCreate.setVisibility(View.GONE);
        layoutCreated.setVisibility(View.VISIBLE);
        tvMyId.setText(id);
    }

    private void refreshFcmToken(String alertId) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            dbRef.child("users").child(alertId).child("fcmToken").setValue(token);
        });
    }

    private void copyId() {
        String id = prefs.getString("alertId", "");
        ClipboardManager cm = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AlertID", id));
        Toast.makeText(getContext(), "ID copied!", Toast.LENGTH_SHORT).show();
    }

    private void checkDndPermission() {
        NotificationManager nm = (NotificationManager) requireActivity().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.isNotificationPolicyAccessGranted()) {
            tvDndStatus.setText("✅  DND Bypass: Enabled");
            tvDndStatus.setTextColor(0xFF22C55E);
            btnDndPermission.setVisibility(View.GONE);
        } else {
            tvDndStatus.setText("⚠️  Grant DND Bypass permission");
            tvDndStatus.setTextColor(0xFFFF2D2D);
            btnDndPermission.setVisibility(View.VISIBLE);
        }
    }

    private void requestDndPermission() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) requireActivity().getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch1 = new NotificationChannel("alertnow_alerts", "Alerts", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch1);
            NotificationChannel ch2 = new NotificationChannel("alertnow_high", "Alarms", NotificationManager.IMPORTANCE_HIGH);
            ch2.setBypassDnd(true);
            nm.createNotificationChannel(ch2);
        }
    }

    /**
     * Refresh the three SOS readiness rows on the Home Dashboard:
     * — Accessibility Service (needed to auto-enable Wi-Fi / Data / GPS via Quick Settings)
     * — Location permission (needed to share coordinates)
     * — SMS permission (needed for offline secret-SMS trigger and reply)
     */
    private void refreshSosReadiness() {
        if (tvSosAccessibilityStatus == null) return; // layout not inflated yet

        // ① Accessibility Service
        String enabledServices = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String servicePkg = requireContext().getPackageName() + "/.SosAccessibilityService";
        boolean accessOk = !TextUtils.isEmpty(enabledServices) && enabledServices.contains(servicePkg);
        if (accessOk) {
            tvSosAccessibilityStatus.setText("✅  Auto-Enable (Wi-Fi/Data/GPS): ACTIVE");
            tvSosAccessibilityStatus.setTextColor(0xFF22C55E);
        } else {
            tvSosAccessibilityStatus.setText("⚠️  Auto-Enable (Wi-Fi/Data/GPS): Go to Settings → tap ⚙️");
            tvSosAccessibilityStatus.setTextColor(0xFFFF4444);
        }

        // ② Location permission
        boolean locationOk = ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (locationOk) {
            tvSosLocationStatus.setText("✅  Location Permission: GRANTED");
            tvSosLocationStatus.setTextColor(0xFF22C55E);
        } else {
            tvSosLocationStatus.setText("⚠️  Location: NOT granted — go to Settings → tap ⚙️");
            tvSosLocationStatus.setTextColor(0xFFFF4444);
        }

        // ③ SMS permission
        boolean smsOk = ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        if (smsOk) {
            tvSosSmsStatus.setText("✅  SMS Trigger: READY");
            tvSosSmsStatus.setTextColor(0xFF22C55E);
        } else {
            tvSosSmsStatus.setText("⚠️  SMS: NOT granted — go to Settings → tap ⚙️");
            tvSosSmsStatus.setTextColor(0xFFFF4444);
        }
    }
}

