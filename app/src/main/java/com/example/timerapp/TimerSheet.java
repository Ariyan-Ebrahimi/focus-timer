package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bottom sheet for creating or editing a timer: a keypad for plain timers, steppers for
 * Pomodoro, and a step list for custom sequences.
 */
public final class TimerSheet {

    public interface Callback {
        /** steps is empty for a plain timer (then ms is its duration). */
        void onDone(String name, long ms, List<TimerItem.Step> steps, int rounds, long projectId,
                    boolean start);
    }

    private static final int MODE_TIMER = 0;
    private static final int MODE_POMO = 1;
    private static final int MODE_SEQ = 2;

    private final Activity a;
    private final TimerItem editing;
    private final Callback cb;
    private final Dialog dialog;
    private final int main;
    private final int sub;
    private final int surface;
    private final int surface2;
    private final int accent;

    private int mode = MODE_TIMER;
    private final LinearLayout body;
    private final TextView[] segs = new TextView[3];
    private EditText nameInput;
    private long project;
    private LinearLayout projectRow;

    // timer mode: typed digits, read right to left as hh mm ss
    private String digits = "";

    // pomodoro
    private int pFocus;
    private int pShort;
    private int pLong;
    private int pRounds;
    private TextView pSummary;

    // sequence
    private final List<TimerItem.Step> seq = new ArrayList<TimerItem.Step>();
    private int seqRounds = 1;
    private LinearLayout seqList;
    private TextView seqSummary;

    public static void show(Activity a, TimerItem editing, long projectId, Callback cb) {
        new TimerSheet(a, editing, projectId, cb).dialog.show();
    }

    private TimerSheet(Activity a, TimerItem editing, long projectId, Callback cb) {
        this.a = a;
        this.editing = editing;
        this.cb = cb;
        this.project = editing != null ? editing.projectId : projectId;
        main = ThemeHelper.resolve(a, R.attr.textMain);
        sub = ThemeHelper.resolve(a, R.attr.textSub);
        surface = ThemeHelper.resolve(a, R.attr.surface);
        surface2 = ThemeHelper.resolve(a, R.attr.surface2);
        accent = ThemeHelper.resolve(a, R.attr.accent);

        pFocus = PrefsStore.getPomoFocus(a);
        pShort = PrefsStore.getPomoShort(a);
        pLong = PrefsStore.getPomoLong(a);
        pRounds = PrefsStore.getPomoRounds(a);

        if (editing != null && editing.isSequence()) {
            mode = MODE_SEQ;
            for (TimerItem.Step s : editing.steps) {
                seq.add(new TimerItem.Step(s.name, s.ms, s.rest));
            }
            seqRounds = editing.rounds;
        } else {
            seq.add(new TimerItem.Step(a.getString(R.string.work), 45_000L, false));
            seq.add(new TimerItem.Step(a.getString(R.string.rest), 15_000L, true));
            seqRounds = 8;
            digits = editing != null ? toDigits(editing.total) : "2500";
        }

        ScrollView scroll = new ScrollView(a);
        scroll.setClipToPadding(false);
        scroll.setPaddingRelative(dp(10), dp(10), dp(10), dp(10));
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(22), dp(12), dp(22), dp(22));
        root.setClipToPadding(false);
        root.setClipChildren(false);
        scroll.addView(root);
        Clay.style(root, surface, 32, 6, false);

        View handle = new View(a);
        handle.setBackground(round(Clay.mix(sub, 0x00000000, 0.55f), 3));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(40), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(14);
        root.addView(handle, hl);

        TextView title = new TextView(a);
        title.setText(editing == null ? R.string.new_timer : R.string.edit_timer);
        Clay.text(title, 900, 22, main);
        root.addView(title);

