package com.example.relapseshield;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "daily_alarm_channel";
    public static final String ACTION_DAILY_ALARM = "com.example.relapseshield.ACTION_DAILY_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        showAlarmNotification(context);

        // Reschedule for tomorrow if the daily reminder is still enabled
        boolean on = context.getSharedPreferences("relapse_prefs", Context.MODE_PRIVATE)
                .getBoolean("reminder_on", false);
        if (on) {
            int hour = context.getSharedPreferences("relapse_prefs", Context.MODE_PRIVATE)
                    .getInt("reminder_hour", 20);
            int minute = context.getSharedPreferences("relapse_prefs", Context.MODE_PRIVATE)
                    .getInt("reminder_minute", 0);
            ReminderScheduler.schedule(context, hour, minute);
        }
    }

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.rm_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(ctx.getString(R.string.rm_channel_desc));
            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public static void showAlarmNotification(Context ctx) {
        createChannel(ctx);

        Intent intent = new Intent(ctx, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        Notification publicVersion = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.rm_notif_public_title))
                .build();

        Notification notification = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.rm_notif_title))
                .setContentText(ctx.getString(R.string.rm_notif_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setSound(alarmSound)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManagerCompat.from(ctx).notify(1001, notification);
    }
}
