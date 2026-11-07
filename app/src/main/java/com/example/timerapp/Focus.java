package com.example.timerapp;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * "Focus mode" side effects while a focus timer (or the stopwatch) runs: optional Do Not
 * Disturb, and an optional generated background sound. Breaks in a sequence don't count.
 */
public final class Focus {

    public static final int SOUND_OFF = 0;
    public static final int SOUND_RAIN = 1;
    public static final int SOUND_WIND = 2;
    public static final int SOUND_FIRE = 3;
    /** All the piano recordings, played one after another. */
    public static final int SOUND_PIANO = 4;
    public static final int SOUND_GUITAR = 5;
    public static final int SOUND_FAN = 6;
    public static final int SOUND_OCEAN = 7;
    public static final int SOUND_FOREST = 8;
    public static final int SOUND_STREAM = 9;
    public static final int SOUND_THUNDER = 10;
    public static final int SOUND_BROWN = 11;
    /** The piano playlist (public domain / CC0, Clair de Lune CC BY 3.0; credits in Settings). */
    static final int[] MUSIC_RES = {R.raw.music_gymnopedie, R.raw.music_aria,
            R.raw.music_nocturne, R.raw.music_moonlight, R.raw.music_traumerei,
            R.raw.music_goldberg13, R.raw.music_gymnopedie3, R.raw.music_consolation,
            R.raw.music_prelude, R.raw.music_gnossienne, R.raw.music_nocturne72,
            R.raw.music_clairdelune};
    /** The guitar playlist (public domain / CC0 / CC BY; credits in Settings). */
    static final int[] GUITAR_RES = {R.raw.guitar_romance, R.raw.guitar_sarabande,
            R.raw.guitar_breezy, R.raw.guitar_noi, R.raw.guitar_daphne, R.raw.guitar_sunset,
            R.raw.guitar_narvaez, R.raw.guitar_menuet};
    private static final int[] SOUND_NAMES = {R.string.sound_off, R.string.sound_rain,
            R.string.sound_wind, R.string.sound_fire, R.string.sound_piano, R.string.sound_guitar,
            R.string.sound_fan, R.string.sound_ocean, R.string.sound_forest, R.string.sound_stream,
            R.string.sound_thunder, R.string.sound_brown};
    public static final int SOUND_COUNT = SOUND_NAMES.length;
    /** The order the choices are listed in: weather and nature, noise, then music. */
    public static final int[] ORDER = {SOUND_OFF, SOUND_RAIN, SOUND_THUNDER, SOUND_WIND,
            SOUND_FIRE, SOUND_OCEAN, SOUND_STREAM, SOUND_FOREST, SOUND_FAN, SOUND_BROWN,
            SOUND_PIANO, SOUND_GUITAR};

    /** The recordings a sound plays (a playlist, or one seamless loop); null if generated. */
    static int[] recordings(int sound) {
        switch (sound) {
            case SOUND_PIANO:
                return MUSIC_RES;
            case SOUND_GUITAR:
                return GUITAR_RES;
            case SOUND_FAN:
                return new int[]{R.raw.amb_fan};
            case SOUND_OCEAN:
                return new int[]{R.raw.amb_ocean};
            case SOUND_FOREST:
                return new int[]{R.raw.amb_forest};
            case SOUND_STREAM:
                return new int[]{R.raw.amb_stream};
            case SOUND_THUNDER:
                return new int[]{R.raw.amb_thunder};
            default:
                return null;
        }
    }

    public static boolean isMusic(int sound) {
        return recordings(sound) != null;
    }

    /** The name shown for a sound choice. */
    public static String name(Context c, int sound) {
        return c.getString(SOUND_NAMES[sound >= 0 && sound < SOUND_COUNT ? sound : 0]);
    }

    private static Generator player;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable END_PREVIEW = new Runnable() {
        @Override
        public void run() {
            previewing = false;
            stopSound();
        }
    };
    private static boolean previewing;

    private Focus() {
    }

    /** True while a timer is in a focus (non-break) step or the stopwatch runs. */
    public static boolean focusing(Context c) {
        long now = System.currentTimeMillis();
        for (TimerItem it : PrefsStore.loadTimers(c)) {
            if (it.state == TimerItem.RUNNING && it.deadline > now
                    && !(it.isSequence() && it.currentStep().rest)) {
                return true;
            }
        }
        return PrefsStore.loadSw(c).running;
    }

