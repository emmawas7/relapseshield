package com.example.relapseshield;


import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CheckinActivity extends AppCompatActivity {

    Slider sliderMood;
    TextView txtMoodLabel, txtCancel;
    RadioGroup rgCravings;
    CheckBox chkTriggered;
    TextInputEditText edtNote;
    Button btnSaveCheckin;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_checkin);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Keep the header behind the status bar and the form clear of the navigation bar
        View root = findViewById(R.id.main);
        View header = findViewById(R.id.layoutHeader);
        View content = findViewById(R.id.layoutContent);
        int headerTop = header.getPaddingTop();
        int contentBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), contentBottom + bars.bottom);
            return insets;
        });

        // Connect each view to the layout by its ID
        sliderMood = findViewById(R.id.sliderMood);
        txtMoodLabel = findViewById(R.id.txtMoodLabel);
        rgCravings = findViewById(R.id.rgCravings);
        chkTriggered = findViewById(R.id.chkTriggered);
        edtNote = findViewById(R.id.edtNote);
        btnSaveCheckin = findViewById(R.id.btnSaveCheckin);
        txtCancel = findViewById(R.id.txtCancel);

        // Show the chosen mood next to the label, starting at the slider's default
        updateMoodLabel((int) sliderMood.getValue());
        sliderMood.addOnChangeListener((slider, value, fromUser) ->
                updateMoodLabel((int) value));

        btnSaveCheckin.setOnClickListener(v -> saveCheckin());
        txtCancel.setOnClickListener(v -> finish());
    }

    private void updateMoodLabel(int mood) {
        txtMoodLabel.setText(getString(R.string.checkin_mood_label_format, mood));
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
        // Validate before saving
        if (rgCravings.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, R.string.ck_error_cravings_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (note.length() > 500) {
            edtNote.setError(getString(R.string.ck_error_note_long));
            return;
        }
        btnSaveCheckin.setEnabled(false);

        // Each check-in is stamped with the patient's counsellor so it reaches the right dashboard
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String counsellorId = profile.getString("counsellorId");
                    if (counsellorId == null) {
                        btnSaveCheckin.setEnabled(true);
                        Toast.makeText(this, R.string.ck_error_not_linked,
                                Toast.LENGTH_LONG).show();
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
                    Toast.makeText(this, R.string.error_checkin_failed,
                            Toast.LENGTH_LONG).show();
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