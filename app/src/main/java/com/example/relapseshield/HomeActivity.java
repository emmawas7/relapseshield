package com.example.relapseshield;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

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
        btnEmergency.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, EmergencyActivity.class)));

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
                    // Remembered session, but an administrator has removed the profile
                    if (!doc.exists()) {
                        auth.signOut();
                        Toast.makeText(HomeActivity.this, R.string.rm_account_removed,
                                Toast.LENGTH_LONG).show();
                        goToWelcome();
                        return;
                    }

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
            } else if (id == R.id.action_delete_account) {
                confirmDeleteAccount();
                return true;
            } else if (id == R.id.action_logout) {
                auth.signOut();
                Toast.makeText(HomeActivity.this, R.string.toast_logged_out,
                        Toast.LENGTH_SHORT).show();
                goToWelcome();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void goToWelcome() {
        Intent intent = new Intent(HomeActivity.this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // ---- Delete my account: ask for the password first, then remove everything ----

    private void confirmDeleteAccount() {
        View view = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout til = view.findViewById(R.id.tilDelPassword);
        TextInputEditText edt = view.findViewById(R.id.edtDelPassword);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.da_title)
                .setMessage(R.string.da_message)
                .setView(view)
                .setPositiveButton(R.string.da_confirm, null)
                .setNegativeButton(R.string.ce_cancel, null)
                .create();
        dialog.show();

        // Set the click here so the pop-up stays open if the password is empty
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = String.valueOf(edt.getText());
            til.setError(null);
            if (password.isEmpty()) {
                til.setError(getString(R.string.error_password_required));
                return;
            }
            dialog.dismiss();
            reauthenticateAndDelete(password);
        });
    }

    // Firebase wants a fresh login before it will delete an account
    private void reauthenticateAndDelete(String password) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            return;
        }
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential)
                .addOnSuccessListener(r -> deleteEverything(user))
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.da_wrong_password, Toast.LENGTH_LONG).show());
    }

    // Order matters: check-ins, then alert, request, count and profile in one batch,
    // and the login last. If any step fails, the rest is not attempted.
    private void deleteEverything(FirebaseUser user) {
        String uid = user.getUid();
        DocumentReference profileRef = db.collection("users").document(uid);
        DocumentReference alertRef = db.collection("alerts").document(uid);
        DocumentReference requestRef = db.collection("requests").document(uid);

        Task<DocumentSnapshot> profileTask = profileRef.get();
        Task<DocumentSnapshot> alertTask = alertRef.get();
        Task<DocumentSnapshot> requestTask = requestRef.get();
        Task<QuerySnapshot> checkinTask =
                db.collection("checkins").whereEqualTo("userId", uid).get();

        Tasks.whenAllSuccess(profileTask, alertTask, requestTask, checkinTask)
                .addOnSuccessListener(results -> {
                    DocumentSnapshot profile = (DocumentSnapshot) results.get(0);
                    DocumentSnapshot alert = (DocumentSnapshot) results.get(1);
                    DocumentSnapshot request = (DocumentSnapshot) results.get(2);
                    QuerySnapshot checkins = (QuerySnapshot) results.get(3);

                    // 1. Check-ins, in groups of up to 400 (one batch holds at most 500 writes)
                    List<DocumentSnapshot> docs = checkins.getDocuments();
                    List<Task<Void>> groups = new ArrayList<>();
                    for (int i = 0; i < docs.size(); i += 400) {
                        WriteBatch batch = db.batch();
                        for (int j = i; j < Math.min(i + 400, docs.size()); j++) {
                            batch.delete(docs.get(j).getReference());
                        }
                        groups.add(batch.commit());
                    }

                    // 2. Everything else, in one batch: all of it happens or none of it does
                    WriteBatch rest = db.batch();
                    if (alert.exists()) {
                        rest.delete(alertRef);
                    }
                    if (request.exists()) {
                        rest.delete(requestRef);
                    }
                    String counsellorId = profile.getString("counsellorId");
                    if (counsellorId != null) {
                        rest.update(db.collection("users").document(counsellorId),
                                "clientCount", FieldValue.increment(-1));
                    }
                    rest.delete(profileRef);

                    Tasks.whenAll(groups)
                            .onSuccessTask(x -> rest.commit())
                            .onSuccessTask(x -> user.delete())
                            .addOnSuccessListener(x -> finishDeletion())
                            .addOnFailureListener(e -> {
                                Log.e("DeleteAccount", "Deleting the account failed", e);
                                Toast.makeText(this, R.string.da_failed,
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("DeleteAccount", "Could not read the data to delete", e);
                    Toast.makeText(this, R.string.da_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void finishDeletion() {
        SharedPreferences prefs = getSharedPreferences("relapse_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        auth.signOut();
        Toast.makeText(this, R.string.da_done, Toast.LENGTH_LONG).show();
        goToWelcome();
    }
}