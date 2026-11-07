package com.example.timerapp;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen "Time's up" screen, shown over the lock screen when a timer ends. */
public class AlarmActivity extends Activity {

    private long timerId;
    private long rangAt;
    private TextView ago;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long s = Math.max(0L, (System.currentTimeMillis() - rangAt) / 1000L);
            ago.setText(getString(R.string.rang_ago,
                    String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)));
            handler.postDelayed(this, 1000);
        }
    };

    public static Intent intent(Context c, long id, String name, long total) {
        Intent i = new Intent(c, AlarmActivity.class);
        i.putExtra("timer_id", id);
        i.putExtra("timer_name", name);
        i.putExtra("timer_total", total);
        i.putExtra("rang_at", System.currentTimeMillis());
        return i;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(App.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.themeRes(PrefsStore.getMode(this), PrefsStore.getAcc(this)));
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int bg = ThemeHelper.resolve(this, R.attr.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        Intent in = getIntent();
        timerId = in.getLongExtra("timer_id", -1);
        String name = in.getStringExtra("timer_name");
        long total = in.getLongExtra("timer_total", 0);
        rangAt = in.getLongExtra("rang_at", System.currentTimeMillis());
        if (name == null || name.length() == 0) {
            name = getString(R.string.timer);
        }

        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int surface = ThemeHelper.resolve(this, R.attr.surface);
        int accent = ThemeHelper.resolve(this, R.attr.accent);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(bg);
        root.setPaddingRelative(dp(28), dp(72), dp(28), dp(40));
        root.setClipToPadding(false);
        root.setClipChildren(false);

        TextView label = new TextView(this);
        label.setText(R.string.times_up);
        Clay.text(label, 700, 16, sub);
        root.addView(label);

        TextView title = new TextView(this);
        title.setText(name);
        Clay.text(title, 900, 36, main);
        title.setGravity(Gravity.CENTER);
        title.setPaddingRelative(0, dp(4), 0, 0);
        root.addView(title);

        // pulsing clay dial
        FrameLayout dial = new FrameLayout(this);
        dial.setClipChildren(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(230), dp(230));
        dlp.topMargin = dp(36);
        dlp.bottomMargin = dp(18);
        dial.setLayoutParams(dlp);
        RingView ring = new RingView(this);
        ring.setRing(16, 20);
        ring.setProgress(1f);
        Clay.style(ring, surface, -1, 8, false);
        dial.addView(ring, new FrameLayout.LayoutParams(dp(230), dp(230)));
        TextView dur = new TextView(this);
        dur.setText(MainActivity.formatDuration(total));
        Clay.text(dur, 900, 40, main);
        dur.setFontFeatureSettings("tnum");
        dur.setElevation(dp(10));
        FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        durLp.gravity = Gravity.CENTER;
        dial.addView(dur, durLp);
        root.addView(dial);
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(dial,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f));
        pulse.setDuration(700);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        ago = new TextView(this);
        Clay.text(ago, 600, 14, sub);
        root.addView(ago);

        // the finished run was logged as a session: offer to say what it was spent on
        final PrefsStore.Hist logged = NoteDialog.justLogged(this, rangAt - 60_000L);
        if (logged != null) {
            final TextView note = new TextView(this);
            note.setText(R.string.add_note);
            Clay.text(note, 800, 15, accent);
            note.setGravity(Gravity.CENTER);
            note.setPaddingRelative(dp(22), dp(11), dp(22), dp(11));
            Clay.style(note, surface, -1, 3, true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(18);
            root.addView(note, nlp);
            note.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // quiet while typing; closing the note closes this screen
                    NotificationHelper.cancelAlarm(AlarmActivity.this, timerId);
                    NoteDialog.show(AlarmActivity.this, logged, null, new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
                }
            });
        }

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        TextView plus = button(getString(R.string.plus_min), surface, main);
        TextView restart = button(getString(R.string.restart), surface, main);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(58), 1f);
        half.setMarginEnd(dp(8));
        row.addView(plus, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(58), 1f);
        half2.setMarginStart(dp(8));
        row.addView(restart, half2);
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView stop = button(getString(R.string.stop), accent, 0xFFFFFFFF);
        Clay.text(stop, 900, 20, 0xFFFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        slp.topMargin = dp(18);
        root.addView(stop, slp);

        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act(new TimerOps.Op() {
                    @Override
                    public void run(Context c, TimerItem it) {
                        TimerOps.addMinute(c, it);
                    }
                });
            }
        });
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act(new TimerOps.Op() {
                    @Override
                    public void run(Context c, TimerItem it) {
                        TimerOps.reset(c, it);
                        TimerOps.start(c, it);
                    }
                });
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act(null);
            }
        });

        setContentView(root);
        handler.post(tick);
    }

    private TextView button(String text, int fill, int color) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        Clay.text(b, 800, 17, color);
        Clay.style(b, fill, -1, 6, true);
        return b;
    }

    /** Silences the alarm, optionally changes the timer, then closes. */
    private void act(TimerOps.Op op) {
        NotificationHelper.cancelAlarm(this, timerId);
        if (op != null) {
            TimerOps.apply(this, timerId, op);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        act(null);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
