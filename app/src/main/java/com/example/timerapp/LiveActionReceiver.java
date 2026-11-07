package com.example.timerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the buttons on the live timer/stopwatch notifications and the alarm. */
public class LiveActionReceiver extends BroadcastReceiver {

    public static final String TIMER_PAUSE = "com.example.timerapp.TIMER_PAUSE";
    public static final String TIMER_RESUME = "com.example.timerapp.TIMER_RESUME";
    public static final String TIMER_RESET = "com.example.timerapp.TIMER_RESET";
    public static final String TIMER_PLUS = "com.example.timerapp.TIMER_PLUS";
    public static final String ALARM_STOP = "com.example.timerapp.ALARM_STOP";
    public static final String ALARM_PLUS = "com.example.timerapp.ALARM_PLUS";
    public static final String SW_PAUSE = "com.example.timerapp.SW_PAUSE";
    public static final String SW_RESUME = "com.example.timerapp.SW_RESUME";
    public static final String SW_LAP = "com.example.timerapp.SW_LAP";
    public static final String SW_RESET = "com.example.timerapp.SW_RESET";
    public static final String WIDGET_MAIN = "com.example.timerapp.WIDGET_MAIN";
    public static final String WIDGET_FINISH = "com.example.timerapp.WIDGET_FINISH";
    public static final String MUSIC_STOP = "com.example.timerapp.MUSIC_STOP";

    @Override
    public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if (act == null) {
            return;
        }
        if (MUSIC_STOP.equals(act)) {
            Focus.stopSleep(c);
            return;
        }
        if (WIDGET_FINISH.equals(act)) {
            TimerWidget.finishStopwatch(c);
            return;
        }
        if (WIDGET_MAIN.equals(act)) {
            TimerWidget.mainAction(c, i.getIntExtra(
                    android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
            return;
        }
        if (act.startsWith("com.example.timerapp.SW_")) {
            long now = System.currentTimeMillis();
            PrefsStore.Sw sw = PrefsStore.loadSw(c);
            if (SW_PAUSE.equals(act) && sw.running) {
                sw.accum = sw.current(now);
                sw.running = false;
                sw.pauses++;
            } else if (SW_RESUME.equals(act) && !sw.running) {
                sw.base = now;
                sw.running = true;
            } else if (SW_LAP.equals(act) && sw.running) {
                sw.laps.add(sw.current(now));
            } else if (SW_RESET.equals(act)) {
                PrefsStore.logStopwatch(c, sw, now, c.getString(R.string.stopwatch));
            }
            PrefsStore.saveSw(c, sw);
            LiveNotifier.update(c);
            MainActivity.notifyExternalChange();
            return;
        }

        long id = i.getLongExtra("timer_id", -1);
        TimerOps.Op op;
        if (TIMER_PAUSE.equals(act)) {
            op = new TimerOps.Op() {
                @Override
                public void run(Context ctx, TimerItem it) {
                    TimerOps.pause(ctx, it);
                }
            };
        } else if (TIMER_RESUME.equals(act)) {
            op = new TimerOps.Op() {
                @Override
                public void run(Context ctx, TimerItem it) {
                    if (it.state == TimerItem.PAUSED) {
                        TimerOps.start(ctx, it);
                    }
                }
            };
        } else if (TIMER_RESET.equals(act)) {
            op = new TimerOps.Op() {
                @Override
                public void run(Context ctx, TimerItem it) {
                    TimerOps.reset(ctx, it);
                }
            };
        } else if (TIMER_PLUS.equals(act) || ALARM_PLUS.equals(act)) {
            if (ALARM_PLUS.equals(act)) {
                NotificationHelper.cancelAlarm(c, id);
            }
            op = new TimerOps.Op() {
                @Override
                public void run(Context ctx, TimerItem it) {
                    TimerOps.addMinute(ctx, it);
                }
            };
        } else if (ALARM_STOP.equals(act)) {
            NotificationHelper.cancelAlarm(c, id);
            return;
        } else {
            return;
        }
        TimerOps.apply(c, id, op);
    }
}
