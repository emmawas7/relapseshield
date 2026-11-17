package com.example.relapseshield;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResourcesActivity extends AppCompatActivity {

    // One entry in the list, read from the JSON
    static class Resource {
        String name = "", type = "", phone = "", district = "", notes = "";
    }

    // A server answer other than 200 OK, carrying the status code
    static class ApiException extends IOException {
        final int code;

        ApiException(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    TextView txtBack, txtStatus;
    ProgressBar progress;
    Button btnRetry;
    ListView lstResources;

    final ArrayList<Resource> resources = new ArrayList<>();
    final ArrayList<String> lines = new ArrayList<>();
    ArrayAdapter<String> adapter;

    // The network call runs on this background thread, never on the main (screen) thread
    final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_resources);

        ScreenInsets.apply(findViewById(R.id.main), findViewById(R.id.layoutHeader),
                findViewById(R.id.lstResources));

        txtBack = findViewById(R.id.txtBack);
        txtStatus = findViewById(R.id.txtStatus);
        progress = findViewById(R.id.progress);
        btnRetry = findViewById(R.id.btnRetry);
        lstResources = findViewById(R.id.lstResources);

        adapter = new ArrayAdapter<>(this, R.layout.item_checkin, R.id.txtItem, lines);
        lstResources.setAdapter(adapter);
        lstResources.setOnItemClickListener((parent, view, position, id) -> {
            if (position < resources.size()) {
                showDetails(resources.get(position));
            }
        });

        txtBack.setOnClickListener(v -> finish());
        btnRetry.setOnClickListener(v -> load());

        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // Runs a screen update only if the screen is still there
    private void ui(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    // GET request to the Firestore REST address of this project's "resources" collection.
    // Each kind of failure gets its own message, and none of them can crash the app.
    private void load() {
        showLoading();
        String project = FirebaseApp.getInstance().getOptions().getProjectId();
        String address = "https://firestore.googleapis.com/v1/projects/" + project
                + "/databases/(default)/documents/resources?pageSize=100";

        executor.execute(() -> {
            try {
                String json = httpGet(address);
                List<Resource> parsed = parse(json);
                ui(() -> showResources(parsed));
            } catch (UnknownHostException | ConnectException e) {
                Log.e("Resources", "No connection", e);
                ui(() -> showError(getString(R.string.rs_error_offline)));
            } catch (SocketTimeoutException e) {
                Log.e("Resources", "Timed out", e);
                ui(() -> showError(getString(R.string.rs_error_timeout)));
            } catch (ApiException e) {
                Log.e("Resources", "Server error " + e.code, e);
                ui(() -> showError(getString(R.string.rs_error_server, e.code)));
            } catch (IOException e) {
                Log.e("Resources", "Network problem", e);
                ui(() -> showError(getString(R.string.rs_error_offline)));
            } catch (JSONException e) {
                Log.e("Resources", "Could not read the JSON", e);
                ui(() -> showError(getString(R.string.rs_error_data)));
            }
        });
    }

    private String httpGet(String address) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000); // give up connecting after 8 seconds
            conn.setReadTimeout(8000);    // give up waiting for data after 8 seconds
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new ApiException(code);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Firestore wraps each value, for example "name": { "stringValue": "..." }
    private List<Resource> parse(String json) throws JSONException {
        List<Resource> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray docs = root.optJSONArray("documents");
        if (docs == null) {
            return out; // an empty collection has no "documents" key
        }
        for (int i = 0; i < docs.length(); i++) {
            JSONObject fields = docs.getJSONObject(i).optJSONObject("fields");
            if (fields == null) {
                continue;
            }
            Resource r = new Resource();
            r.name = text(fields, "name");
            r.type = text(fields, "type");
            r.phone = text(fields, "phone");
            r.district = text(fields, "district");
            r.notes = text(fields, "notes");
            if (!r.name.isEmpty()) {
                out.add(r);
            }
        }
        out.sort((a, b) -> {
            int t = a.type.compareToIgnoreCase(b.type);
            return t != 0 ? t : a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    private String text(JSONObject fields, String key) {
        JSONObject field = fields.optJSONObject(key);
        return field == null ? "" : field.optString("stringValue", "");
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        txtStatus.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        lstResources.setVisibility(View.GONE);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        txtStatus.setText(message);
        txtStatus.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);
        lstResources.setVisibility(View.GONE);
    }

    private void showResources(List<Resource> parsed) {
        resources.clear();
        resources.addAll(parsed);
        lines.clear();
        for (Resource r : resources) {
            String first = r.type.isEmpty() ? r.name : r.name + " · " + r.type;
            String second = (r.phone.isEmpty() ? getString(R.string.rs_no_phone) : r.phone)
                    + (r.district.isEmpty() ? "" : " · " + r.district);
            lines.add(first + "\n" + second);
        }
        adapter.notifyDataSetChanged();

        progress.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        if (resources.isEmpty()) {
            txtStatus.setText(R.string.rs_empty);
            txtStatus.setVisibility(View.VISIBLE);
            lstResources.setVisibility(View.GONE);
        } else {
            txtStatus.setVisibility(View.GONE);
            lstResources.setVisibility(View.VISIBLE);
        }
    }

    // Tapping an entry shows its details, with a Call button when there is a number
    private void showDetails(Resource r) {
        StringBuilder sb = new StringBuilder();
        if (!r.type.isEmpty()) {
            sb.append(r.type).append("\n");
        }
        sb.append(r.phone.isEmpty() ? getString(R.string.rs_no_phone) : r.phone);
        if (!r.district.isEmpty()) {
            sb.append("\n").append(r.district);
        }
        if (!r.notes.isEmpty()) {
            sb.append("\n\n").append(r.notes);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(r.name)
                .setMessage(sb.toString())
                .setNegativeButton(R.string.mn_close, null);
        if (!r.phone.isEmpty()) {
            builder.setPositiveButton(R.string.rs_call, (d, w) -> dial(r.phone));
        }
        builder.show();
    }

    // Opens the dialler with the number filled in; the user presses the green button
    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.fromParts("tel", number.replace(" ", ""), null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.em_no_dialler, Toast.LENGTH_LONG).show();
        }
    }
}