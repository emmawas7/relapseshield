package com.example.relapseshield;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmergencyActivity extends AppCompatActivity {

    // One cycle is 4 s in + 6 s out = 10 s, and 6 cycles make one minute
    private static final int TOTAL_SECONDS = 60;
    private static final int IN_SECONDS = 4;
    private static final int OUT_SECONDS = 6;
    private static final int CYCLE = IN_SECONDS + OUT_SECONDS;

    TextView txtBack, txtBreath, txtTotal, txtAlertStatus, txtAlertNote;
    View viewCircle;
    Button btnDial, btnBreathe, btnAlert;

    FirebaseAuth auth;
    FirebaseFirestore db;
    CountDownTimer timer;

    boolean exerciseDone = false; // Step 1 finished: unlocks Step 2
    boolean alertOpen = false;    // an alert from this patient is already waiting
    int lastPhase = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emergency);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        txtBack = findViewById(R.id.txtBack);
        txtBreath = findViewById(R.id.txtBreath);
        txtTotal = findViewById(R.id.txtTotal);
        txtAlertNote = findViewById(R.id.txtAlertNote);
        viewCircle = findViewById(R.id.viewCircle);
        btnDial = findViewById(R.id.btnDial);
        btnBreathe = findViewById(R.id.btnBreathe);
        btnAlert = findViewById(R.id.btnAlert);

        // After a rotation, remember that the exercise was already finished
        if (savedInstanceState != null) {
            exerciseDone = savedInstanceState.getBoolean("exerciseDone", false);
        }

        resetCircle();
        btnDial.setText(getString(R.string.em_call_format, getString(R.string.em_number)));
        updateAlertButton();

        txtBack.setOnClickListener(v -> finish());
        btnDial.setOnClickListener(v -> dialEmergency());
        btnBreathe.setOnClickListener(v -> startBreathing());
        btnAlert.setOnClickListener(v -> sendAlert());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("exerciseDone", exerciseDone);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlertStatus();
    }

    // Lifecycle: stop the exercise when the screen leaves the front, so it never runs unseen
    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) {
            timer.cancel();
            timer = null;
            btnBreathe.setEnabled(true);
            txtBreath.setText(R.string.em_breath_ready);
            txtTotal.setText("");
            resetCircle();
        }
    }

    // Opens the dialler with the number filled in. The patient presses the green button.
    // This is never locked: in real danger nobody should wait.
    private void dialEmergency() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.fromParts("tel", getString(R.string.em_number), null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.em_no_dialler, Toast.LENGTH_LONG).show();
        }
    }

    private void resetCircle() {
        viewCircle.animate().cancel();
        viewCircle.setScaleX(0.5f);
        viewCircle.setScaleY(0.5f);
        lastPhase = -1;
    }

    // Step 1: one minute of guided breathing with an animated circle
    private void startBreathing() {
        if (timer != null) {
            timer.cancel();
        }
        exerciseDone = false;
        updateAlertButton();
        resetCircle();
        btnBreathe.setEnabled(false);

        timer = new CountDownTimer(TOTAL_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                int elapsed = TOTAL_SECONDS - remaining;
                int pos = elapsed % CYCLE;

                int phase;
                int left;
                String label;
                if (pos < IN_SECONDS) {
                    phase = 0;
                    left = IN_SECONDS - pos;
                    label = getString(R.string.em_breathe_in);
                } else {
                    phase = 1;
                    left = CYCLE - pos;
                    label = getString(R.string.em_breathe_out);
                }

                // Start the animation only when the phase changes
                if (phase != lastPhase) {
                    lastPhase = phase;
                    if (phase == 0) {
                        viewCircle.animate().scaleX(1.0f).scaleY(1.0f)
                                .setDuration(IN_SECONDS * 1000L).start();
                    } else {
                        viewCircle.animate().scaleX(0.5f).scaleY(0.5f)
                                .setDuration(OUT_SECONDS * 1000L).start();
                    }
                }

                txtBreath.setText(getString(R.string.em_circle_format, label, left));
                txtTotal.setText(getString(R.string.em_total_left_format, remaining));
            }

            @Override
            public void onFinish() {
                timer = null;
                exerciseDone = true;
                resetCircle();
                txtBreath.setText(R.string.em_breath_done);
                txtTotal.setText("");
                btnBreathe.setText(R.string.em_breath_again);
                btnBreathe.setEnabled(true);
                updateAlertButton();
            }
        }.start();
    }

    // Step 2 is available only after Step 1, and only if no alert is already waiting
    private void updateAlertButton() {
        btnAlert.setEnabled(exerciseDone && !alertOpen);
        if (alertOpen) {
            txtAlertNote.setText("");
        } else if (exerciseDone) {
            txtAlertNote.setText(R.string.em_alert_unlocked);
        } else {
            txtAlertNote.setText(R.string.em_alert_locked);
        }
    }

    // Show whether an alert is already open for this patient
    private void loadAlertStatus() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        db.collection("alerts").document(me.getUid()).get()
                .addOnSuccessListener(alert -> {
                    alertOpen = alert.exists() && "open".equals(alert.getString("status"));
                    if (alertOpen) {
                        Timestamp t = alert.getTimestamp("createdAt");
                        String when = (t == null) ? "" : new SimpleDateFormat("HH:mm",
                                Locale.getDefault()).format(t.toDate());
                        txtAlertNote.setText(getString(R.string.em_alerted_format, when));
                    }
                    updateAlertButton();
                    if (alertOpen) {
                        txtAlertNote.setText(getString(R.string.em_alerted_format,
                                alert.getTimestamp("createdAt") == null ? ""
                                        : new SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(alert.getTimestamp("createdAt").toDate())));
                    }
                })
                .addOnFailureListener(e -> Log.e("Emergency", "Could not read alert status", e));
    }

    // One open alert per patient. A new one is possible once the counsellor marks it handled.
    private void sendAlert() {
        if (!exerciseDone) {
            Toast.makeText(this, R.string.em_alert_locked_toast, Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        btnAlert.setEnabled(false);
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(profile -> createAlert(me, profile))
                .addOnFailureListener(this::alertFailed);
    }

    private void createAlert(FirebaseUser me, DocumentSnapshot profile) {
        String counsellorId = profile.getString("counsellorId");
        if (counsellorId == null) {
            alertFailed(new IllegalStateException("Patient has no counsellorId"));
            return;
        }
        Map<String, Object> alert = new HashMap<>();
        alert.put("patientId", me.getUid());
        alert.put("patientName", profile.getString("name"));
        alert.put("counsellorId", counsellorId);
        alert.put("counsellorName", profile.getString("counsellorName"));
        alert.put("status", "open");
        alert.put("createdAt", FieldValue.serverTimestamp());

        db.collection("alerts").document(me.getUid()).set(alert)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.em_alert_sent, Toast.LENGTH_LONG).show();
                    loadAlertStatus();
                })
                .addOnFailureListener(this::alertFailed);
    }

    // The real reason goes to Logcat (search the tag "Emergency")
    private void alertFailed(Exception e) {
        Log.e("Emergency", "Alert failed", e);
        updateAlertButton();
        Toast.makeText(this, R.string.em_alert_failed, Toast.LENGTH_LONG).show();
    }
}