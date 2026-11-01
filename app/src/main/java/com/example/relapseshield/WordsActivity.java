package com.example.relapseshield;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WordsActivity extends AppCompatActivity {

    // A starting point only. A real list should be built with a clinician,
    // in the languages the patients actually use.
    private static final String[] STARTER_URGENT = {
            "suicide", "kill myself", "end my life", "want to die",
            "wish i was dead", "hurt myself", "no reason to live"};
    private static final String[] STARTER_WATCH = {
            "relapse", "relapsed", "used again", "drank", "drunk",
            "craving", "hopeless", "give up", "can't cope"};

    TextView txtBack, txtEmpty;
    ListView lstWords;
    Button btnAddWord, btnStarter;

    FirebaseAuth auth;
    FirebaseFirestore db;

    final ArrayList<DocumentSnapshot> docs = new ArrayList<>();
    final ArrayList<String> lines = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_words);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.layoutBottom));

        txtBack = findViewById(R.id.txtBack);
        txtEmpty = findViewById(R.id.txtEmpty);
        lstWords = findViewById(R.id.lstWords);
        btnAddWord = findViewById(R.id.btnAddWord);
        btnStarter = findViewById(R.id.btnStarter);

        adapter = new ArrayAdapter<>(this, R.layout.item_checkin, R.id.txtItem, lines);
        lstWords.setAdapter(adapter);

        // Tapping a word offers to remove it
        lstWords.setOnItemClickListener((parent, view, position, id) -> {
            if (position < docs.size()) {
                confirmRemove(docs.get(position));
            }
        });

        txtBack.setOnClickListener(v -> finish());
        btnAddWord.setOnClickListener(v -> showAddDialog());
        btnStarter.setOnClickListener(v -> addStarterWords());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWords();
    }

    private void loadWords() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        db.collection("flagWords").whereEqualTo("counsellorId", me.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    docs.clear();
                    docs.addAll(snapshot.getDocuments());
                    // Urgent words first, then alphabetical
                    docs.sort((a, b) -> {
                        int la = "urgent".equals(a.getString("level")) ? 0 : 1;
                        int lb = "urgent".equals(b.getString("level")) ? 0 : 1;
                        if (la != lb) {
                            return la - lb;
                        }
                        String wa = a.getString("word") == null ? "" : a.getString("word");
                        String wb = b.getString("word") == null ? "" : b.getString("word");
                        return wa.compareToIgnoreCase(wb);
                    });

                    lines.clear();
                    for (DocumentSnapshot d : docs) {
                        String label = getString("urgent".equals(d.getString("level"))
                                ? R.string.fw_level_urgent : R.string.fw_level_watch);
                        lines.add(getString(R.string.fw_row_format, label, d.getString("word")));
                    }
                    txtEmpty.setVisibility(lines.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_checkins, Toast.LENGTH_LONG).show());
    }

    private Set<String> existingWords() {
        Set<String> set = new HashSet<>();
        for (DocumentSnapshot d : docs) {
            if (d.getString("word") != null) {
                set.add(d.getString("word"));
            }
        }
        return set;
    }

    private Map<String, Object> wordEntry(String counsellorId, String word, String level) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("counsellorId", counsellorId);
        entry.put("word", word);
        entry.put("level", level);
        entry.put("createdAt", FieldValue.serverTimestamp());
        return entry;
    }

    // Pop-up with a word field and a Watch / Urgent choice
    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_word, null);
        TextInputLayout til = view.findViewById(R.id.tilWord);
        TextInputEditText edt = view.findViewById(R.id.edtWord);
        RadioGroup rg = view.findViewById(R.id.rgLevel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.fw_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.fw_save, null)
                .setNegativeButton(R.string.fw_cancel, null)
                .create();
        dialog.show();

        // Set the click here so the pop-up stays open when the word is not valid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String word = FlagRules.normalise(String.valueOf(edt.getText()));
            til.setError(null);
            if (word.isEmpty()) {
                til.setError(getString(R.string.fw_error_required));
                return;
            }
            if (word.length() > 40) {
                til.setError(getString(R.string.fw_error_long));
                return;
            }
            if (existingWords().contains(word)) {
                til.setError(getString(R.string.fw_error_duplicate));
                return;
            }
            FirebaseUser me = auth.getCurrentUser();
            if (me == null) {
                return;
            }
            String level = (rg.getCheckedRadioButtonId() == R.id.rbUrgent) ? "urgent" : "watch";
            db.collection("flagWords").add(wordEntry(me.getUid(), word, level))
                    .addOnSuccessListener(ref -> {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.fw_added, Toast.LENGTH_SHORT).show();
                        loadWords();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, R.string.fw_failed, Toast.LENGTH_LONG).show());
        });
    }

    // One tap adds the suggested words, skipping any already in the list
    private void addStarterWords() {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) {
            return;
        }
        Set<String> have = existingWords();
        WriteBatch batch = db.batch();
        int added = 0;
        for (String w : STARTER_URGENT) {
            if (!have.contains(w)) {
                batch.set(db.collection("flagWords").document(),
                        wordEntry(me.getUid(), w, "urgent"));
                added++;
            }
        }
        for (String w : STARTER_WATCH) {
            if (!have.contains(w)) {
                batch.set(db.collection("flagWords").document(),
                        wordEntry(me.getUid(), w, "watch"));
                added++;
            }
        }
        if (added == 0) {
            return;
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, R.string.fw_starter_done, Toast.LENGTH_SHORT).show();
                    loadWords();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.fw_failed, Toast.LENGTH_LONG).show());
    }

    // Always ask before removing
    private void confirmRemove(DocumentSnapshot doc) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.fw_delete_title)
                .setMessage(doc.getString("word"))
                .setPositiveButton(R.string.fw_delete_yes, (d, w) ->
                        doc.getReference().delete()
                                .addOnSuccessListener(v -> {
                                    Toast.makeText(this, R.string.fw_removed,
                                            Toast.LENGTH_SHORT).show();
                                    loadWords();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        R.string.fw_failed, Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.fw_cancel, null)
                .show();
    }
}