    /** Brings DND and the background sound in line with the current state. */
    public static void sync(Context c) {
        boolean focus;
        try {
            focus = focusing(c);
        } catch (Exception e) {
            return;
        }
        syncDnd(c, focus);
        if (!previewing) {
            int s = PrefsStore.getFocusSound(c);
            boolean chosen = s > SOUND_OFF && s < SOUND_COUNT;
            if (focus && chosen) {
                cancelSleep(c);
                startSound(c, s);
            } else if (chosen && sleepUntil > System.currentTimeMillis()) {
                // the sleep timer is running: keep playing until it ends
            } else if (chosen && soundOn && sleepUntil == 0 && PrefsStore.getSleepMin(c) > 0) {
                startSleep(c, PrefsStore.getSleepMin(c));
            } else {
                cancelSleep(c);
                stopSound();
            }
        }
    }

    // ------------------------------------------------------------------ sleep timer

    private static final String MUSIC_CHANNEL = "focus_music";
    private static final int MUSIC_NOTIF = 0x7310;
    private static long sleepUntil;
    private static Context appContext;
    private static final Runnable SLEEP_END = new Runnable() {
        @Override
        public void run() {
            if (appContext != null) {
                stopSleep(appContext);
            }
        }
    };

    /** Keeps the sound going for `minutes` after focus ends, with a notification to stop it. */
    private static void startSleep(Context c, int minutes) {
        appContext = c.getApplicationContext();
        sleepUntil = System.currentTimeMillis() + minutes * 60_000L;
        MAIN.removeCallbacks(SLEEP_END);
        MAIN.postDelayed(SLEEP_END, minutes * 60_000L);
        android.app.NotificationManager nm = (android.app.NotificationManager)
                c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        android.app.NotificationChannel ch = new android.app.NotificationChannel(MUSIC_CHANNEL,
                c.getString(R.string.music_after), android.app.NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
        android.content.Intent stop = new android.content.Intent(c, LiveActionReceiver.class);
        stop.setAction(LiveActionReceiver.MUSIC_STOP);
        String at = android.text.format.DateFormat.getTimeFormat(c)
                .format(new java.util.Date(sleepUntil));
        android.app.Notification n = new android.app.Notification.Builder(c, MUSIC_CHANNEL)
                .setSmallIcon(R.drawable.ic_music)
                .setColor(Clay.accentColor(c))
                .setContentTitle(name(c, PrefsStore.getFocusSound(c)))
                .setContentText(c.getString(R.string.music_until, at))
                .setOngoing(true)
                .addAction(0, c.getString(R.string.stop), android.app.PendingIntent.getBroadcast(
                        c, 0x7311, stop, android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_IMMUTABLE))
                .build();
        nm.notify(MUSIC_NOTIF, n);
    }

    private static void cancelSleep(Context c) {
        if (sleepUntil == 0) {
            return;
        }
        sleepUntil = 0;
        MAIN.removeCallbacks(SLEEP_END);
        android.app.NotificationManager nm = (android.app.NotificationManager)
                c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(MUSIC_NOTIF);
        }
    }

    /** Ends the after-focus music now (the timer ran out, or Stop was tapped). */
    public static void stopSleep(Context c) {
        cancelSleep(c);
        stopSound();
    }

    // ------------------------------------------------------------------ Do Not Disturb

