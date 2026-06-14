package com.alertnow;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

public class CallFilterService extends CallScreeningService {

    private static final String TAG = "CallFilterService";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
            String phoneNumber = "";
            if (callDetails.getHandle() != null) {
                phoneNumber = callDetails.getHandle().getSchemeSpecificPart();
            }

            Log.d(TAG, "Incoming call from: " + phoneNumber);

            SharedPreferences prefs = getSharedPreferences("call_filter", Context.MODE_PRIVATE);
            Set<String> whitelist = prefs.getStringSet("whitelist", new HashSet<>());
            Set<String> blacklist = prefs.getStringSet("blacklist", new HashSet<>());

            CallResponse.Builder response = new CallResponse.Builder();

            if (isNumberInList(phoneNumber, blacklist)) {
                Log.d(TAG, "Number is in blacklist. Rejecting call silently.");
                response.setDisallowCall(true);
                response.setRejectCall(true);
                response.setSkipCallLog(true);
                response.setSkipNotification(true);
                respondToCall(callDetails, response.build());
                return;
            }

            if (isNumberInList(phoneNumber, whitelist)) {
                Log.d(TAG, "Number is in whitelist. Forcing ringer volume to MAX.");
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    try {
                        audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to bypass DND for whitelist: " + e.getMessage());
                        // Fallback: ring our custom loud alarm
                        MyFirebaseService.ringAlarm(this);
                    }
                }
            }

            // For whitelist and normal numbers, allow the call
            respondToCall(callDetails, response.build());
        }
    }

    private boolean isNumberInList(String number, Set<String> list) {
        if (number == null || number.isEmpty()) return false;
        for (String numInList : list) {
            // Simple match: check if the incoming number contains or equals the stored number.
            // E.g. stored "123", incoming "+15551234567" might not match unless we normalize.
            // For simplicity, we check if one contains the other.
            if (number.contains(numInList) || numInList.contains(number)) {
                return true;
            }
        }
        return false;
    }
}
