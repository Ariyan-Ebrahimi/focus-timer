package com.example.timerapp;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Full-screen screen for a ringing clock alarm. Without a mission: Snooze or Stop.
 * With missions: Snooze, or Start mission \u2014 the alarm only turns off once every mission
 * is finished. Also opens in preview mode ("Try it") with no alarm ringing.
 */
public class ClockRingActivity extends Activity {

    /** How long touching the mission keeps the alarm quiet. */
    private static final long QUIET_MS = 20_000L;

    private long alarmId;
    private boolean preview;
    private boolean quiet;
    private List<Missions.Mission> missions;
    private int step;
    private Missions.Run run;
    private LinearLayout root;
    private int main;
    private int sub;
    private int surface;
    private int accent;

    public static Intent intent(Context c, long id) {
        return new Intent(c, ClockRingActivity.class).putExtra("id", id);
    }

    /** Opens the missions to try them out, without an alarm. */
    public static Intent preview(Context c, List<Missions.Mission> missions) {
        return new Intent(c, ClockRingActivity.class).putExtra("preview",
                Missions.toJson(missions).toString());
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
        String pv = getIntent().getStringExtra("preview");
        preview = pv != null;
        if (!preview) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        int bg = ThemeHelper.resolve(this, R.attr.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        main = ThemeHelper.resolve(this, R.attr.textMain);
        sub = ThemeHelper.resolve(this, R.attr.textSub);
        surface = ThemeHelper.resolve(this, R.attr.surface);
        accent = ThemeHelper.resolve(this, R.attr.accent);

        ClockAlarms.Alarm al;
        if (preview) {
            al = new ClockAlarms.Alarm();
            try {
                al.missions = Missions.fromJson(new org.json.JSONArray(pv));
            } catch (Exception ignored) {
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            al.hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            al.minute = cal.get(java.util.Calendar.MINUTE);
            al.quiet = false;
        } else {
            alarmId = getIntent().getLongExtra("id", -1);
            al = ClockAlarms.find(this, alarmId);
            if (al == null) {
                finish();
                return;
            }
        }
        missions = al.missions;
        quiet = al.quiet;
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            onBackPressed();
                        }
                    });
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPaddingRelative(dp(24), dp(56), dp(24), dp(32));
        root.setClipToPadding(false);
        root.setClipChildren(false);
        scroll.addView(root);
        setContentView(scroll);
        if (preview && !missions.isEmpty()) {
            startMissions();
        } else {
            showRinging(al);
        }
    }

    private TextView text(String s, int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        Clay.text(t, weight, sp, color);
        return t;
    }

    private TextView button(String s, int fill, int color) {
        TextView b = text(s, 800, 17, color);
        Clay.style(b, fill, -1, 6, true);
        return b;
    }

    /** Time, label and the pulsing alarm clock, with Snooze and Stop / Start mission. */
    private void showRinging(ClockAlarms.Alarm al) {
        root.removeAllViews();
        root.addView(text(al.label.length() > 0 ? al.label : getString(R.string.clock_alarm),
                800, 20, sub));
        TextView time = text(ClockAlarms.timeText(this, al), 900, 64, main);
        time.setFontFeatureSettings("tnum");
        root.addView(time);

        FrameLayout disc = new FrameLayout(this);
        disc.setClipChildren(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(170), dp(170));
        dlp.topMargin = dp(28);
        root.addView(disc, dlp);
        Clay.style(disc, surface, -1, 8, false);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_alarm);
        icon.setImageTintList(ColorStateList.valueOf(accent));
        disc.addView(icon, new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER));
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(disc,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
        pulse.setDuration(700);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
        ObjectAnimator wiggle = ObjectAnimator.ofFloat(icon, View.ROTATION, -12f, 12f);
        wiggle.setDuration(140);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.start();

        if (!missions.isEmpty()) {
            TextView need = text(getString(R.string.m_finish_to_stop), 700, 15, sub);
            need.setPaddingRelative(0, dp(22), 0, 0);
            root.addView(need);
            root.addView(text(Missions.names(this, missions), 900, 16, accent));
        }

        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView snooze = button(getString(R.string.clock_snooze_n, al.snooze),
                surface, main);
        LinearLayout.LayoutParams snl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        snl.topMargin = dp(28);
        root.addView(snooze, snl);
        TextView primary = button(getString(missions.isEmpty() ? R.string.stop : R.string.m_start),
                accent, 0xFFFFFFFF);
        Clay.text(primary, 900, 20, 0xFFFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        slp.topMargin = dp(16);
        root.addView(primary, slp);

        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int min = ClockAlarms.snooze(ClockRingActivity.this, alarmId);
                Toast.makeText(ClockRingActivity.this, getString(R.string.clock_snoozed_n, min),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (missions.isEmpty()) {
                    ClockAlarms.stop(ClockRingActivity.this, alarmId);
                    finish();
                } else {
                    startMissions();
                }
            }
        });
    }

    private void startMissions() {
        step = 0;
        keepQuiet();
        showMission();
    }

    /** "Mission 1 of 2 \u00b7 Math", then the mission itself. */
    private void showMission() {
        if (run != null) {
            run.stop();
        }
        root.removeAllViews();
        root.setPaddingRelative(dp(20), dp(36), dp(20), dp(24));
        Missions.Mission m = missions.get(step);
        String head = missions.size() > 1
                ? getString(R.string.m_step_of, step + 1, missions.size()) + "  \u00b7  "
                + Missions.name(this, m.type)
                : Missions.name(this, m.type);
        root.addView(text(head, 800, 15, sub));
        run = Missions.start(this, m, new Runnable() {
            @Override
            public void run() {
                step++;
                if (step < missions.size()) {
                    showMission();
                } else {
                    allDone();
                }
            }
        }, new Runnable() {
            @Override
            public void run() {
                keepQuiet();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(run.view(), lp);
    }

    private void allDone() {
        run = null;
        if (!preview) {
            ClockAlarms.stop(this, alarmId);
            Toast.makeText(this, R.string.m_done, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    /** While someone works on the mission the alarm stays silent (if the alarm allows it). */
    private void keepQuiet() {
        if (!preview && quiet) {
            AlarmService.quiet(QUIET_MS);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (run != null) {
            keepQuiet();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (preview) {
            finish();
            return;
        }
        if (missions.isEmpty()) {
            ClockAlarms.stop(this, alarmId);
            finish();
        }
        // with a mission, Back does nothing: only finishing it turns the alarm off
    }

    @Override
    protected void onDestroy() {
        if (run != null) {
            run.stop();
        }
        super.onDestroy();
    }
}
