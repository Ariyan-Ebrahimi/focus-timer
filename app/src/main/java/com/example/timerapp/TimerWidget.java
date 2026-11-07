package com.example.timerapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home-screen widget: today's study time and streak, what's running (the system ticks
 * the clock), and one button. Each widget has its own settings: what the button
 * controls (smart, the stopwatch, one timer or one project), its look, and what it shows.
 */
public class TimerWidget extends AppWidgetProvider {

    public static final int MODE_SMART = 0;
    public static final int MODE_TIMER = 1;
    public static final int MODE_PROJECT = 2;
    public static final int MODE_STOPWATCH = 3;

    public static final int LOOK_APP = 0;
    public static final int LOOK_LIGHT = 1;
    public static final int LOOK_DARK = 2;

    private static final int PAUSE_TIMER = 1;
    private static final int PAUSE_SW = 2;
    private static final int RESUME = 3;
    private static final int START = 4;
    private static final int START_SW = 5;

    /** One widget's settings. */
    public static final class Config {
        public int mode = MODE_SMART;
        /** Timer id (MODE_TIMER) or project id (MODE_PROJECT). */
        public long target;
        public int look = LOOK_APP;
        public boolean stats = true;
        /** Length of a timer the widget has to create (no timer to start yet). */
        public int quickMin = 25;
        /** With no timer to start: count up with the stopwatch (true) or make a timer. */
        public boolean noTimerStopwatch = true;
    }

    /** Lengths offered for quick focus, in minutes. */
    public static final int[] QUICK_LENGTHS = {5, 10, 15, 25, 30, 45, 60};

    /** What the big button does right now. */
    private static final class Plan {
        int kind;
        TimerItem timer;
        PrefsStore.Project project;
        /** The widget is tied to this timer, so show it even when it isn't running. */
        boolean pinnedTimer;
        /** START_SW: the project the stopwatch counts toward (null: none). */
        PrefsStore.Project swProject;
        /** START_SW: continue the paused stopwatch (else start it fresh). */
        boolean swResume;
    }

    // ------------------------------------------------------------ settings

    private static String key(int widgetId) {
        return "widget_" + widgetId;
    }

    public static Config loadConfig(Context c, int widgetId) {
        Config cfg = new Config();
        String raw = PrefsStore.raw(c).getString(key(widgetId), "");
        String[] p = raw == null ? new String[0] : raw.split("\\|");
        if (p.length >= 4) {
            try {
                cfg.mode = Integer.parseInt(p[0]);
                cfg.target = Long.parseLong(p[1]);
                cfg.look = Integer.parseInt(p[2]);
                cfg.stats = "1".equals(p[3]);
                if (p.length >= 5) {
                    cfg.quickMin = Math.max(1, Integer.parseInt(p[4]));
                }
                if (p.length >= 6) {
                    cfg.noTimerStopwatch = "1".equals(p[5]);
                }
            } catch (NumberFormatException ignored) {
                return new Config();
            }
        }
        return cfg;
    }

    public static void saveConfig(Context c, int widgetId, Config cfg) {
        PrefsStore.raw(c).edit().putString(key(widgetId), cfg.mode + "|" + cfg.target + "|"
                + cfg.look + "|" + (cfg.stats ? "1" : "0") + "|" + cfg.quickMin
                + "|" + (cfg.noTimerStopwatch ? "1" : "0")).apply();
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) {
            PrefsStore.raw(c).edit().remove(key(id)).apply();
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        refresh(c);
    }

    public static int[] ids(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        return mgr == null ? new int[0]
                : mgr.getAppWidgetIds(new ComponentName(c, TimerWidget.class));
    }

    // ------------------------------------------------------------ what the button does

    /**
     * The project studied most in the last 7 days; else the one used last; else the
     * first one. Null when there are no projects.
     */
    static PrefsStore.Project favourite(List<PrefsStore.Hist> history,
                                        List<PrefsStore.Project> projects) {
        if (projects.isEmpty()) {
            return null;
        }
        long since = Projects.startOfToday() - 6 * Projects.DAY;
        Map<Long, Long> sum = new HashMap<Long, Long>();
        long lastUsed = 0;
        for (PrefsStore.Hist h : history) {
            if (h.project == 0) {
                continue;
            }
            if (lastUsed == 0) {
                lastUsed = h.project;
            }
            if (h.ts >= since) {
                Long v = sum.get(h.project);
                sum.put(h.project, (v == null ? 0L : v) + h.millis);
            }
        }
        PrefsStore.Project best = null;
        long bestMs = 0;
        PrefsStore.Project last = null;
        for (PrefsStore.Project p : projects) {
            Long v = sum.get(p.id);
            if (v != null && v > bestMs) {
                best = p;
                bestMs = v;
            }
            if (p.id == lastUsed) {
                last = p;
            }
        }
        return best != null ? best : last != null ? last : projects.get(0);
    }

