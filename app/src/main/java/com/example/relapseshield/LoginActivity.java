package com.example.relapseshield;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    TextInputLayout tilEmail, tilPassword;
    TextInputEditText edtEmail, edtPassword;
    CheckBox chkRemember;
    Button btnLogin;
    TextView txtGoRegister;

    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

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
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        chkRemember = findViewById(R.id.chkRemember);
        btnLogin = findViewById(R.id.btnLogin);
        txtGoRegister = findViewById(R.id.txtGoRegister);
        String prefillEmail = getIntent().getStringExtra("email");
        if (prefillEmail != null) {
            edtEmail.setText(prefillEmail);
        }

        // Two user actions: log in, and go to register
        btnLogin.setOnClickListener(v -> attemptLogin());

        txtGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = String.valueOf(edtEmail.getText()).trim();
        String password = String.valueOf(edtPassword.getText());

        // Clear any old errors first
        tilEmail.setError(null);
        tilPassword.setError(null);

        boolean valid = true;

        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_email_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_email_invalid));
            valid = false;
        }

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        } else if (password.length() < 6) {
            tilPassword.setError(getString(R.string.error_password_short));
            valid = false;
        }

        if (!valid) {
            return;
        }

        // Stop double taps while the request is running
        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> loadProfileAndContinue(result.getUser().getUid()))
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);

                    if (e instanceof FirebaseAuthInvalidCredentialsException
                            || e instanceof FirebaseAuthInvalidUserException) {
                        // Wrong email or wrong password: one message for both
                        tilPassword.setError(getString(R.string.error_login_failed));
                    } else if (e instanceof FirebaseNetworkException) {
                        Toast.makeText(this, R.string.error_network, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.error_login_unexpected,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // After signing in, read the user's profile to learn their name and role
    private void loadProfileAndContinue(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        // Signed in, but no profile was saved for this account
                        auth.signOut();
                        btnLogin.setEnabled(true);
                        Toast.makeText(this, R.string.error_profile_missing,
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String name = doc.getString("name");
                    String role = doc.getString("role");
                    if (name == null) {
                        name = "";
                    }
                    if (role == null) {
                        role = "patient";
                    }

                    Toast.makeText(this, getString(R.string.welcome_back_format, name),
                            Toast.LENGTH_LONG).show();

                    // Both roles open Home for now. Once the counsellor dashboard exists,
                    // choose the destination here based on the role.
                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    intent.putExtra("name", name);
                    intent.putExtra("role", role);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    btnLogin.setEnabled(true);
                    Toast.makeText(this, R.string.error_login_unexpected,
                            Toast.LENGTH_LONG).show();
                });
    }
}