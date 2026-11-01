package com.example.relapseshield;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

        // Find the patient's counsellor, then that counsellor's flag words
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String counsellorId = profile.getString("counsellorId");
                    if (counsellorId == null) {
                        btnSaveCheckin.setEnabled(true);
                        Toast.makeText(this, R.string.ce_not_linked, Toast.LENGTH_LONG).show();
                        return;
                    }
                    loadWords(counsellorId, words -> {
                        List<String> urgent = FlagRules.matchWords(note, words, "urgent");
                        List<String> watch = FlagRules.matchWords(note, words, "watch");
                        if (editId != null) {
                            updateCheckin(user, profile, mood, cravings, triggered, note,
                                    urgent, watch);
                        } else {
                            createCheckin(user, profile, mood, cravings, triggered, note,
                                    urgent, watch);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    btnSaveCheckin.setEnabled(true);
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                });
    }

    // If the list cannot be loaded, the check-in is still saved, just without word matching
    private void loadWords(String counsellorId, Consumer<List<FlagRules.Word>> done) {
        db.collection("flagWords").whereEqualTo("counsellorId", counsellorId).get()
                .addOnSuccessListener(snapshot -> {
                    List<FlagRules.Word> words = new ArrayList<>();
                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        String w = d.getString("word");
                        String level = d.getString("level");
                        if (w != null && level != null) {
                            words.add(new FlagRules.Word(w, level));
                        }
                    }
                    done.accept(words);
                })
                .addOnFailureListener(e -> {
                    Log.e("FlagWords", "Could not load the word list", e);
                    done.accept(new ArrayList<>());
                });
    }

    private void createCheckin(FirebaseUser user, DocumentSnapshot profile, int mood,
                               int cravings, boolean triggered, String note,
                               List<String> urgent, List<String> watch) {
        Map<String, Object> checkin = new HashMap<>();
        checkin.put("userId", user.getUid());
        checkin.put("counsellorId", profile.getString("counsellorId"));
        checkin.put("mood", mood);
        checkin.put("cravings", cravings);
        checkin.put("triggered", triggered);
        checkin.put("note", note);
        checkin.put("urgentWords", urgent);
        checkin.put("watchWords", watch);
        checkin.put("reviewed", false);
        checkin.put("createdAt", FieldValue.serverTimestamp());

        db.collection("checkins").add(checkin)
                .addOnSuccessListener(ref -> afterSave(user, profile, urgent, R.string.checkin_saved))
                .addOnFailureListener(e -> {
                    btnSaveCheckin.setEnabled(true);
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                });
    }

    // Editing marks the check-in as unreviewed again and records when it was edited
    private void updateCheckin(FirebaseUser user, DocumentSnapshot profile, int mood,
                               int cravings, boolean triggered, String note,
                               List<String> urgent, List<String> watch) {
        Map<String, Object> changes = new HashMap<>();
        changes.put("mood", mood);
        changes.put("cravings", cravings);
        changes.put("triggered", triggered);
        changes.put("note", note);
        changes.put("urgentWords", urgent);
        changes.put("watchWords", watch);
        changes.put("reviewed", false);
        changes.put("editedAt", FieldValue.serverTimestamp());

        db.collection("checkins").document(editId).update(changes)
                .addOnSuccessListener(v -> afterSave(user, profile, urgent, R.string.ce_updated))
                .addOnFailureListener(e -> {
                    btnSaveCheckin.setEnabled(true);
                    Toast.makeText(this, R.string.error_checkin_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void afterSave(FirebaseUser user, DocumentSnapshot profile,
                           List<String> urgent, int savedMessage) {
        Toast.makeText(this, savedMessage, Toast.LENGTH_SHORT).show();
        if (urgent.isEmpty()) {
            finish();
        } else {
            raiseUrgentAlert(user, profile, urgent);
        }
    }

    // Urgent wording raises an alert for the counsellor (unless one is already open),
    // and the patient is shown help straight away
    private void raiseUrgentAlert(FirebaseUser user, DocumentSnapshot profile,
                                  List<String> urgent) {
        DocumentReference ref = db.collection("alerts").document(user.getUid());
        ref.get().addOnSuccessListener(existing -> {
            if (existing.exists() && "open".equals(existing.getString("status"))) {
                showHelp();
                return;
            }
            Map<String, Object> alert = new HashMap<>();
            alert.put("patientId", user.getUid());
            alert.put("patientName", profile.getString("name"));
            alert.put("counsellorId", profile.getString("counsellorId"));
            alert.put("counsellorName", profile.getString("counsellorName"));
            alert.put("status", "open");
            alert.put("source", "checkin");
            alert.put("words", urgent);
            alert.put("createdAt", FieldValue.serverTimestamp());
            ref.set(alert)
                    .addOnSuccessListener(v -> showHelp())
                    .addOnFailureListener(e -> {
                        Log.e("FlagWords", "Could not raise the alert", e);
                        showHelp();
                    });
        }).addOnFailureListener(e -> {
            Log.e("FlagWords", "Could not read the alert", e);
            showHelp();
        });
    }

    // Shown after a check-in with urgent wording; the patient must choose to continue
    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.fw_help_title)
                .setMessage(R.string.fw_help_message)
                .setCancelable(false)
                .setPositiveButton(R.string.fw_help_support, (d, w) -> {
                    startActivity(new Intent(CheckinActivity.this, EmergencyActivity.class));
                    finish();
                })
                .setNegativeButton(getString(R.string.em_call_format,
                        getString(R.string.em_number)), (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_DIAL,
                                Uri.fromParts("tel", getString(R.string.em_number), null)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.em_no_dialler, Toast.LENGTH_LONG).show();
                    }
                    finish();
                })
                .setNeutralButton(R.string.fw_help_close, (d, w) -> finish())
                .show();
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