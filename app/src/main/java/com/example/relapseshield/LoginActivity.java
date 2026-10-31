package com.example.relapseshield;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "Lifecycle";
    private static final String PREFS = "relapse_prefs";

    TextInputLayout tilEmail, tilPassword;
    TextInputEditText edtEmail, edtPassword;
    CheckBox chkRemember;
    Button btnLogin;
    TextView txtGoRegister, txtForgot;

    FirebaseAuth auth;
    FirebaseFirestore db;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        Log.d(TAG, "LoginActivity onCreate, saved state: "
                + (savedInstanceState == null ? "none (fresh start)" : "restored"));

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        chkRemember = findViewById(R.id.chkRemember);
        btnLogin = findViewById(R.id.btnLogin);
        txtGoRegister = findViewById(R.id.txtGoRegister);
        txtForgot = findViewById(R.id.txtForgot);

        // Fill in the email: the one just registered, or the one remembered last time.
        // Only on a fresh start, so a rotation does not overwrite what the user typed.
        if (savedInstanceState == null) {
            boolean remembered = prefs.getBoolean("remember", false);
            chkRemember.setChecked(remembered);
            String fromRegister = getIntent().getStringExtra("email");
            if (fromRegister != null) {
                edtEmail.setText(fromRegister);
            } else if (remembered) {
                edtEmail.setText(prefs.getString("lastEmail", ""));
            }
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
        txtForgot.setOnClickListener(v -> showForgotDialog());

        // Swap this screen for Register, so Back does not bounce between the two
        txtGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = String.valueOf(edtEmail.getText()).trim();
        String password = String.valueOf(edtPassword.getText());

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

        btnLogin.setEnabled(false);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    // Remember me saves only the email and a yes/no flag, never the password
                    SharedPreferences.Editor editor = prefs.edit();
                    if (chkRemember.isChecked()) {
                        editor.putBoolean("remember", true).putString("lastEmail", email);
                    } else {
                        editor.putBoolean("remember", false).remove("lastEmail");
                    }
                    editor.apply();
                    openHome(result.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    if (e instanceof FirebaseAuthInvalidUserException
                            || e instanceof FirebaseAuthInvalidCredentialsException) {
                        // One message for a wrong email or a wrong password
                        tilPassword.setError(getString(R.string.error_login_failed));
                    } else if (e instanceof FirebaseNetworkException) {
                        Toast.makeText(this, R.string.error_network, Toast.LENGTH_LONG).show();
                    } else if (e instanceof FirebaseTooManyRequestsException) {
                        Toast.makeText(this, R.string.lg_error_too_many, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.error_login_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }
    // Welcome the user, then open Home (which sends counsellors on to their dashboard)
    private void openHome(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(profile -> {
                    // A login with no profile means an administrator removed this account
                    if (!profile.exists()) {
                        auth.signOut();
                        btnLogin.setEnabled(true);
                        Toast.makeText(this, R.string.rm_account_removed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String name = profile.getString("name");
                    Toast.makeText(this, getString(R.string.welcome_back_format,
                            name == null ? "" : name), Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                    intent.putExtra("name", name);
                    intent.putExtra("role", profile.getString("role"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    btnLogin.setEnabled(true);
                    Toast.makeText(this, R.string.lg_error_profile, Toast.LENGTH_LONG).show();
                });
    }

    // Forgot password: ask for the email, then Firebase sends the reset link
    private void showForgotDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        TextInputLayout til = view.findViewById(R.id.tilResetEmail);
        TextInputEditText edt = view.findViewById(R.id.edtResetEmail);
        edt.setText(String.valueOf(edtEmail.getText()).trim());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lg_reset_title)
                .setView(view)
                .setPositiveButton(R.string.lg_reset_send, null)
                .setNegativeButton(R.string.lg_reset_cancel, null)
                .create();
        dialog.show();

        // Set the click here so the dialog stays open when the email is invalid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = String.valueOf(edt.getText()).trim();
            til.setError(null);
            if (email.isEmpty()) {
                til.setError(getString(R.string.error_email_required));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                til.setError(getString(R.string.error_email_invalid));
                return;
            }
            auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(x -> {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.lg_reset_sent, Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        if (e instanceof FirebaseAuthInvalidUserException) {
                            // Same answer as success, so nobody can discover who has an account
                            dialog.dismiss();
                            Toast.makeText(this, R.string.lg_reset_sent, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, R.string.lg_reset_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    // Lifecycle logging for Milestone 1 Part E (Logcat tag: Lifecycle)
    @Override protected void onStart() { super.onStart(); Log.d(TAG, "LoginActivity onStart"); }
    @Override protected void onResume() { super.onResume(); Log.d(TAG, "LoginActivity onResume"); }
    @Override protected void onPause() { super.onPause(); Log.d(TAG, "LoginActivity onPause"); }
    @Override protected void onStop() { super.onStop(); Log.d(TAG, "LoginActivity onStop"); }
    @Override protected void onRestart() { super.onRestart(); Log.d(TAG, "LoginActivity onRestart"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "LoginActivity onDestroy"); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "LoginActivity onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "LoginActivity onRestoreInstanceState");
    }
}