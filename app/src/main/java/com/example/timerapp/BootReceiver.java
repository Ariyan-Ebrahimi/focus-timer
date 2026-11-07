package com.example.timerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Alarms don't survive a reboot or an app update: re-schedule running timers, ring the
 * overdue ones.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) {
            return;
        }
        List<TimerItem> list = PrefsStore.loadTimers(c);
        long now = System.currentTimeMillis();
        for (TimerItem it : list) {
            if (it.state != TimerItem.RUNNING) {
                continue;
            }
            if (it.deadline <= now) {
                TimerOps.handleDeadline(c, it.id);
            } else if (AlarmHelper.canSchedule(c)) {
                try {
                    AlarmHelper.schedule(c, it);
                } catch (Exception ignored) {
                }
            }
        }
        TimerOps.refreshSurfaces(c);
        WeeklyReport.schedule(c);
        ClockAlarms.scheduleAll(c);
    }
}
