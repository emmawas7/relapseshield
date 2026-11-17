package com.example.relapseshield;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class ReminderScheduler {

    public static void schedule(Context ctx, int hour, int minute) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }

        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_DAILY_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAtMillis = delayUntil(hour, minute);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException e) {
            Log.e("ReminderScheduler", "Exact alarm not allowed, fallback to setAndAllowWhileIdle", e);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }

        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_DAILY_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    public static void sendTestNow(Context ctx) {
        ReminderReceiver.showAlarmNotification(ctx);
    }

    private static long delayUntil(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis();
    }
}
