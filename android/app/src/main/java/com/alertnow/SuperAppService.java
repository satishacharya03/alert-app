package com.alertnow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class SuperAppService extends Service implements SensorEventListener {

    private static final String TAG = "SuperAppService";
    private SensorManager sensorManager;
    private float mAccel; 
    private float mAccelCurrent;
    private float mAccelLast;
    
    private boolean batterySosSent = false;
    private long lastShakeTime = 0;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float) scale;
            
            if (batteryPct <= 5.0 && !batterySosSent) {
                sendSos("Battery is critically low (" + batteryPct + "%). Phone may die soon!");
                batterySosSent = true;
            } else if (batteryPct > 10.0) {
                batterySosSent = false;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Setup shake detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        mAccel = 10f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        // Register battery receiver
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        startForegroundService();
    }

    private void startForegroundService() {
        String channelId = "super_app_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Super App Background", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("AlertNow Super App Active")
                .setContentText("Monitoring for Shake & Low Battery SOS")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();

        startForeground(2001, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        unregisterReceiver(batteryReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        mAccelLast = mAccelCurrent;
        mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
        float delta = mAccelCurrent - mAccelLast;
        mAccel = mAccel * 0.9f + delta;

        // Shake detected
        if (mAccel > 20) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastShakeTime) > 5000) { // 5 seconds cooldown
                lastShakeTime = currentTime;
                sendSos("I am in an emergency! (Triggered by Shake-to-Alert)");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void sendSos(String message) {
        SharedPreferences prefs = getSharedPreferences("super_settings", Context.MODE_PRIVATE);
        String sosContact = prefs.getString("sos_contact", "");
        
        if (!sosContact.isEmpty()) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(sosContact, null, message, null, null);
                Log.d(TAG, "SOS SMS sent to " + sosContact);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SOS: " + e.getMessage());
            }
        }
    }
}
