package com.example.timerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Rings a clock alarm: the alarm sound on repeat plus vibration, in a foreground service
 * with the full-screen notification. Playing the sound here (not from the notification)
 * lets a wake-up mission keep it quiet while the person is solving.
 */
public class AlarmService extends Service {

    private static final String CHANNEL = "clock_ring";
    /** Gives up after this long if nobody answers. */
    private static final long TIMEOUT = 15L * 60L * 1000L;

    /** The alarm ringing now, or -1. */
    static volatile long ringingId = -1;
    private static volatile long quietUntil;
    private static AlarmService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private boolean loud;
    /** When ringing began and how many seconds the volume takes to rise. */
    private long startedAt;
    private int rise;
    private float volume = -1f;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            apply();
            handler.postDelayed(this, 500);
        }
    };
    private final Runnable giveUp = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    static void start(Context c, long id) {
        Intent i = new Intent(c, AlarmService.class).putExtra("id", id);
        try {
            c.startForegroundService(i);
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AlarmService.class));
    }

    /** Silence for `ms` (someone is solving the mission); the alarm returns after it. */
    static void quiet(long ms) {
        quietUntil = System.currentTimeMillis() + ms;
        AlarmService s = instance;
        if (s != null) {
            s.handler.post(new Runnable() {
                @Override
                public void run() {
                    AlarmService i = instance;
                    if (i != null) {
                        i.apply();
                    }
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long id = intent != null ? intent.getLongExtra("id", -1) : -1;
        ClockAlarms.Alarm al = ClockAlarms.find(this, id);
        if (al == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        instance = this;
        ringingId = id;
        quietUntil = 0;
        Notification n = notification(al);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ClockAlarms.notifId(id), n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ClockAlarms.notifId(id), n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startedAt = System.currentTimeMillis();
        rise = al.rise;
        volume = -1f;
        startSound();
        loud = false;
        handler.removeCallbacks(tick);
        handler.post(tick);
        handler.removeCallbacks(giveUp);
        handler.postDelayed(giveUp, TIMEOUT);
        if (MainActivity.isVisible()) {
            try {
                startActivity(ClockRingActivity.intent(this, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
        return START_NOT_STICKY;
    }

    private Notification notification(ClockAlarms.Alarm al) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.clock_alarms), NotificationManager.IMPORTANCE_HIGH);
            // the sound and vibration come from the service itself
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        String title = al.label.length() > 0 ? al.label : getString(R.string.clock_alarm);
        PendingIntent screen = PendingIntent.getActivity(this, 0x5E000000 + (int) al.id,
                ClockRingActivity.intent(this, al.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setColor(Clay.accentColor(this))
                .setContentTitle(title)
                .setContentText(al.missions.isEmpty() ? ClockAlarms.timeText(this, al)
                        : getString(R.string.m_finish_to_stop))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(screen)
                .setFullScreenIntent(screen, true);
        // with a mission, only finishing it turns the alarm off
        if (al.missions.isEmpty()) {
            b.addAction(0, getString(R.string.stop),
                    ClockAlarms.action(this, ClockAlarms.STOP, al.id, 0x5F000000));
        }
        b.addAction(0, getString(R.string.clock_snooze_n, al.snooze),
                ClockAlarms.action(this, ClockAlarms.SNOOZE, al.id, 0x5F800000));
        return b.build();
    }

    private void startSound() {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, NotificationHelper.chosenSound(this));
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            player = null;
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Sound unless a mission asked for quiet a moment ago; with rising volume it starts
     * soft and grows to full over the alarm's rise time.
     */
    private void apply() {
        long now = System.currentTimeMillis();
        boolean want = now >= quietUntil;
        float ramp = rise <= 0 ? 1f
                : Math.min(1f, 0.08f + 0.92f * (now - startedAt) / (rise * 1000f));
        float v = want ? ramp : 0f;
        if (Math.abs(v - volume) > 0.005f) {
            volume = v;
            try {
                if (player != null) {
                    player.setVolume(v, v);
                }
            } catch (Exception ignored) {
            }
        }
        if (want == loud) {
            return;
        }
        loud = want;
        if (vibrator != null) {
            if (want) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 400, 600, 900}, 0),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            } else {
                vibrator.cancel();
            }
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        ringingId = -1;
        instance = null;
        super.onDestroy();
    }
}
