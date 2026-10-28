package com.example.relapseshield;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    TextView txtGreeting, txtLogout;
    ListView lstCheckins;
    Button btnNewCheckin, btnEmergency;

    FirebaseAuth auth;
    FirebaseFirestore db;

    // What the list shows, and the longer text shown when a row is tapped
    final ArrayList<String> items = new ArrayList<>();
    final ArrayList<String> details = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Keep the header behind the status bar and the buttons clear of the navigation bar
        View root = findViewById(R.id.main);
        View header = findViewById(R.id.layoutHeader);
        View bottom = findViewById(R.id.layoutBottom);
        int headerTop = header.getPaddingTop();
        int bottomPad = bottom.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(),
                    bottom.getPaddingRight(), bottomPad + bars.bottom);
            return insets;
        });

        // Connect each view to the layout by its ID
        txtGreeting = findViewById(R.id.txtGreeting);
        txtLogout = findViewById(R.id.txtLogout);
        lstCheckins = findViewById(R.id.lstCheckins);
        btnNewCheckin = findViewById(R.id.btnNewCheckin);
        btnEmergency = findViewById(R.id.btnEmergency);

        // Greet the user by the nickname Login passed in
        String name = getIntent().getStringExtra("name");
        if (name == null || name.isEmpty()) {
            name = getString(R.string.home_default_name);
        }
        txtGreeting.setText(getString(R.string.home_greeting_format, name));

        // Login also passes the role ("patient" or "counsellor"). It is not used on this
        // screen yet; it will decide which dashboard opens once the counsellor side exists.
        String role = getIntent().getStringExtra("role");

        // The list starts empty; real check-ins are loaded in onResume
        adapter = new ArrayAdapter<>(this, R.layout.item_checkin, R.id.txtItem, items);
        lstCheckins.setAdapter(adapter);

        // Tapping a row shows the full record, including the note
        lstCheckins.setOnItemClickListener((parent, view, position, id) -> {
            if (position < details.size()) {
                new AlertDialog.Builder(HomeActivity.this)
                        .setTitle(R.string.mn_checkin_title)
                        .setMessage(details.get(position))
                        .setPositiveButton(R.string.mn_close, null)
                        .show();
            }
        });

        btnNewCheckin.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, CheckinActivity.class)));

        // Placeholder until the Emergency screen exists
        btnEmergency.setOnClickListener(v ->
                Toast.makeText(HomeActivity.this, R.string.toast_emergency_soon, Toast.LENGTH_SHORT).show());

        // Log out: end the Firebase session, return to Welcome and clear the screens behind it
        txtLogout.setText(R.string.mn_menu);
        txtLogout.setOnClickListener(v -> showMenu(v));
    }

    // Reload each time Home comes back to the front, so a new check-in appears straight away
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
                    }
                });
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
    private void loadCheckins() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }

        // Filtering by user only (no server-side sort) avoids needing a Firestore index;
        // the newest-first sort is done here instead.
        db.collection("checkins").whereEqualTo("userId", user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    docs.sort((a, b) -> {
                        Timestamp ta = a.getTimestamp("createdAt");
                        Timestamp tb = b.getTimestamp("createdAt");
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return -1;
                        if (tb == null) return 1;
                        return tb.compareTo(ta);
                    });

                    items.clear();
                    details.clear();
                    int shown = 0;
                    for (DocumentSnapshot doc : docs) {
                        if (shown == 20) {
                            break;
                        }
                        String line = describe(doc, false);
                        items.add(line);
                        details.add(describe(doc, true));
                        shown++;
                    }
                    if (items.isEmpty()) {
                        items.add(getString(R.string.home_no_checkins));
                        details.add(getString(R.string.home_no_checkins));
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_checkins,
                                Toast.LENGTH_LONG).show());
    }

    // Build one line such as "Sat 24 Oct · Mood 4/5 · Cravings low · Felt triggered"
    private String describe(DocumentSnapshot doc, boolean includeNote) {
        Timestamp ts = doc.getTimestamp("createdAt");
        Long mood = doc.getLong("mood");
        Long cravings = doc.getLong("cravings");
        Boolean triggered = doc.getBoolean("triggered");
        String note = doc.getString("note");

        String date = (ts == null)
                ? getString(R.string.checkin_date_unknown)
                : new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(ts.toDate());

        String[] levels = getResources().getStringArray(R.array.craving_levels);
        int level = (cravings == null) ? 0 : cravings.intValue();
        if (level < 0 || level >= levels.length) {
            level = 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(date)
                .append(" · ")
                .append(getString(R.string.checkin_line_mood_format, mood == null ? 0 : mood.intValue()))
                .append(" · ")
                .append(getString(R.string.checkin_line_cravings_format, levels[level]));
        if (triggered != null && triggered) {
            sb.append(" · ").append(getString(R.string.checkin_line_triggered));
        }
        if (includeNote && note != null && !note.isEmpty()) {
            sb.append("\n").append(note);
        }
        return sb.toString();
    }
}