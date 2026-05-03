package com.alertnow;

import android.os.Bundle;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import android.app.Activity;

/**
 * AlertFullScreenActivity
 *
 * This activity is launched by MyFirebaseService when an alert arrives.
 * It forces the device screen on, shows above the lock screen, and
 * presents a large red "emergency" UI with a dismiss button.
 *
 * Window flags used:
 *   FLAG_KEEP_SCREEN_ON   — prevents screen from turning off while alert is shown
 *   FLAG_SHOW_WHEN_LOCKED — renders above the keyguard / lock screen
 *   FLAG_TURN_SCREEN_ON   — wakes the device display
 */
public class AlertFullScreenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_alert);

        // Pull data from the intent
        String from    = getIntent().getStringExtra("from");
        String message = getIntent().getStringExtra("message");

        // Populate UI
        TextView tvFrom    = findViewById(R.id.tvAlertFrom);
        TextView tvMessage = findViewById(R.id.tvAlertMessage);
        Button   btnDismiss = findViewById(R.id.btnDismiss);

        tvFrom.setText("From: " + (from != null ? from : "Someone"));
        tvMessage.setText(message != null ? message : "You have an emergency alert!");

        // Dismiss stops vibration and closes the activity
        btnDismiss.setOnClickListener(v -> dismiss());
    }

    private void dismiss() {
        // Stop the alarm and vibration
        MyFirebaseService.stopAlarm();

        // Bring the app to the foreground so it doesn't leave a black screen
        finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental dismissal via back button; force using the Dismiss button
        // Comment out this override if you want back-button to dismiss too.
    }
}
