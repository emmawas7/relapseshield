package com.example.relapseshield;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    TextView txtGreeting, txtLogout;
    ListView lstCheckins;
    Button btnNewCheckin, btnEmergency;

    FirebaseAuth auth;
    FirebaseFirestore db;

    // Three parallel lists: the row text, the longer text for the pop-up, and the document ID
    final ArrayList<String> items = new ArrayList<>();
    final ArrayList<String> details = new ArrayList<>();
    final ArrayList<String> ids = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutBottom));

        txtGreeting = findViewById(R.id.txtGreeting);
        txtLogout = findViewById(R.id.txtLogout);
        lstCheckins = findViewById(R.id.lstCheckins);
        btnNewCheckin = findViewById(R.id.btnNewCheckin);
        btnEmergency = findViewById(R.id.btnEmergency);

        String name = getIntent().getStringExtra("name");
        if (name == null || name.isEmpty()) {
            name = getString(R.string.home_default_name);
        }
        txtGreeting.setText(getString(R.string.home_greeting_format, name));

        adapter = new ArrayAdapter<>(this, R.layout.item_checkin, R.id.txtItem, items);
        lstCheckins.setAdapter(adapter);

        // Tapping a row opens a pop-up with the full record, and Edit and Delete
        lstCheckins.setOnItemClickListener((parent, view, position, id) -> {
            if (position < ids.size() && ids.get(position) != null) {
                showCheckin(position);
            }
        });

        btnNewCheckin.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, CheckinActivity.class)));

        // Placeholder until the Emergency screen is built
        btnEmergency.setOnClickListener(v ->
                Toast.makeText(HomeActivity.this, R.string.toast_emergency_soon,
                        Toast.LENGTH_SHORT).show());

        txtLogout.setText(R.string.mn_menu);
        txtLogout.setOnClickListener(v -> showMenu(v));
    }

    // Reload each time Home comes back to the front, so changes appear straight away
    @Override
    protected void onResume() {
        super.onResume();
        routeByProfile();
        loadCheckins();
    }

    // Counsellors go to their dashboard; patients not yet linked go to the connect screen
    private void routeByProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if ("counsellor".equals(role)) {
                        startActivity(new Intent(HomeActivity.this, DashboardActivity.class));
                        finish();
                    } else if (doc.getString("counsellorId") == null) {
                        startActivity(new Intent(HomeActivity.this, LinkActivity.class));
                        finish();
                    } else if (doc.getString("name") != null) {
                        txtGreeting.setText(getString(R.string.home_greeting_format,
                                doc.getString("name")));
                    }
                });
    }

    private void loadCheckins() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }
        db.collection("checkins").whereEqualTo("userId", user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    docs.sort(FlagRules::newestFirst);

                    items.clear();
                    details.clear();
                    ids.clear();
                    int shown = 0;
                    for (DocumentSnapshot doc : docs) {
                        if (shown == 20) {
                            break;
                        }
                        items.add(FlagRules.describe(this, doc, false));
                        details.add(FlagRules.describe(this, doc, true));
                        ids.add(doc.getId());
                        shown++;
                    }
                    if (items.isEmpty()) {
                        items.add(getString(R.string.home_no_checkins));
                        details.add(getString(R.string.home_no_checkins));
                        ids.add(null);
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_checkins,
                                Toast.LENGTH_LONG).show());
    }

    private void showCheckin(int position) {
        String id = ids.get(position);
        new AlertDialog.Builder(this)
                .setTitle(R.string.mn_checkin_title)
                .setMessage(details.get(position))
                .setPositiveButton(R.string.ce_edit, (d, w) -> {
                    Intent intent = new Intent(HomeActivity.this, CheckinActivity.class);
                    intent.putExtra("checkinId", id);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.ce_delete, (d, w) -> confirmDelete(id))
                .setNeutralButton(R.string.mn_close, null)
                .show();
    }

    // Always ask before deleting
    private void confirmDelete(String id) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ce_delete_title)
                .setMessage(R.string.ce_delete_message)
                .setPositiveButton(R.string.ce_delete_yes, (d, w) -> deleteCheckin(id))
                .setNegativeButton(R.string.ce_cancel, null)
                .show();
    }

    private void deleteCheckin(String id) {
        db.collection("checkins").document(id).delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.ce_deleted, Toast.LENGTH_SHORT).show();
                    loadCheckins();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.ce_delete_failed, Toast.LENGTH_LONG).show());
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.menu_app, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                loadCheckins();
                return true;
            } else if (id == R.id.action_about) {
                new AlertDialog.Builder(HomeActivity.this)
                        .setTitle(R.string.mn_about_title)
                        .setMessage(R.string.mn_about_message)
                        .setPositiveButton(R.string.mn_close, null)
                        .show();
                return true;
            } else if (id == R.id.action_logout) {
                auth.signOut();
                Toast.makeText(HomeActivity.this, R.string.toast_logged_out,
                        Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(HomeActivity.this, WelcomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                return true;
            }
            return false;
        });
        menu.show();
    }
}