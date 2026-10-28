package com.example.relapseshield;

import android.content.Context;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FlagRules {

    // THE FLAG RULES. "latest" is null when the patient has no check-ins yet.
    public static List<String> reasons(Context ctx, DocumentSnapshot latest, Timestamp assignedAt) {
        List<String> out = new ArrayList<>();

        // Signals in the latest check-in, until the counsellor marks it reviewed
        if (latest != null && !Boolean.TRUE.equals(latest.getBoolean("reviewed"))) {
            Long mood = latest.getLong("mood");
            Long cravings = latest.getLong("cravings");
            boolean triggered = Boolean.TRUE.equals(latest.getBoolean("triggered"));
            int c = (cravings == null) ? 0 : cravings.intValue();

            if (mood != null && mood <= 2) {
                out.add(ctx.getString(R.string.fl_mood_format, mood.intValue()));
            }
            if (c == 3) {
                out.add(ctx.getString(R.string.fl_cravings_high));
            }
            if (triggered && c >= 2) {
                out.add(ctx.getString(R.string.fl_triggered));
            }
        }

        // Silence: three or more days since the last check-in (or since being confirmed)
        Timestamp base = (latest != null) ? latest.getTimestamp("createdAt") : assignedAt;
        if (base != null) {
            long days = TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - base.toDate().getTime());
            if (days >= 3) {
                out.add(ctx.getString(R.string.fl_silent_format, (int) days));
            }
        }
        return out;
    }

    public static int newestFirst(DocumentSnapshot a, DocumentSnapshot b) {
        Timestamp ta = a.getTimestamp("createdAt");
        Timestamp tb = b.getTimestamp("createdAt");
        if (ta == null && tb == null) return 0;
        if (ta == null) return -1;
        if (tb == null) return 1;
        return tb.compareTo(ta);
    }

    // One line such as "Sat 24 Oct · Mood 4/5 · Cravings low · Felt triggered"
    public static String describe(Context ctx, DocumentSnapshot doc, boolean includeNote) {
        Timestamp ts = doc.getTimestamp("createdAt");
        Long mood = doc.getLong("mood");
        Long cravings = doc.getLong("cravings");
        Boolean triggered = doc.getBoolean("triggered");
        String note = doc.getString("note");

        String date = (ts == null)
                ? ctx.getString(R.string.checkin_date_unknown)
                : new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(ts.toDate());

        String[] levels = ctx.getResources().getStringArray(R.array.craving_levels);
        int level = (cravings == null) ? 0 : cravings.intValue();
        if (level < 0 || level >= levels.length) {
            level = 0;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(date).append(" · ")
                .append(ctx.getString(R.string.checkin_line_mood_format, mood == null ? 0 : mood.intValue()))
                .append(" · ")
                .append(ctx.getString(R.string.checkin_line_cravings_format, levels[level]));
        if (triggered != null && triggered) {
            sb.append(" · ").append(ctx.getString(R.string.checkin_line_triggered));
        }
        if (includeNote && note != null && !note.isEmpty()) {
            sb.append("\n").append(note);
        }
        return sb.toString();
    }
}