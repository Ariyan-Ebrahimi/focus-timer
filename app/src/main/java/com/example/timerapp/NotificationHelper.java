package com.example.timerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public final class NotificationHelper {

    private static final String CHANNEL = "focus_alarms";
    private static final String STEP_CHANNEL = "focus_steps";
    private static final String TAG_STEP = "step";
    /** Stop ringing on its own after this long. */
    private static final long RING_TIMEOUT = 3L * 60L * 1000L;

    private NotificationHelper() {
    }

    public static Uri chosenSound(Context c) {
        String s = PrefsStore.getSoundUri(c);
        if (s == null || s.length() == 0) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        try {
            return Uri.parse(s);
        } catch (Exception e) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Returns the alarm channel for the currently chosen sound. A channel's sound can't be
     * changed after creation, and re-creating a deleted channel with the same id restores
     * its old settings, so every sound gets its own channel id and stale ones are removed.
     */
    static String ensureChannel(Context c) {
        Uri sound = chosenSound(c);
        String id = CHANNEL + "_" + Integer.toHexString(String.valueOf(sound).hashCode());
        NotificationManager nm = nm(c);
        if (nm == null || nm.getNotificationChannel(id) != null) {
            return id;
        }
        for (NotificationChannel old : nm.getNotificationChannels()) {
            if (old.getId().startsWith(CHANNEL) && !old.getId().equals(id)) {
                try {
                    nm.deleteNotificationChannel(old.getId());
                } catch (Exception ignored) {
                }
            }
        }
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        NotificationChannel ch = new NotificationChannel(
                id, c.getString(R.string.ch_alarms), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.ch_alarms_desc));
        ch.setSound(sound, aa);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 400, 600});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        try {
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {
        }
        return id;
    }

    /** Applies a newly picked alarm sound right away (called from Settings). */
    public static void applySound(Context c) {
        ensureChannel(c);
    }

    public static int alarmId(long id) {
        return (int) (id & 0x7fffffff);
    }

    private static PendingIntent action(Context c, String act, long id, int req) {
        Intent i = new Intent(c, LiveActionReceiver.class);
        i.setAction(act);
        i.putExtra("timer_id", id);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Insistent alarm (sound + vibration repeat until stopped) with a full-screen screen. */
    public static void notifyAlarm(Context c, long id, String name, long totalMillis) {
        String channel = ensureChannel(c);
        NotificationManager nm = nm(c);
        if (nm == null) {
            return;
        }
        int nid = alarmId(id);
        int req = nid & 0x00ffffff;
        PendingIntent screen = PendingIntent.getActivity(c, 0x50000000 | req,
                AlarmActivity.intent(c, id, name, totalMillis),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(Clay.accentColor(c))
                .setContentTitle(c.getString(R.string.times_up))
                .setContentText(name + "  \u00b7  " + MainActivity.formatDuration(totalMillis))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(RING_TIMEOUT)
                .setContentIntent(screen)
                .setFullScreenIntent(screen, true)
                .addAction(0, c.getString(R.string.stop), action(c, LiveActionReceiver.ALARM_STOP, id,
                        0x60000000 | req))
                .addAction(0, c.getString(R.string.plus_min), action(c, LiveActionReceiver.ALARM_PLUS, id,
                        0x70000000 | req))
                .build();
        n.flags |= Notification.FLAG_INSISTENT;
        try {
            nm.notify(nid, n);
        } catch (Exception ignored) {
        }
    }

    public static void cancelAlarm(Context c, long id) {
        NotificationManager nm = nm(c);
        if (nm != null) {
            nm.cancel(alarmId(id));
        }
    }

    /** Short chime + buzz when a sequence moves on to its next step. */
    public static void notifyStep(Context c, TimerItem it) {
        NotificationManager nm = nm(c);
        if (nm == null || !it.isSequence()) {
            return;
        }
        if (nm.getNotificationChannel(STEP_CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(STEP_CHANNEL, c.getString(R.string.ch_steps),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(c.getString(R.string.ch_steps_desc));
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 250, 150, 250});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        TimerItem.Step st = it.currentStep();
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0x48000000 | (alarmId(it.id) & 0xffffff),
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, STEP_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(Clay.accentColor(c))
                .setContentTitle(st.name + " \u00b7 " + MainActivity.formatDuration(st.ms))
                .setContentText(c.getString(R.string.n_step_text, it.name, it.position() + 1,
                        it.stepCount()))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(15_000L)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 26) {
            b.setCategory(Notification.CATEGORY_REMINDER);
        }
        try {
            nm.notify(TAG_STEP, alarmId(it.id), b.build());
        } catch (Exception ignored) {
        }
    }
}
