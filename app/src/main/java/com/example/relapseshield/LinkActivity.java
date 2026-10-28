package com.example.relapseshield;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class LinkActivity extends AppCompatActivity {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{6}$");

    TextView txtStatus, txtLogout, txtRefresh;
    TextInputLayout tilCode;
    TextInputEditText edtCode;
    Button btnSend;

    FirebaseAuth auth;
    FirebaseFirestore db;
    String myName, myPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_link);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        txtStatus = findViewById(R.id.txtStatus);
        txtLogout = findViewById(R.id.txtLogout);
        txtRefresh = findViewById(R.id.txtRefresh);
        tilCode = findViewById(R.id.tilCode);
        edtCode = findViewById(R.id.edtCode);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> sendRequest());
        txtRefresh.setOnClickListener(v -> refresh());

        txtLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(LinkActivity.this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // Has the counsellor confirmed yet? If so, go to Home; if not, show where things stand.
    private void refresh() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(profile -> {
                    if (profile.getString("counsellorId") != null) {
                        Intent intent = new Intent(LinkActivity.this, HomeActivity.class);
                        intent.putExtra("name", profile.getString("name"));
                        intent.putExtra("role", "patient");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        return;
                    }
                    myName = profile.getString("name");
                    myPhone = profile.getString("phone");
                    db.collection("requests").document(me.getUid()).get()
                            .addOnSuccessListener(this::showStatus);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_checkins, Toast.LENGTH_LONG).show());
    }

    private void showStatus(DocumentSnapshot req) {
        boolean canSend;
        if (req.exists()) {
            String counsellor = req.getString("counsellorName");
            if ("declined".equals(req.getString("status"))) {
                txtStatus.setText(getString(R.string.lk_status_declined, counsellor));
                canSend = true;
            } else {
                txtStatus.setText(getString(R.string.lk_status_pending, counsellor));
                canSend = false;
            }
        } else {
            txtStatus.setText(R.string.lk_status_none);
            canSend = true;
        }
        tilCode.setEnabled(canSend);
        btnSend.setEnabled(canSend);
    }

    private void sendRequest() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        String code = String.valueOf(edtCode.getText()).trim().toUpperCase(Locale.ROOT);
        tilCode.setError(null);
        if (code.isEmpty()) {
            tilCode.setError(getString(R.string.rg_error_code_required));
            return;
        }
        if (!CODE.matcher(code).matches()) {
            tilCode.setError(getString(R.string.rg_error_code_invalid));
            return;
        }

        btnSend.setEnabled(false);
        db.collection("counsellorCodes").document(code).get()
                .addOnSuccessListener(codeDoc -> {
                    if (!codeDoc.exists()) {
                        btnSend.setEnabled(true);
                        tilCode.setError(getString(R.string.rg_error_code_invalid));
                        return;
                    }
                    Map<String, Object> request = new HashMap<>();
                    request.put("patientId", me.getUid());
                    request.put("patientName", myName);
                    request.put("patientPhone", myPhone);
                    request.put("counsellorId", codeDoc.getString("counsellorId"));
                    request.put("counsellorName", codeDoc.getString("counsellorName"));
                    request.put("status", "pending");
                    request.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("requests").document(me.getUid()).set(request)
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, R.string.lk_sent, Toast.LENGTH_SHORT).show();
                                refresh();
                            })
                            .addOnFailureListener(e -> {
                                btnSend.setEnabled(true);
                                Toast.makeText(this, R.string.lk_failed, Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, R.string.lk_failed, Toast.LENGTH_LONG).show();
                });
    }
}