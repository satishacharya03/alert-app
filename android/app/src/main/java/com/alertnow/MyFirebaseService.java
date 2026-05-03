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
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "AlertNowFCM";

    // ── Receive a push message ─────────────────────────────────────
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) return;

        String type    = data.getOrDefault("type",    "");
        String from    = data.getOrDefault("sender_name", "Someone");
        String message = data.getOrDefault("message", "You have an emergency alert!");

        if ("ALERT".equals(type)) {
            // ① Try to launch full-screen activity (works when screen is on)
            launchAlertActivity(from, message);

            // ② Also post a high-priority notification (fallback for locked/off screen)
            showFullScreenNotification(from, message);

            // ③ Ring the alarm stream (bypasses silent mode)
            ringAlarm();

            // ④ Vibrate
            vibratePhone();
        }
    }

    // ── Called by Firebase when the FCM token is refreshed ─────────
    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "FCM token refreshed");
        String alertId = getSharedPreferences("alertnow", MODE_PRIVATE)
            .getString("alertId", null);
        if (alertId != null) {
            FirebaseDatabase.getInstance()
                .getReference("users")
                .child(alertId)
                .child("fcmToken")
                .setValue(token);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Launch the big red alert activity over the lock screen
    // ──────────────────────────────────────────────────────────────
    private void launchAlertActivity(String from, String message) {
        Intent intent = new Intent(this, AlertFullScreenActivity.class);
        intent.putExtra("from",    from);
        intent.putExtra("message", message);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                      | Intent.FLAG_ACTIVITY_CLEAR_TOP
                      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    // ──────────────────────────────────────────────────────────────
    //  High-priority notification with a full-screen intent
    //  (shows even when the device is locked / screen off)
    // ──────────────────────────────────────────────────────────────
    private void showFullScreenNotification(String from, String message) {
        Intent fullScreenIntent = new Intent(this, AlertFullScreenActivity.class);
        fullScreenIntent.putExtra("from",    from);
        fullScreenIntent.putExtra("message", message);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, fullScreenIntent, flags);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID_HIGH)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨  Alert from " + from)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)   // ← the key flag for lock-screen overlay
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(1001, builder.build());
    }

    // ──────────────────────────────────────────────────────────────
    //  Ring using the ALARM audio stream.
    //  USAGE_ALARM is a separate volume channel from the ringer —
    //  it plays even when the phone is on silent.
    // ──────────────────────────────────────────────────────────────
    private void ringAlarm() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            mp.setDataSource(this, alarmUri);
            mp.setLooping(true);
            mp.prepare();

            // Max out the alarm volume so it cannot be missed
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);

            mp.start();

            // Auto-stop after 30 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (mp.isPlaying()) {
                    mp.stop();
                }
                mp.release();
            }, 30_000);

        } catch (Exception e) {
            Log.e(TAG, "ringAlarm failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Vibrate in a repeating SOS-style pattern
    // ──────────────────────────────────────────────────────────────
    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;

        long[] pattern = {0, 500, 200, 500, 200, 800};   // off, on, off, on …
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0));  // 0 = loop
        } else {
            v.vibrate(pattern, 0);
        }
    }
}
