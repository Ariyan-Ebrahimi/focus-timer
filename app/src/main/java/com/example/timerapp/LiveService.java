package com.example.timerapp;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;

/**
 * Runs only while a timer or the stopwatch is running and refreshes their notifications
 * every second, so the progress bars move even when the app is closed. Its foreground
 * notification is the first running item's own notification.
 */
public class LiveService extends Service {

    private static final int FG_ID = 0x7100;
    private static LiveService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foreground;
    private int ticks;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (instance == LiveService.this) {
                // land just after each whole second so the bar moves with the countdown
                long now = System.currentTimeMillis();
                handler.postDelayed(this, 1000 - (now % 1000) + 30);
            }
        }
    };

    /**
     * Starts, refreshes or stops the service. Returns true when the service is now in
     * charge of posting (false: caller posts the static notifications itself).
     */
    static boolean sync(Context c, boolean anythingRunning) {
        LiveService s = instance;
        if (s != null) {
            s.refresh();
            return true;
        }
        if (!anythingRunning) {
            return false;
        }
        try {
            c.getApplicationContext().startForegroundService(new Intent(c, LiveService.class));
            return true;
        } catch (Exception e) {
            // e.g. not allowed to start from the background right now: fall back
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean first = instance != this;
        instance = this;
        List<LiveNotifier.Entry> entries = LiveNotifier.build(this);
        if (!foreground) {
            // must go foreground promptly after startForegroundService, even if empty
            showForeground(entries.isEmpty() ? placeholder() : entries.get(0).n);
        }
        if (first) {
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
        return START_STICKY;
    }

    private Notification placeholder() {
        return new Notification.Builder(this, "focus_live_v2")
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(getString(R.string.app_name))
                .build();
    }

    void showForeground(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(FG_ID, n);
            }
            foreground = true;
        } catch (Exception ignored) {
        }
    }

    private void refresh() {
        List<LiveNotifier.Entry> entries = LiveNotifier.build(this);
        if (entries.isEmpty()) {
            LiveNotifier.post(this, entries, null);
            stop();
            return;
        }
        LiveNotifier.post(this, entries, this);
        Shield.check(this);
        if (++ticks % 30 == 0) {
            TimerWidget.refresh(this);
        }
    }

    private void stop() {
        handler.removeCallbacks(tick);
        if (instance == this) {
            instance = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
