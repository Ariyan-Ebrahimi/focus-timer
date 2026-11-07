package com.example.timerapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Alarms: the next one on a clock face, one-tap quick alarms, and every alarm as a card
 * with its days and an on/off switch. Tapping a card (or +) opens the editor sheet.
 */
public class ClockActivity extends Activity {

    private static final int[] QUICK_MIN = {10, 20, 30, 60};

    private int main;
    private int sub;
    private int surface;
    private int surface2;
    private int accent;
    private int bad;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Keeps the "in 9h 12m" lines fresh. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 30_000L);
        }
    };

    public static Intent intent(Context c) {
        return new Intent(c, ClockActivity.class);
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
        main = ThemeHelper.resolve(this, R.attr.textMain);
        sub = ThemeHelper.resolve(this, R.attr.textSub);
        surface = ThemeHelper.resolve(this, R.attr.surface);
        surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        accent = ThemeHelper.resolve(this, R.attr.accent);
        bad = ThemeHelper.resolve(this, R.attr.badC);
        int bg = ThemeHelper.resolve(this, R.attr.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!Clay.isDark(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        // \u2039  Alarms                (+)
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipToPadding(false);
        head.setPaddingRelative(dp(18), dp(24), dp(22), dp(16));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(ColorStateList.valueOf(main));
        back.setPadding(dp(13), dp(13), dp(13), dp(13));
        back.setContentDescription(getString(R.string.cancel));
        Clay.style(back, surface, -1, 4, true);
        head.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView title = new TextView(this);
        title.setText(R.string.clock_alarms);
        Clay.text(title, 900, 30, main);
        title.setPaddingRelative(dp(14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView add = new ImageView(this);
        add.setImageResource(R.drawable.ic_plus);
        add.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        add.setPadding(dp(15), dp(15), dp(15), dp(15));
        add.setContentDescription(getString(R.string.clock_add));
        Clay.style(add, accent, -1, 6, true);
        head.addView(add, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPaddingRelative(dp(18), dp(4), dp(18), dp(32));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(null);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }

    // ------------------------------------------------------------------ building blocks

    private LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private TextView text(CharSequence s, int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        Clay.text(t, weight, sp, color);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private LinearLayout card(int topDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        c.setClipToPadding(false);
        c.setPaddingRelative(dp(22), dp(18), dp(20), dp(18));
        Clay.style(c, surface, 28, 5, false);
        list.addView(c, full(topDp));
        return c;
    }

    /** "7:30" big with " AM" small after it (12-hour phones). */
    private CharSequence bigTime(ClockAlarms.Alarm al) {
        String t = ClockAlarms.timeText(this, al);
        SpannableString s = new SpannableString(t);
        int sp = t.indexOf(' ');
        if (sp > 0 && !Character.isDigit(t.charAt(t.length() - 1))) {
            s.setSpan(new RelativeSizeSpan(0.45f), sp, t.length(), 0);
        }
        return s;
    }

    /** A clay on/off switch: a pill with a white knob that sits at the end when on. */
    private View switchView(boolean on) {
        FrameLayout track = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(on ? accent : surface2);
        if (!on) {
            g.setStroke(Math.max(1, dp(1.5f)), Clay.mix(surface2, sub, 0.5f));
        }
        track.setBackground(g);
        track.setPadding(dp(3), dp(3), dp(3), dp(3));
        View knob = new View(this);
        GradientDrawable k = new GradientDrawable();
        k.setShape(GradientDrawable.OVAL);
        k.setColor(on ? 0xFFFFFFFF : sub);
        knob.setBackground(k);
        knob.setElevation(dp(2));
        track.addView(knob, new FrameLayout.LayoutParams(dp(26), dp(26),
                Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START)));
        track.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(32)));
        track.setContentDescription(getString(on ? R.string.on : R.string.off));
        return track;
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        list.removeAllViews();
        List<ClockAlarms.Alarm> alarms = ClockAlarms.sorted(this);
        long now = System.currentTimeMillis();
        renderNext(ClockAlarms.soonest(this), now);
        renderQuick();
        if (alarms.isEmpty()) {
            return;
        }
        if (!AlarmHelper.canSchedule(this)) {
            TextView warn = text(getString(R.string.enable_exact), 700, 14, accent);
            warn.setPaddingRelative(dp(18), dp(12), dp(18), dp(12));
            Clay.inset(warn, surface2, 18);
            list.addView(warn, full(14));
            warn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlarmHelper.openExactAlarmSettings(ClockActivity.this);
                }
            });
        }
        TextView label = text(getString(R.string.clock_yours), 800, 14, sub);
        label.setPaddingRelative(dp(8), dp(26), 0, 0);
        list.addView(label);
        for (ClockAlarms.Alarm al : alarms) {
            renderCard(al, now);
        }
    }

    /** Top card: the next alarm on a clock face, or a short hint when none is on. */
    private void renderNext(ClockAlarms.Alarm next, long now) {
        LinearLayout c = card(4);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        ClockFaceView face = new ClockFaceView(this);
        if (next != null) {
            face.setTime(next.hour, next.minute);
        } else {
            Calendar cal = Calendar.getInstance();
            face.setTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            face.setAlpha(0.45f);
        }
        c.addView(face, new LinearLayout.LayoutParams(dp(92), dp(92)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPaddingRelative(dp(18), 0, 0, 0);
        if (next != null) {
            long at = ClockAlarms.next(next, now);
            info.addView(text(getString(R.string.clock_next), 800, 13, sub));
            TextView t = text(bigTime(next), 900, 38, main);
            t.setFontFeatureSettings("tnum");
            info.addView(t);
            info.addView(text(ClockAlarms.dayWord(this, at) + "  \u00b7  "
                    + getString(R.string.clock_in, ClockAlarms.untilText(at)), 800, 14, accent));
            if (next.label.length() > 0) {
                TextView l = text(next.label, 700, 13, sub);
                l.setPaddingRelative(0, dp(2), 0, 0);
                info.addView(l);
            }
        } else {
            info.addView(text(getString(ClockAlarms.load(this).isEmpty()
                    ? R.string.clock_none : R.string.clock_none_on), 900, 19, main));
            TextView s = text(getString(R.string.clock_none_sub), 600, 14, sub);
            s.setPaddingRelative(0, dp(4), 0, 0);
            info.addView(s);
        }
        c.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    /** "Quick alarm": +10 min, +20 min, +30 min, +1 h \u2014 one tap, rings once. */
    private void renderQuick() {
        TextView label = text(getString(R.string.clock_quick), 800, 14, sub);
        label.setPaddingRelative(dp(8), dp(22), 0, dp(4));
        list.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (final int m : QUICK_MIN) {
            TextView chip = new TextView(this);
            chip.setText("+" + (m >= 60 ? getString(R.string.hour_n, m / 60)
                    : getString(R.string.min_n, m)));
            chip.setGravity(Gravity.CENTER);
            Clay.text(chip, 800, 14, main);
            Clay.style(chip, surface, -1, 3, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    toastIn(ClockAlarms.quick(ClockActivity.this, m));
                    render();
                }
            });
        }
        list.addView(row, full(0));
    }

    private void renderCard(final ClockAlarms.Alarm al, long now) {
        LinearLayout c = card(12);
        c.setAlpha(al.on ? 1f : 0.6f);
        // time ............. switch
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = text(bigTime(al), 900, 34, al.on ? main : sub);
        time.setFontFeatureSettings("tnum");
        top.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View sw = switchView(al.on);
        top.addView(sw);
        c.addView(top);
        if (al.label.length() > 0) {
            c.addView(text(al.label, 800, 15, main));
        }
        // the week, with its ringing days lit; a one-time alarm says when it rings
        LinearLayout days = new LinearLayout(this);
        days.setGravity(Gravity.CENTER_VERTICAL);
        days.setPaddingRelative(0, dp(10), 0, 0);
        if (al.days == 0) {
            days.addView(text(getString(R.string.clock_once) + "  \u00b7  "
                    + ClockAlarms.dayWord(this, ClockAlarms.next(al, now)), 700, 14, sub));
        } else {
            String[] narrow = narrowDays();
            for (int i = 0; i < 7; i++) {
                boolean lit = (al.days & (1 << i)) != 0;
                TextView d = new TextView(this);
                d.setText(narrow[i]);
                d.setGravity(Gravity.CENTER);
                Clay.text(d, lit ? 900 : 700, 12, lit ? accent : sub);
                if (lit) {
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(Clay.mix(surface2, accent, 0.18f));
                    d.setBackground(g);
                } else {
                    d.setAlpha(0.55f);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
                lp.setMarginEnd(dp(5));
                days.addView(d, lp);
            }
        }
        c.addView(days);
        if (!al.missions.isEmpty()) {
            TextView ms = text(getString(R.string.m_line, Missions.names(this, al.missions)),
                    800, 13, main);
            ms.setPaddingRelative(0, dp(8), 0, 0);
            c.addView(ms);
        }
        if (al.on) {
            TextView in = text(getString(R.string.clock_in,
                    ClockAlarms.untilText(ClockAlarms.next(al, now))), 800, 13, accent);
            in.setPaddingRelative(0, dp(8), 0, 0);
            c.addView(in);
        }
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                al.on = !al.on;
                ClockAlarms.put(ClockActivity.this, al);
                if (al.on) {
                    toastIn(al);
                }
                render();
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(al);
            }
        });
    }

    private void toastIn(ClockAlarms.Alarm al) {
        Toast.makeText(this, getString(R.string.clock_set_in,
                ClockAlarms.untilText(ClockAlarms.next(al, System.currentTimeMillis()))),
                Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ editor sheet

    /** Add (existing == null) or change an alarm, in a sheet like the New timer one. */
    private void edit(final ClockAlarms.Alarm existing) {
        final ClockAlarms.Alarm al = new ClockAlarms.Alarm();
        if (existing != null) {
            al.id = existing.id;
            al.hour = existing.hour;
            al.minute = existing.minute;
            al.days = existing.days;
            al.label = existing.label;
            al.snooze = existing.snooze;
            al.quiet = existing.quiet;
            al.rise = existing.rise;
            for (Missions.Mission m : existing.missions) {
                al.missions.add(m.copy());
            }
        } else {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR_OF_DAY, 1);
            al.hour = cal.get(Calendar.HOUR_OF_DAY);
            al.minute = cal.get(Calendar.MINUTE);
        }
        al.on = true;

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPaddingRelative(dp(10), dp(10), dp(10), dp(10));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(22), dp(12), dp(22), dp(22));
        root.setClipToPadding(false);
        root.setClipChildren(false);
        scroll.addView(root);
        Clay.style(root, surface, 32, 6, false);
        final Dialog dialog = new Dialog(this);

        View handleBar = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(Clay.mix(sub, 0x00000000, 0.55f));
        hb.setCornerRadius(dp(3));
        handleBar.setBackground(hb);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(14);
        root.addView(handleBar, hl);
        root.addView(text(getString(existing == null ? R.string.clock_add : R.string.edit),
                900, 22, main));

        // hour : minute (and AM/PM on 12-hour phones) wheels
        final boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        LinearLayout wheels = new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        wheels.setPaddingRelative(dp(8), dp(6), dp(8), dp(6));
        Clay.inset(wheels, surface2, 24);
        final NumberPicker wh = wheel(wheels, h24 ? 0 : 1, h24 ? 23 : 12, true);
        TextView colon = text(":", 900, 30, main);
        colon.setPaddingRelative(dp(2), 0, dp(2), dp(4));
        wheels.addView(colon);
        final NumberPicker wm = wheel(wheels, 0, 59, true);
        NumberPicker ampm = null;
        if (h24) {
            wh.setValue(al.hour);
        } else {
            wh.setValue(al.hour % 12 == 0 ? 12 : al.hour % 12);
            ampm = wheel(wheels, 0, 1, false);
            ampm.setDisplayedValues(new java.text.DateFormatSymbols().getAmPmStrings());
            ampm.setValue(al.hour >= 12 ? 1 : 0);
        }
        final NumberPicker wampm = ampm;
        wm.setValue(al.minute);
        LinearLayout.LayoutParams wl = full(16);
        root.addView(wheels, wl);

        // repeat: presets, then the single days
        root.addView(section(getString(R.string.clock_repeat)));
        final int[] presetDays = {0, ClockAlarms.EVERY_DAY, ClockAlarms.WEEKDAYS, ClockAlarms.WEEKENDS};
        final int[] presetNames = {R.string.clock_once, R.string.clock_every_day,
                R.string.clock_weekdays, R.string.clock_weekends};
        final TextView[] presets = new TextView[4];
        final TextView[] chips = new TextView[7];
        final Runnable paintDays = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 4; i++) {
                    paintChip(presets[i], al.days == presetDays[i]);
                }
                for (int i = 0; i < 7; i++) {
                    paintChip(chips[i], (al.days & (1 << i)) != 0);
                }
            }
        };
        LinearLayout pr = new LinearLayout(this);
        pr.setClipChildren(false);
        pr.setClipToPadding(false);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView p = new TextView(this);
            p.setText(presetNames[i]);
            p.setGravity(Gravity.CENTER);
            p.setMaxLines(1);
            p.setPaddingRelative(dp(4), 0, dp(4), 0);
            presets[i] = p;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            pr.addView(p, lp);
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    al.days = presetDays[idx];
                    paintDays.run();
                }
            });
        }
        root.addView(pr, full(0));
        LinearLayout days = new LinearLayout(this);
        days.setClipToPadding(false);
        days.setClipChildren(false);
        String[] narrow = narrowDays();
        String[] fullNames = ClockAlarms.dayNames();
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView c = new TextView(this);
            c.setText(narrow[i]);
            c.setGravity(Gravity.CENTER);
            c.setContentDescription(fullNames[i]);
            chips[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(3), dp(6), dp(3), dp(3));
            days.addView(c, lp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    al.days ^= 1 << bit;
                    paintDays.run();
                }
            });
        }
        root.addView(days, full(0));
        paintDays.run();

        // label
        final EditText label = new EditText(this);
        label.setHint(R.string.clock_label_hint);
        label.setText(al.label);
        label.setSingleLine(true);
        Clay.text(label, 700, 16, main);
        label.setHintTextColor(sub);
        label.setPaddingRelative(dp(18), dp(13), dp(18), dp(13));
        Clay.inset(label, surface2, 18);
        root.addView(label, full(16));

        // snooze length
        root.addView(section(getString(R.string.clock_snooze_len)));
        LinearLayout sr = new LinearLayout(this);
        sr.setClipChildren(false);
        sr.setClipToPadding(false);
        final TextView[] snoozeChips = new TextView[ClockAlarms.SNOOZE_CHOICES.length];
        final Runnable paintSnooze = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < snoozeChips.length; i++) {
                    paintChip(snoozeChips[i], al.snooze == ClockAlarms.SNOOZE_CHOICES[i]);
                }
            }
        };
        for (int i = 0; i < snoozeChips.length; i++) {
            final int m = ClockAlarms.SNOOZE_CHOICES[i];
            TextView s = new TextView(this);
            s.setText(getString(R.string.min_n, m));
            s.setGravity(Gravity.CENTER);
            snoozeChips[i] = s;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            sr.addView(s, lp);
            s.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    al.snooze = m;
                    paintSnooze.run();
                }
            });
        }
        root.addView(sr, full(0));
        paintSnooze.run();

        // rising volume: Off / 30 s / 60 s
        root.addView(section(getString(R.string.clock_rise)));
        LinearLayout rr = new LinearLayout(this);
        rr.setClipChildren(false);
        rr.setClipToPadding(false);
        final TextView[] riseChips = new TextView[ClockAlarms.RISE_CHOICES.length];
        final Runnable paintRise = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < riseChips.length; i++) {
                    paintChip(riseChips[i], al.rise == ClockAlarms.RISE_CHOICES[i]);
                }
            }
        };
        for (int i = 0; i < riseChips.length; i++) {
            final int s = ClockAlarms.RISE_CHOICES[i];
            TextView r = new TextView(this);
            r.setText(s == 0 ? getString(R.string.off) : getString(R.string.clock_sec_n, s));
            r.setGravity(Gravity.CENTER);
            riseChips[i] = r;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            rr.addView(r, lp);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    al.rise = s;
                    paintRise.run();
                }
            });
        }
        root.addView(rr, full(0));
        paintRise.run();
        TextView riseSub = text(getString(R.string.clock_rise_sub), 600, 12, sub);
        riseSub.setPaddingRelative(dp(6), dp(2), 0, 0);
        root.addView(riseSub);

        // wake-up mission: pick any, set each one up, try it
        root.addView(section(getString(R.string.m_section)));
        final LinearLayout missionBox = new LinearLayout(this);
        missionBox.setOrientation(LinearLayout.VERTICAL);
        missionBox.setClipChildren(false);
        missionBox.setClipToPadding(false);
        root.addView(missionBox, full(0));
        renderMissions(missionBox, al);

        // Delete / Cancel  +  Save
        LinearLayout actions = new LinearLayout(this);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        TextView left = pill(getString(existing != null ? R.string.delete : R.string.cancel),
                surface, existing != null ? bad : main);
        TextView save = pill(getString(R.string.save), accent, 0xFFFFFFFF);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(56), 1f);
        l1.setMarginEnd(dp(7));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(56), 1.3f);
        l2.setMarginStart(dp(7));
        actions.addView(left, l1);
        actions.addView(save, l2);
        root.addView(actions, full(22));
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (existing != null) {
                    ClockAlarms.delete(ClockActivity.this, existing.id);
                    render();
                }
                dialog.dismiss();
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wh.clearFocus();
                wm.clearFocus();
                int h = wh.getValue();
                if (!h24) {
                    h = h % 12 + (wampm != null && wampm.getValue() == 1 ? 12 : 0);
                }
                al.hour = h;
                al.minute = wm.getValue();
                al.label = label.getText().toString().trim();
                ClockAlarms.put(ClockActivity.this, al);
                toastIn(al);
                render();
                dialog.dismiss();
            }
        });

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(scroll);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        dialog.show();
    }

    /** The mission chips, then a settings card for each chosen mission. */
    private void renderMissions(final LinearLayout box, final ClockAlarms.Alarm al) {
        box.removeAllViews();
        // None + every mission this phone can do, three to a row so long names fit
        List<Integer> kinds = new java.util.ArrayList<Integer>();
        kinds.add(-1);
        for (int t = 0; t < Missions.COUNT; t++) {
            if (Missions.available(this, t)) {
                kinds.add(t);
            }
        }
        LinearLayout row = null;
        for (int i = 0; i < kinds.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                box.addView(row, full(0));
            }
            final int type = kinds.get(i);
            int at = indexOf(al, type);
            TextView chip = new TextView(this);
            chip.setText(type < 0 ? getString(R.string.m_none) : Missions.name(this, type));
            chip.setGravity(Gravity.CENTER);
            chip.setPaddingRelative(dp(4), 0, dp(4), 0);
            paintChip(chip, type < 0 ? al.missions.isEmpty() : at >= 0);
            fitOneLine(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(chip, lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (type < 0) {
                        al.missions.clear();
                    } else if (indexOf(al, type) >= 0) {
                        al.missions.remove(indexOf(al, type));
                    } else {
                        al.missions.add(Missions.create(ClockActivity.this, type));
                        if (type == Missions.WALK && Build.VERSION.SDK_INT >= 29
                                && !Missions.canCountSteps(ClockActivity.this)) {
                            requestPermissions(new String[]{
                                    android.Manifest.permission.ACTIVITY_RECOGNITION}, 77);
                        }
                    }
                    renderMissions(box, al);
                }
            });
        }
        // keep the row lengths even
        while (row != null && row.getChildCount() < 3) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        if (al.missions.isEmpty()) {
            return;
        }
        for (final Missions.Mission m : al.missions) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClipChildren(false);
            card.setClipToPadding(false);
            card.setPaddingRelative(dp(14), dp(12), dp(14), dp(12));
            Clay.inset(card, surface2, 20);
            card.addView(text(Missions.name(this, m.type), 900, 15, main));
            if (m.type == Missions.MATH) {
                final TextView[] lv = new TextView[3];
                final Runnable paintLv = new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 3; i++) {
                            paintChip(lv[i], m.level == i);
                        }
                    }
                };
                card.addView(optionRow(lv, new String[]{getString(R.string.m_easy),
                        getString(R.string.m_medium), getString(R.string.m_hard)},
                        new IntPick() {
                            @Override
                            public void pick(int i) {
                                m.level = i;
                                paintLv.run();
                            }
                        }));
                paintLv.run();
            }
            if (m.type == Missions.TYPE) {
                final EditText phrase = new EditText(this);
                phrase.setText(m.text);
                phrase.setHint(R.string.m_phrase);
                Clay.text(phrase, 700, 15, main);
                phrase.setHintTextColor(sub);
                phrase.setPaddingRelative(dp(14), dp(10), dp(14), dp(10));
                Clay.inset(phrase, surface, 14);
                phrase.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        m.text = s.toString();
                    }
                });
                card.addView(phrase, full(8));
            } else {
                final int[] amounts = Missions.AMOUNTS[m.type];
                TextView what = text(getString(Missions.AMOUNT_NAMES[m.type]), 700, 13, sub);
                what.setPaddingRelative(dp(4), dp(8), 0, 0);
                card.addView(what);
                final TextView[] av = new TextView[amounts.length];
                String[] labels = new String[amounts.length];
                for (int i = 0; i < amounts.length; i++) {
                    labels[i] = String.valueOf(amounts[i]);
                }
                final Runnable paintAm = new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < amounts.length; i++) {
                            paintChip(av[i], m.amount == amounts[i]);
                        }
                    }
                };
                card.addView(optionRow(av, labels, new IntPick() {
                    @Override
                    public void pick(int i) {
                        m.amount = amounts[i];
                        paintAm.run();
                    }
                }));
                paintAm.run();
            }
            box.addView(card, full(10));
        }
        // quiet while solving
        LinearLayout q = new LinearLayout(this);
        q.setGravity(Gravity.CENTER_VERTICAL);
        q.setPaddingRelative(dp(6), dp(4), 0, dp(4));
        LinearLayout qt = new LinearLayout(this);
        qt.setOrientation(LinearLayout.VERTICAL);
        qt.addView(text(getString(R.string.m_quiet), 800, 15, main));
        qt.addView(text(getString(R.string.m_quiet_sub), 600, 12, sub));
        q.addView(qt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View sw = switchView(al.quiet);
        q.addView(sw);
        box.addView(q, full(14));
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                al.quiet = !al.quiet;
                renderMissions(box, al);
            }
        });
        // try it now
        TextView tryIt = pill(getString(R.string.m_try), surface, accent);
        box.addView(tryIt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        ((LinearLayout.LayoutParams) tryIt.getLayoutParams()).topMargin = dp(12);
        tryIt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(ClockRingActivity.preview(ClockActivity.this, al.missions));
            }
        });
    }

    /** Keeps a chip's label on one line, shrinking the text if it is long. */
    private static void fitOneLine(TextView t) {
        t.setSingleLine(true);
        t.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
    }

    private interface IntPick {
        void pick(int index);
    }

    private LinearLayout optionRow(TextView[] out, String[] labels, final IntPick pick) {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView c = new TextView(this);
            c.setText(labels[i]);
            c.setGravity(Gravity.CENTER);
            c.setMaxLines(1);
            out[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(3), dp(6), dp(3), dp(2));
            r.addView(c, lp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.pick(idx);
                }
            });
        }
        return r;
    }

    private static int indexOf(ClockAlarms.Alarm al, int type) {
        for (int i = 0; i < al.missions.size(); i++) {
            if (al.missions.get(i).type == type) {
                return i;
            }
        }
        return -1;
    }

    private View section(String s) {
        TextView t = text(s, 800, 14, sub);
        t.setPaddingRelative(dp(6), dp(18), 0, dp(4));
        return t;
    }

    private TextView pill(String s, int fill, int color) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        Clay.text(b, 800, 16, color);
        Clay.style(b, fill, -1, 5, true);
        return b;
    }

    private void paintChip(TextView c, boolean on) {
        if (on) {
            Clay.text(c, 900, 13, 0xFFFFFFFF);
            Clay.style(c, accent, -1, 2, false);
        } else {
            Clay.text(c, 800, 13, main);
            Clay.style(c, surface, -1, 2, true);
        }
    }

    private NumberPicker wheel(LinearLayout row, int min, int max, boolean twoDigits) {
        NumberPicker np = new NumberPicker(this);
        np.setMinValue(min);
        np.setMaxValue(max);
        if (twoDigits) {
            String[] shown = new String[max - min + 1];
            for (int i = min; i <= max; i++) {
                shown[i - min] = String.format(Locale.US, "%02d", i);
            }
            np.setDisplayedValues(shown);
        }
        np.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            np.setTextColor(main);
            np.setTextSize(dp(twoDigits ? 30 : 20));
            np.setSelectionDividerHeight(0);
        }
        row.addView(np, new LinearLayout.LayoutParams(dp(twoDigits ? 84 : 72), dp(160)));
        return np;
    }

    /** One-letter weekday names (M T W ...), Monday first. */
    private static String[] narrowDays() {
        String[] wd = android.icu.text.DateFormatSymbols.getInstance().getWeekdays(
                android.icu.text.DateFormatSymbols.STANDALONE,
                android.icu.text.DateFormatSymbols.NARROW);
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        String[] out = new String[7];
        for (int i = 0; i < 7; i++) {
            out[i] = wd[order[i]];
        }
        return out;
    }
}
