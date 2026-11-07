package com.example.timerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Badges earned from the study history, and a notification the first time each one is. */
public final class Achievements {

    public static final int COUNT = 0;
    public static final int MINUTES = 1;
    public static final int DAYS = 2;

    /** One badge: reached when its measure gets to target. */
    public static final class Badge {
        public final String id;
        public final int icon;
        public final int name;
        public final int desc;
        public final int unit;
        public final int target;

        Badge(String id, int icon, int name, int desc, int unit, int target) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.desc = desc;
            this.unit = unit;
            this.target = target;
        }
    }

    public static final Badge[] ALL = {
            new Badge("first", R.drawable.ic_star, R.string.b_first, R.string.b_first_d, COUNT, 1),
            new Badge("h1", R.drawable.ic_tab_timer, R.string.b_h1, R.string.b_h1_d, MINUTES, 60),
            new Badge("h10", R.drawable.ic_tab_timer, R.string.b_h10, R.string.b_h10_d, MINUTES, 600),
            new Badge("h50", R.drawable.ic_tab_timer, R.string.b_h50, R.string.b_h50_d, MINUTES, 3000),
            new Badge("h100", R.drawable.ic_tab_timer, R.string.b_h100, R.string.b_h100_d, MINUTES, 6000),
            new Badge("s3", R.drawable.ic_flame, R.string.b_s3, R.string.b_s3_d, DAYS, 3),
            new Badge("s7", R.drawable.ic_flame, R.string.b_s7, R.string.b_s7_d, DAYS, 7),
            new Badge("s30", R.drawable.ic_flame, R.string.b_s30, R.string.b_s30_d, DAYS, 30),
            new Badge("early", R.drawable.ic_sun, R.string.b_early, R.string.b_early_d, COUNT, 1),
            new Badge("owl", R.drawable.ic_moon, R.string.b_owl, R.string.b_owl_d, COUNT, 1),
            new Badge("deep", R.drawable.ic_bolt, R.string.b_deep, R.string.b_deep_d, MINUTES, 240),
            new Badge("marathon", R.drawable.ic_flag, R.string.b_marathon, R.string.b_marathon_d, MINUTES, 120),
            new Badge("explorer", R.drawable.ic_tab_proj, R.string.b_explorer, R.string.b_explorer_d, COUNT, 3),
            new Badge("laser", R.drawable.ic_target, R.string.b_laser, R.string.b_laser_d, COUNT, 1),
    };

    private static final String K_SEEN = "badges_seen";
    private static final String CHANNEL = "focus_achievements";

    private Achievements() {
    }

    /** How far along each badge is, in its own unit (index-aligned with ALL). */
    public static int[] progress(List<PrefsStore.Hist> history) {
        long totalMs = 0;
        long longestSession = 0;
        int early = 0;
        int owl = 0;
        int laser = 0;
        Set<Long> projects = new HashSet<Long>();
        Map<Long, Long> perDay = new HashMap<Long, Long>();
        Calendar cal = Calendar.getInstance();
        for (PrefsStore.Hist h : history) {
            totalMs += h.millis;
            longestSession = Math.max(longestSession, h.millis);
            if (h.project != 0) {
                projects.add(h.project);
            }
            long d = Insights.dayKey(h.ts);
            Long v = perDay.get(d);
            perDay.put(d, (v == null ? 0L : v) + h.millis);
            cal.setTimeInMillis(Insights.startOf(h));
            int startHour = cal.get(Calendar.HOUR_OF_DAY);
            if (startHour >= 4 && startHour < 7) {
                early++;
            }
            cal.setTimeInMillis(h.ts);
            int endHour = cal.get(Calendar.HOUR_OF_DAY);
            if (endHour == 23 || endHour < 4) {
                owl++;
            }
            if (h.score == 100 && h.millis >= 25 * 60_000L) {
                laser++;
            }
        }
        long bestDay = 0;
        for (long v : perDay.values()) {
            bestDay = Math.max(bestDay, v);
        }
        int totalMin = (int) (totalMs / 60_000L);
        int streak = Insights.longestStreak(history);
        int[] p = new int[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            String id = ALL[i].id;
            if (id.equals("first")) {
                p[i] = history.size();
            } else if (id.startsWith("h")) {
                p[i] = totalMin;
            } else if (id.startsWith("s")) {
                p[i] = streak;
            } else if (id.equals("early")) {
                p[i] = early;
            } else if (id.equals("owl")) {
                p[i] = owl;
            } else if (id.equals("deep")) {
                p[i] = (int) (bestDay / 60_000L);
            } else if (id.equals("marathon")) {
                p[i] = (int) (longestSession / 60_000L);
            } else if (id.equals("explorer")) {
                p[i] = projects.size();
            } else if (id.equals("laser")) {
                p[i] = laser;
            }
        }
        return p;
    }

    /** "6h 20m / 10h", "4 / 7 days" or "1 / 3". */
    public static String progressText(Context c, Badge b, int value) {
        int v = Math.min(value, b.target);
        if (b.unit == MINUTES) {
            return c.getString(R.string.progress_of, Projects.hours(v * 60_000L),
                    Projects.hours(b.target * 60_000L));
        }
        if (b.unit == DAYS) {
            return c.getString(R.string.progress_of, String.valueOf(v),
                    c.getResources().getQuantityString(R.plurals.days, b.target, b.target));
        }
        return c.getString(R.string.progress_of, String.valueOf(v), String.valueOf(b.target));
    }

    /**
     * Run at app start: the first time ever, records what's already earned without
     * notifying, so updating the app doesn't bring a flood of old badges.
     */
    public static void baseline(Context c) {
        if (PrefsStore.raw(c).getString(K_SEEN, null) == null) {
            check(c, PrefsStore.loadHistory(c));
        }
    }

    /**
     * Notifies about badges earned since the last check (the very first check, see
     * baseline, only records them).
     */
    public static void check(Context c, List<PrefsStore.Hist> history) {
        try {
            int[] p = progress(history);
            SharedPreferences sp = PrefsStore.raw(c);
            String raw = sp.getString(K_SEEN, null);
            Set<String> seen = new HashSet<String>();
            if (raw != null) {
                for (String s : raw.split(",")) {
                    seen.add(s);
                }
            }
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < ALL.length; i++) {
                if (p[i] < ALL[i].target) {
                    continue;
                }
                if (all.length() > 0) {
                    all.append(',');
                }
                all.append(ALL[i].id);
                if (raw != null && !seen.contains(ALL[i].id)) {
                    notifyBadge(c, ALL[i], i);
                }
            }
            sp.edit().putString(K_SEEN, all.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void notifyBadge(Context c, Badge b, int index) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.ch_achievements), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.ch_achievements_desc));
        nm.createNotificationChannel(ch);
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("tab", 3);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_star)
                .setColor(Clay.accentColor(c))
                .setContentTitle(c.getString(R.string.badge_unlocked, c.getString(b.name)))
                .setContentText(c.getString(b.desc))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0x60000 + index, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
        nm.notify("badge", index, n);
    }
}