    /**
     * A timer the widget made earlier ("Quick focus", or named after its project), not
     * mid-run: it takes the widget's length.
     */
    private static boolean isQuick(Context c, TimerItem it, PrefsStore.Project project) {
        if (it.isSequence() || it.state == TimerItem.PAUSED || it.state == TimerItem.RUNNING) {
            return false;
        }
        return c.getString(R.string.quick_focus).equals(it.name) || (project != null
                && it.projectId == project.id && project.name.equals(it.name));
    }

    private static String quickLabel(Context c, String name, Config cfg) {
        return name + " \u00b7 " + c.getString(R.string.min_n, cfg.quickMin);
    }

    private static boolean running(TimerItem it, long now) {
        return it.state == TimerItem.RUNNING && it.deadline > now;
    }

    private static PrefsStore.Project findProject(List<PrefsStore.Project> projects, long id) {
        for (PrefsStore.Project p : projects) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    /** Pause what's running, resume what's paused, else start; limited to `project` if set. */
    private static Plan smart(Config cfg, List<TimerItem> timers, PrefsStore.Sw sw,
                              PrefsStore.Project project, boolean withStopwatch,
                              List<PrefsStore.Hist> history,
                              List<PrefsStore.Project> projects) {
        Plan p = new Plan();
        long now = System.currentTimeMillis();
        for (TimerItem it : timers) {
            if (running(it, now) && (project == null || it.projectId == project.id)) {
                p.kind = PAUSE_TIMER;
                p.timer = it;
                return p;
            }
        }
        // the stopwatch: any run (smart), or one counting toward this project
        if (sw.running && (withStopwatch || sw.project == project.id)) {
            p.kind = PAUSE_SW;
            return p;
        }
        for (TimerItem it : timers) {
            if (it.state == TimerItem.PAUSED && (project == null || it.projectId == project.id)) {
                p.kind = RESUME;
                p.timer = it;
                return p;
            }
        }
        p.kind = START;
        p.project = project != null ? project : favourite(history, projects);
        for (TimerItem it : timers) {
            // the project's first timer, or with no projects, the first timer
            if (p.project == null || it.projectId == p.project.id) {
                p.timer = it;
                break;
            }
        }
        if (p.timer == null && cfg.noTimerStopwatch) {
            // nothing to start: count up instead, toward the project
            p.kind = START_SW;
            p.swProject = p.project;
            p.swResume = !sw.running && sw.accum > 0
                    && sw.project == (p.project != null ? p.project.id : 0);
        }
        return p;
    }

    private static Plan plan(Config cfg, List<TimerItem> timers, PrefsStore.Sw sw,
                             List<PrefsStore.Hist> history, List<PrefsStore.Project> projects) {
        long now = System.currentTimeMillis();
        if (cfg.mode == MODE_STOPWATCH) {
            Plan p = new Plan();
            p.kind = sw.running ? PAUSE_SW : START_SW;
            p.swResume = sw.accum > 0;
            p.swProject = findProject(projects, sw.project);
            return p;
        }
        if (cfg.mode == MODE_TIMER) {
            for (TimerItem it : timers) {
                if (it.id == cfg.target) {
                    Plan p = new Plan();
                    p.timer = it;
                    p.pinnedTimer = true;
                    p.kind = running(it, now) ? PAUSE_TIMER
                            : it.state == TimerItem.PAUSED ? RESUME : START;
                    return p;
                }
            }
            // the chosen timer was deleted: behave like "smart"
        }
        if (cfg.mode == MODE_PROJECT) {
            PrefsStore.Project proj = findProject(projects, cfg.target);
            if (proj != null) {
                return smart(cfg, timers, sw, proj, false, history, projects);
            }
        }
        return smart(cfg, timers, sw, null, true, history, projects);
    }

    // ------------------------------------------------------------ drawing

    private static String timerTitle(TimerItem it) {
        return it.isSequence() ? it.name + " \u00b7 " + it.currentStep().name : it.name;
    }

    public static void refresh(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            for (int id : ids(c)) {
                mgr.updateAppWidget(id, build(c, id, loadConfig(c, id)));
            }
        } catch (Exception ignored) {
        }
    }

