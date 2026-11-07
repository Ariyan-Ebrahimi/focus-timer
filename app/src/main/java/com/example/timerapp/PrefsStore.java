package com.example.timerapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class PrefsStore {

    private static final String P = "focus_prefs";
    private static final String K_TIMERS = "timers_v1";
    private static final String K_HISTORY = "history_v1";
    private static final String K_MODE = "theme_mode";
    private static final String K_ACC = "theme_acc";
    private static final String K_SOUND_URI = "sound_uri";
    private static final String K_SOUND_NAME = "sound_name";
    private static final int MAX_HISTORY = 5000;

    private PrefsStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('|', ' ').replace(';', ' ');
    }

    private static String cleanStep(String s) {
        return clean(s).replace('~', ' ').replace(',', ' ');
    }

    public static List<TimerItem> loadTimers(Context c) {
        List<TimerItem> out = new ArrayList<TimerItem>();
        String raw = prefs(c).getString(K_TIMERS, "");
        if (raw == null || raw.length() == 0) {
            return out;
        }
        for (String row : raw.split(";")) {
            String[] p = row.split("\\|", -1);
            if (p.length < 6) {
                continue;
            }
            try {
                TimerItem it = new TimerItem(Long.parseLong(p[0]), p[1], Long.parseLong(p[2]));
                it.remaining = Long.parseLong(p[3]);
                it.state = Integer.parseInt(p[4]);
                it.deadline = Long.parseLong(p[5]);
                it.pinned = p.length >= 7 && "1".equals(p[6]);
                if (p.length >= 13) {
                    it.span = Long.parseLong(p[7]);
                    it.rounds = Math.max(1, Integer.parseInt(p[8]));
                    it.stepIdx = Integer.parseInt(p[9]);
                    it.round = Integer.parseInt(p[10]);
                    it.bonus = "1".equals(p[11]);
                    if (p[12].length() > 0) {
                        for (String st : p[12].split(",")) {
                            String[] q = st.split("~", -1);
                            if (q.length >= 3) {
                                it.steps.add(new TimerItem.Step(q[0], Long.parseLong(q[1]),
                                        "1".equals(q[2])));
                            }
                        }
                    }
                    if (p.length >= 14) {
                        it.projectId = Long.parseLong(p[13]);
                    }
                    if (p.length >= 15) {
                        it.pauses = Integer.parseInt(p[14]);
                    }
                    if (it.isSequence()) {
                        it.stepIdx = Math.max(0, Math.min(it.stepIdx, it.steps.size() - 1));
                        it.round = Math.max(0, Math.min(it.round, it.rounds - 1));
                    }
                } else {
                    it.span = it.total;
                }
                out.add(it);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static void saveTimers(Context c, List<TimerItem> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            TimerItem it = list.get(i);
            if (i > 0) {
                sb.append(';');
            }
            sb.append(it.id).append('|').append(clean(it.name)).append('|')
                    .append(it.total).append('|').append(it.remaining).append('|')
                    .append(it.state).append('|').append(it.deadline)
                    .append('|').append(it.pinned ? "1" : "0")
                    .append('|').append(it.span).append('|').append(it.rounds)
                    .append('|').append(it.stepIdx).append('|').append(it.round)
                    .append('|').append(it.bonus ? "1" : "0").append('|');
            StringBuilder steps = new StringBuilder();
            for (int k = 0; k < it.steps.size(); k++) {
                TimerItem.Step st = it.steps.get(k);
                if (k > 0) {
                    steps.append(',');
                }
                steps.append(cleanStep(st.name)).append('~').append(st.ms).append('~')
                        .append(st.rest ? "1" : "0");
            }
            sb.append(steps).append('|').append(it.projectId).append('|').append(it.pauses);
        }
        prefs(c).edit().putString(K_TIMERS, sb.toString()).apply();
    }

    private static final String K_SW_RUN = "sw_running";
    private static final String K_SW_BASE = "sw_base";
    private static final String K_SW_ACC = "sw_accum";
    private static final String K_SW_LAPS = "sw_laps";
    private static final String K_SW_PROJ = "sw_project";
    private static final String K_SW_PAUSES = "sw_pauses";

    /** Stopwatch state; base is wall-clock ms when the current run segment started. */
    public static class Sw {
        public boolean running;
        public long base;
        public long accum;
        public List<Long> laps = new ArrayList<Long>();
        /** Project the stopwatch's time is logged to, 0 for none. */
        public long project;
        /** Times paused during this run: lowers its focus score. */
        public int pauses;

        public long current(long now) {
            return running ? accum + Math.max(0L, now - base) : accum;
        }
    }

    public static Sw loadSw(Context c) {
        SharedPreferences sp = prefs(c);
        Sw s = new Sw();
        s.running = sp.getBoolean(K_SW_RUN, false);
        s.base = sp.getLong(K_SW_BASE, 0L);
        s.accum = sp.getLong(K_SW_ACC, 0L);
        s.project = sp.getLong(K_SW_PROJ, 0L);
        s.pauses = sp.getInt(K_SW_PAUSES, 0);
        String raw = sp.getString(K_SW_LAPS, "");
        if (raw != null && raw.length() > 0) {
            for (String p : raw.split(",")) {
                try {
                    s.laps.add(Long.parseLong(p));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return s;
    }

    public static void saveSw(Context c, Sw s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.laps.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(s.laps.get(i));
        }
        prefs(c).edit().putBoolean(K_SW_RUN, s.running).putLong(K_SW_BASE, s.base)
                .putLong(K_SW_ACC, s.accum).putString(K_SW_LAPS, sb.toString())
                .putLong(K_SW_PROJ, s.project).putInt(K_SW_PAUSES, s.pauses).apply();
    }

    /**
     * Logs a stopwatch run as a study session (at least a minute) and clears it.
     * Returns true when something was logged.
     */
    public static boolean logStopwatch(Context c, Sw s, long now, String name) {
        long t = s.current(now);
        int pauses = s.pauses;
        s.running = false;
        s.accum = 0;
        s.pauses = 0;
        s.laps.clear();
        if (t < 60_000L) {
            return false;
        }
        // a stopwatch has no planned length, so only pauses count against it
        addHistory(c, name, t, s.project, Insights.score(pauses, 1f));
        return true;
    }

    public static class Hist {
        public long ts;
        public String name;
        public long millis;
        /** Project this session counts toward, 0 for none. */
        public long project;
        /** What was worked on, "" for none. */
        public String note = "";
        /** Focus score 0-100, -1 when unknown (added by hand, or logged before scores). */
        public int score = -1;

        public Hist(long ts, String name, long millis, long project) {
            this.ts = ts;
            this.name = name;
            this.millis = millis;
            this.project = project;
        }
    }

    /** Notes are one line; separators used by the storage format are replaced. */
    public static String cleanNote(String s) {
        return clean(s).replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static List<Hist> loadHistory(Context c) {
        List<Hist> out = new ArrayList<Hist>();
        String raw = prefs(c).getString(K_HISTORY, "");
        if (raw == null || raw.length() == 0) {
            return out;
        }
        for (String row : raw.split(";")) {
            String[] p = row.split("\\|", -1);
            if (p.length < 3) {
                continue;
            }
            try {
                Hist h = new Hist(Long.parseLong(p[0]), p[1], Long.parseLong(p[2]),
                        p.length >= 4 ? Long.parseLong(p[3]) : 0L);
                if (p.length >= 5) {
                    h.note = p[4];
                }
                if (p.length >= 6 && p[5].length() > 0) {
                    h.score = Integer.parseInt(p[5]);
                }
                out.add(h);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static void saveHistory(Context c, List<Hist> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Hist h = list.get(i);
            if (i > 0) {
                sb.append(';');
            }
            sb.append(h.ts).append('|').append(h.name).append('|').append(h.millis)
                    .append('|').append(h.project).append('|').append(cleanNote(h.note))
                    .append('|').append(h.score);
        }
        prefs(c).edit().putString(K_HISTORY, sb.toString()).apply();
        // today's total and the streak are on the home-screen widget
        TimerWidget.refresh(c);
        Achievements.check(c, list);
    }

    private static final String K_LAST_LOG = "last_log";

    public static void addHistory(Context c, String name, long millis, long project) {
        addHistory(c, name, millis, project, -1);
    }

    /** Logs a finished (or stopped) session with its focus score (-1: unknown). */
    public static void addHistory(Context c, String name, long millis, long project, int score) {
        List<Hist> list = loadHistory(c);
        long ts = System.currentTimeMillis();
        Hist added = new Hist(ts, clean(name), millis, project);
        added.score = score;
        list.add(0, added);
        while (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
        prefs(c).edit().putLong(K_LAST_LOG, ts).apply();
        saveHistory(c, list);
    }

    /** When the last session was logged automatically (finished or stopped timer). */
    public static long getLastLog(Context c) {
        return prefs(c).getLong(K_LAST_LOG, 0L);
    }

    /** The session logged at ts, or null if it's gone. */
    public static Hist findHistory(Context c, long ts) {
        if (ts <= 0) {
            return null;
        }
        for (Hist h : loadHistory(c)) {
            if (h.ts == ts) {
                return h;
            }
        }
        return null;
    }

    public static void setNote(Context c, long ts, String note) {
        List<Hist> list = loadHistory(c);
        for (Hist h : list) {
            if (h.ts == ts) {
                h.note = cleanNote(note);
                saveHistory(c, list);
                return;
            }
        }
    }

    private static final String K_LAST_BACKUP = "last_backup";

    public static long getLastBackup(Context c) {
        return prefs(c).getLong(K_LAST_BACKUP, 0L);
    }

    public static void setLastBackup(Context c, long ts) {
        prefs(c).edit().putLong(K_LAST_BACKUP, ts).apply();
    }

    /** The raw preferences, for backup and restore. */
    static SharedPreferences raw(Context c) {
        return prefs(c);
    }

    /** Detaches a deleted project's sessions (they stay in History, unassigned). */
    public static void unlinkHistory(Context c, long project) {
        List<Hist> list = loadHistory(c);
        for (Hist h : list) {
            if (h.project == project) {
                h.project = 0;
            }
        }
        saveHistory(c, list);
    }

    // ---------------------------------------------------------------- projects

    private static final String K_PROJECTS = "projects_v1";

    public static class Project {
        public long id;
        public String name;
        /** Index into the fixed project palette (assigned in order, never cycled). */
        public int color;
        /** Weekly goal in minutes, 0 for none. */
        public int goalMin;

        public Project(long id, String name, int color, int goalMin) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.goalMin = goalMin;
        }
    }

    public static List<Project> loadProjects(Context c) {
        List<Project> out = new ArrayList<Project>();
        String raw = prefs(c).getString(K_PROJECTS, "");
        if (raw == null || raw.length() == 0) {
            return out;
        }
        for (String row : raw.split(";")) {
            String[] p = row.split("\\|", -1);
            if (p.length < 4) {
                continue;
            }
            try {
                out.add(new Project(Long.parseLong(p[0]), p[1], Integer.parseInt(p[2]),
                        Integer.parseInt(p[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static void saveProjects(Context c, List<Project> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Project p = list.get(i);
            if (i > 0) {
                sb.append(';');
            }
            sb.append(p.id).append('|').append(clean(p.name)).append('|').append(p.color)
                    .append('|').append(p.goalMin);
        }
        prefs(c).edit().putString(K_PROJECTS, sb.toString()).apply();
    }

    public static Project findProject(Context c, long id) {
        if (id == 0) {
            return null;
        }
        for (Project p : loadProjects(c)) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    /** Replaces the session logged at ts (manual edit); a null h deletes it. */
    public static void replaceHistory(Context c, long ts, Hist h) {
        List<Hist> list = loadHistory(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).ts == ts) {
                if (h == null) {
                    list.remove(i);
                } else {
                    h.name = clean(h.name);
                    h.note = cleanNote(h.note);
                    list.set(i, h);
                }
                break;
            }
        }
        sortHistory(list);
        saveHistory(c, list);
    }

    /** Adds a session at a chosen time (manual entry). */
    public static void insertHistory(Context c, Hist h) {
        List<Hist> list = loadHistory(c);
        h.name = clean(h.name);
        list.add(h);
        sortHistory(list);
        while (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
        saveHistory(c, list);
    }

    private static void sortHistory(List<Hist> list) {
        java.util.Collections.sort(list, new java.util.Comparator<Hist>() {
            @Override
            public int compare(Hist a, Hist b) {
                return a.ts == b.ts ? 0 : (a.ts > b.ts ? -1 : 1);
            }
        });
    }

    // ---------------------------------------------------------------- focus options

    private static final String K_DND = "focus_dnd";
    private static final String K_DND_ACTIVE = "focus_dnd_active";
    private static final String K_DND_PREV = "focus_dnd_prev";
    // v2: the sound list changed (rain, wind, fire, piano), so old choices don't carry over
    private static final String K_SOUND_BG = "focus_sound_v2";
    private static final String K_LANG = "app_lang";

    // ---------------------------------------------------------------- music & shield

    private static final String K_SLEEP = "music_sleep_min";
    private static final String K_SHIELD = "shield_on";
    private static final String K_SHIELD_APPS = "shield_apps";

    /** Minutes the background sound keeps playing after focus ends (0 = stop at once). */
    public static int getSleepMin(Context c) {
        return prefs(c).getInt(K_SLEEP, 0);
    }

    public static void setSleepMin(Context c, int v) {
        prefs(c).edit().putInt(K_SLEEP, v).apply();
    }

    public static boolean getShield(Context c) {
        return prefs(c).getBoolean(K_SHIELD, false);
    }

    public static void setShield(Context c, boolean v) {
        prefs(c).edit().putBoolean(K_SHIELD, v).apply();
    }

    /** Packages the shield guards; null when never chosen (use the defaults). */
    public static java.util.Set<String> getShieldApps(Context c) {
        String raw = prefs(c).getString(K_SHIELD_APPS, null);
        if (raw == null) {
            return null;
        }
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String s : raw.split(",")) {
            if (s.length() > 0) {
                out.add(s);
            }
        }
        return out;
    }

    public static void setShieldApps(Context c, java.util.Set<String> apps) {
        StringBuilder sb = new StringBuilder();
        for (String s : apps) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(s);
        }
        prefs(c).edit().putString(K_SHIELD_APPS, sb.toString()).apply();
    }

    private static final String K_WEEKLY = "weekly_report";

    /** The Sunday evening summary notification (on unless turned off). */
    public static boolean getWeeklyReport(Context c) {
        return prefs(c).getBoolean(K_WEEKLY, true);
    }

    public static void setWeeklyReport(Context c, boolean v) {
        prefs(c).edit().putBoolean(K_WEEKLY, v).apply();
    }

    public static boolean getDnd(Context c) {
        return prefs(c).getBoolean(K_DND, false);
    }

    public static void setDnd(Context c, boolean v) {
        prefs(c).edit().putBoolean(K_DND, v).apply();
    }

    /** Whether we switched DND on (and which filter to restore afterwards). */
    public static boolean getDndActive(Context c) {
        return prefs(c).getBoolean(K_DND_ACTIVE, false);
    }

    public static int getDndPrev(Context c) {
        return prefs(c).getInt(K_DND_PREV, 1);
    }

    public static void setDndActive(Context c, boolean active, int prev) {
        prefs(c).edit().putBoolean(K_DND_ACTIVE, active).putInt(K_DND_PREV, prev).apply();
    }

    /** Background sound while focusing: 0 off, then FocusSound.NAMES order. */
    /** Strict mode: focus sessions can't be paused or stopped until they end. */
    public static boolean getStrict(Context c) {
        return prefs(c).getBoolean("strict_mode", false);
    }

    public static void setStrict(Context c, boolean on) {
        prefs(c).edit().putBoolean("strict_mode", on).apply();
    }

    public static int getFocusSound(Context c) {
        // the separate piano pieces (4-8) became one nonstop piano choice (4)
        int v = prefs(c).getInt(K_SOUND_BG, 0);
        return v >= 0 && v < Focus.SOUND_COUNT ? v : 0;
    }

    public static void setFocusSound(Context c, int v) {
        prefs(c).edit().putInt(K_SOUND_BG, v).apply();
    }

    /** App language tag ("" = follow the phone). */
    public static String getLang(Context c) {
        String s = prefs(c).getString(K_LANG, "");
        return s == null ? "" : s;
    }

    public static void setLang(Context c, String tag) {
        prefs(c).edit().putString(K_LANG, tag).apply();
    }

    public static void clearHistory(Context c) {
        prefs(c).edit().remove(K_HISTORY).apply();
    }

    public static int getMode(Context c) {
        return prefs(c).getInt(K_MODE, ThemeHelper.MODE_LIGHT);
    }

    public static void setMode(Context c, int mode) {
        prefs(c).edit().putInt(K_MODE, mode).apply();
    }

    public static int getAcc(Context c) {
        return prefs(c).getInt(K_ACC, ThemeHelper.ACC_BLUE);
    }

    public static void setAcc(Context c, int acc) {
        prefs(c).edit().putInt(K_ACC, acc).apply();
    }

    public static String getSoundUri(Context c) {
        String s = prefs(c).getString(K_SOUND_URI, "");
        return s == null ? "" : s;
    }

    public static String getSoundName(Context c) {
        // "" = system default (shown localized by the UI)
        String s = prefs(c).getString(K_SOUND_NAME, "");
        return s == null || s.equals("System default") ? "" : s;
    }

    public static void setSound(Context c, String uri, String name) {
        prefs(c).edit().putString(K_SOUND_URI, uri == null ? "" : uri)
                .putString(K_SOUND_NAME, name == null ? "" : name).apply();
    }

    private static final String K_PF = "pomo_focus";
    private static final String K_PS = "pomo_short";
    private static final String K_PL = "pomo_long";
    private static final String K_PR = "pomo_rounds";
    private static final String K_PA = "pomo_auto";
    private static final String K_PPH = "pomo_phase";
    private static final String K_PDN = "pomo_done";
    private static final String K_PST = "pomo_state";
    private static final String K_PDL = "pomo_deadline";
    private static final String K_PRM = "pomo_remaining";

    public static int getPomoFocus(Context c) {
        return prefs(c).getInt(K_PF, 25);
    }

    public static void setPomoFocus(Context c, int v) {
        prefs(c).edit().putInt(K_PF, v).apply();
    }

    public static int getPomoShort(Context c) {
        return prefs(c).getInt(K_PS, 5);
    }

    public static void setPomoShort(Context c, int v) {
        prefs(c).edit().putInt(K_PS, v).apply();
    }

    public static int getPomoLong(Context c) {
        return prefs(c).getInt(K_PL, 15);
    }

    public static void setPomoLong(Context c, int v) {
        prefs(c).edit().putInt(K_PL, v).apply();
    }

    public static int getPomoRounds(Context c) {
        return prefs(c).getInt(K_PR, 4);
    }

    public static void setPomoRounds(Context c, int v) {
        prefs(c).edit().putInt(K_PR, v).apply();
    }

    public static boolean getPomoAuto(Context c) {
        return prefs(c).getBoolean(K_PA, true);
    }

    public static void setPomoAuto(Context c, boolean v) {
        prefs(c).edit().putBoolean(K_PA, v).apply();
    }

    public static class Pomo {
        public int phase;
        public int done;
        public int state;
        public long deadline;
        public long remaining;
    }

    public static Pomo loadPomo(Context c) {
        Pomo p = new Pomo();
        SharedPreferences sp = prefs(c);
        p.phase = sp.getInt(K_PPH, 0);
        p.done = sp.getInt(K_PDN, 0);
        p.state = sp.getInt(K_PST, 0);
        p.deadline = sp.getLong(K_PDL, 0L);
        p.remaining = sp.getLong(K_PRM, getPomoFocus(c) * 60000L);
        if (p.phase < 0 || p.phase > 2) {
            p.phase = 0;
        }
        return p;
    }

    public static void savePomo(Context c, Pomo p) {
        prefs(c).edit().putInt(K_PPH, p.phase).putInt(K_PDN, p.done)
                .putInt(K_PST, p.state).putLong(K_PDL, p.deadline)
                .putLong(K_PRM, p.remaining).apply();
    }
}
