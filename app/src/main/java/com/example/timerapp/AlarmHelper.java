package com.example.timerapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class AlarmHelper {

    public static final String ACTION = "com.example.timerapp.TIMER_ALARM";
    public static final int NOTIF_PERM_CODE = 1001;

    private AlarmHelper() {
    }

    private static PendingIntent alarmIntent(Context c, TimerItem it) {
        Intent i = new Intent(c, TimerAlarmReceiver.class);
        i.setAction(ACTION);
        i.putExtra("timer_id", it.id);
        i.putExtra("timer_name", it.name);
        i.putExtra("timer_total", it.total);
        return PendingIntent.getBroadcast(c, (int) (it.id & 0x7fffffff), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, TimerItem it) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.deadline, alarmIntent(c, it));
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        TimerItem fake = new TimerItem(id, "", 0);
        try {
            am.cancel(alarmIntent(c, fake));
        } catch (Exception ignored) {
        }
    }

    public static final int POMO_REQ = 987654;

    public static void schedulePomo(Context c, long deadline, String name, long total) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent i = new Intent(c, TimerAlarmReceiver.class);
        i.setAction(ACTION);
        i.putExtra("timer_id", -100L);
        i.putExtra("timer_name", name);
        i.putExtra("timer_total", total);
        i.putExtra("pomo", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, POMO_REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi);
    }

    public static void cancelPomo(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        Intent i = new Intent(c, TimerAlarmReceiver.class);
        i.setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(c, POMO_REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.cancel(pi);
        } catch (Exception ignored) {
        }
    }

    public static boolean canSchedule(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static void openExactAlarmSettings(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    public static boolean hasNotifPermission(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void ensureNotifPermission(Activity a) {
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERM_CODE);
        }
    }

    public static void openAppSettings(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