    /** The views for widget id with settings cfg (also used for the settings preview). */
    static RemoteViews build(Context c, int widgetId, Config cfg) {
        boolean dark = cfg.look == LOOK_DARK || (cfg.look == LOOK_APP
                && PrefsStore.getMode(c) == ThemeHelper.MODE_DARK);
        Context t = new ContextThemeWrapper(c, ThemeHelper.themeRes(
                dark ? ThemeHelper.MODE_DARK : ThemeHelper.MODE_LIGHT, PrefsStore.getAcc(c)));
        int main = ThemeHelper.resolve(t, R.attr.textMain);
        int sub = ThemeHelper.resolve(t, R.attr.textSub);
        int accent = Clay.accentColor(c);

        List<TimerItem> timers = PrefsStore.loadTimers(c);
        PrefsStore.Sw sw = PrefsStore.loadSw(c);
        List<PrefsStore.Hist> history = PrefsStore.loadHistory(c);
        List<PrefsStore.Project> projects = PrefsStore.loadProjects(c);
        Plan plan = plan(cfg, timers, sw, history, projects);
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        PrefsStore.Project fixedProject = cfg.mode == MODE_PROJECT
                ? findProject(projects, cfg.target) : null;

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_timer);
        rv.setInt(R.id.widgetRoot, "setBackgroundResource",
                dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg);
        rv.setTextColor(R.id.wTodayLabel, sub);
        rv.setTextColor(R.id.wStreak, sub);
        rv.setTextColor(R.id.wToday, main);
        rv.setTextColor(R.id.wRunName, sub);
        rv.setTextColor(R.id.wChrono, main);
        rv.setTextColor(R.id.wIdle, sub);
        rv.setInt(R.id.wGear, "setColorFilter", sub);

        // header: today's time (for the chosen project, if any) and the streak
        if (cfg.stats) {
            long today;
            if (fixedProject != null) {
                today = Projects.stats(history, fixedProject.id).today;
                rv.setTextViewText(R.id.wTodayLabel,
                        c.getString(R.string.w_today_in, fixedProject.name));
            } else {
                today = Projects.stats(history, -1).today;
                rv.setTextViewText(R.id.wTodayLabel, c.getString(R.string.today));
            }
            rv.setTextViewText(R.id.wToday, Projects.hours(today));
            int streak = Projects.streak(history);
            rv.setViewVisibility(R.id.wStreak, streak > 0 ? View.VISIBLE : View.GONE);
            if (streak > 0) {
                rv.setTextViewText(R.id.wStreak, c.getString(R.string.streak_n,
                        c.getResources().getQuantityString(R.plurals.days, streak, streak)));
            }
            rv.setViewVisibility(R.id.wToday, View.VISIBLE);
        } else {
            rv.setTextViewText(R.id.wTodayLabel, fixedProject != null ? fixedProject.name
                    : c.getString(R.string.app_name));
            rv.setViewVisibility(R.id.wStreak, View.GONE);
            rv.setViewVisibility(R.id.wToday, View.GONE);
        }

