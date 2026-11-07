package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One project: study stats, a 7-day chart, its timers and recent sessions. */
public class ProjectActivity extends Activity {

    private long projectId;
    private PrefsStore.Project project;
    private int main;
    private int sub;
    private int surface;
    private int surface2;
    private int accent;
    private int pcolor;
    private LinearLayout content;
    private LinearLayout timerList;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Signature of the timers' states; the rows are only rebuilt when it changes. */
    private String rowsKey = "";
    private final java.util.Map<Long, TextView> rowTimes = new java.util.HashMap<Long, TextView>();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderTimers();
            handler.postDelayed(this, 500);
        }
    };

    public static Intent intent(Context c, long projectId) {
        return new Intent(c, ProjectActivity.class).putExtra("project", projectId);
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
        projectId = getIntent().getLongExtra("project", 0);
        main = ThemeHelper.resolve(this, R.attr.textMain);
        sub = ThemeHelper.resolve(this, R.attr.textSub);
        surface = ThemeHelper.resolve(this, R.attr.surface);
        surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        accent = ThemeHelper.resolve(this, R.attr.accent);
        int bg = ThemeHelper.resolve(this, R.attr.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!Clay.isDark(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFitsSystemWindows(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPaddingRelative(dp(18), dp(16), dp(18), dp(32));
        content.setClipToPadding(false);
        content.setClipChildren(false);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onStart() {
        super.onStart();
        project = PrefsStore.findProject(this, projectId);
        if (project == null) {
            finish();
            return;
        }
        pcolor = Projects.color(this, project.color);
        render();
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }

    // ------------------------------------------------------------------ building blocks

    private LinearLayout.LayoutParams full(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private TextView text(String s, int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        Clay.text(t, weight, sp, color);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private LinearLayout card(int topMarginDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPaddingRelative(dp(20), dp(16), dp(20), dp(18));
        c.setClipChildren(false);
        c.setClipToPadding(false);
        Clay.style(c, surface, 28, 5, false);
        content.addView(c, full(topMarginDp));
        return c;
    }

    private View dot(int color, int sizeDp) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    private ImageView roundIcon(int icon, int fill, int tint, int sizeDp) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(tint));
        int p = Math.round(sizeDp * 0.28f);
        b.setPaddingRelative(dp(p), dp(p), dp(p), dp(p));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        Clay.style(b, fill, -1, 4, true);
        return b;
    }

    private View label(String s) {
        TextView t = text(s, 800, 14, sub);
        t.setPaddingRelative(dp(8), dp(26), 0, dp(10));
        return t;
    }

    // ------------------------------------------------------------------ screen

    private void render() {
        content.removeAllViews();
        List<PrefsStore.Hist> history = PrefsStore.loadHistory(this);
        Projects.Stats st = Projects.stats(history, project.id);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipToPadding(false);
        head.setClipChildren(false);
        head.setPaddingRelative(0, 0, 0, dp(6));
        ImageView back = roundIcon(R.drawable.ic_back, surface, main, 46);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);
        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        ImageView more = roundIcon(R.drawable.ic_more, surface, main, 46);
        more.setContentDescription(getString(R.string.project_options));
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu();
            }
        });
        head.addView(more);
        content.addView(head);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        View d = dot(pcolor, 14);
        ((LinearLayout.LayoutParams) d.getLayoutParams()).rightMargin = dp(12);
        titleRow.addView(d);
        TextView title = text(project.name, 900, 30, main);
        titleRow.addView(title);
        content.addView(titleRow, full(18));
        TextView summary = text(getString(R.string.project_summary,
                getResources().getQuantityString(R.plurals.sessions, st.sessions, st.sessions),
                Projects.hours(st.total)), 600, 14, sub);
        summary.setPaddingRelative(dp(26), dp(2), 0, 0);
        content.addView(summary);

        // stat tiles
        LinearLayout tiles = new LinearLayout(this);
        tiles.setClipChildren(false);
        tiles.setClipToPadding(false);
        String[][] vals = {{getString(R.string.today), Projects.hours(st.today)},
                {getString(R.string.this_week), Projects.hours(st.week)},
                {getString(R.string.total), Projects.hours(st.total)}};
        for (int i = 0; i < 3; i++) {
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPaddingRelative(dp(16), dp(14), dp(12), dp(16));
            Clay.style(t, surface, 24, 5, false);
            t.addView(text(vals[i][0], 700, 13, sub));
            TextView v = text(vals[i][1], 900, 21, main);
            v.setFontFeatureSettings("tnum");
            v.setSingleLine(true);
            v.setPaddingRelative(0, dp(8), 0, 0);
            t.addView(v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(i == 0 ? 0 : dp(5));
            lp.setMarginEnd(i == 2 ? 0 : dp(5));
            tiles.addView(t, lp);
        }
        content.addView(tiles, full(22));

        // weekly goal
        if (project.goalMin > 0) {
            LinearLayout g = card(16);
            LinearLayout gr = new LinearLayout(this);
            gr.addView(text(getString(R.string.weekly_goal), 800, 15, main),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            long goalMs = project.goalMin * 60_000L;
            int pct = (int) Math.min(100, st.week * 100 / Math.max(1, goalMs));
            gr.addView(text(pct + "%", 900, 15, main));
            g.addView(gr);
            g.addView(goalBar(st.week, goalMs), full(10));
            TextView gt = text(getString(st.week >= goalMs ? R.string.goal_reached
                    : R.string.goal_progress, Projects.hours(st.week), Projects.hours(goalMs)),
                    600, 13, sub);
            gt.setPaddingRelative(0, dp(8), 0, 0);
            g.addView(gt);
        }

        // 7-day chart
        LinearLayout chartCard = card(16);
        chartCard.addView(text(getString(R.string.last_7_days), 800, 15, main));
        BarChartView chart = new BarChartView(this);
        String[] days = new String[7];
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Projects.startOfToday());
        cal.add(Calendar.DAY_OF_MONTH, -6);
        SimpleDateFormat dn = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            days[i] = i == 6 ? getString(R.string.today) : dn.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        chart.setData(st.days, days, pcolor);
        chartCard.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));

        // timers in this project
        content.addView(label(getString(R.string.tab_timers)));
        timerList = new LinearLayout(this);
        rowsKey = "";
        timerList.setOrientation(LinearLayout.VERTICAL);
        timerList.setClipChildren(false);
        timerList.setClipToPadding(false);
        content.addView(timerList, full(0));
        renderTimers();
        TextView add = text(getString(R.string.new_timer_plus), 800, 14, main);
        add.setPaddingRelative(dp(18), dp(10), dp(18), dp(11));
        Clay.style(add, surface, -1, 4, true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTimer();
            }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = dp(4);
        content.addView(add, al);

        // recent sessions (the table view behind the chart)
        content.addView(label(getString(R.string.recent_sessions)));
        LinearLayout list = card(0);
        list.setPaddingRelative(dp(4), dp(6), dp(4), dp(6));
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        int shown = 0;
        for (PrefsStore.Hist h : history) {
            if (h.project != project.id) {
                continue;
            }
            if (shown++ >= 12) {
                break;
            }
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(16), dp(11), dp(16), dp(11));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(h.name, 800, 15, main);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(n);
            mid.addView(text(df.format(new Date(h.ts)), 600, 12, sub));
            if (!h.note.isEmpty()) {
                TextView note = text(android.text.BidiFormatter.getInstance().unicodeWrap(h.note),
                        600, 13, sub);
                note.setTypeface(note.getTypeface(), android.graphics.Typeface.ITALIC);
                note.setMaxLines(2);
                note.setEllipsize(android.text.TextUtils.TruncateAt.END);
                note.setPaddingRelative(0, dp(3), 0, 0);
                mid.addView(note);
            }
            row.addView(mid, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView dur = text(Projects.hours(h.millis), 800, 15, main);
            dur.setFontFeatureSettings("tnum");
            row.addView(dur);
            list.addView(row);
        }
        if (shown == 0) {
            TextView none = text(getString(R.string.no_sessions_project), 600, 14, sub);
            none.setPaddingRelative(dp(16), dp(14), dp(16), dp(14));
            list.addView(none);
        }
    }

    private View goalBar(long done, long goal) {
        LinearLayout track = new LinearLayout(this);
        track.setPaddingRelative(dp(3), dp(3), dp(3), dp(3));
        Clay.inset(track, surface2, -1);
        View fill = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(pcolor);
        g.setCornerRadius(dp(6));
        fill.setBackground(g);
        float f = Math.max(0.02f, Math.min(1f, done / (float) Math.max(1, goal)));
        track.setWeightSum(1f);
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(10), f));
        track.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        return track;
    }

    private void renderTimers() {
        if (timerList == null) {
            return;
        }
        List<TimerItem> all = PrefsStore.loadTimers(this);
        StringBuilder key = new StringBuilder();
        for (TimerItem it : all) {
            if (it.projectId == project.id) {
                key.append(it.id).append(it.state).append(it.name).append(it.stepIdx)
                        .append(it.round).append(',');
            }
        }
        if (key.toString().equals(rowsKey) && timerList.getChildCount() > 0) {
            // same rows: just tick the times, no rebuild (keeps scrolling smooth)
            for (TimerItem it : all) {
                TextView t = rowTimes.get(it.id);
                if (t != null) {
                    long left = it.state == TimerItem.RUNNING
                            ? Math.max(0L, it.deadline - System.currentTimeMillis()) : it.remaining;
                    String txt = MainActivity.formatDuration(left);
                    if (!txt.contentEquals(t.getText())) {
                        t.setText(txt);
                    }
                }
            }
            return;
        }
        rowsKey = key.toString();
        rowTimes.clear();
        timerList.removeAllViews();
        int count = 0;
        for (final TimerItem it : all) {
            if (it.projectId != project.id) {
                continue;
            }
            count++;
            boolean running = it.state == TimerItem.RUNNING;
            long left = running ? Math.max(0L, it.deadline - System.currentTimeMillis())
                    : it.remaining;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(20), dp(12), dp(12), dp(12));
            row.setClipChildren(false);
            row.setClipToPadding(false);
            Clay.style(row, surface, 24, 4, false);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(text(it.name, 800, 15, main));
            String state = running ? (it.isSequence() ? it.currentStep().name + " \u00b7 " : "")
                    + getString(R.string.running) : it.state == TimerItem.PAUSED
                    ? getString(R.string.st_paused) : it.state == TimerItem.FINISHED
                    ? getString(R.string.times_up) : getString(R.string.st_ready);
            mid.addView(text(state, 600, 12, running ? accent : sub));
            row.addView(mid, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = text(MainActivity.formatDuration(left), 900, 22, main);
            t.setFontFeatureSettings("tnum");
            rowTimes.put(it.id, t);
            t.setPaddingRelative(0, 0, dp(14), 0);
            row.addView(t);
            ImageView play = roundIcon(running ? R.drawable.ic_pause : R.drawable.ic_play,
                    accent, 0xFFFFFFFF, 44);
            play.setContentDescription(getString(running ? R.string.pause : R.string.start));
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TimerOps.apply(ProjectActivity.this, it.id, new TimerOps.Op() {
                        @Override
                        public void run(Context c, TimerItem x) {
                            if (x.state == TimerItem.RUNNING) {
                                TimerOps.pause(c, x);
                            } else {
                                NotificationHelper.cancelAlarm(c, x.id);
                                TimerOps.start(c, x);
                            }
                        }
                    });
                    renderTimers();
                }
            });
            row.addView(play);
            LinearLayout.LayoutParams lp = full(0);
            lp.bottomMargin = dp(12);
            timerList.addView(row, lp);
        }
        if (count == 0) {
            TextView none = text(getString(R.string.no_timers_project), 600, 14, sub);
            none.setPaddingRelative(dp(8), 0, 0, dp(8));
            timerList.addView(none);
        }
    }

    private void newTimer() {
        TimerSheet.show(this, null, project.id, new TimerSheet.Callback() {
            @Override
            public void onDone(String name, long ms, List<TimerItem.Step> steps, int rounds,
                               long proj, boolean start) {
                List<TimerItem> all = PrefsStore.loadTimers(ProjectActivity.this);
                if (all.size() >= MainActivity.MAX_TIMERS) {
                    android.widget.Toast.makeText(ProjectActivity.this,
                            getString(R.string.max_timers, MainActivity.MAX_TIMERS),
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                TimerItem it = new TimerItem(System.currentTimeMillis(), name, ms);
                it.steps.addAll(steps);
                it.rounds = Math.max(1, rounds);
                it.projectId = proj;
                it.rewind();
                if (start) {
                    TimerOps.start(ProjectActivity.this, it);
                }
                all.add(it);
                PrefsStore.saveTimers(ProjectActivity.this, all);
                TimerOps.refreshSurfaces(ProjectActivity.this);
                renderTimers();
            }
        });
    }

    private void menu() {
        new AlertDialog.Builder(this)
                .setTitle(project.name)
                .setItems(new String[] {getString(R.string.edit_project),
                                getString(R.string.delete_project)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (which == 0) {
                                    ProjectDialog.show(ProjectActivity.this, project,
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    project = PrefsStore.findProject(
                                                            ProjectActivity.this, projectId);
                                                    pcolor = Projects.color(
                                                            ProjectActivity.this, project.color);
                                                    render();
                                                }
                                            });
                                } else {
                                    confirmDelete();
                                }
                            }
                        })
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_project_q,
                        android.text.BidiFormatter.getInstance().unicodeWrap(project.name)))
                .setMessage(R.string.delete_project_msg)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        deleteProject(ProjectActivity.this, projectId);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void deleteProject(Context c, long id) {
        List<PrefsStore.Project> list = PrefsStore.loadProjects(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == id) {
                list.remove(i);
            }
        }
        PrefsStore.saveProjects(c, list);
        List<TimerItem> timers = PrefsStore.loadTimers(c);
        for (TimerItem it : timers) {
            if (it.projectId == id) {
                it.projectId = 0;
            }
        }
        PrefsStore.saveTimers(c, timers);
        PrefsStore.unlinkHistory(c, id);
    }
}
