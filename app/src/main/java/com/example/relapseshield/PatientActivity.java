package com.example.relapseshield;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientActivity extends AppCompatActivity {

    TextView txtBack, txtPatientName, txtReasons, txtPhone, txtArea, txtKin, txtCheckins;
    Button btnCall, btnMessage, btnCallKin, btnReviewed;

    FirebaseAuth auth;
    FirebaseFirestore db;

    String patientId, patientName, phone, kinPhone;
    Timestamp assignedAt;
    boolean viewLogged = false;
    final List<DocumentSnapshot> checkins = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_patient);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        // The dashboard passes the patient's ID and name in the Intent
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null) {
            finish();
            return;
        }

        txtBack = findViewById(R.id.txtBack);
        txtPatientName = findViewById(R.id.txtPatientName);
        txtReasons = findViewById(R.id.txtReasons);
        txtPhone = findViewById(R.id.txtPhone);
        txtArea = findViewById(R.id.txtArea);
        txtKin = findViewById(R.id.txtKin);
        txtCheckins = findViewById(R.id.txtCheckins);
        btnCall = findViewById(R.id.btnCall);
        btnMessage = findViewById(R.id.btnMessage);
        btnCallKin = findViewById(R.id.btnCallKin);
        btnReviewed = findViewById(R.id.btnReviewed);

        txtPatientName.setText(getIntent().getStringExtra("patientName"));

        txtBack.setOnClickListener(v -> finish());
        btnCall.setOnClickListener(v -> dial(phone, "call"));
        btnCallKin.setOnClickListener(v -> dial(kinPhone, "call_kin"));
        btnMessage.setOnClickListener(v -> message());
        btnReviewed.setOnClickListener(v -> markReviewed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (patientId != null) {
            loadPatient();
        }
    }

    private void loadPatient() {
        db.collection("users").document(patientId).get()
                .addOnSuccessListener(p -> {
                    patientName = p.getString("name");
                    phone = p.getString("phone");
                    kinPhone = p.getString("kinPhone");
                    assignedAt = p.getTimestamp("assignedAt");

                    txtPatientName.setText(patientName);
                    txtPhone.setText(getString(R.string.pt_phone_format, phone));
                    txtArea.setText(getString(R.string.pt_area_format, p.getString("homeArea")));
                    txtKin.setText(getString(R.string.pt_kin_format,
                            p.getString("kinName"), p.getString("kinRelation"), kinPhone));

                    // Record once per visit that this counsellor opened the contact details
                    if (!viewLogged) {
                        viewLogged = true;
                        logAccess("view");
                    }
                    loadCheckins();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.pt_error_load, Toast.LENGTH_LONG).show());
    }

    // Only this counsellor's check-ins for this patient
    private void loadCheckins() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        db.collection("checkins")
                .whereEqualTo("counsellorId", me.getUid())
                .whereEqualTo("userId", patientId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    checkins.clear();
                    checkins.addAll(snapshot.getDocuments());
                    checkins.sort(FlagRules::newestFirst);

                    StringBuilder sb = new StringBuilder();
                    int n = Math.min(10, checkins.size());
                    if (n == 0) {
                        sb.append(getString(R.string.db_no_checkins));
                    }
                    for (int i = 0; i < n; i++) {
                        if (i > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(FlagRules.describe(this, checkins.get(i), true));
                    }
                    txtCheckins.setText(sb.toString());

                    DocumentSnapshot latest = checkins.isEmpty() ? null : checkins.get(0);
                    List<String> reasons = FlagRules.reasons(this, latest, assignedAt);
                    txtReasons.setText(reasons.isEmpty()
                            ? getString(R.string.pt_no_flags)
                            : getString(R.string.pt_flags_format, TextUtils.join(", ", reasons)));

                    boolean anyUnreviewed = false;
                    for (DocumentSnapshot c : checkins) {
                        if (!Boolean.TRUE.equals(c.getBoolean("reviewed"))) {
                            anyUnreviewed = true;
                        }
                    }
                    btnReviewed.setEnabled(anyUnreviewed);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.pt_error_load, Toast.LENGTH_LONG).show());
    }

    // Opens the phone's dialler with the number already filled in. The counsellor presses
    // the green button; the app never places a call by itself, so no permission is needed.
    private void dial(String number, String action) {
        if (number == null || number.isEmpty()) {
            return;
        }
        logAccess(action);
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_error_no_app, Toast.LENGTH_LONG).show();
        }
    }

    // Opens the messaging app addressed to the patient, with an editable opening line
    private void message() {
        if (phone == null || phone.isEmpty()) {
            return;
        }
        logAccess("message");
        String first = (patientName == null) ? "" : patientName.split(" ")[0];
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null));
        intent.putExtra("sms_body", getString(R.string.pt_msg_body, first));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.pt_error_no_app, Toast.LENGTH_LONG).show();
        }
    }

    // Accountability: who looked at or contacted which patient, and when
    private void logAccess(String action) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("counsellorId", me.getUid());
        entry.put("patientId", patientId);
        entry.put("action", action);
        entry.put("createdAt", FieldValue.serverTimestamp());
        db.collection("accessLog").add(entry);
    }

    private void markReviewed() {
        WriteBatch batch = db.batch();
        int changes = 0;
        for (DocumentSnapshot c : checkins) {
            if (!Boolean.TRUE.equals(c.getBoolean("reviewed"))) {
                batch.update(c.getReference(), "reviewed", true);
                changes++;
            }
        }
        if (changes == 0) {
            return;
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.pt_reviewed_done, Toast.LENGTH_SHORT).show();
                    loadCheckins();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.pt_error_load, Toast.LENGTH_LONG).show());
    }
}