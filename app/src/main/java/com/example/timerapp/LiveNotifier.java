package com.example.timerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ongoing notifications for running timers and the running stopwatch. The time is
 * rendered by the system chronometer, so it keeps ticking while the app is closed.
 */
public final class LiveNotifier {

    private static final String CHANNEL = "focus_live_v2";
    private static final String OLD_CHANNEL = "focus_live";
    private static final String TAG_TIMER = "live_timer";
    private static final String TAG_SW = "live_sw";
    private static final int SW_ID = 1;
    private static boolean channelReady;

    private LiveNotifier() {
    }

    private static NotificationManager ensureChannel(Context c) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && !channelReady) {
            channelReady = true;
            // Low-importance ("silent") channels are hidden on the lock screen, so use
            // default importance but strip sound/vibration to keep it quiet.
            try {
                nm.deleteNotificationChannel(OLD_CHANNEL);
            } catch (Exception ignored) {
            }
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, c.getString(R.string.ch_running), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(c.getString(R.string.ch_running_desc));
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.enableLights(false);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        return nm;
    }

    private static int notifId(long timerId) {
        return (int) (timerId ^ (timerId >>> 32)) & 0x7fffffff;
    }

    private static PendingIntent action(Context c, String act, long id, int req) {
        Intent i = new Intent(c, LiveActionReceiver.class);
        i.setAction(act);
        i.putExtra("timer_id", id);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent open(Context c, int tab, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("tab", tab);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int accentColor(Context c) {
        return Clay.accentColor(c);
    }

    private static Notification.Builder base(Context c) {
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(accentColor(c))
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STOPWATCH);
    }

    /**
     * The custom body: a large clock the system ticks (counting down when countDown),
     * the project's colour dot, and a progress bar (progress < 0 hides it).
     */
    private static void bigClock(Context c, Notification.Builder b, String title, String sub,
                                 int dot, long elapsedBase, boolean countDown, String ends,
                                 int progress) {
        RemoteViews[] views = {new RemoteViews(c.getPackageName(), R.layout.notif_live),
                new RemoteViews(c.getPackageName(), R.layout.notif_live_big)};
        for (RemoteViews rv : views) {
            rv.setTextViewText(R.id.nTitle, title);
            rv.setTextViewText(R.id.nSub, sub);
            rv.setInt(R.id.nDot, "setColorFilter", dot);
            rv.setChronometer(R.id.nChrono, elapsedBase, null, true);
            rv.setChronometerCountDown(R.id.nChrono, countDown);
        }
        RemoteViews big = views[1];
        big.setTextViewText(R.id.nEnds, ends == null ? "" : ends);
        if (progress >= 0) {
            big.setProgressBar(R.id.nBar, 1000, progress, false);
            if (Build.VERSION.SDK_INT >= 31) {
                big.setColorStateList(R.id.nBar, "setProgressTintList",
                        ColorStateList.valueOf(dot));
            }
        } else {
            big.setViewVisibility(R.id.nBar, android.view.View.GONE);
        }
        // the clock is in the body, so the header doesn't repeat it
        b.setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(views[0])
                .setCustomBigContentView(big)
                .setUsesChronometer(false)
                .setShowWhen(false);
    }

    private static PrefsStore.Project project(List<PrefsStore.Project> projects, long id) {
        for (PrefsStore.Project p : projects) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    /** One live notification: posted with tag+id, or as the service's foreground one. */
    static final class Entry {
        final String tag;
        final int id;
        final Notification n;

        Entry(String tag, int id, Notification n) {
            this.tag = tag;
            this.id = id;
            this.n = n;
        }
    }

    /** Re-posts or removes every live notification to match the saved state. */
    public static void update(Context c) {
        Focus.sync(c);
        TimerWidget.refresh(c);
        try {
            List<Entry> entries = build(c);
            // while something runs, the service owns posting so the progress bars stay live
            if (LiveService.sync(c, !entries.isEmpty())) {
                return;
            }
            post(c, entries, null);
        } catch (Exception ignored) {
        }
    }

    /** Builds the notifications for running timers and the running stopwatch. */
    static List<Entry> build(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        if (ensureChannel(c) == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        java.text.DateFormat hm = android.text.format.DateFormat.getTimeFormat(c);
        List<PrefsStore.Project> projects = PrefsStore.loadProjects(c);
        int accent = accentColor(c);

        for (TimerItem it : PrefsStore.loadTimers(c)) {
            // only timers that are actually counting down get a notification
            if (it.state != TimerItem.RUNNING || it.deadline <= now) {
                continue;
            }
            int nid = notifId(it.id);
            int req = nid & 0x00ffffff;
            String title = it.name;
            String prefix = c.getString(R.string.timer);
            if (it.isSequence()) {
                title = it.name + " \u00b7 " + it.currentStep().name;
                prefix = c.getString(R.string.step_of, it.position() + 1, it.stepCount());
            }
            // strict mode: no Pause or Reset while focusing, only the extra minute
            boolean locked = TimerOps.strictLocked(c, it);
            Notification.Builder b = base(c)
                    .setContentTitle(title)
                    .setContentIntent(open(c, 0, 0x10000000 | req));
            if (!locked) {
                b.addAction(0, c.getString(R.string.pause), action(c, LiveActionReceiver.TIMER_PAUSE,
                        it.id, 0x20000000 | req));
            }
            b.addAction(0, c.getString(R.string.plus_min), action(c, LiveActionReceiver.TIMER_PLUS,
                    it.id, 0x38000000 | req));
            if (!locked) {
                b.addAction(0, c.getString(R.string.reset), action(c, LiveActionReceiver.TIMER_RESET,
                        it.id, 0x30000000 | req));
            }
            b.setOngoing(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(it.deadline)
                    .setContentText(c.getString(R.string.n_ends, prefix,
                            hm.format(new Date(it.deadline))));
            long span = it.progressSpan();
            long left = it.deadline - now;
            int progress = span > 0 ? (int) Math.max(0, Math.min(1000,
                    1000 - 1000 * left / span)) : -1;
            PrefsStore.Project p = project(projects, it.projectId);
            String sub = p == null ? prefix : it.isSequence() ? p.name + " \u00b7 " + prefix
                    : p.name;
            if (p != null) {
                b.setSubText(p.name);
            }
            bigClock(c, b, title, sub, p != null ? Projects.color(c, p.color) : accent,
                    elapsed + left, true, c.getString(R.string.st_ends,
                            hm.format(new Date(it.deadline))), progress);
            out.add(new Entry(TAG_TIMER, nid, b.build()));
        }

        // the stopwatch, likewise, only while it is running
        PrefsStore.Sw sw = PrefsStore.loadSw(c);
        if (sw.running) {
            long cur = sw.current(now);
            Notification.Builder sb = base(c)
                    .setContentTitle(c.getString(R.string.stopwatch))
                    .setContentIntent(open(c, 2, 0x40000001))
                    .setOngoing(true)
                    .setUsesChronometer(true)
                    .setShowWhen(true)
                    .setWhen(now - cur)
                    .setContentText(sw.laps.isEmpty() ? c.getString(R.string.running)
                            : c.getString(R.string.running_lap, sw.laps.size() + 1))
                    .addAction(0, c.getString(R.string.pause), action(c, LiveActionReceiver.SW_PAUSE, 0,
                            0x40000002))
                    .addAction(0, c.getString(R.string.lap), action(c, LiveActionReceiver.SW_LAP, 0,
                            0x40000003))
                    .addAction(0, c.getString(R.string.reset), action(c, LiveActionReceiver.SW_RESET, 0,
                            0x40000005));
            PrefsStore.Project p = project(projects, sw.project);
            String state = sw.laps.isEmpty() ? c.getString(R.string.running)
                    : c.getString(R.string.running_lap, sw.laps.size() + 1);
            if (p != null) {
                sb.setSubText(p.name);
            }
            bigClock(c, sb, c.getString(R.string.stopwatch), p != null ? p.name : state,
                    p != null ? Projects.color(c, p.color) : accent, elapsed - cur, false,
                    p != null ? state : null, -1);
            out.add(new Entry(TAG_SW, SW_ID, sb.build()));
        }
        return out;
    }

    /**
     * Posts entries and removes stale ones. With a service, the first entry becomes its
     * foreground notification (so no extra "service running" notification is shown).
     */
    static void post(Context c, List<Entry> entries, LiveService svc) {
        NotificationManager nm = ensureChannel(c);
        if (nm == null) {
            return;
        }
        Set<String> keep = new HashSet<String>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (i == 0 && svc != null) {
                svc.showForeground(e.n);
                continue;
            }
            keep.add(e.tag + ":" + e.id);
            nm.notify(e.tag, e.id, e.n);
        }
        for (StatusBarNotification s : nm.getActiveNotifications()) {
            String tag = s.getTag();
            if ((TAG_TIMER.equals(tag) || TAG_SW.equals(tag))
                    && !keep.contains(tag + ":" + s.getId())) {
                nm.cancel(tag, s.getId());
            }
        }
    }
}
