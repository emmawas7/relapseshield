package com.example.relapseshield;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("relapse_prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("reminder_on", false)) {
                int hour = prefs.getInt("reminder_hour", 20);
                int minute = prefs.getInt("reminder_minute", 0);
                ReminderScheduler.schedule(context, hour, minute);
            }
        }
    }
}
