package com.example.relapseshield;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class WelcomeActivity extends AppCompatActivity {

    Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Remember me: Firebase keeps the user signed in on this phone. If they asked to be
        // remembered, go straight in; if not, sign them out and ask for the password again.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        SharedPreferences prefs = getSharedPreferences("relapse_prefs", MODE_PRIVATE);
        if (auth.getCurrentUser() != null) {
            if (prefs.getBoolean("remember", false)) {
                String uid = auth.getCurrentUser().getUid();
                String cachedRole = prefs.getString("cache_role_" + uid, "");
                if ("counsellor".equals(cachedRole)) {
                    startActivity(new Intent(this, DashboardActivity.class));
                    finish();
                    return;
                } else if ("patient".equals(cachedRole)) {
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                    return;
                }
                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String role = doc.getString("role");
                                String name = doc.getString("name");
                                prefs.edit()
                                        .putString("cache_name_" + uid, name == null ? "" : name)
                                        .putString("cache_role_" + uid, role == null ? "patient" : role)
                                        .apply();
                                if ("counsellor".equals(role)) {
                                    startActivity(new Intent(this, DashboardActivity.class));
                                } else {
                                    startActivity(new Intent(this, HomeActivity.class));
                                }
                            } else {
                                auth.signOut();
                                startActivity(new Intent(this, WelcomeActivity.class));
                            }
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            startActivity(new Intent(this, HomeActivity.class));
                            finish();
                        });
                return;
            }
            auth.signOut();
        }

        setContentView(R.layout.activity_welcome);
        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutContent));

        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);

        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class)));
        btnLogin.setOnClickListener(v ->
                startActivity(new Intent(WelcomeActivity.this, LoginActivity.class)));
    }
}
