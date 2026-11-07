package com.example.timerapp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Every Sunday at 19:00: a short notification summing up the week's study. */
public class WeeklyReport extends BroadcastReceiver {

    private static final String CHANNEL = "focus_weekly";
    private static final int HOUR = 19;

    @Override
    public void onReceive(Context c, Intent i) {
        if (PrefsStore.getWeeklyReport(c)) {
            post(c);
        }
        schedule(c);
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, WeeklyReport.class);
        return PendingIntent.getBroadcast(c, 0x7700, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets (or, when turned off, clears) the alarm for the next Sunday evening. */
    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pending(c);
        if (!PrefsStore.getWeeklyReport(c)) {
            am.cancel(pi);
            return;
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        next.set(Calendar.HOUR_OF_DAY, HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        while (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 7);
        }
        try {
            // a few minutes late is fine, so no exact-alarm permission is needed
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
        } catch (Exception ignored) {
        }
    }

    /** The report's lines: total, change from last week, best day, top project, focus. */
    public static List<String> lines(Context c) {
        Insights.Week w = Insights.week(PrefsStore.loadHistory(c), PrefsStore.loadProjects(c));
        List<String> out = new ArrayList<String>();
        if (w.total <= 0) {
            out.add(c.getString(R.string.week_empty));
            return out;
        }
        long diff = w.total - w.lastWeek;
        if (Math.abs(diff) < 60_000L) {
            out.add(c.getString(R.string.week_same));
        } else if (diff > 0) {
            out.add(c.getString(R.string.week_up, Projects.hours(diff)));
        } else {
            out.add(c.getString(R.string.week_down, Projects.hours(-diff)));
        }
        if (w.bestDay > 0) {
            String day = new DateFormatSymbols().getWeekdays()[w.bestDay];
            out.add(c.getString(R.string.week_best, day, Projects.hours(w.bestDayMs)));
        }
        if (w.topProject != null) {
            out.add(c.getString(R.string.week_top, w.topProject.name,
                    Projects.hours(w.topProjectMs)));
        }
        if (w.avgScore >= 0) {
            out.add(c.getString(R.string.week_focus, w.avgScore));
        }
        return out;
    }

    public static String title(Context c) {
        Insights.Week w = Insights.week(PrefsStore.loadHistory(c), PrefsStore.loadProjects(c));
        return c.getString(R.string.week_title, Projects.hours(w.total));
    }

    public static void post(Context c) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.weekly_report), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.weekly_sub));
        nm.createNotificationChannel(ch);
        List<String> lines = lines(c);
        StringBuilder body = new StringBuilder();
        for (String l : lines) {
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(l);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("tab", 3);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(Clay.accentColor(c))
                .setContentTitle(title(c))
                .setContentText(lines.get(0))
                .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0x7701, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
        nm.notify("weekly", 1, n);
    }
}
