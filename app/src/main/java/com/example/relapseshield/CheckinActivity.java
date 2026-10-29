package com.example.relapseshield;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CheckinActivity extends AppCompatActivity {

    Slider sliderMood;
    TextView txtTitle, txtMoodLabel, txtCancel;
    RadioGroup rgCravings;
    CheckBox chkTriggered;
    TextInputLayout tilNote;
    TextInputEditText edtNote;
    Button btnSaveCheckin;

    FirebaseAuth auth;
    FirebaseFirestore db;

    // null when creating a new check-in; the document's ID when editing one
    String editId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_checkin);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        txtTitle = findViewById(R.id.txtTitle);
        txtCancel = findViewById(R.id.txtCancel);
        txtMoodLabel = findViewById(R.id.txtMoodLabel);
        sliderMood = findViewById(R.id.sliderMood);
        rgCravings = findViewById(R.id.rgCravings);
        chkTriggered = findViewById(R.id.chkTriggered);
        tilNote = findViewById(R.id.tilNote);
        edtNote = findViewById(R.id.edtNote);
        btnSaveCheckin = findViewById(R.id.btnSaveCheckin);

        // Edit mode: Home passes the check-in's ID. Load it only on a fresh start,
        // so a rotation does not wipe what the user has changed.
        editId = getIntent().getStringExtra("checkinId");
        if (editId != null) {
            txtTitle.setText(R.string.ce_title_edit);
            btnSaveCheckin.setText(R.string.ce_save_changes);
            if (savedInstanceState == null) {
                loadForEdit();
            }
        }

        updateMoodLabel((int) sliderMood.getValue());
        sliderMood.addOnChangeListener((slider, value, fromUser) ->
                updateMoodLabel((int) value));

        btnSaveCheckin.setOnClickListener(v -> saveCheckin());
        txtCancel.setOnClickListener(v -> finish());
    }

    private void updateMoodLabel(int mood) {
        txtMoodLabel.setText(getString(R.string.ce_mood_label_format, mood));
    }

    // Fill the form with the saved values
    private void loadForEdit() {
        db.collection("checkins").document(editId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        finish();
                        return;
                    }
                    Long mood = doc.getLong("mood");
                    if (mood != null) {
                        sliderMood.setValue(Math.max(1, Math.min(5, mood.floatValue())));
                    }
                    Long cr = doc.getLong("cravings");
                    int c = (cr == null) ? 0 : cr.intValue();
                    rgCravings.check(c == 1 ? R.id.rbCravLow
                            : c == 2 ? R.id.rbCravMedium
                              : c == 3 ? R.id.rbCravHigh : R.id.rbCravNone);
                    chkTriggered.setChecked(Boolean.TRUE.equals(doc.getBoolean("triggered")));
                    edtNote.setText(doc.getString("note"));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void saveCheckin() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int mood = (int) sliderMood.getValue();
        int cravings = cravingLevel();
        boolean triggered = chkTriggered.isChecked();
        String note = String.valueOf(edtNote.getText()).trim();

        tilNote.setError(null);
        if (note.length() > 500) {
            tilNote.setError(getString(R.string.ce_error_note_long));
            return;
        }

        btnSaveCheckin.setEnabled(false);
        if (editId != null) {
            updateCheckin(mood, cravings, triggered, note);
        } else {
            createCheckin(user, mood, cravings, triggered, note);
        }
    }

    // Editing marks the check-in as unreviewed again and records when it was edited,
    // so the counsellor can see that it changed.
    private void updateCheckin(int mood, int cravings, boolean triggered, String note) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("mood", mood);
        changes.put("cravings", cravings);
        changes.put("triggered", triggered);
        changes.put("note", note);
        changes.put("reviewed", false);
        changes.put("editedAt", FieldValue.serverTimestamp());

        db.collection("checkins").document(editId).update(changes)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.ce_updated, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveCheckin.setEnabled(true);
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                });
    }

    // New check-ins are stamped with the patient's counsellor so they reach the right dashboard
    private void createCheckin(FirebaseUser user, int mood, int cravings,
                               boolean triggered, String note) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String counsellorId = profile.getString("counsellorId");
                    if (counsellorId == null) {
                        btnSaveCheckin.setEnabled(true);
                        Toast.makeText(this, R.string.ce_not_linked, Toast.LENGTH_LONG).show();
                        return;
                    }

                    Map<String, Object> checkin = new HashMap<>();
                    checkin.put("userId", user.getUid());
                    checkin.put("counsellorId", counsellorId);
                    checkin.put("mood", mood);
                    checkin.put("cravings", cravings);
                    checkin.put("triggered", triggered);
                    checkin.put("note", note);
                    checkin.put("reviewed", false);
                    checkin.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("checkins").add(checkin)
                            .addOnSuccessListener(ref -> {
                                Toast.makeText(this, R.string.checkin_saved,
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSaveCheckin.setEnabled(true);
                                Toast.makeText(this, R.string.error_checkin_failed,
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSaveCheckin.setEnabled(true);
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                });
    }

    private int cravingLevel() {
        int id = rgCravings.getCheckedRadioButtonId();
        if (id == R.id.rbCravLow) {
            return 1;
        } else if (id == R.id.rbCravMedium) {
            return 2;
        } else if (id == R.id.rbCravHigh) {
            return 3;
        }
        return 0;
    }
}
