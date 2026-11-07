package com.example.timerapp;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class SnoozeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        long id = i.getLongExtra("timer_id", -1);
        String name = i.getStringExtra("timer_name");
        if (name == null || name.length() == 0) {
            name = c.getString(R.string.timer);
        }
        long total = i.getLongExtra("timer_total", 0);
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel((int) (id & 0x7fffffff));
            }
        } catch (Exception ignored) {
        }
        if (!AlarmHelper.canSchedule(c)) {
            Toast.makeText(c, R.string.enable_exact_snooze, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent a = new Intent(c, TimerAlarmReceiver.class);
            a.setAction(AlarmHelper.ACTION);
            a.putExtra("timer_id", id);
            a.putExtra("timer_name", name);
            a.putExtra("timer_total", total);
            a.putExtra("snoozed", true);
            PendingIntent pi = PendingIntent.getBroadcast(c,
                    (int) (id & 0x7fffffff) + 2000007, a,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 5L * 60L * 1000L, pi);
            Toast.makeText(c, R.string.snoozed, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(c, R.string.could_not_snooze, Toast.LENGTH_SHORT).show();
        }
    }
}
