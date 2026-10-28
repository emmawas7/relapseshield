package com.example.relapseshield;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    // Ugandan mobile numbers: 07 plus 8 digits, or +256 7 plus 8 digits
    private static final Pattern PHONE = Pattern.compile("^(07\\d{8}|\\+2567\\d{8})$");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{6}$");

    TextInputLayout tilName, tilEmail, tilPhone, tilArea, tilKinName, tilKinPhone,
            tilCode, tilPassword, tilConfirm;
    TextInputEditText edtName, edtEmail, edtPhone, edtArea, edtKinName, edtKinPhone,
            edtCode, edtPassword, edtConfirm;
    RadioGroup rgRole;
    Spinner spnKinRelation;
    LinearLayout layoutPatient;
    CheckBox chkConsent;
    TextView txtConsentError, txtGoLogin;
    Button btnRegister;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilArea = findViewById(R.id.tilArea);
        tilKinName = findViewById(R.id.tilKinName);
        tilKinPhone = findViewById(R.id.tilKinPhone);
        tilCode = findViewById(R.id.tilCode);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);
        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtArea = findViewById(R.id.edtArea);
        edtKinName = findViewById(R.id.edtKinName);
        edtKinPhone = findViewById(R.id.edtKinPhone);
        edtCode = findViewById(R.id.edtCode);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirm = findViewById(R.id.edtConfirm);
        rgRole = findViewById(R.id.rgRole);
        spnKinRelation = findViewById(R.id.spnKinRelation);
        layoutPatient = findViewById(R.id.layoutPatient);
        chkConsent = findViewById(R.id.chkConsent);
        txtConsentError = findViewById(R.id.txtConsentError);
        btnRegister = findViewById(R.id.btnRegister);
        txtGoLogin = findViewById(R.id.txtGoLogin);

        // Patients fill in the intake details; counsellors do not
        rgRole.setOnCheckedChangeListener((group, checkedId) ->
                layoutPatient.setVisibility(checkedId == R.id.rbPatient ? View.VISIBLE : View.GONE));

        chkConsent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                txtConsentError.setVisibility(View.GONE);
            }
        });

        btnRegister.setOnClickListener(v -> attemptRegister());

        txtGoLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private String text(TextInputEditText e) {
        return String.valueOf(e.getText()).trim();
    }

    // 07XXXXXXXX becomes +2567XXXXXXXX, so every stored number has one format
    private String normalise(String phone) {
        return phone.startsWith("07") ? "+256" + phone.substring(1) : phone;
    }

    private void attemptRegister() {
        boolean isPatient = rgRole.getCheckedRadioButtonId() == R.id.rbPatient;

        String name = text(edtName);
        String email = text(edtEmail);
        String phone = text(edtPhone).replace(" ", "");
        String area = text(edtArea);
        String kinName = text(edtKinName);
        String kinPhone = text(edtKinPhone).replace(" ", "");
        String code = text(edtCode).toUpperCase(Locale.ROOT);
        String password = String.valueOf(edtPassword.getText());
        String confirm = String.valueOf(edtConfirm.getText());

        // Clear any old errors first
        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilArea.setError(null);
        tilKinName.setError(null);
        tilKinPhone.setError(null);
        tilCode.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        boolean valid = true;

        if (name.isEmpty()) {
            tilName.setError(getString(R.string.rg_error_name_required));
            valid = false;
        } else if (name.length() < 2) {
            tilName.setError(getString(R.string.rg_error_name_short));
            valid = false;
        }

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_email_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email_invalid));
            valid = false;
        }

        if (phone.isEmpty()) {
            tilPhone.setError(getString(R.string.rg_error_phone_required));
            valid = false;
        } else if (!PHONE.matcher(phone).matches()) {
            tilPhone.setError(getString(R.string.rg_error_phone_invalid));
            valid = false;
        }

        if (isPatient) {
            if (area.isEmpty()) {
                tilArea.setError(getString(R.string.rg_error_area_required));
                valid = false;
            }
            if (kinName.isEmpty()) {
                tilKinName.setError(getString(R.string.rg_error_kin_name_required));
                valid = false;
            }
            if (kinPhone.isEmpty()) {
                tilKinPhone.setError(getString(R.string.rg_error_kin_phone_required));
                valid = false;
            } else if (!PHONE.matcher(kinPhone).matches()) {
                tilKinPhone.setError(getString(R.string.rg_error_phone_invalid));
                valid = false;
            }
            if (code.isEmpty()) {
                tilCode.setError(getString(R.string.rg_error_code_required));
                valid = false;
            } else if (!CODE.matcher(code).matches()) {
                tilCode.setError(getString(R.string.rg_error_code_invalid));
                valid = false;
            }
        }

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        } else if (!isStrongPassword(password)) {
            tilPassword.setError(getString(R.string.error_password_weak));
            valid = false;
        }

        if (confirm.isEmpty()) {
            tilConfirm.setError(getString(R.string.error_confirm_required));
            valid = false;
        } else if (!confirm.equals(password)) {
            tilConfirm.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }

        if (!chkConsent.isChecked()) {
            txtConsentError.setVisibility(View.VISIBLE);
            valid = false;
        }

        if (!valid) {
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("phone", normalise(phone));
        if (isPatient) {
            profile.put("role", "patient");
            profile.put("status", "pending");
            profile.put("homeArea", area);
            profile.put("kinName", kinName);
            profile.put("kinPhone", normalise(kinPhone));
            profile.put("kinRelation", String.valueOf(spnKinRelation.getSelectedItem()));
        } else {
            profile.put("role", "counsellor");
            profile.put("approved", false);
            profile.put("clientCount", 0);
        }

        createAccount(profile, email, password, isPatient, code);
    }
    // After a rotation, make sure the patient-only block matches the restored choice
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        layoutPatient.setVisibility(
                rgRole.getCheckedRadioButtonId() == R.id.rbPatient ? View.VISIBLE : View.GONE);
    }

    private void createAccount(Map<String, Object> profile, String email, String password,
                               boolean isPatient, String code) {
        btnRegister.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser fu = result.getUser();
                    if (isPatient) {
                        registerPatient(fu, profile, code);
                    } else {
                        registerCounsellor(fu, profile);
                    }
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        tilEmail.setError(getString(R.string.error_email_taken));
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        tilPassword.setError(getString(R.string.error_password_weak));
                    } else if (e instanceof FirebaseNetworkException) {
                        Toast.makeText(this, R.string.error_network, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.error_register_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // A patient names their counsellor by code. The profile and a pending request are saved
    // together, and the counsellor must confirm before seeing anything.
    private void registerPatient(FirebaseUser fu, Map<String, Object> profile, String code) {
        db.collection("counsellorCodes").document(code).get()
                .addOnSuccessListener(codeDoc -> {
                    if (!codeDoc.exists()) {
                        abort(fu, tilCode, R.string.rg_error_code_invalid);
                        return;
                    }

                    Map<String, Object> request = new HashMap<>();
                    request.put("patientId", fu.getUid());
                    request.put("patientName", profile.get("name"));
                    request.put("patientPhone", profile.get("phone"));
                    request.put("counsellorId", codeDoc.getString("counsellorId"));
                    request.put("counsellorName", codeDoc.getString("counsellorName"));
                    request.put("status", "pending");
                    request.put("createdAt", FieldValue.serverTimestamp());

                    WriteBatch batch = db.batch();
                    batch.set(db.collection("users").document(fu.getUid()), profile);
                    batch.set(db.collection("requests").document(fu.getUid()), request);
                    batch.commit()
                            .addOnSuccessListener(v -> finishRegistration(
                                    String.valueOf(profile.get("email")), R.string.rg_success_patient))
                            .addOnFailureListener(e ->
                                    abort(fu, null, R.string.rg_error_request_failed));
                })
                .addOnFailureListener(e -> abort(fu, null, R.string.error_register_failed));
    }

    // A counsellor gets a personal code. The code lookup entry is saved with the profile.
    private void registerCounsellor(FirebaseUser fu, Map<String, Object> profile) {
        String myCode = newCode();
        profile.put("code", myCode);

        Map<String, Object> codeEntry = new HashMap<>();
        codeEntry.put("counsellorId", fu.getUid());
        codeEntry.put("counsellorName", profile.get("name"));

        WriteBatch batch = db.batch();
        batch.set(db.collection("users").document(fu.getUid()), profile);
        batch.set(db.collection("counsellorCodes").document(myCode), codeEntry);
        batch.commit()
                .addOnSuccessListener(v -> finishRegistration(
                        String.valueOf(profile.get("email")), R.string.rg_success_counsellor))
                .addOnFailureListener(e -> abort(fu, null, R.string.error_register_failed));
    }

    // Remove the half-made account so the user can try again cleanly
    private void abort(FirebaseUser fu, TextInputLayout field, int messageRes) {
        fu.delete();
        btnRegister.setEnabled(true);
        if (field != null) {
            field.setError(getString(messageRes));
        } else {
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        }
    }

    private void finishRegistration(String email, int messageRes) {
        auth.signOut();
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.putExtra("email", email);
        startActivity(intent);
        finish();
    }

    // Six characters, leaving out look-alikes such as 0, O, 1 and I
    private String newCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // At least 8 characters, with at least one letter and one digit
    private boolean isStrongPassword(String password) {
        if (password.length() < 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }
}