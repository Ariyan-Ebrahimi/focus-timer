package com.example.timerapp;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Focus scores and the numbers behind the weekly report and achievements. */
public final class Insights {

    /** Time-of-day buckets, by when a session started. */
    public static final int MORNING = 0;   // 5:00 - 12:00
    public static final int AFTERNOON = 1; // 12:00 - 17:00
    public static final int EVENING = 2;   // 17:00 - 22:00
    public static final int NIGHT = 3;     // 22:00 - 5:00
    public static final int[] PART_NAMES = {R.string.part_morning, R.string.part_afternoon,
            R.string.part_evening, R.string.part_night};

    private Insights() {
    }

    /**
     * Focus score 0-100: each pause costs 10 (at most 40), and stopping early scales it
     * down toward half by how much of the planned time was left.
     */
    public static int score(int pauses, float completion) {
        float s = 100 - Math.min(40, Math.max(0, pauses) * 10);
        float done = Math.max(0f, Math.min(1f, completion));
        return Math.round(s * (0.5f + 0.5f * done));
    }

    /** When a session started (it is logged when it ends). */
    public static long startOf(PrefsStore.Hist h) {
        return h.ts - h.millis;
    }

    public static int partOfDay(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return MORNING;
        }
        if (hour >= 12 && hour < 17) {
            return AFTERNOON;
        }
        if (hour >= 17 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }

    /** Local calendar day number (so days split at local midnight). */
    public static long dayKey(long ts) {
        return (ts + TimeZone.getDefault().getOffset(ts)) / Projects.DAY;
    }

    /** Average score of scored sessions since `since`, or -1 if none. */
    public static int averageScore(List<PrefsStore.Hist> history, long since) {
        int n = 0;
        long sum = 0;
        for (PrefsStore.Hist h : history) {
            if (h.ts >= since && h.score >= 0) {
                sum += h.score;
                n++;
            }
        }
        return n == 0 ? -1 : Math.round(sum / (float) n);
    }

    /**
     * The part of the day with the best average score over the last 30 days, needing
     * at least 3 scored sessions there to count. Returns {part, average} or null.
     */
    public static int[] bestPartOfDay(List<PrefsStore.Hist> history) {
        long since = System.currentTimeMillis() - 30 * Projects.DAY;
        long[] sum = new long[4];
        int[] n = new int[4];
        for (PrefsStore.Hist h : history) {
            if (h.ts >= since && h.score >= 0) {
                int p = partOfDay(startOf(h));
                sum[p] += h.score;
                n[p]++;
            }
        }
        int best = -1;
        float bestAvg = -1;
        for (int p = 0; p < 4; p++) {
            if (n[p] >= 3 && sum[p] / (float) n[p] > bestAvg) {
                best = p;
                bestAvg = sum[p] / (float) n[p];
            }
        }
        return best < 0 ? null : new int[]{best, Math.round(bestAvg)};
    }

    /** Longest run of consecutive days with at least a minute of study. */
    public static int longestStreak(List<PrefsStore.Hist> history) {
        Map<Long, Long> perDay = new HashMap<Long, Long>();
        for (PrefsStore.Hist h : history) {
            long d = dayKey(h.ts);
            Long v = perDay.get(d);
            perDay.put(d, (v == null ? 0L : v) + h.millis);
        }
        int best = 0;
        for (Map.Entry<Long, Long> e : perDay.entrySet()) {
            if (e.getValue() < 60_000L) {
                continue;
            }
            Long prev = perDay.get(e.getKey() - 1);
            if (prev != null && prev >= 60_000L) {
                continue; // not the start of a run
            }
            int len = 0;
            Long v;
            while ((v = perDay.get(e.getKey() + len)) != null && v >= 60_000L) {
                len++;
            }
            best = Math.max(best, len);
        }
        return best;
    }

    /** One week's numbers for the report. */
    public static final class Week {
        public long total;
        public long lastWeek;
        /** Day of the week (Calendar.DAY_OF_WEEK) with the most study, or 0. */
        public int bestDay;
        public long bestDayMs;
        public PrefsStore.Project topProject;
        public long topProjectMs;
        public int avgScore = -1;
    }

    /** This week (from Monday) and the whole week before it. */
    public static Week week(List<PrefsStore.Hist> history, List<PrefsStore.Project> projects) {
        Week w = new Week();
        long start = Projects.startOfWeek();
        long lastStart = start - 7 * Projects.DAY;
        Map<Integer, Long> perDay = new HashMap<Integer, Long>();
        Map<Long, Long> perProject = new HashMap<Long, Long>();
        Calendar cal = Calendar.getInstance();
        for (PrefsStore.Hist h : history) {
            if (h.ts >= start) {
                w.total += h.millis;
                cal.setTimeInMillis(h.ts);
                int d = cal.get(Calendar.DAY_OF_WEEK);
                Long v = perDay.get(d);
                perDay.put(d, (v == null ? 0L : v) + h.millis);
                if (h.project != 0) {
                    Long p = perProject.get(h.project);
                    perProject.put(h.project, (p == null ? 0L : p) + h.millis);
                }
            } else if (h.ts >= lastStart) {
                w.lastWeek += h.millis;
            }
        }
        for (Map.Entry<Integer, Long> e : perDay.entrySet()) {
            if (e.getValue() > w.bestDayMs) {
                w.bestDay = e.getKey();
                w.bestDayMs = e.getValue();
            }
        }
        for (PrefsStore.Project p : projects) {
            Long v = perProject.get(p.id);
            if (v != null && v > w.topProjectMs) {
                w.topProject = p;
                w.topProjectMs = v;
            }
        }
        w.avgScore = averageScore(history, start);
        return w;
    }
}
