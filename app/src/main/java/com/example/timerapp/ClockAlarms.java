package com.example.timerapp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Clock alarms: ring at a chosen hour and minute, once or on chosen weekdays.
 * Stored as JSON in the app prefs; scheduled with AlarmManager.setAlarmClock so they
 * fire on time even in Doze and show in the status bar.
 */
public final class ClockAlarms {

    static final String KEY = "clock_alarms";
    static final String RING = "com.example.timerapp.CLOCK_RING";
    static final String STOP = "com.example.timerapp.CLOCK_STOP";
    static final String SNOOZE = "com.example.timerapp.CLOCK_SNOOZE";
    /** Rising-volume lengths offered, in seconds (0 = off). */
    static final int[] RISE_CHOICES = {0, 30, 60};
    /** Snooze lengths offered, in minutes. */
    static final int[] SNOOZE_CHOICES = {5, 10, 15};
    /** Monday ... Sunday as bits 0 ... 6. */
    static final int WEEKDAYS = 0x1F;
    static final int WEEKENDS = 0x60;
    static final int EVERY_DAY = 0x7F;

    private ClockAlarms() {
    }

    public static final class Alarm {
        public long id;
        public int hour;
        public int minute;
        /** Repeat days (bit 0 = Monday); 0 = rings once. */
        public int days;
        public String label = "";
        public boolean on = true;
        /** Minutes Snooze waits before ringing again. */
        public int snooze = 10;
        /** Wake-up missions to finish before it turns off (empty = a plain Stop). */
        public List<Missions.Mission> missions = new ArrayList<Missions.Mission>();
        /** Keep the alarm silent while a mission is being solved. */
        public boolean quiet = true;
        /** Seconds the sound takes to grow from soft to full (0 = full at once). */
        public int rise = 30;
    }

    // ------------------------------------------------------------------ storage

    public static List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<Alarm>();
        try {
            JSONArray a = new JSONArray(PrefsStore.raw(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Alarm al = new Alarm();
                al.id = o.getLong("id");
                al.hour = o.getInt("h");
                al.minute = o.getInt("m");
                al.days = o.optInt("d", 0);
                al.label = o.optString("l", "");
                al.on = o.optBoolean("on", true);
                al.snooze = o.optInt("s", 10);
                al.missions = Missions.fromJson(o.optJSONArray("ms"));
                al.quiet = o.optBoolean("q", true);
                al.rise = o.optInt("r", 0);
                out.add(al);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Alarm> list) {
        JSONArray a = new JSONArray();
        try {
            for (Alarm al : list) {
                JSONObject o = new JSONObject();
                o.put("id", al.id);
                o.put("h", al.hour);
                o.put("m", al.minute);
                o.put("d", al.days);
                o.put("l", al.label);
                o.put("on", al.on);
                o.put("s", al.snooze);
                o.put("ms", Missions.toJson(al.missions));
                o.put("q", al.quiet);
                o.put("r", al.rise);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        PrefsStore.raw(c).edit().putString(KEY, a.toString()).apply();
    }

    static Alarm find(Context c, long id) {
        for (Alarm al : load(c)) {
            if (al.id == id) {
                return al;
            }
        }
        return null;
    }

    /** Adds or replaces the alarm, saves, and schedules everything again. */
    public static void put(Context c, Alarm al) {
        List<Alarm> list = load(c);
        if (al.id == 0) {
            long max = 0;
            for (Alarm x : list) {
                max = Math.max(max, x.id);
            }
            al.id = max + 1;
            list.add(al);
        } else {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == al.id) {
                    list.set(i, al);
                }
            }
        }
        save(c, list);
        scheduleAll(c);
    }

    /** A one-time alarm `minutes` from now (a nap or a short break). */
    public static Alarm quick(Context c, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutes);
        Alarm al = new Alarm();
        al.hour = cal.get(Calendar.HOUR_OF_DAY);
        al.minute = cal.get(Calendar.MINUTE);
        put(c, al);
        return al;
    }

    /** All alarms, earliest time of day first. */
    public static List<Alarm> sorted(Context c) {
        List<Alarm> list = load(c);
        java.util.Collections.sort(list, new java.util.Comparator<Alarm>() {
            @Override
            public int compare(Alarm x, Alarm y) {
                return (x.hour * 60 + x.minute) - (y.hour * 60 + y.minute);
            }
        });
        return list;
    }

    /** "Today", "Tomorrow" or the weekday the alarm rings next. */
    public static String dayWord(Context c, long at) {
        long d = Insights.dayKey(at) - Insights.dayKey(System.currentTimeMillis());
        if (d == 0) {
            return c.getString(R.string.today);
        }
        if (d == 1) {
            return c.getString(R.string.clock_tomorrow);
        }
        return new java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
                .format(new java.util.Date(at));
    }

    public static void delete(Context c, long id) {
        List<Alarm> list = load(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == id) {
                list.remove(i);
            }
        }
        cancel(c, id);
        save(c, list);
        scheduleAll(c);
    }

