package com.alertnow;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage smsMessage;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            String format = bundle.getString("format");
                            smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                        } else {
                            smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                        }

                        String sender = smsMessage.getOriginatingAddress();
                        String body = smsMessage.getMessageBody();

                        SharedPreferences prefs = context.getSharedPreferences("super_settings", Context.MODE_PRIVATE);
                        String secretWord = prefs.getString("secret_word", "EMERGENCY_SOS");

                        if (body != null && body.contains(secretWord)) {
                            Log.d("SmsReceiver", "Secret SMS received from: " + sender);
                            triggerEmergencyMode(context, sender);
                        }
                    }
                }
            }
        }
    }

    private void triggerEmergencyMode(Context context, String sender) {
        // ── STEP 1: Auto-enable Wi-Fi, Mobile Data & GPS ────────────────────
        // Three cascaded strategies (Direct API → Reflection → Quick Settings tile)
        SosAccessibilityService.triggerSos(context);

        // ── STEP 2: Full-screen alarm + loud siren ───────────────────────────
        Intent alarmIntent = new Intent(context, AlertFullScreenActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarmIntent.putExtra("message", "🚨 SOS received from " + sender);
        context.startActivity(alarmIntent);

        MyFirebaseService.ringAlarm(context);
        MyFirebaseService.vibratePhone(context);

        // ── STEP 3: Fetch GPS location and reply via SMS ─────────────────────
        // Delay slightly so GPS has a moment to warm up after being enabled
        new Handler(Looper.getMainLooper()).postDelayed(() -> fetchLocationAndReply(context, sender), 4000);
    }

    private void fetchLocationAndReply(Context context, String sender) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            client.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    sendLocationSms(sender, location);
                } else {
                    // Last-known is null: request a fresh location update
                    requestFreshLocation(context, sender, client);
                }
            }).addOnFailureListener(e ->
                sendSms(sender, "🚨 SOS! Alarm ringing. Location unavailable: " + e.getMessage()));
        } else {
            sendSms(sender, "🚨 SOS! Alarm is ringing. Location permission not granted — cannot share coordinates.");
        }
    }

    /** Request a single high-accuracy location fix when last-known is stale/null */
    private void requestFreshLocation(Context context, String sender,
                                      FusedLocationProviderClient client) {
        try {
            com.google.android.gms.location.LocationRequest req =
                    com.google.android.gms.location.LocationRequest.create()
                            .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                            .setNumUpdates(1)
                            .setInterval(1000);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                sendSms(sender, "🚨 SOS! Alarm ringing. Location permission missing.");
                return;
            }

            client.requestLocationUpdates(req,
                    new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(com.google.android.gms.location.LocationResult result) {
                            client.removeLocationUpdates(this);
                            Location location = result.getLastLocation();
                            if (location != null) {
                                sendLocationSms(sender, location);
                            } else {
                                sendSms(sender, "🚨 SOS! Alarm ringing. Could not obtain GPS fix.");
                            }
                        }
                    },
                    Looper.getMainLooper());

            // Timeout: if no fix in 15 s, send a fallback SMS
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    sendSms(sender, "🚨 SOS! Alarm ringing. GPS timed out — location unavailable."), 15000);

        } catch (Exception e) {
            sendSms(sender, "🚨 SOS! Alarm ringing. Failed to get location: " + e.getMessage());
        }
    }

    private void sendLocationSms(String phoneNumber, Location location) {
        String mapsLink = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
        String message = "SOS Received! Alarm is ringing. My current location: " + mapsLink;
        sendSms(phoneNumber, message);
    }

    private void sendSms(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d("SmsReceiver", "SMS sent to " + phoneNumber);
        } catch (Exception e) {
            Log.e("SmsReceiver", "Failed to send SMS: " + e.getMessage());
        }
    }
}
