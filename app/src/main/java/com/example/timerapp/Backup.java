package com.example.timerapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves everything (timers, projects, history with notes, settings) to one JSON file
 * and loads it back, e.g. on a new phone.
 */
public final class Backup {

    private static final String FORMAT = "focus-timer-backup";
    private static final int VERSION = 1;
    /** Larger than any real backup (5000 sessions is well under 1 MB). */
    private static final int MAX_BYTES = 8 << 20;

    /** Device-specific state that must not travel with a backup. */
    private static final Set<String> SKIP = new HashSet<String>();

    static {
        SKIP.add("focus_dnd_active");
        SKIP.add("focus_dnd_prev");
        SKIP.add("last_backup");
        SKIP.add("last_log");
        // the alarm sound points at a file on this phone; another phone keeps its own
        SKIP.add("sound_uri");
        SKIP.add("sound_name");
    }

    private Backup() {
    }

    /** Settings that belong to this phone (widget settings are per placed widget). */
    private static boolean skip(String key) {
        return SKIP.contains(key) || key.startsWith("widget_");
    }

    /** A backup file's contents, checked but not applied yet. */
    public static final class Snapshot {
        public final long created;
        final JSONObject prefs;

        Snapshot(long created, JSONObject prefs) {
            this.created = created;
            this.prefs = prefs;
        }
    }

    public static String csvName() {
        return "focus-timer-sessions-" + new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.US).format(new java.util.Date()) + ".csv";
    }

    /** One CSV field, quoted when it holds a comma, quote or line break. */
    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /**
     * Every session as a spreadsheet (oldest first): date, start, end, minutes, name,
     * project, note and focus score. Returns how many sessions were written.
     */
    public static int writeCsv(Context c, Uri uri) throws Exception {
        List<PrefsStore.Hist> history = PrefsStore.loadHistory(c);
        java.util.Map<Long, String> projects = new java.util.HashMap<Long, String>();
        for (PrefsStore.Project p : PrefsStore.loadProjects(c)) {
            projects.put(p.id, p.name);
        }
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.US);
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm",
                java.util.Locale.US);
        StringBuilder sb = new StringBuilder();
        // a byte-order mark tells Excel the file is UTF-8 (names in any script)
        sb.append('\uFEFF');
        int[] heads = {R.string.csv_date, R.string.csv_start, R.string.csv_end,
                R.string.csv_minutes, R.string.csv_name, R.string.csv_project,
                R.string.csv_note, R.string.csv_score};
        for (int i = 0; i < heads.length; i++) {
            sb.append(i > 0 ? "," : "").append(csv(c.getString(heads[i])));
        }
        sb.append("\r\n");
        for (int i = history.size() - 1; i >= 0; i--) {
            PrefsStore.Hist h = history.get(i);
            java.util.Date start = new java.util.Date(h.ts - h.millis);
            java.util.Date end = new java.util.Date(h.ts);
            String project = projects.get(h.project);
            sb.append(day.format(start)).append(',')
                    .append(time.format(start)).append(',')
                    .append(time.format(end)).append(',')
                    .append(Math.round(h.millis / 60000.0)).append(',')
                    .append(csv(h.name)).append(',')
                    .append(csv(project == null ? "" : project)).append(',')
                    .append(csv(h.note)).append(',')
                    .append(h.score >= 0 ? String.valueOf(h.score) : "")
                    .append("\r\n");
        }
        OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) {
            throw new IllegalStateException("no stream");
        }
        try {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        return history.size();
    }

    public static String fileName() {
        return "focus-timer-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date()) + ".json";
    }

    public static void write(Context c, Uri uri) throws Exception {
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : PrefsStore.raw(c).getAll().entrySet()) {
            if (skip(e.getKey()) || e.getValue() == null) {
                continue;
            }
            Object v = e.getValue();
            JSONArray typed = new JSONArray();
            if (v instanceof Boolean) {
                typed.put("b").put(v);
            } else if (v instanceof Integer) {
                typed.put("i").put(v);
            } else if (v instanceof Long) {
                typed.put("l").put(v);
            } else if (v instanceof Float) {
                typed.put("f").put(((Float) v).doubleValue());
            } else if (v instanceof String) {
                typed.put("s").put(v);
            } else {
                continue;
            }
            prefs.put(e.getKey(), typed);
        }
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("created", System.currentTimeMillis());
        root.put("prefs", prefs);
        OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) {
            throw new IllegalStateException("no stream");
        }
        try {
            out.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        PrefsStore.setLastBackup(c, System.currentTimeMillis());
    }

    /** Reads and checks a backup file; throws if it isn't one of ours. */
    public static Snapshot read(Context c, Uri uri) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IllegalStateException("no stream");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_BYTES) {
                    throw new IllegalStateException("too large");
                }
            }
        } finally {
            in.close();
        }
        JSONObject root = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        if (!FORMAT.equals(root.optString("format")) || root.optInt("version") < 1) {
            throw new JSONException("not a backup");
        }
        return new Snapshot(root.optLong("created"), root.getJSONObject("prefs"));
    }

    /**
     * Replaces all data with the snapshot. Timers that were running when the backup was
     * made come back paused at the time they had then, and so does the stopwatch.
     */
    public static void restore(Context c, Snapshot s) throws JSONException {
        // stop anything scheduled for the timers being replaced
        for (TimerItem it : PrefsStore.loadTimers(c)) {
            AlarmHelper.cancel(c, it.id);
            NotificationHelper.cancelAlarm(c, it.id);
        }
        SharedPreferences sp = PrefsStore.raw(c);
        SharedPreferences.Editor ed = sp.edit();
        for (String k : sp.getAll().keySet()) {
            if (!skip(k)) {
                ed.remove(k);
            }
        }
        JSONArray names = s.prefs.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String k = names.getString(i);
            if (skip(k)) {
                continue;
            }
            JSONArray typed = s.prefs.getJSONArray(k);
            String t = typed.getString(0);
            if ("b".equals(t)) {
                ed.putBoolean(k, typed.getBoolean(1));
            } else if ("i".equals(t)) {
                ed.putInt(k, typed.getInt(1));
            } else if ("l".equals(t)) {
                ed.putLong(k, typed.getLong(1));
            } else if ("f".equals(t)) {
                ed.putFloat(k, (float) typed.getDouble(1));
            } else if ("s".equals(t)) {
                ed.putString(k, typed.getString(1));
            }
        }
        ed.commit();

        long at = s.created > 0 ? s.created : System.currentTimeMillis();
        List<TimerItem> timers = PrefsStore.loadTimers(c);
        for (TimerItem it : timers) {
            if (it.state == TimerItem.RUNNING) {
                it.remaining = Math.max(0L, it.deadline - at);
                it.state = TimerItem.PAUSED;
                if (it.remaining <= 0) {
                    it.rewind();
                }
            }
        }
        PrefsStore.saveTimers(c, timers);
        PrefsStore.Sw sw = PrefsStore.loadSw(c);
        if (sw.running) {
            sw.accum = sw.current(at);
            sw.running = false;
            PrefsStore.saveSw(c, sw);
        }
        App.setLanguage(c, PrefsStore.getLang(c));
        TimerOps.refreshSurfaces(c);
    }
}
