package com.alertnow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * BootReceiver — Restarts the SuperAppService automatically after device reboot.
 * The RECEIVE_BOOT_COMPLETED permission is declared in the manifest.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, SuperAppService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
