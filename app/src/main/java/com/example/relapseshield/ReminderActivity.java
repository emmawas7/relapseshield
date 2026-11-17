package com.example.relapseshield;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;

public class ReminderActivity extends AppCompatActivity {

    TextView txtBack, txtTime, txtStatus;
    SwitchCompat switchReminder;
    Button btnPickTime, btnSettings, btnTest;

    SharedPreferences prefs;
    boolean ignoreSwitch = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            enableReminder();
                        } else {
                            Toast.makeText(this, R.string.rm_denied, Toast.LENGTH_LONG).show();
                            refresh();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reminder);

        prefs = getSharedPreferences("relapse_prefs", MODE_PRIVATE);
        ReminderReceiver.createChannel(this);

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        txtBack = findViewById(R.id.txtBack);
        txtTime = findViewById(R.id.txtTime);
        txtStatus = findViewById(R.id.txtStatus);
        switchReminder = findViewById(R.id.switchReminder);
        btnPickTime = findViewById(R.id.btnPickTime);
        btnSettings = findViewById(R.id.btnSettings);
        btnTest = findViewById(R.id.btnTest);

        txtBack.setOnClickListener(v -> finish());

        switchReminder.setOnCheckedChangeListener((button, checked) -> {
            if (ignoreSwitch) {
                return;
            }
            if (!checked) {
                disableReminder();
            } else {
                if (Build.VERSION.SDK_INT >= 33 && !canNotify()) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    enableReminder();
                }
            }
        });

        btnPickTime.setOnClickListener(v -> {
            int hour = prefs.getInt("reminder_hour", 20);
            int minute = prefs.getInt("reminder_minute", 0);
            new TimePickerDialog(this, (view, h, m) -> {
                prefs.edit().putInt("reminder_hour", h).putInt("reminder_minute", m).apply();
                if (prefs.getBoolean("reminder_on", false)) {
                    ReminderScheduler.schedule(this, h, m);
                }
                refresh();
            }, hour, minute, true).show();
        });

        btnSettings.setOnClickListener(v -> openNotificationSettings());

        btnTest.setOnClickListener(v -> {
            if (canNotify()) {
                ReminderScheduler.sendTestNow(this);
                Toast.makeText(this, R.string.rm_test_sent, Toast.LENGTH_SHORT).show();
            } else if (Build.VERSION.SDK_INT >= 33) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Toast.makeText(this, R.string.rm_denied, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean canNotify() {
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private void setSwitch(boolean on) {
        ignoreSwitch = true;
        switchReminder.setChecked(on);
        ignoreSwitch = false;
    }

    private void refresh() {
        int hour = prefs.getInt("reminder_hour", 20);
        int minute = prefs.getInt("reminder_minute", 0);
        boolean on = prefs.getBoolean("reminder_on", false);
        txtTime.setText(getString(R.string.rm_time_format, hour, minute));

        setSwitch(on);

        if (!canNotify()) {
            txtStatus.setText(R.string.rm_denied);
            btnSettings.setVisibility(View.VISIBLE);
        } else {
            txtStatus.setText(on ? getString(R.string.rm_enabled_format, hour, minute)
                    : getString(R.string.rm_disabled));
            btnSettings.setVisibility(View.GONE);
        }
    }

    private void enableReminder() {
        prefs.edit().putBoolean("reminder_on", true).apply();
        ReminderScheduler.schedule(this, prefs.getInt("reminder_hour", 20),
                prefs.getInt("reminder_minute", 0));
        refresh();
        Toast.makeText(this, R.string.rm_alarm_enabled, Toast.LENGTH_SHORT).show();
    }

    private void disableReminder() {
        prefs.edit().putBoolean("reminder_on", false).apply();
        ReminderScheduler.cancel(this);
        refresh();
        Toast.makeText(this, R.string.rm_alarm_disabled, Toast.LENGTH_SHORT).show();
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }
}