        // what's running (or paused, or the widget's own timer/stopwatch)
        boolean showRow = true;
        boolean showBar = false;
        int dot = accent;
        if (plan.timer != null && (plan.kind == PAUSE_TIMER || plan.kind == RESUME
                || plan.pinnedTimer)) {
            TimerItem it = plan.timer;
            boolean isRunning = plan.kind == PAUSE_TIMER;
            long left = isRunning ? Math.max(0L, it.deadline - now)
                    : it.remaining > 0 ? it.remaining : it.total;
            rv.setTextViewText(R.id.wRunName, timerTitle(it));
            rv.setChronometer(R.id.wChrono, elapsed + left, null, isRunning);
            rv.setChronometerCountDown(R.id.wChrono, true);
            long span = it.progressSpan();
            if (span > 0 && (plan.kind == PAUSE_TIMER || plan.kind == RESUME)) {
                showBar = true;
                rv.setProgressBar(R.id.wBar, 1000,
                        (int) Math.max(0, Math.min(1000, 1000 - 1000 * left / span)), false);
            }
            PrefsStore.Project p = findProject(projects, it.projectId);
            if (p != null) {
                dot = Projects.color(c, p.color);
            }
        } else if (plan.kind == PAUSE_SW || (plan.kind == START_SW && plan.swResume)) {
            PrefsStore.Project p = PrefsStore.findProject(c, sw.project);
            // a project widget already names the project in its header
            rv.setTextViewText(R.id.wRunName, p != null && fixedProject == null
                    ? c.getString(R.string.stopwatch) + " \u00b7 " + p.name
                    : c.getString(R.string.stopwatch));
            rv.setChronometer(R.id.wChrono, elapsed - sw.current(now), null, sw.running);
            rv.setChronometerCountDown(R.id.wChrono, false);
            if (p != null) {
                dot = Projects.color(c, p.color);
            }
        } else {
            showRow = false;
        }
        rv.setViewVisibility(R.id.wRunRow, showRow ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wBar, showBar ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(R.id.wIdle, showRow ? View.GONE : View.VISIBLE);
        rv.setInt(R.id.wRunDot, "setColorFilter", dot);

        // the button
        String label;
        if (plan.kind == PAUSE_TIMER || plan.kind == PAUSE_SW) {
            label = c.getString(R.string.pause);
        } else if (plan.kind == START_SW) {
            label = c.getString(sw.accum > 0 ? R.string.resume : R.string.start);
        } else if (plan.kind == RESUME) {
            label = c.getString(R.string.resume);
        } else {
            label = c.getString(R.string.start);
        }
        rv.setViewVisibility(R.id.wFinish, plan.kind == START_SW && plan.swResume
                ? View.VISIBLE : View.GONE);
        if (!showRow) {
            // nothing running: say what the button will start, and for how long
            String what;
            if (plan.kind == START_SW) {
                what = plan.swProject != null ? c.getString(R.string.stopwatch) + " \u00b7 "
                        + plan.swProject.name : c.getString(R.string.stopwatch);
            } else if (plan.timer == null) {
                what = quickLabel(c, plan.project != null ? plan.project.name
                        : c.getString(R.string.quick_focus), cfg);
            } else if (isQuick(c, plan.timer, plan.project)) {
                what = quickLabel(c, plan.timer.name, cfg);
            } else {
                what = plan.timer.name + " \u00b7 " + MainActivity.formatDuration(
                        plan.timer.isSequence() ? plan.timer.plannedTotal() : plan.timer.total);
            }
            rv.setTextViewText(R.id.wIdle, what);
        }
        rv.setTextViewText(R.id.wBtnText, label);
        boolean pausing = plan.kind == PAUSE_TIMER || plan.kind == PAUSE_SW;
        rv.setImageViewResource(R.id.wBtnIcon, pausing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (Build.VERSION.SDK_INT >= 31) {
            // starting a project wears the project's colour
            int btn = plan.kind == START && plan.project != null && !plan.pinnedTimer
                    ? Projects.color(c, plan.project.color)
                    : plan.kind == START_SW && plan.swProject != null
                    ? Projects.color(c, plan.swProject.color) : accent;
            rv.setColorStateList(R.id.wBtn, "setBackgroundTintList",
                    ColorStateList.valueOf(btn));
            rv.setColorStateList(R.id.wBar, "setProgressTintList",
                    ColorStateList.valueOf(dot));
        }

        Intent act = new Intent(c, LiveActionReceiver.class);
        act.setAction(LiveActionReceiver.WIDGET_MAIN);
        act.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        act.setData(Uri.parse("focus://widget/" + widgetId));
        rv.setOnClickPendingIntent(R.id.wBtn, PendingIntent.getBroadcast(c, 0, act,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent fin = new Intent(c, LiveActionReceiver.class);
        fin.setAction(LiveActionReceiver.WIDGET_FINISH);
        rv.setOnClickPendingIntent(R.id.wFinish, PendingIntent.getBroadcast(c, 0, fin,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        rv.setTextColor(R.id.wFinish, main);
        rv.setOnClickPendingIntent(R.id.widgetRoot, open(c, plan.kind == PAUSE_SW
                || plan.kind == START_SW ? 2 : 0, 0x51000000 | widgetId));
        rv.setOnClickPendingIntent(R.id.wToday, open(c, 3, 0x52000000 | widgetId));
        Intent set = new Intent(c, WidgetConfigActivity.class);
        set.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        set.putExtra(WidgetConfigActivity.EXTRA_EDIT, true);
        set.setData(Uri.parse("focus://widget-settings/" + widgetId));
        set.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        rv.setOnClickPendingIntent(R.id.wGear, PendingIntent.getActivity(c, 0, set,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return rv;
    }

    private static PendingIntent open(Context c, int tab, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("tab", tab);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------ the button

    /** The widget's Finish button: saves the paused stopwatch run as a session. */
    static void finishStopwatch(Context c) {
        PrefsStore.Sw sw = PrefsStore.loadSw(c);
        long now = System.currentTimeMillis();
        long t = sw.current(now);
        PrefsStore.Project p = PrefsStore.findProject(c, sw.project);
        if (PrefsStore.logStopwatch(c, sw, now, c.getString(R.string.stopwatch))) {
            android.widget.Toast.makeText(c, p != null
                    ? c.getString(R.string.logged_to, Projects.hours(t), p.name)
                    : c.getString(R.string.logged, Projects.hours(t)),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
        PrefsStore.saveSw(c, sw);
        TimerOps.refreshSurfaces(c);
    }

    /** Widget widgetId's button: what it does depends on that widget's settings. */
    static void mainAction(Context c, int widgetId) {
        Config cfg = loadConfig(c, widgetId);
        List<TimerItem> timers = PrefsStore.loadTimers(c);
        PrefsStore.Sw sw = PrefsStore.loadSw(c);
        List<PrefsStore.Project> projects = PrefsStore.loadProjects(c);
        Plan plan = plan(cfg, timers, sw, PrefsStore.loadHistory(c), projects);
        long now = System.currentTimeMillis();
        if (plan.kind == PAUSE_SW) {
            sw.accum = sw.current(now);
            sw.running = false;
            sw.pauses++;
            PrefsStore.saveSw(c, sw);
            TimerOps.refreshSurfaces(c);
            return;
        }
        if (plan.kind == START_SW) {
            if (!plan.swResume && cfg.mode != MODE_STOPWATCH) {
                // starting this project's stopwatch: save any other run first
                if (sw.running || sw.accum > 0) {
                    PrefsStore.logStopwatch(c, sw, now, c.getString(R.string.stopwatch));
                }
                sw.laps.clear();
                sw.accum = 0;
                sw.project = plan.swProject != null ? plan.swProject.id : 0;
            }
            sw.base = now;
            sw.running = true;
            PrefsStore.saveSw(c, sw);
            TimerOps.refreshSurfaces(c);
            return;
        }
        if (plan.timer != null) {
            final int kind = plan.kind;
            final long quickMs = isQuick(c, plan.timer, plan.project)
                    ? cfg.quickMin * TimerOps.MINUTE : 0;
            TimerOps.apply(c, plan.timer.id, new TimerOps.Op() {
                @Override
                public void run(Context ctx, TimerItem it) {
                    if (kind == PAUSE_TIMER) {
                        TimerOps.pause(ctx, it);
                        return;
                    }
                    if (quickMs > 0) {
                        it.total = quickMs;
                        it.remaining = quickMs;
                        it.span = quickMs;
                        it.state = TimerItem.IDLE;
                    }
                    TimerOps.start(ctx, it);
                }
            });
            return;
        }
        // no timer to start yet: make a focus timer (in the project, if any)
        if (timers.size() >= MainActivity.MAX_TIMERS) {
            try {
                open(c, 0, 0x53000000).send();
            } catch (Exception ignored) {
            }
            return;
        }
        TimerItem it = new TimerItem(now, plan.project != null
                ? plan.project.name : c.getString(R.string.quick_focus),
                cfg.quickMin * TimerOps.MINUTE);
        if (plan.project != null) {
            it.projectId = plan.project.id;
        }
        TimerOps.start(c, it);
        timers.add(it);
        PrefsStore.saveTimers(c, timers);
        TimerOps.refreshSurfaces(c);
    }
}