    public static boolean dndAllowed(Context c) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private static void syncDnd(Context c, boolean focus) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        boolean granted = nm.isNotificationPolicyAccessGranted();
        boolean want = focus && granted && PrefsStore.getDnd(c);
        boolean active = PrefsStore.getDndActive(c);
        try {
            if (want && !active) {
                int prev = nm.getCurrentInterruptionFilter();
                // leave it alone if the user already has some DND mode on
                if (prev == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    PrefsStore.setDndActive(c, true, prev);
                }
            } else if (!want && active) {
                // restore only if nobody changed it meanwhile; alarms still ring under it
                if (granted && nm.getCurrentInterruptionFilter()
                        == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(PrefsStore.getDndPrev(c));
                }
                PrefsStore.setDndActive(c, false, PrefsStore.getDndPrev(c));
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ sound


    /** Plays a sound for a few seconds from Settings. */
    public static void preview(Context c, int sound) {
        MAIN.removeCallbacks(END_PREVIEW);
        if (sound == SOUND_OFF) {
            previewing = false;
            sync(c);
            return;
        }
        previewing = true;
        startSound(c, sound);
        // long enough to hear it after the gentle fade-in
        MAIN.postDelayed(END_PREVIEW, 9000);
    }

    /** True while a sound plays (or is fading in). */
    private static volatile boolean soundOn;

    private static synchronized void startSound(Context c, int type) {
        soundOn = true;
        if (isMusic(type)) {
            if (player != null) {
                player.running = false;
                player = null;
            }
            Music.play(c.getApplicationContext(), type);
            return;
        }
        Music.stop();
        if (player != null && player.type == type && player.isAlive()) {
            return;
        }
        if (player != null) {
            player.running = false;
        }
        player = new Generator(type);
        player.start();
    }

    private static synchronized void stopSound() {
        soundOn = false;
        Music.stop();
        if (player != null) {
            player.running = false;
            player = null;
        }
    }

    /**
     * Recorded sounds: a playlist (piano, guitar) plays every piece one after another
     * without a break, then from the top again; a single recording loops seamlessly (the
     * files are cut so their end flows into their start). Fades in and out when focus
     * starts and stops, and each sound continues where it left off.
     */
    private static final class Music {
        private static final float VOLUME = 0.7f;
        private static final int STEP_MS = 50;
        private static android.media.MediaPlayer mp;
        private static Context app;
        private static float vol;
        private static boolean fadingOut;
        /** The sound playing and its recordings. */
        private static int sound = -1;
        private static int[] list;
        /** Where it is now: piece and position in it. */
        private static int track;
        private static int position;
        /** Where each sound stopped last time: {piece, position}. */
        private static final java.util.Map<Integer, int[]> SPOTS =
                new java.util.HashMap<Integer, int[]>();
        private static final Runnable FADE = new Runnable() {
            @Override
            public void run() {
                if (mp == null) {
                    return;
                }
                if (fadingOut) {
                    vol -= VOLUME * STEP_MS / 1500f;
                    if (vol <= 0f) {
                        release(true);
                        return;
                    }
                } else {
                    vol = Math.min(VOLUME, vol + VOLUME * STEP_MS / 3000f);
                }
                // ease the volume so the start and end are gentle
                float v = (vol / VOLUME) * (vol / VOLUME) * VOLUME;
                try {
                    mp.setVolume(v, v);
                } catch (Exception ignored) {
                }
                if (fadingOut || vol < VOLUME) {
                    MAIN.postDelayed(this, STEP_MS);
                }
            }
        };

        static void play(final Context c, final int which) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    app = c.getApplicationContext();
                    if (mp != null && sound == which) {
                        // already playing: just undo a fade-out
                        if (fadingOut) {
                            fadingOut = false;
                            MAIN.removeCallbacks(FADE);
                            MAIN.post(FADE);
                        }
                        return;
                    }
                    if (mp != null) {
                        release(true);
                    }
                    sound = which;
                    list = recordings(which);
                    int[] spot = SPOTS.get(which);
                    track = spot != null && spot[0] < list.length ? spot[0] : 0;
                    position = spot != null ? spot[1] : 0;
                    vol = 0f;
                    fadingOut = false;
                    open(track, position, 0f);
                    MAIN.removeCallbacks(FADE);
                    MAIN.post(FADE);
                }
            });
        }

        /** Starts piece `index` at `from` ms; when it ends, the next piece follows. */
        private static void open(int index, int from, float volume) {
            try {
                android.media.MediaPlayer p = new android.media.MediaPlayer();
                p.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                android.content.res.AssetFileDescriptor fd =
                        app.getResources().openRawResourceFd(list[index]);
                p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
                p.prepare();
                if (from > 0 && from < p.getDuration() - 2000) {
                    p.seekTo(from);
                }
                p.setVolume(volume, volume);
                // one recording: loop it forever (its end already flows into its start)
                p.setLooping(list.length == 1);
                p.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(android.media.MediaPlayer done) {
                        if (done != mp) {
                            return;
                        }
                        // next piece at the same volume, so the music never stops
                        float v = (vol / VOLUME) * (vol / VOLUME) * VOLUME;
                        mp = null;
                        done.release();
                        track = (track + 1) % list.length;
                        position = 0;
                        open(track, 0, v);
                    }
                });
                p.start();
                mp = p;
                track = index;
            } catch (Exception ignored) {
                mp = null;
            }
        }

        static void stop() {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    if (mp != null && !fadingOut) {
                        fadingOut = true;
                        MAIN.removeCallbacks(FADE);
                        MAIN.post(FADE);
                    }
                }
            });
        }

        /** Stops playing; `remember` keeps the spot so this sound continues from it next time. */
        private static void release(boolean remember) {
            MAIN.removeCallbacks(FADE);
            if (mp != null) {
                try {
                    if (remember) {
                        position = mp.getCurrentPosition();
                        SPOTS.put(sound, new int[]{track, position});
                    }
                    mp.stop();
                } catch (Exception ignored) {
                }
                mp.release();
                mp = null;
            }
            fadingOut = false;
        }
    }

    /**
     * Streams a generated stereo nature sound. Everything is low-passed (no hiss), moves
     * slowly, fades in gently, and is levelled to the same quiet loudness.
     */
    private static final class Generator extends Thread {
        private static final int RATE = 44100;
        /** Loudness everything is levelled to (RMS, full scale = 1). */
        private static final float TARGET_RMS = 0.075f;
        final int type;
        volatile boolean running = true;
        private final Random rnd = new Random();
        private long t;
        private float outL;
        private float outR;

        /** Pink noise (Paul Kellet's filter); one per channel so the sides differ. */
        private final class Pink {
            float b0, b1, b2, b3, b4, b5, b6;

            float next() {
                float w = rnd.nextFloat() * 2f - 1f;
                b0 = 0.99886f * b0 + w * 0.0555179f;
                b1 = 0.99332f * b1 + w * 0.0750759f;
                b2 = 0.96900f * b2 + w * 0.1538520f;
                b3 = 0.86650f * b3 + w * 0.3104856f;
                b4 = 0.55000f * b4 + w * 0.5329522f;
                b5 = -0.7616f * b5 - w * 0.0168980f;
                float p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f;
                b6 = w * 0.115926f;
                return p * 0.11f;
            }
        }

        /** Brown (red) noise: a leaky random walk, deep and soft. */
        private final class Brown {
            float v;

            float next() {
                v = (v + 0.02f * (rnd.nextFloat() * 2f - 1f)) / 1.02f;
                return v * 3.5f;
            }
        }

        /** Two one-pole low-passes in a row (12 dB/octave); the cutoff can change. */
        private static final class Lp {
            float a;
            float y1;
            float y2;

            Lp(float hz) {
                set(hz);
            }

            void set(float hz) {
                a = (float) (1 - Math.exp(-2 * Math.PI * hz / RATE));
            }

            float next(float x) {
                y1 += a * (x - y1);
                y2 += a * (y1 - y2);
                return y2;
            }
        }

        private final Pink pinkL = new Pink();
        private final Pink pinkR = new Pink();
        private final Brown brownL = new Brown();
        private final Brown brownR = new Brown();
        private final Lp lpL;
        private final Lp lpR;
        private final Lp lowL = new Lp(140);
        private final Lp lowR = new Lp(140);

        // rain: soft, muffled taps now and then
        private float tapEnv;
        private float tapPan;
        private final Lp tapLp = new Lp(1200);

        // wind: gusts drift toward a new strength every few seconds
        private float gust = 0.4f;
        private float gustTarget = 0.6f;
        private long nextGustAt;

        // fire: a warm bed, quiet crackles, an occasional soft pop
        private float flicker = 1f;
        private float crackEnv;
        private float crackDecay;
        private float crackPan;
        private final Lp crackLp = new Lp(1500);
        private float popEnv;
        private float popPan;
        private final Lp popLp = new Lp(500);

        Generator(int type) {
            super("focus-sound");
            this.type = type;
            setPriority(Thread.MAX_PRIORITY);
            float cut = type == SOUND_RAIN ? 2600 : type == SOUND_FIRE ? 380
                    : type == SOUND_BROWN ? 420 : 600;
            lpL = new Lp(cut);
            lpR = new Lp(cut);
        }

        /** Computes the next stereo frame into outL/outR. */
        private void frame() {
            switch (type) {
                case SOUND_RAIN: {
                    // warm rain: pink noise without the hiss or the rumble
                    float l = lpL.next(pinkL.next());
                    float r = lpR.next(pinkR.next());
                    l -= lowL.next(l);
                    r -= lowR.next(r);
                    float breathe = 0.9f + 0.1f * (float) Math.sin(2 * Math.PI * t / (RATE * 17.0));
                    l *= breathe;
                    r *= breathe;
                    if (tapEnv < 0.002f && rnd.nextInt(RATE / 9) == 0) {
                        tapEnv = 0.15f + rnd.nextFloat() * 0.2f;
                        tapPan = rnd.nextFloat();
                    }
                    if (tapEnv >= 0.002f) {
                        float tap = tapLp.next(rnd.nextFloat() * 2f - 1f) * tapEnv;
                        l += tap * (1f - tapPan);
                        r += tap * tapPan;
                        tapEnv *= 0.9975f;
                    }
                    outL = l;
                    outR = r;
                    break;
                }
                case SOUND_WIND: {
                    if (t >= nextGustAt) {
                        gustTarget = 0.2f + rnd.nextFloat() * 0.8f;
                        nextGustAt = t + (long) (RATE * (3 + rnd.nextFloat() * 5));
                    }
                    gust += (gustTarget - gust) * (1f / (RATE * 1.8f));
                    if ((t & 63) == 0) {
                        // stronger gusts sound a little brighter, never hissy
                        lpL.set(220 + 700 * gust);
                        lpR.set(240 + 700 * gust);
                    }
                    float l = lpL.next(pinkL.next() * 0.6f + brownL.next() * 0.4f);
                    float r = lpR.next(pinkR.next() * 0.6f + brownR.next() * 0.4f);
                    l -= lowL.next(l) * 0.7f;
                    r -= lowR.next(r) * 0.7f;
                    float level = 0.3f + 0.7f * gust;
                    float sway = (float) Math.sin(2 * Math.PI * t / (RATE * 19.0)) * 0.2f * gust;
                    outL = l * level * (1f - sway);
                    outR = r * level * (1f + sway);
                    break;
                }
                case SOUND_BROWN: {
                    // deep, even brown noise that swells very slowly, a little wider apart
                    float swell = 0.88f + 0.12f * (float) Math.sin(2 * Math.PI * t / (RATE * 23.0));
                    outL = lpL.next(brownL.next()) * swell;
                    outR = lpR.next(brownR.next()) * swell;
                    break;
                }
                case SOUND_FIRE:
                default: {
                    // the bed: deep and warm, flickering a little
                    if ((t & 255) == 0) {
                        flicker += ((0.85f + rnd.nextFloat() * 0.15f) - flicker) * 0.2f;
                    }
                    float l = lpL.next(brownL.next()) * flicker;
                    float r = lpR.next(brownR.next()) * flicker;
                    // crackles: tiny, quiet, mostly small with the odd bigger one
                    if (crackEnv < 0.001f && rnd.nextInt(RATE / 7) == 0) {
                        float size = rnd.nextFloat();
                        crackEnv = 0.08f + size * size * size * 0.5f;
                        crackDecay = 0.994f + rnd.nextFloat() * 0.004f;
                        crackPan = 0.2f + rnd.nextFloat() * 0.6f;
                    }
                    if (crackEnv >= 0.001f) {
                        float w = rnd.nextFloat() * 2f - 1f;
                        float c = (w - crackLp.next(w)) * crackEnv;
                        l += c * (1f - crackPan);
                        r += c * crackPan;
                        crackEnv *= crackDecay;
                    }
                    // a soft, low pop every few seconds
                    if (popEnv < 0.001f && rnd.nextInt(RATE * 4) == 0) {
                        popEnv = 0.35f + rnd.nextFloat() * 0.25f;
                        popPan = 0.3f + rnd.nextFloat() * 0.4f;
                    }
                    if (popEnv >= 0.001f) {
                        float p = popLp.next(rnd.nextFloat() * 2f - 1f) * popEnv * 2.5f;
                        l += p * (1f - popPan);
                        r += p * popPan;
                        popEnv *= 0.9992f;
                    }
                    outL = l;
                    outR = r;
                    break;
                }
            }
        }

        @Override
        public void run() {
            // level every sound to the same quiet loudness: measure a few seconds first
            double sum = 0;
            int warm = RATE * 6;
            for (int i = 0; i < warm; i++) {
                frame();
                sum += outL * outL + outR * outR;
                t++;
            }
            float rms = (float) Math.sqrt(sum / (warm * 2.0));
            float level = rms > 1e-5f ? Math.min(30f, TARGET_RMS / rms) : 1f;

            AudioTrack at;
            try {
                int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT);
                at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(Math.max(min * 2, RATE))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                at.play();
            } catch (Exception e) {
                return;
            }
            short[] buf = new short[4096];
            float gain = 0f;
            final float fadeIn = 1f / (RATE * 3f);
            final float fadeOut = 1f / (RATE * 1.5f);
            try {
                while (running || gain > 0f) {
                    for (int i = 0; i < buf.length; i += 2) {
                        gain = running ? Math.min(1f, gain + fadeIn) : Math.max(0f, gain - fadeOut);
                        // ease the fade so it starts and ends softly
                        float g = gain * gain * level;
                        frame();
                        float l = outL * g;
                        float r = outR * g;
                        // soft limit instead of a hard clip
                        l = l / (1f + Math.abs(l));
                        r = r / (1f + Math.abs(r));
                        buf[i] = (short) (l * 32767);
                        buf[i + 1] = (short) (r * 32767);
                        t++;
                    }
                    at.write(buf, 0, buf.length);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    at.stop();
                } catch (Exception ignored) {
                }
                at.release();
            }
        }
    }
}