        if (editing == null) {
            LinearLayout well = new LinearLayout(a);
            well.setOrientation(LinearLayout.HORIZONTAL);
            well.setPaddingRelative(dp(5), dp(5), dp(5), dp(5));
            well.setClipToPadding(false);
            Clay.inset(well, surface2, -1);
            String[] labels = {a.getString(R.string.mode_timer), a.getString(R.string.pomodoro),
                    a.getString(R.string.mode_sequence)};
            for (int i = 0; i < 3; i++) {
                final int m = i;
                TextView t = new TextView(a);
                t.setText(labels[i]);
                t.setGravity(Gravity.CENTER);
                Clay.text(t, 800, 14, sub);
                t.setPaddingRelative(0, dp(9), 0, dp(9));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        setMode(m);
                    }
                });
                segs[i] = t;
                well.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            LinearLayout.LayoutParams wl = full();
            wl.topMargin = dp(16);
            root.addView(well, wl);
        }

        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        root.addView(body, full());

        nameInput = new EditText(a);
        nameInput.setSingleLine(true);
        Clay.text(nameInput, 700, 16, main);
        nameInput.setHintTextColor(sub);
        Clay.inset(nameInput, surface2, 18);
        nameInput.setPaddingRelative(dp(18), dp(13), dp(18), dp(13));
        if (editing != null) {
            nameInput.setText(editing.name);
        }
        LinearLayout.LayoutParams nl = full();
        nl.topMargin = dp(16);
        root.addView(nameInput, nl);

        if (!PrefsStore.loadProjects(a).isEmpty()) {
            HorizontalScrollView ps = new HorizontalScrollView(a);
            ps.setHorizontalScrollBarEnabled(false);
            ps.setClipToPadding(false);
            ps.setPaddingRelative(0, dp(4), 0, dp(4));
            projectRow = new LinearLayout(a);
            projectRow.setClipToPadding(false);
            ps.addView(projectRow);
            LinearLayout.LayoutParams pl = full();
            pl.topMargin = dp(12);
            root.addView(ps, pl);
            renderProjects();
        }

        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        TextView secondary = pillButton(a.getString(editing == null ? R.string.save : R.string.cancel), surface, main);
        TextView primary = pillButton(a.getString(editing == null ? R.string.start : R.string.save), accent,
                0xFFFFFFFF);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(56), 1f);
        l1.setMarginEnd(dp(7));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(56), 1.3f);
        l2.setMarginStart(dp(7));
        actions.addView(secondary, l1);
        actions.addView(primary, l2);
        LinearLayout.LayoutParams al = full();
        al.topMargin = dp(20);
        root.addView(actions, al);
        secondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TimerSheet.this.editing == null) {
                    submit(false);
                } else {
                    dialog.dismiss();
                }
            }
        });
        primary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit(TimerSheet.this.editing == null);
            }
        });

        dialog = new Dialog(a);
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
        setMode(mode);
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private android.graphics.drawable.GradientDrawable round(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView pillButton(String text, int fill, int color) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        Clay.text(b, 800, 16, color);
        Clay.style(b, fill, -1, 5, true);
        return b;
    }

    private TextView chip(String text) {
        TextView c = new TextView(a);
        c.setText(text);
        Clay.text(c, 800, 13, main);
        c.setPaddingRelative(dp(14), dp(7), dp(14), dp(8));
        Clay.style(c, surface, -1, 3, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    private ImageView roundIcon(int icon, float sizeDp) {
        ImageView b = new ImageView(a);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(main));
        int p = dp(sizeDp * 0.28f);
        b.setPaddingRelative(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        Clay.style(b, surface, -1, 3, true);
        return b;
    }

    private void renderProjects() {
        projectRow.removeAllViews();
        List<PrefsStore.Project> list = PrefsStore.loadProjects(a);
        for (int i = -1; i < list.size(); i++) {
            final long id = i < 0 ? 0 : list.get(i).id;
            boolean sel = id == project;
            TextView c = new TextView(a);
            c.setText(i < 0 ? a.getString(R.string.no_project) : list.get(i).name);
            Clay.text(c, 800, 13, sel ? main : sub);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPaddingRelative(dp(12), dp(7), dp(14), dp(8));
            if (i >= 0) {
                android.graphics.drawable.GradientDrawable dot =
                        new android.graphics.drawable.GradientDrawable();
                dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dot.setColor(Projects.color(a, list.get(i).color));
                dot.setSize(dp(10), dp(10));
                dot.setBounds(0, 0, dp(10), dp(10));
                c.setCompoundDrawablesRelative(dot, null, null, null);
                c.setCompoundDrawablePadding(dp(7));
            }
            if (sel) {
                Clay.inset(c, surface2, -1);
            } else {
                c.setBackground(null);
            }
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    project = id;
                    renderProjects();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            projectRow.addView(c, lp);
        }
    }

    private void setMode(int m) {
        mode = m;
        for (int i = 0; i < 3; i++) {
            if (segs[i] == null) {
                continue;
            }
            boolean sel = i == m;
            if (sel) {
                Clay.style(segs[i], surface, -1, 2, false);
            } else {
                segs[i].setBackground(null);
                segs[i].setElevation(0);
            }
            segs[i].setTextColor(sel ? main : sub);
        }
        nameInput.setHint(m == MODE_TIMER ? R.string.name_optional
                : m == MODE_POMO ? R.string.pomodoro : R.string.name_workout);
        body.removeAllViews();
        if (m == MODE_TIMER) {
            buildKeypad();
        } else if (m == MODE_POMO) {
            buildPomodoro();
        } else {
            buildSequence();
        }
    }

    // ------------------------------------------------------------------ timer keypad

    static String toDigits(long ms) {
        long s = ms / 1000L;
        String d = String.format(Locale.US, "%02d%02d%02d", s / 3600, (s / 60) % 60, s % 60);
        int i = 0;
        while (i < d.length() - 1 && d.charAt(i) == '0') {
            i++;
        }
        return d.substring(i).equals("0") ? "" : d.substring(i);
    }

    private long digitsMs() {
        String d = "000000".substring(digits.length()) + digits;
        long h = Long.parseLong(d.substring(0, 2));
        long m = Long.parseLong(d.substring(2, 4));
        long s = Long.parseLong(d.substring(4, 6));
        return (h * 3600L + m * 60L + s) * 1000L;
    }

    /** Quick lengths in minutes, and the chips showing them (highlighted when chosen). */
    private static final int[] QUICK_MIN = {5, 10, 15, 25, 30, 45, 60, 90};
    private final TextView[] quickChips = new TextView[QUICK_MIN.length];
    private NumberPicker wheelH;
    private NumberPicker wheelM;
    private NumberPicker wheelS;

    /** Timer: one-tap lengths, plus hour / minute / second wheels for anything else. */
    private void buildKeypad() {
        // 5 min  10 min  15 min  25 min ... in two even rows
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(a);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            for (int k = 0; k < 4; k++) {
                final int idx = r * 4 + k;
                final int m = QUICK_MIN[idx];
                TextView c = new TextView(a);
                c.setText(m >= 60 && m % 60 == 0 ? a.getString(R.string.hour_n, m / 60)
                        : m > 60 ? a.getString(R.string.fmt_hm, m / 60, m % 60)
                        : a.getString(R.string.min_n, m));
                c.setGravity(Gravity.CENTER);
                c.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        digits = toDigits(m * 60_000L);
                        syncWheels();
                        paintChips();
                    }
                });
                quickChips[idx] = c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(c, lp);
            }
            LinearLayout.LayoutParams rl = full();
            if (r == 0) {
                rl.topMargin = dp(16);
            }
            body.addView(row, rl);
        }

        // or pick any length on the wheels
        LinearLayout wheels = new LinearLayout(a);
        wheels.setGravity(Gravity.CENTER);
        wheels.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        wheels.setPaddingRelative(0, dp(10), 0, dp(4));
        Clay.inset(wheels, surface2, 24);
        wheelH = wheel(wheels, a.getString(R.string.unit_hours), 23);
        wheelM = wheel(wheels, a.getString(R.string.unit_min), 59);
        wheelS = wheel(wheels, a.getString(R.string.unit_sec), 59);
        LinearLayout.LayoutParams wl = full();
        wl.topMargin = dp(12);
        body.addView(wheels, wl);
        syncWheels();
        paintChips();
        NumberPicker.OnValueChangeListener changed = new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker p, int oldV, int newV) {
                long ms = (wheelH.getValue() * 3600L + wheelM.getValue() * 60L
                        + wheelS.getValue()) * 1000L;
                digits = toDigits(ms);
                paintChips();
            }
        };
        wheelH.setOnValueChangedListener(changed);
        wheelM.setOnValueChangedListener(changed);
        wheelS.setOnValueChangedListener(changed);
    }

    private NumberPicker wheel(LinearLayout row, String label, int max) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        NumberPicker np = new NumberPicker(a);
        np.setMinValue(0);
        np.setMaxValue(max);
        String[] shown = new String[max + 1];
        for (int i = 0; i <= max; i++) {
            shown[i] = String.format(Locale.US, "%02d", i);
        }
        np.setDisplayedValues(shown);
        np.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            np.setTextColor(main);
            np.setTextSize(dp(26));
            np.setSelectionDividerHeight(0);
        }
        col.addView(np, new LinearLayout.LayoutParams(dp(76), dp(150)));
        TextView t = new TextView(a);
        t.setText(label);
        Clay.text(t, 800, 12, sub);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return np;
    }

    private void syncWheels() {
        long s = digitsMs() / 1000L;
        wheelH.setValue((int) Math.min(23, s / 3600));
        wheelM.setValue((int) ((s / 60) % 60));
        wheelS.setValue((int) (s % 60));
    }

    private void paintChips() {
        long ms = digitsMs();
        for (int i = 0; i < quickChips.length; i++) {
            TextView c = quickChips[i];
            boolean sel = ms == QUICK_MIN[i] * 60_000L;
            if (sel) {
                Clay.text(c, 900, 14, 0xFFFFFFFF);
                Clay.style(c, accent, -1, 3, false);
            } else {
                Clay.text(c, 800, 14, main);
                Clay.style(c, surface, -1, 3, true);
            }
        }
    }

    // ------------------------------------------------------------------ pomodoro

    private interface IntSetter {
        void set(int v);
    }

    private View stepper(String label, final int value, final int min, final int max,
                         final int step, final String unit, final IntSetter setter) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(0, dp(8), 0, dp(8));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        TextView l = new TextView(a);
        l.setText(label);
        Clay.text(l, 800, 16, main);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView minus = roundIcon(R.drawable.ic_minus, 40);
        final TextView val = new TextView(a);
        Clay.text(val, 900, 17, main);
        val.setGravity(Gravity.CENTER);
        val.setFontFeatureSettings("tnum");
        ImageView plus = roundIcon(R.drawable.ic_plus, 40);
        final int[] cur = {value};
        val.setText(cur[0] + unit);
        View.OnClickListener ch = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int d = v.getTag() == null ? step : -step;
                cur[0] = Math.max(min, Math.min(max, cur[0] + d));
                val.setText(cur[0] + unit);
                setter.set(cur[0]);
            }
        };
        minus.setTag("minus");
        minus.setOnClickListener(ch);
        plus.setOnClickListener(ch);
        row.addView(minus);
        row.addView(val, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus);
        return row;
    }

    private String minUnit() {
        return " " + a.getString(R.string.unit_min);
    }

    private List<TimerItem.Step> pomodoroSteps() {
        List<TimerItem.Step> out = new ArrayList<TimerItem.Step>();
        for (int r = 0; r < pRounds; r++) {
            out.add(new TimerItem.Step(a.getString(R.string.focus_step), pFocus * 60_000L, false));
            boolean last = r == pRounds - 1;
            out.add(new TimerItem.Step(a.getString(last ? R.string.long_break : R.string.short_break),
                    (last ? pLong : pShort) * 60_000L, true));
        }
        return out;
    }

    private void updatePomoSummary() {
        long total = 0;
        for (TimerItem.Step s : pomodoroSteps()) {
            total += s.ms;
        }
        pSummary.setText(a.getString(R.string.pomo_summary, pRounds, Projects.hours(total)));
    }

    private void buildPomodoro() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        LinearLayout.LayoutParams bl = full();
        bl.topMargin = dp(12);
        body.addView(box, bl);
        box.addView(stepper(a.getString(R.string.focus_step), pFocus, 5, 120, 5, minUnit(), new IntSetter() {
            @Override
            public void set(int v) {
                pFocus = v;
                updatePomoSummary();
            }
        }));
        box.addView(stepper(a.getString(R.string.short_break), pShort, 1, 30, 1, minUnit(), new IntSetter() {
            @Override
            public void set(int v) {
                pShort = v;
                updatePomoSummary();
            }
        }));
        box.addView(stepper(a.getString(R.string.long_break), pLong, 5, 60, 5, minUnit(), new IntSetter() {
            @Override
            public void set(int v) {
                pLong = v;
                updatePomoSummary();
            }
        }));
        box.addView(stepper(a.getString(R.string.rounds), pRounds, 1, 8, 1, "", new IntSetter() {
            @Override
            public void set(int v) {
                pRounds = v;
                updatePomoSummary();
            }
        }));
        pSummary = new TextView(a);
        Clay.text(pSummary, 600, 13, sub);
        pSummary.setPaddingRelative(0, dp(8), 0, 0);
        box.addView(pSummary);
        updatePomoSummary();
    }

    // ------------------------------------------------------------------ sequence

    private void updateSeqSummary() {
        long sum = 0;
        for (TimerItem.Step s : seq) {
            sum += s.ms;
        }
        seqSummary.setText(a.getString(R.string.seq_summary, seq.size(), seqRounds,
                Projects.hours(sum * seqRounds)));
    }

    private void renderSeq() {
        seqList.removeAllViews();
        for (int i = 0; i < seq.size(); i++) {
            final int idx = i;
            TimerItem.Step s = seq.get(i);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(16), dp(11), dp(8), dp(11));
            Clay.inset(row, surface2, 18);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editStep(idx);
                }
            });
            TextView n = new TextView(a);
            n.setText((i + 1) + ".  " + s.name);
            Clay.text(n, 800, 15, main);
            row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView d = new TextView(a);
            d.setText((s.rest ? a.getString(R.string.break_label) + " \u00b7 " : "")
                    + MainActivity.formatDuration(s.ms));
            Clay.text(d, 700, 14, s.rest ? sub : accent);
            d.setFontFeatureSettings("tnum");
            row.addView(d);
            ImageView x = new ImageView(a);
            x.setImageResource(R.drawable.ic_close);
            x.setImageTintList(ColorStateList.valueOf(sub));
            x.setPaddingRelative(dp(9), dp(9), dp(9), dp(9));
            x.setContentDescription(a.getString(R.string.remove_step));
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (seq.size() > 1) {
                        seq.remove(idx);
                        renderSeq();
                    }
                }
            });
            row.addView(x, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout.LayoutParams rl = full();
            rl.bottomMargin = dp(8);
            seqList.addView(row, rl);
        }
        updateSeqSummary();
    }

    private void buildSequence() {
        seqList = new LinearLayout(a);
        seqList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ll = full();
        ll.topMargin = dp(16);
        body.addView(seqList, ll);

        TextView add = chip(a.getString(R.string.add_step));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editStep(-1);
            }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = dp(4);
        al.bottomMargin = dp(6);
        body.addView(add, al);

        body.addView(stepper(a.getString(R.string.rounds), seqRounds, 1, 30, 1, "", new IntSetter() {
            @Override
            public void set(int v) {
                seqRounds = v;
                updateSeqSummary();
            }
        }));
        seqSummary = new TextView(a);
        Clay.text(seqSummary, 600, 13, sub);
        body.addView(seqSummary);
        renderSeq();
    }

    private NumberPicker picker(LinearLayout row, String label, int max, int value) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPaddingRelative(dp(10), 0, dp(10), 0);
        TextView t = new TextView(a);
        t.setText(label);
        Clay.text(t, 800, 12, sub);
        NumberPicker np = new NumberPicker(a);
        np.setMinValue(0);
        np.setMaxValue(max);
        np.setValue(value);
        if (Build.VERSION.SDK_INT >= 29) {
            np.setTextColor(main);
            np.setTextSize(dp(22));
        }
        col.addView(t);
        col.addView(np);
        row.addView(col);
        return np;
    }

    private void editStep(final int idx) {
        final TimerItem.Step s = idx >= 0 ? seq.get(idx) : new TimerItem.Step(a.getString(R.string.step), 60_000L, false);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(24), dp(6), dp(24), 0);
        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setText(s.name);
        Clay.text(name, 700, 16, main);
        Clay.inset(name, surface2, 18);
        name.setPaddingRelative(dp(18), dp(13), dp(18), dp(13));
        root.addView(name);
        LinearLayout pr = new LinearLayout(a);
        pr.setGravity(Gravity.CENTER);
        long sec = s.ms / 1000L;
        final NumberPicker mm = picker(pr, a.getString(R.string.unit_min), 180, (int) (sec / 60));
        final NumberPicker ss = picker(pr, a.getString(R.string.unit_sec), 59, (int) (sec % 60));
        root.addView(pr);
        final CheckBox rest = new CheckBox(a);
        rest.setText(R.string.break_checkbox);
        Clay.text(rest, 600, 14, main);
        rest.setChecked(s.rest);
        root.addView(rest);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(idx >= 0 ? R.string.edit_step : R.string.new_step)
                .setView(root)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        long ms = (mm.getValue() * 60L + ss.getValue()) * 1000L;
                        if (ms <= 0) {
                            Toast.makeText(a, R.string.set_time_first, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String n = name.getText().toString().trim();
                        s.name = n.length() == 0 ? a.getString(R.string.step) : n;
                        s.ms = ms;
                        s.rest = rest.isChecked();
                        if (idx < 0) {
                            seq.add(s);
                        }
                        renderSeq();
                    }
                })
                .setNegativeButton(R.string.cancel, null);
        b.show();
    }

    // ------------------------------------------------------------------ submit

    private void submit(boolean start) {
        String name = nameInput.getText().toString().trim();
        if (mode == MODE_TIMER) {
            long ms = digitsMs();
            if (ms <= 0) {
                Toast.makeText(a, R.string.set_time_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (name.length() == 0) {
                name = editing != null ? editing.name
                        : a.getString(R.string.duration_timer, MainActivity.formatDuration(ms));
            }
            cb.onDone(name, ms, new ArrayList<TimerItem.Step>(), 1, project, start);
        } else if (mode == MODE_POMO) {
            PrefsStore.setPomoFocus(a, pFocus);
            PrefsStore.setPomoShort(a, pShort);
            PrefsStore.setPomoLong(a, pLong);
            PrefsStore.setPomoRounds(a, pRounds);
            cb.onDone(name.length() == 0 ? a.getString(R.string.pomodoro) : name, 0, pomodoroSteps(), 1, project,
                    start);
        } else {
            if (seq.isEmpty()) {
                Toast.makeText(a, R.string.add_step_first, Toast.LENGTH_SHORT).show();
                return;
            }
            cb.onDone(name.length() == 0 ? a.getString(R.string.mode_sequence) : name, 0,
                    new ArrayList<TimerItem.Step>(seq), seqRounds, project, start);
        }
        dialog.dismiss();
    }
}
