package com.example.timerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at a timer's deadline: advances a sequence step or rings the alarm. */
public class TimerAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        long id = i.getLongExtra("timer_id", -1);
        if (i.getBooleanExtra("snoozed", false)) {
            String name = i.getStringExtra("timer_name");
            TimerOps.ring(c, id, name == null || name.length() == 0 ? c.getString(R.string.timer) : name,
                    i.getLongExtra("timer_total", 0));
            return;
        }
        TimerOps.handleDeadline(c, id);
    }
}
