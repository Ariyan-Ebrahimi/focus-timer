package com.example.timerapp;

import android.content.Context;

import java.util.Calendar;
import java.util.List;

/** Project colors and study-time statistics computed from History. */
public final class Projects {

    /**
     * Soft project colors that match the clay look, in a fixed order (validated for
     * color-vision deficiency and distinctness on both surfaces). Assigned in order and
     * never cycled, so at most 8 projects; the name is always shown beside the color.
     */
    private static final int[] LIGHT = {0xFF6C8EEF, 0xFFF07549, 0xFF1FA38C, 0xFFDDA032,
            0xFF9B7FE6, 0xFF78B062, 0xFFE07AA8, 0xFF4DB3E6};
    private static final int[] DARK = {0xFF5F80E0, 0xFFDB6E48, 0xFF1F9A86, 0xFFBF8820,
            0xFF8F73DE, 0xFF64A04F, 0xFFD06A9F, 0xFF3A9ACF};
    public static final int[] COLOR_NAMES = {R.string.c_periwinkle, R.string.c_coral,
            R.string.c_teal, R.string.c_honey, R.string.c_lavender, R.string.c_sage,
            R.string.c_rose, R.string.c_sky};
    public static final int MAX = LIGHT.length;
    public static final long DAY = 24L * 3600L * 1000L;

    private Projects() {
    }

    public static int color(Context c, int slot) {
        int[] p = Clay.isDark(c) ? DARK : LIGHT;
        return p[Math.max(0, Math.min(slot, p.length - 1))];
    }

    /** First palette slot not used by an existing project, or -1 when all are taken. */
    public static int nextColor(List<PrefsStore.Project> list) {
        for (int slot = 0; slot < MAX; slot++) {
            boolean used = false;
            for (PrefsStore.Project p : list) {
                if (p.color == slot) {
                    used = true;
                }
            }
            if (!used) {
                return slot;
            }
        }
        return -1;
    }

    public static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Start of the current week (Monday 00:00). */
    public static long startOfWeek() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startOfToday());
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int back = (dow + 5) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -back);
        return cal.getTimeInMillis();
    }

    public static class Stats {
        public long today;
        public long week;
        public long total;
        public int sessions;
        /** Last 7 days, oldest first; index 6 is today. */
        public final long[] days = new long[7];
    }

    /** Study time for one project (project = -1 counts everything). */
    public static Stats stats(List<PrefsStore.Hist> history, long project) {
        Stats s = new Stats();
        long today = startOfToday();
        long week = startOfWeek();
        long first = today - 6 * DAY;
        for (PrefsStore.Hist h : history) {
            if (project >= 0 && h.project != project) {
                continue;
            }
            s.total += h.millis;
            s.sessions++;
            if (h.ts >= today) {
                s.today += h.millis;
            }
            if (h.ts >= week) {
                s.week += h.millis;
            }
            if (h.ts >= first) {
                int idx = (int) ((h.ts - first) / DAY);
                if (idx >= 0 && idx < 7) {
                    s.days[idx] += h.millis;
                }
            }
        }
        return s;
    }

    /** Whole days ago (0 = today) for a timestamp. */
    public static int daysAgo(long ts) {
        long today = startOfToday();
        if (ts >= today) {
            return 0;
        }
        return (int) ((today - ts + DAY - 1) / DAY);
    }

    /**
     * Consecutive days with at least a minute of study, ending today (or yesterday, so a
     * streak isn't shown as broken before today's session).
     */
    public static int streak(List<PrefsStore.Hist> history) {
        java.util.Map<Integer, Long> perDay = new java.util.HashMap<Integer, Long>();
        for (PrefsStore.Hist h : history) {
            int d = daysAgo(h.ts);
            Long v = perDay.get(d);
            perDay.put(d, (v == null ? 0L : v) + h.millis);
        }
        int start = has(perDay, 0) ? 0 : 1;
        int n = 0;
        while (has(perDay, start + n)) {
            n++;
        }
        return n;
    }

    private static boolean has(java.util.Map<Integer, Long> perDay, int day) {
        Long v = perDay.get(day);
        return v != null && v >= 60_000L;
    }

    /** "3h 20m", "45m", "0m" - study totals don't need seconds. */
    public static String hours(long ms) {
        long m = ms / 60000L;
        if (m >= 60) {
            return App.str(R.string.fmt_hm, m / 60, m % 60);
        }
        return App.str(R.string.fmt_m, m);
    }
}
