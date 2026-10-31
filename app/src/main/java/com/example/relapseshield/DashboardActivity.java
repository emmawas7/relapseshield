package com.example.relapseshield;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    TextView txtGreeting, txtLogout, txtSubtitle, txtEmpty;
    ListView lstPatients;

    FirebaseAuth auth;
    FirebaseFirestore db;
    String myName = "";

    // One list row: an urgent alert, a request to confirm, or a confirmed patient
    static class Row {
        boolean isAlert, isRequest;
        String id, name, phone, line;
        List<String> reasons = new ArrayList<>();
        List<DocumentSnapshot> checkins = new ArrayList<>(); // newest first
    }

    final ArrayList<Row> rows = new ArrayList<>();
    final ArrayList<String> lines = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.lstPatients));

        txtGreeting = findViewById(R.id.txtGreeting);
        txtLogout = findViewById(R.id.txtLogout);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtEmpty = findViewById(R.id.txtEmpty);
        lstPatients = findViewById(R.id.lstPatients);

        // Urgent alerts and flagged patients show in red, requests in teal
        adapter = new ArrayAdapter<String>(this, R.layout.item_checkin, R.id.txtItem, lines) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t = v.findViewById(R.id.txtItem);
                int color = R.color.text_dark;
                if (position < rows.size()) {
                    Row r = rows.get(position);
                    if (r.isAlert || (!r.isRequest && !r.reasons.isEmpty())) {
                        color = R.color.error_red;
                    } else if (r.isRequest) {
                        color = R.color.primary;
                    }
                }
                t.setTextColor(ContextCompat.getColor(getContext(), color));
                return v;
            }
        };
        lstPatients.setAdapter(adapter);

        lstPatients.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= rows.size()) {
                return;
            }
            Row r = rows.get(position);
            if (r.isRequest) {
                showRequest(r);
            } else {
                showPatient(r);
            }
        });

        txtLogout.setText(R.string.mn_menu);
        txtLogout.setOnClickListener(v -> showMenu(v));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAll();
    }

    private void loadAll() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        db.collection("users").document(me.getUid()).get()
                .addOnSuccessListener(profile -> {
                    myName = profile.getString("name") == null ? "" : profile.getString("name");
                    Long count = profile.getLong("clientCount");
                    String code = profile.getString("code");
                    txtGreeting.setText(getString(R.string.db_greeting_format, myName));
                    txtSubtitle.setText(getString(R.string.db_subtitle_format,
                            code == null ? "" : code, count == null ? 0 : count.intValue()));

                    if (!Boolean.TRUE.equals(profile.getBoolean("approved"))) {
                        showMessage(getString(R.string.db_pending));
                        return;
                    }
                    loadRows(me.getUid());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_checkins, Toast.LENGTH_LONG).show());
    }

    // Four questions, all limited to this counsellor: alerts, requests, patients, check-ins
    private void loadRows(String myId) {
        Task<QuerySnapshot> alertTask =
                db.collection("alerts").whereEqualTo("counsellorId", myId).get();
        Task<QuerySnapshot> reqTask =
                db.collection("requests").whereEqualTo("counsellorId", myId).get();
        Task<QuerySnapshot> patTask =
                db.collection("users").whereEqualTo("counsellorId", myId).get();
        Task<QuerySnapshot> chkTask =
                db.collection("checkins").whereEqualTo("counsellorId", myId).get();

        Tasks.whenAllSuccess(alertTask, reqTask, patTask, chkTask).addOnSuccessListener(results -> {
            QuerySnapshot alerts = (QuerySnapshot) results.get(0);
            QuerySnapshot requests = (QuerySnapshot) results.get(1);
            QuerySnapshot patients = (QuerySnapshot) results.get(2);
            QuerySnapshot checkins = (QuerySnapshot) results.get(3);

            // Urgent alerts first, newest on top
            List<DocumentSnapshot> openAlerts = new ArrayList<>();
            for (DocumentSnapshot a : alerts.getDocuments()) {
                if ("open".equals(a.getString("status"))) {
                    openAlerts.add(a);
                }
            }
            openAlerts.sort(FlagRules::newestFirst);
            ArrayList<Row> alertRows = new ArrayList<>();
            for (DocumentSnapshot a : openAlerts) {
                Row row = new Row();
                row.isAlert = true;
                row.id = a.getId();
                row.name = a.getString("patientName");
                Timestamp t = a.getTimestamp("createdAt");
                String when = (t == null) ? "" : new SimpleDateFormat("EEE d MMM, HH:mm",
                        Locale.getDefault()).format(t.toDate());
                row.line = getString(R.string.db_alert_row_format, row.name, when);
                alertRows.add(row);
            }

            Map<String, List<DocumentSnapshot>> byPatient = new HashMap<>();
            for (DocumentSnapshot c : checkins.getDocuments()) {
                String pid = c.getString("userId");
                if (pid != null) {
                    byPatient.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
                }
            }

            ArrayList<Row> requestRows = new ArrayList<>();
            for (DocumentSnapshot r : requests.getDocuments()) {
                if (!"pending".equals(r.getString("status"))) {
                    continue;
                }
                Row row = new Row();
                row.isRequest = true;
                row.id = r.getId();
                row.name = r.getString("patientName");
                row.phone = r.getString("patientPhone");
                row.line = getString(R.string.db_request_row_format, row.name, row.phone);
                requestRows.add(row);
            }

            ArrayList<Row> patientRows = new ArrayList<>();
            for (DocumentSnapshot p : patients.getDocuments()) {
                Row row = new Row();
                row.id = p.getId();
                row.name = p.getString("name") == null ? "" : p.getString("name");
                List<DocumentSnapshot> list = byPatient.get(row.id);
                if (list != null) {
                    list.sort(FlagRules::newestFirst);
                    row.checkins = list;
                }
                DocumentSnapshot latest = row.checkins.isEmpty() ? null : row.checkins.get(0);
                row.reasons = FlagRules.reasons(this, latest, p.getTimestamp("assignedAt"));
                String latestLine = (latest == null)
                        ? getString(R.string.db_no_checkins)
                        : FlagRules.describe(this, latest, false);
                row.line = row.name
                        + (row.reasons.isEmpty() ? "" : " · " + TextUtils.join(", ", row.reasons))
                        + "\n" + latestLine;
                patientRows.add(row);
            }

            // Most flags first, then alphabetical
            patientRows.sort((a, b) -> {
                int diff = b.reasons.size() - a.reasons.size();
                return diff != 0 ? diff : a.name.compareToIgnoreCase(b.name);
            });

            rows.clear();
            rows.addAll(alertRows);
            rows.addAll(requestRows);
            rows.addAll(patientRows);
            lines.clear();
            for (Row r : rows) {
                lines.add(r.line);
            }
            if (rows.isEmpty()) {
                txtEmpty.setText(R.string.db_empty);
                txtEmpty.setVisibility(View.VISIBLE);
            } else {
                txtEmpty.setVisibility(View.GONE);
            }
            adapter.notifyDataSetChanged();
        }).addOnFailureListener(e ->
                Toast.makeText(this, R.string.error_load_checkins, Toast.LENGTH_LONG).show());
    }

    private void showRequest(Row r) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.db_confirm_title, r.name))
                .setMessage(getString(R.string.db_confirm_msg, r.phone))
                .setPositiveButton(R.string.db_confirm_yes, (d, w) -> accept(r))
                .setNegativeButton(R.string.db_decline, (d, w) -> decline(r))
                .setNeutralButton(R.string.db_cancel, null)
                .show();
    }

    // One transaction: check the cap of 10, add one to the count, link the patient, close the request
    private void accept(Row r) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        DocumentReference myRef = db.collection("users").document(me.getUid());
        DocumentReference patientRef = db.collection("users").document(r.id);
        DocumentReference requestRef = db.collection("requests").document(r.id);

        db.runTransaction(transaction -> {
            DocumentSnapshot mine = transaction.get(myRef);
            Long count = mine.getLong("clientCount");
            long taken = (count == null) ? 0 : count;
            if (taken >= 10) {
                throw new FirebaseFirestoreException("Full",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            transaction.update(myRef, "clientCount", taken + 1);

            Map<String, Object> link = new HashMap<>();
            link.put("counsellorId", me.getUid());
            link.put("counsellorName", myName);
            link.put("status", "linked");
            link.put("assignedAt", FieldValue.serverTimestamp());
            transaction.update(patientRef, link);

            transaction.delete(requestRef);
            return null;
        }).addOnSuccessListener(result -> {
            Toast.makeText(this, R.string.db_linked, Toast.LENGTH_SHORT).show();
            loadAll();
        }).addOnFailureListener(e -> {
            boolean full = e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.ABORTED;
            Toast.makeText(this, full ? R.string.db_full : R.string.db_failed,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void decline(Row r) {
        db.collection("requests").document(r.id).update("status", "declined")
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.db_declined, Toast.LENGTH_SHORT).show();
                    loadAll();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.db_failed, Toast.LENGTH_LONG).show());
    }

    // Alerts and patients both open the patient screen
    private void showPatient(Row row) {
        Intent intent = new Intent(DashboardActivity.this, PatientActivity.class);
        intent.putExtra("patientId", row.id);
        intent.putExtra("patientName", row.name);
        startActivity(intent);
    }

    // The app-wide menu
    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.menu_app, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                loadAll();
                return true;
            } else if (id == R.id.action_about) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.mn_about_title)
                        .setMessage(R.string.mn_about_message)
                        .setPositiveButton(R.string.mn_close, null)
                        .show();
                return true;
            } else if (id == R.id.action_logout) {
                auth.signOut();
                Toast.makeText(this, R.string.toast_logged_out, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(DashboardActivity.this, WelcomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showMessage(String message) {
        rows.clear();
        lines.clear();
        adapter.notifyDataSetChanged();
        txtEmpty.setText(message);
        txtEmpty.setVisibility(View.VISIBLE);
    }
}