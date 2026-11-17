package com.example.relapseshield;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;

// The background job that shows the reminder. WorkManager runs it at the chosen time.
public class ReminderWorker extends Worker {

    public static final String CHANNEL_ID = "daily_checkin";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    // Android 8 and later need a notification channel
    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    ctx.getString(R.string.rm_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(ctx.getString(R.string.rm_channel_desc));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission") // checked just below
    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        // Only remind someone who is signed in on this phone
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return Result.success();
        }
        // From Android 13 the user must have allowed notifications
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        createChannel(ctx);

        // Tapping the reminder opens the app (which goes straight in if Remember me is on)
        Intent intent = new Intent(ctx, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // On a locked screen only a neutral "Reminder" shows, so nothing private is exposed
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
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        NotificationManagerCompat.from(ctx).notify(1001, notification);
        return Result.success();
    }
}