    // ------------------------------------------------------------------ time

    /** Next moment this alarm rings, after `now`. */
    public static long next(Alarm al, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, al.hour);
        cal.set(Calendar.MINUTE, al.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            if (cal.getTimeInMillis() > now) {
                int bit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                if (al.days == 0 || (al.days & (1 << bit)) != 0) {
                    return cal.getTimeInMillis();
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    /** The soonest switched-on alarm, or null. */
    public static Alarm soonest(Context c) {
        long now = System.currentTimeMillis();
        Alarm best = null;
        for (Alarm al : load(c)) {
            if (al.on && (best == null || next(al, now) < next(best, now))) {
                best = al;
            }
        }
        return best;
    }

    public static String timeText(Context c, Alarm al) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, al.hour);
        cal.set(Calendar.MINUTE, al.minute);
        return android.text.format.DateFormat.getTimeFormat(c).format(cal.getTime());
    }

    /** "Once", "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
    public static String daysText(Context c, int days) {
        if (days == 0) {
            return c.getString(R.string.clock_once);
        }
        if (days == EVERY_DAY) {
            return c.getString(R.string.clock_every_day);
        }
        if (days == WEEKDAYS) {
            return c.getString(R.string.clock_weekdays);
        }
        if (days == WEEKENDS) {
            return c.getString(R.string.clock_weekends);
        }
        String[] names = dayNames();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(names[i]);
            }
        }
        return sb.toString();
    }

    /** Short weekday names, Monday first. */
    public static String[] dayNames() {
        String[] wd = new DateFormatSymbols().getShortWeekdays();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) {
            out[i] = wd[order[i]];
        }
        return out;
    }

    /** "9h 12m" until it rings (at least a minute); whole hours read "1 h". */
    public static String untilText(long at) {
        long ms = Math.max(60_000L, at - System.currentTimeMillis() + 59_999L);
        long min = ms / 60_000L;
        if (min >= 60 && min % 60 == 0) {
            return App.str(R.string.hour_n, (int) (min / 60));
        }
        return Projects.hours(ms);
    }

    // ------------------------------------------------------------------ scheduling

    private static PendingIntent ringIntent(Context c, long id, boolean snooze) {
        Intent i = new Intent(c, Receiver.class).setAction(RING).putExtra("id", id)
                .putExtra("snooze", snooze);
        return PendingIntent.getBroadcast(c, (snooze ? 0x5C000000 : 0x5A000000) + (int) id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAt(Context c, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent open = new Intent(c, ClockActivity.class);
        PendingIntent show = PendingIntent.getActivity(c, 0x5B000000, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
        } catch (SecurityException e) {
            // exact alarms not allowed: still ring, maybe a little late
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(ringIntent(c, id, false));
        }
    }

    /** Schedules every switched-on alarm (safe to call any time). */
    public static void scheduleAll(Context c) {
        long now = System.currentTimeMillis();
        for (Alarm al : load(c)) {
            if (al.on) {
                setAt(c, next(al, now), ringIntent(c, al.id, false));
            } else {
                cancel(c, al.id);
            }
        }
    }

    // ------------------------------------------------------------------ ringing

    static int notifId(long id) {
        return 0x5D000000 + (int) id;
    }

    static PendingIntent action(Context c, String act, long id, int req) {
        Intent i = new Intent(c, Receiver.class).setAction(act).putExtra("id", id);
        return PendingIntent.getBroadcast(c, req + (int) id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Rings: sound, vibration and the full-screen alarm, from AlarmService. */
    static void ring(Context c, Alarm al) {
        AlarmService.start(c, al.id);
    }

    static void stop(Context c, long id) {
        AlarmService.stop(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(notifId(id));
        }
    }

    /** Silences the alarm and rings it again after its snooze length; returns the minutes. */
    static int snooze(Context c, long id) {
        stop(c, id);
        Alarm al = find(c, id);
        int min = al != null ? al.snooze : 10;
        setAt(c, System.currentTimeMillis() + min * 60_000L, ringIntent(c, id, true));
        return min;
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            long id = i.getLongExtra("id", -1);
            String act = i.getAction();
            if (STOP.equals(act)) {
                Alarm al = find(c, id);
                // an alarm with a mission only turns off once the mission is done
                if (al == null || al.missions.isEmpty()) {
                    stop(c, id);
                }
                return;
            }
            if (SNOOZE.equals(act)) {
                int min = snooze(c, id);
                android.widget.Toast.makeText(c, c.getString(R.string.clock_snoozed_n, min),
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (!RING.equals(act)) {
                return;
            }
            Alarm al = find(c, id);
            if (al == null || (!al.on && !i.getBooleanExtra("snooze", false))) {
                return;
            }
            if (al.days == 0 && !i.getBooleanExtra("snooze", false)) {
                // a one-time alarm switches itself off after ringing
                al.on = false;
                put(c, al);
            } else {
                scheduleAll(c);
            }
            ring(c, al);
        }
    }
}
