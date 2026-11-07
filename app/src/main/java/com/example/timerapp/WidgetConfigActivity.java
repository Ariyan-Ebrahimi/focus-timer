package com.example.timerapp;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * A widget's settings, shown when the widget is added and from its settings icon:
 * what its button controls, its look and what it shows, with a live preview.
 */
public class WidgetConfigActivity extends Activity {

    /** Opened from the widget itself (not while adding it). */
    public static final String EXTRA_EDIT = "edit";

    private int widgetId;
    private TimerWidget.Config cfg;
    private int main;
    private int sub;
    private int surface;
    private int surface2;
    private int accent;
    private FrameLayout preview;
    private LinearLayout options;
    private LinearLayout lookRow;
    private TextView statsToggle;
    private LinearLayout quickRow;
    private LinearLayout noTimerRow;
    private View quickLabel;
    private View quickScroll;

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
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        // backing out while adding the widget cancels adding it
        setResult(RESULT_CANCELED, result());
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        cfg = TimerWidget.loadConfig(this, widgetId);

        int bg = ThemeHelper.resolve(this, R.attr.bg);
        main = ThemeHelper.resolve(this, R.attr.textMain);
        sub = ThemeHelper.resolve(this, R.attr.textSub);
        surface = ThemeHelper.resolve(this, R.attr.surface);
        surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        accent = ThemeHelper.resolve(this, R.attr.accent);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(20), dp(44), dp(20), dp(32));
        // the window is edge-to-edge: keep the title and Save clear of the system bars
        final LinearLayout padded = root;
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v,
                    android.view.WindowInsets in) {
                int top;
                int bottom;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = in.getInsets(
                            android.view.WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = in.getSystemWindowInsetTop();
                    bottom = in.getSystemWindowInsetBottom();
                }
                padded.setPaddingRelative(dp(20), top + dp(24), dp(20), bottom + dp(24));
                return in;
            }
        });
        root.setClipChildren(false);
        root.setClipToPadding(false);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.widget_title);
        Clay.text(title, 900, 32, main);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.widget_sub);
        Clay.text(subtitle, 600, 15, sub);
        subtitle.setPaddingRelative(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        // live preview (touches don't reach it)
        preview = new FrameLayout(this) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        preview.setPadding(dp(14), dp(14), dp(14), dp(14));
        Clay.inset(preview, surface2, 30);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(214)));

        root.addView(label(getString(R.string.w_button)));
        options = card();
        root.addView(options);

        root.addView(label(getString(R.string.w_no_timer)));
        noTimerRow = new LinearLayout(this);
        noTimerRow.setPaddingRelative(dp(5), dp(5), dp(5), dp(5));
        Clay.inset(noTimerRow, surface2, -1);
        root.addView(noTimerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        quickLabel = label(getString(R.string.w_quick_len));
        root.addView(quickLabel);
        android.widget.HorizontalScrollView qs = new android.widget.HorizontalScrollView(this);
        quickScroll = qs;
        qs.setHorizontalScrollBarEnabled(false);
        qs.setClipToPadding(false);
        quickRow = new LinearLayout(this);
        quickRow.setPaddingRelative(dp(2), dp(4), dp(2), dp(8));
        quickRow.setClipToPadding(false);
        quickRow.setClipChildren(false);
        qs.addView(quickRow);
        root.addView(qs);

        root.addView(label(getString(R.string.w_look)));
        lookRow = new LinearLayout(this);
        lookRow.setPaddingRelative(dp(5), dp(5), dp(5), dp(5));
        Clay.inset(lookRow, surface2, -1);
        root.addView(lookRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(label(getString(R.string.w_show)));
        LinearLayout showCard = card();
        LinearLayout showRow = new LinearLayout(this);
        showRow.setGravity(Gravity.CENTER_VERTICAL);
        showRow.setPaddingRelative(dp(18), dp(14), dp(14), dp(14));
        TextView showText = new TextView(this);
        showText.setText(R.string.w_show_stats);
        Clay.text(showText, 800, 16, main);
        showRow.addView(showText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statsToggle = new TextView(this);
        statsToggle.setGravity(Gravity.CENTER);
        statsToggle.setPaddingRelative(dp(18), dp(8), dp(18), dp(8));
        showRow.addView(statsToggle);
        showRow.setBackgroundResource(R.drawable.row_bg);
        showRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cfg.stats = !cfg.stats;
                paint();
            }
        });
        showCard.addView(showRow);
        root.addView(showCard);

        TextView save = new TextView(this);
        save.setText(R.string.save);
        save.setGravity(Gravity.CENTER);
        Clay.text(save, 900, 18, 0xFFFFFFFF);
        Clay.style(save, accent, -1, 6, true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        slp.topMargin = dp(28);
        root.addView(save, slp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimerWidget.saveConfig(WidgetConfigActivity.this, widgetId, cfg);
                AppWidgetManager.getInstance(WidgetConfigActivity.this).updateAppWidget(widgetId,
                        TimerWidget.build(WidgetConfigActivity.this, widgetId, cfg));
                setResult(RESULT_OK, result());
                finish();
            }
        });

        setContentView(scroll);
        paint();
    }

    private Intent result() {
        Intent i = new Intent();
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        return i;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        Clay.text(t, 800, 14, sub);
        t.setPaddingRelative(dp(6), dp(24), 0, dp(10));
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPaddingRelative(0, dp(6), 0, dp(6));
        Clay.style(l, surface, 28, 4, false);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** Redraws the preview and every choice to match cfg. */
    private void paint() {
        preview.removeAllViews();
        try {
            View v = TimerWidget.build(this, widgetId, cfg).apply(getApplicationContext(), preview);
            preview.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Exception ignored) {
        }

        // what the button controls
        options.removeAllViews();
        option(TimerWidget.MODE_SMART, 0, getString(R.string.w_smart),
                getString(R.string.w_smart_sub), 0, R.drawable.ic_play);
        option(TimerWidget.MODE_STOPWATCH, 0, getString(R.string.stopwatch),
                getString(R.string.w_sw_sub), 0, R.drawable.ic_tab_sw);
        List<PrefsStore.Project> projects = PrefsStore.loadProjects(this);
        List<TimerItem> timers = PrefsStore.loadTimers(this);
        if (!timers.isEmpty()) {
            options.addView(groupLabel(getString(R.string.tab_timers)));
            for (TimerItem it : timers) {
                PrefsStore.Project p = PrefsStore.findProject(this, it.projectId);
                String s = MainActivity.formatDuration(it.isSequence() ? it.plannedTotal()
                        : it.total);
                if (p != null) {
                    s = p.name + "  \u00b7  " + s;
                }
                option(TimerWidget.MODE_TIMER, it.id, it.name, s,
                        p != null ? Projects.color(this, p.color) : 0, R.drawable.ic_tab_timer);
            }
        }
        if (timers.isEmpty() && projects.isEmpty()) {
            TextView hint = groupLabel(getString(R.string.w_empty_hint));
            hint.setPaddingRelative(dp(20), dp(10), dp(20), dp(12));
            options.addView(hint);
        }
        if (!projects.isEmpty()) {
            options.addView(groupLabel(getString(R.string.tab_projects)));
            for (PrefsStore.Project p : projects) {
                option(TimerWidget.MODE_PROJECT, p.id, p.name, getString(R.string.w_proj_sub),
                        Projects.color(this, p.color), 0);
            }
        }

        // with no timer to start: stopwatch or a timer (whose length is then shown)
        noTimerRow.removeAllViews();
        String[] ntNames = {getString(R.string.stopwatch), getString(R.string.timer)};
        for (int i = 0; i < 2; i++) {
            final boolean useSw = i == 0;
            boolean sel = cfg.noTimerStopwatch == useSw;
            TextView seg = new TextView(this);
            seg.setText(ntNames[i]);
            seg.setGravity(Gravity.CENTER);
            seg.setSingleLine(true);
            Clay.text(seg, 800, 14, sel ? main : sub);
            seg.setPaddingRelative(dp(6), dp(10), dp(6), dp(10));
            if (sel) {
                Clay.style(seg, surface, -1, 2, false);
            }
            seg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cfg.noTimerStopwatch = useSw;
                    paint();
                }
            });
            noTimerRow.addView(seg, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        quickLabel.setVisibility(cfg.noTimerStopwatch ? View.GONE : View.VISIBLE);
        quickScroll.setVisibility(cfg.noTimerStopwatch ? View.GONE : View.VISIBLE);

        // quick focus length
        quickRow.removeAllViews();
        for (final int m : TimerWidget.QUICK_LENGTHS) {
            boolean sel = cfg.quickMin == m;
            TextView chip = new TextView(this);
            chip.setText(getString(R.string.min_n, m));
            chip.setGravity(Gravity.CENTER);
            Clay.text(chip, 800, 14, sel ? 0xFFFFFFFF : main);
            chip.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));
            if (sel) {
                Clay.style(chip, accent, -1, 3, false);
            } else {
                Clay.style(chip, surface, -1, 3, false);
            }
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cfg.quickMin = m;
                    paint();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            quickRow.addView(chip, lp);
        }

        // look
        lookRow.removeAllViews();
        int[] looks = {TimerWidget.LOOK_APP, TimerWidget.LOOK_LIGHT, TimerWidget.LOOK_DARK};
        String[] names = {getString(R.string.w_same_app), getString(R.string.light),
                getString(R.string.dark)};
        for (int i = 0; i < looks.length; i++) {
            final int look = looks[i];
            boolean sel = cfg.look == look;
            TextView seg = new TextView(this);
            seg.setText(names[i]);
            seg.setGravity(Gravity.CENTER);
            seg.setSingleLine(true);
            Clay.text(seg, 800, 14, sel ? main : sub);
            seg.setPaddingRelative(dp(6), dp(10), dp(6), dp(10));
            if (sel) {
                Clay.style(seg, surface, -1, 2, false);
            }
            seg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cfg.look = look;
                    paint();
                }
            });
            lookRow.addView(seg, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        // stats on/off
        statsToggle.setText(cfg.stats ? R.string.on : R.string.off);
        if (cfg.stats) {
            Clay.style(statsToggle, accent, -1, 3, false);
            statsToggle.setTextColor(0xFFFFFFFF);
        } else {
            Clay.inset(statsToggle, surface2, -1);
            statsToggle.setTextColor(sub);
        }
        Clay.text(statsToggle, 800, 14, cfg.stats ? 0xFFFFFFFF : sub);
    }

    private TextView groupLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        Clay.text(t, 800, 12, sub);
        t.setPaddingRelative(dp(20), dp(12), dp(20), dp(2));
        return t;
    }

    /** One choice for the button. dot != 0 shows a colour dot, else icon (if any). */
    private void option(final int mode, final long target, String name, String detail, int dot,
                        int icon) {
        boolean sel = cfg.mode == mode && (mode == TimerWidget.MODE_SMART
                || mode == TimerWidget.MODE_STOPWATCH || cfg.target == target);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(18), dp(12), dp(16), dp(12));
        row.setBackgroundResource(R.drawable.row_bg);

        View lead;
        if (dot != 0) {
            View d = new View(this);
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(dot);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(12), dp(12));
            lp.setMarginStart(dp(5));
            lp.setMarginEnd(dp(5));
            d.setLayoutParams(lp);
            lead = d;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageResource(icon);
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(sub));
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
            lead = iv;
        }
        row.addView(lead);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPaddingRelative(dp(14), 0, dp(10), 0);
        TextView n = new TextView(this);
        n.setText(name);
        Clay.text(n, 800, 16, main);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        texts.addView(n);
        TextView s = new TextView(this);
        s.setText(detail);
        Clay.text(s, 600, 13, sub);
        s.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // radio mark
        ImageView mark = new ImageView(this);
        mark.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (sel) {
            mark.setImageResource(R.drawable.ic_check);
            mark.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            Clay.style(mark, accent, -1, 2, false);
        } else {
            Clay.inset(mark, surface2, -1);
        }
        row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cfg.mode = mode;
                cfg.target = target;
                paint();
            }
        });
        options.addView(row);
    }
}
