package com.alertnow;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private EditText etSecretWord, etSosContact;
    private Button btnSaveSecret, btnSaveContact, btnReqLocation, btnEnableAccessibility;
    private TextView tvAccessibilityStatus;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Bind views
        etSecretWord           = view.findViewById(R.id.etSecretWord);
        etSosContact           = view.findViewById(R.id.etSosContact);
        btnSaveSecret          = view.findViewById(R.id.btnSaveSecret);
        btnSaveContact         = view.findViewById(R.id.btnSaveContact);
        btnReqLocation         = view.findViewById(R.id.btnReqLocation);
        btnEnableAccessibility = view.findViewById(R.id.btnEnableAccessibility);
        tvAccessibilityStatus  = view.findViewById(R.id.tvAccessibilityStatus);

        prefs = requireActivity().getSharedPreferences("super_settings", Context.MODE_PRIVATE);

        // Load saved values
        etSecretWord.setText(prefs.getString("secret_word", "EMERGENCY_SOS"));
        etSosContact.setText(prefs.getString("sos_contact", ""));

        // ── Save secret keyword ────────────────────────────────────────────────
        btnSaveSecret.setOnClickListener(v -> {
            String word = etSecretWord.getText().toString().trim();
            if (!word.isEmpty()) {
                prefs.edit().putString("secret_word", word).apply();
                Toast.makeText(getContext(), "✅ Secret keyword saved", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Save SOS contact ───────────────────────────────────────────────────
        btnSaveContact.setOnClickListener(v -> {
            String contact = etSosContact.getText().toString().trim();
            prefs.edit().putString("sos_contact", contact).apply();
            Toast.makeText(getContext(), "✅ SOS contact saved", Toast.LENGTH_SHORT).show();
        });

        // ── Grant runtime permissions (Location + SMS) ─────────────────────────
        btnReqLocation.setOnClickListener(v -> requestRuntimePermissions());

        // ── Open Accessibility Settings ────────────────────────────────────────
        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh status every time the user comes back from Settings
        refreshAccessibilityStatus();
        refreshLocationPermissionButton();
    }

    // ── Accessibility Service status ───────────────────────────────────────────

    /**
     * Check whether our SosAccessibilityService is enabled in system settings.
     * Uses the standard Settings.Secure string instead of the static isRunning()
     * flag so it works even before the service has been bound for the first time.
     */
    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String pkg = requireContext().getPackageName() + "/.SosAccessibilityService";
        return !TextUtils.isEmpty(enabledServices) && enabledServices.contains(pkg);
    }

    private void refreshAccessibilityStatus() {
        if (isAccessibilityEnabled()) {
            tvAccessibilityStatus.setText("✅  SOS Accessibility: ACTIVE — Wi-Fi / Data / GPS will auto-enable");
            tvAccessibilityStatus.setTextColor(0xFF22C55E);
            btnEnableAccessibility.setText("✅ Accessibility Enabled (tap to review)");
            btnEnableAccessibility.getBackground().setTint(0xFF1A5C2A);
        } else {
            tvAccessibilityStatus.setText("⚠️  SOS Accessibility: NOT granted — auto-enable won't work");
            tvAccessibilityStatus.setTextColor(0xFFFF4444);
            btnEnableAccessibility.setText("⚡ Enable SOS Accessibility Service");
            btnEnableAccessibility.getBackground().setTint(0xFFFF2D2D);
        }
    }

    private void openAccessibilitySettings() {
        Toast.makeText(getContext(),
                "Find 'AlertNow Super App' in the list and enable it",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    // ── Runtime permissions ────────────────────────────────────────────────────

    private void refreshLocationPermissionButton() {
        boolean granted = ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            btnReqLocation.setText("✅ Location Permission Granted");
            btnReqLocation.getBackground().setTint(0xFF1A5C2A);
        } else {
            btnReqLocation.setText("📍 Grant Location Permission");
            btnReqLocation.getBackground().setTint(0xFF1A1A1A);
        }
    }

    private void requestRuntimePermissions() {
        ActivityCompat.requestPermissions(requireActivity(), new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE
        }, 100);
    }
}
