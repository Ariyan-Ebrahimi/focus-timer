package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.graphics.drawable.GradientDrawable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static boolean sVisible = false;

    public static boolean isVisible() {
        return sVisible;
    }

    public static final int MAX_TIMERS = 30;

    private ViewFlipper flipper;
    private static final int TABS = 5;
    private static final int TAB_PROJECTS = 1;
    private static final int TAB_STOPWATCH = 2;
    private static final int TAB_HISTORY = 3;
    private static final int TAB_SETTINGS = 4;
    private final LinearLayout[] tabBtns = new LinearLayout[TABS];
    private final TextView[] tabLabs = new TextView[TABS];
    private final ImageView[] tabIcons = new ImageView[TABS];
    private int currentTab = 0;
    private TextView headerTitle;
    private View btnNew;
    private static final int[] TAB_TITLES = {R.string.tab_timers, R.string.tab_projects,
            R.string.stopwatch, R.string.tab_history, R.string.tab_settings};

    private LinearLayout timersBox;
    private View timersEmpty;
    private LinearLayout quickRow;
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.US);
    private final List<TimerCard> cards = new ArrayList<TimerCard>();
    private List<TimerItem> timers = new ArrayList<TimerItem>();
    private List<PrefsStore.Project> projects = new ArrayList<PrefsStore.Project>();
    /** Project shown on the Timers tab, -1 for all. */
    private long filterProject = -1;
    private LinearLayout filterRow;
    private View filterScroll;
    private LinearLayout projBox;
    private View projEmpty;
    private long swProject;
    private LinearLayout swProjRow;
    private View swProjScroll;
    private LinearLayout histChart;
    private LinearLayout histLegend;
    private LinearLayout histBreakdown;
    private TextView dndToggle;
    private TextView strictToggle;
    private TextView strictStatus;
    private TextView dndStatus;
    private TextView focusSoundName;
    private TextView langName;

    private TextView swTime;
    private TextView swSub;
    private StopwatchDial swDial;
    private TextView swLapTime;
    private View btnAlarms;
    private TextView nextAlarm;
    private TextView swResetLabel;
    private TextView swToggleLabel;
    private TextView swLapLabel;
    private View swLapBtn;
    private ImageView swToggle;
    private boolean swRunning = false;
    private long swBase = 0L;
    private long swAccum = 0L;
    private int swShownRunning = -1;
    private final ArrayList<Long> laps = new ArrayList<Long>();
    private LinearLayout lapsBox;
    private View lapsEmpty;
    private View lapsCard;

    private TextView histToday;
    private TextView histWeek;
    private TextView histCount;
    private LinearLayout histBox;
    private View histEmpty;
    private View btnClearHist;

    private TextView segLight;
    private TextView segDark;
    private LinearLayout accentRow;
    private TextView soundName;
    private TextView alarmStatus;
    private TextView btnAlarmPerm;
    private TextView notifStatus;
    private TextView btnNotifPerm;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Ringtone previewRingtone;
    private long lastWidgetPush = 0L;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tickTimers();
            if (System.currentTimeMillis() - lastAlarmChip > 20_000L) {
                refreshNextAlarm();
            }
            boolean watch = currentTab == TAB_STOPWATCH;
            if (watch) {
                tickStopwatch();
            }
            // fast refresh only for the running stopwatch's hundredths; timers show seconds
            handler.postDelayed(this, watch && swRunning ? 50 : 250);
        }
    };

    private static class TimerCard {
        TimerItem item;
        View card;
        TextView tv;
        TextView status;
        RingView ring;
        ImageView toggle;
        View plus;
        LinearLayout dots;
        int lastState = -1;
        int lastPos = -1;
    }

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(App.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.themeRes(PrefsStore.getMode(this), PrefsStore.getAcc(this)));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Clay.applyTags(findViewById(android.R.id.content));

        int bg = ThemeHelper.resolve(this, R.attr.bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int vis = getWindow().getDecorView().getSystemUiVisibility();
        if (PrefsStore.getMode(this) == ThemeHelper.MODE_LIGHT) {
            vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(vis);

        flipper = (ViewFlipper) findViewById(R.id.flipper);
        int[] tabIds = {R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4};
        int[] labIds = {R.id.lab0, R.id.lab1, R.id.lab2, R.id.lab3, R.id.lab4};
        int[] icoIds = {R.id.ico0, R.id.ico1, R.id.ico2, R.id.ico3, R.id.ico4};
        headerTitle = (TextView) findViewById(R.id.headerTitle);
        TextView headerSub = (TextView) findViewById(R.id.headerSub);
        headerSub.setText(new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault()).format(new Date()));
        for (int i = 0; i < TABS; i++) {
            tabBtns[i] = (LinearLayout) findViewById(tabIds[i]);
            tabLabs[i] = (TextView) findViewById(labIds[i]);
            tabIcons[i] = (ImageView) findViewById(icoIds[i]);
            final int tab = i;
            tabBtns[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(tab);
                }
            });
        }
        // long words (e.g. German "Einstellungen") shrink to fit their tab instead of being cut
        tabBtns[0].post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < TABS; i++) {
                    TextView l = tabLabs[i];
                    View tab = tabBtns[i];
                    float room = tab.getWidth() - tab.getPaddingLeft() - tab.getPaddingRight();
                    float w = l.getPaint().measureText(l.getText().toString());
                    if (room > 0 && w > room) {
                        l.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                                Math.max(l.getTextSize() * room / w, l.getTextSize() * 0.7f));
                    }
                }
            }
        });

        timersBox = (LinearLayout) findViewById(R.id.timersBox);
        timersEmpty = findViewById(R.id.timersEmpty);
        addEmptyActions((LinearLayout) timersEmpty);
        quickRow = (LinearLayout) findViewById(R.id.quickRow);
        filterRow = (LinearLayout) findViewById(R.id.filterRow);
        filterScroll = findViewById(R.id.filterScroll);
        projBox = (LinearLayout) findViewById(R.id.projBox);
        projEmpty = findViewById(R.id.projEmpty);
        btnNew = findViewById(R.id.btnNewTimer);
        btnAlarms = findViewById(R.id.btnAlarms);
        nextAlarm = (TextView) findViewById(R.id.nextAlarm);
        View.OnClickListener openAlarms = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(ClockActivity.intent(MainActivity.this));
            }
        };
        btnAlarms.setOnClickListener(openAlarms);
        nextAlarm.setOnClickListener(openAlarms);
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentTab == TAB_HISTORY) {
                    addSession();
                } else if (currentTab == TAB_PROJECTS) {
                    ProjectDialog.show(MainActivity.this, null, new Runnable() {
                        @Override
                        public void run() {
                            refreshProjects();
                        }
                    });
                } else {
                    showNewTimerDialog();
                }
            }
        });

        swTime = (TextView) findViewById(R.id.swTime);
        swSub = (TextView) findViewById(R.id.swSub);
        swDial = (StopwatchDial) findViewById(R.id.swDial);
        swLapTime = (TextView) findViewById(R.id.swLapTime);
        swResetLabel = (TextView) findViewById(R.id.swResetLabel);
        swToggleLabel = (TextView) findViewById(R.id.swToggleLabel);
        swLapLabel = (TextView) findViewById(R.id.swLapLabel);
        swToggle = (ImageView) findViewById(R.id.swToggle);
        View swReset = findViewById(R.id.swReset);
        View swLap = findViewById(R.id.swLap);
        swLapBtn = swLap;
        lapsBox = (LinearLayout) findViewById(R.id.lapsBox);
        lapsEmpty = findViewById(R.id.lapsEmpty);
        lapsCard = findViewById(R.id.lapsCard);
        swToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStopwatch();
            }
        });
        swReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetStopwatch();
            }
        });
        // long-press: throw the run away without saving it
        swReset.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (swCurrent() <= 0) {
                    return false;
                }
                swRunning = false;
                swAccum = 0;
                laps.clear();
                saveStopwatch();
                refreshLaps();
                tickStopwatch();
                Toast.makeText(MainActivity.this, R.string.sw_discarded, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        swLap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLap();
            }
        });

        histToday = (TextView) findViewById(R.id.histToday);
        histWeek = (TextView) findViewById(R.id.histWeek);
        histCount = (TextView) findViewById(R.id.histCount);
        histBox = (LinearLayout) findViewById(R.id.histBox);
        histEmpty = findViewById(R.id.histEmpty);
        btnClearHist = findViewById(R.id.btnClearHist);
        // stat values shrink to fit narrow tiles (e.g. "12 days", longer words in German)
        for (TextView tv : new TextView[] {histToday, histWeek, histCount}) {
            ViewGroup.LayoutParams lp = tv.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            tv.setLayoutParams(lp);
            tv.setAutoSizeTextTypeUniformWithConfiguration(12, 24, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        histChart = (LinearLayout) findViewById(R.id.histChart);
        histLegend = (LinearLayout) findViewById(R.id.histLegend);
        histBreakdown = (LinearLayout) findViewById(R.id.histBreakdown);
        histBadges = (LinearLayout) findViewById(R.id.histBadges);
        histHeatmap = (LinearLayout) findViewById(R.id.histHeatmap);
        histTags = (LinearLayout) findViewById(R.id.histTags);
        sleepName = (TextView) findViewById(R.id.sleepName);
        shieldStatus = (TextView) findViewById(R.id.shieldStatus);
        shieldToggle = (TextView) findViewById(R.id.shieldToggle);
        findViewById(R.id.btnSleep).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSleepDialog();
            }
        });
        findViewById(R.id.btnShield).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShieldApps();
            }
        });
        shieldToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleShield();
            }
        });
        findViewById(R.id.btnAddSession).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addSession();
            }
        });
        swProjRow = (LinearLayout) findViewById(R.id.swProjRow);
        swProjScroll = findViewById(R.id.swProjScroll);
        dndToggle = (TextView) findViewById(R.id.dndToggle);
        strictToggle = (TextView) findViewById(R.id.strictToggle);
        strictStatus = (TextView) findViewById(R.id.strictStatus);
        findViewById(R.id.btnStrict).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStrict();
            }
        });
        dndStatus = (TextView) findViewById(R.id.dndStatus);
        focusSoundName = (TextView) findViewById(R.id.focusSoundName);
        langName = (TextView) findViewById(R.id.langName);
        findViewById(R.id.btnTour).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(OnboardingActivity.intent(MainActivity.this), REQ_TOUR);
            }
        });
        findViewById(R.id.btnDnd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDnd();
            }
        });
        ((TextView) findViewById(R.id.versionText)).setText(getString(R.string.version, "7.4"));
        backupSub = (TextView) findViewById(R.id.backupSub);
        // the piano recordings' performers and licences (Musopen asks to be credited)
        ((TextView) findViewById(R.id.musicCredits)).setText(getString(R.string.music_credits,
                "Robin Alciatore, Peter Johnston, Paul Pitman, Luke Faulkner & Musopen"
                + " (public domain), Kimiko Ishizaka / Open Well-Tempered Clavier & Open"
                + " Goldberg Variations (CC0), Teknopazzo (CC0), Jaan Patterson (CC0),"
                + " Pracchia-78 (public domain), Laurens Goedhart (CC BY 3.0). Guitar & nature:"
                + " Jim Greeninger, Jarrod Stanley, Evillan, jackthemurray (CC0), Jujutacular,"
                + " stephan / PDSounds (public domain), Bert Alink, Axletree, MusicLFiles,"
                + " Work With Sounds / Technical Museum of Slovenia (CC BY 4.0), Tomasi-Musso"
                + " Guitar Duo (CC BY 3.0)"));
        ((TextView) findViewById(R.id.restoreSub)).setText(R.string.restore_sub);
        findViewById(R.id.btnBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, Backup.fileName());
                pick(i, REQ_BACKUP);
            }
        });
        findViewById(R.id.btnRestore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                // downloaded/shared .json files often carry a generic type, so allow any
                i.setType("*/*");
                pick(i, REQ_RESTORE);
            }
        });
        findViewById(R.id.btnLanguage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog();
            }
        });
        weeklyToggle = (TextView) findViewById(R.id.weeklyToggle);
        findViewById(R.id.btnWeekly).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PrefsStore.setWeeklyReport(MainActivity.this,
                        !PrefsStore.getWeeklyReport(MainActivity.this));
                WeeklyReport.schedule(MainActivity.this);
                refreshFocusSettings();
            }
        });
        // long-press: see this week's report now instead of waiting for Sunday
        findViewById(R.id.btnWeekly).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WeeklyReport.post(MainActivity.this);
                return true;
            }
        });
        findViewById(R.id.btnExport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE, Backup.csvName());
                pick(i, REQ_EXPORT);
            }
        });
        findViewById(R.id.btnWidget).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                widgetSettings();
            }
        });
        findViewById(R.id.btnFocusSound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFocusSoundDialog();
            }
        });
        btnClearHist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.clear_history_q)
                        .setMessage(R.string.clear_history_msg)
                        .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                PrefsStore.clearHistory(MainActivity.this);
                                TimerWidget.refresh(MainActivity.this);
                                refreshHistory();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });

        segLight = (TextView) findViewById(R.id.segLight);
        segDark = (TextView) findViewById(R.id.segDark);
        accentRow = (LinearLayout) findViewById(R.id.accentRow);
        soundName = (TextView) findViewById(R.id.soundName);
        View btnSound = findViewById(R.id.btnSound);
        alarmStatus = (TextView) findViewById(R.id.alarmStatus);
        btnAlarmPerm = (TextView) findViewById(R.id.btnAlarmPerm);
        notifStatus = (TextView) findViewById(R.id.notifStatus);
        btnNotifPerm = (TextView) findViewById(R.id.btnNotifPerm);
        segLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PrefsStore.setMode(MainActivity.this, ThemeHelper.MODE_LIGHT);
                recreate();
            }
        });
        segDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PrefsStore.setMode(MainActivity.this, ThemeHelper.MODE_DARK);
                recreate();
            }
        });
        btnSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSoundDialog();
            }
        });
        btnAlarmPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AlarmHelper.canSchedule(MainActivity.this)) {
                    try {
                        startActivity(new android.content.Intent(
                                "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        AlarmHelper.openAppSettings(MainActivity.this);
                    }
                } else {
                    AlarmHelper.openExactAlarmSettings(MainActivity.this);
                }
            }
        });
        btnNotifPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlarmHelper.openAppSettings(MainActivity.this);
            }
        });
        buildAccentRow();

        timers = PrefsStore.loadTimers(this);

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt("tab", 0);
        }
        loadStopwatch();

        int openTab = getIntent().getIntExtra("tab", -1);
        if (openTab >= 0 && openTab < TABS) {
            currentTab = openTab;
        }

        selectTab(currentTab);
        refreshTimers();
        refreshLaps();
        tickStopwatch();
        handler.post(ticker);
        AlarmHelper.ensureNotifPermission(this);
        NotificationHelper.applySound(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        sVisible = true;
        sInstance = this;
        reloadFromStore();
        refreshSettings();
        showWelcomeIfNew();
        ClockAlarms.scheduleAll(this);
        refreshNextAlarm();
    }

    private long lastAlarmChip;

    /** "07:30 \u00b7 in 9h 12m" above the timers while an alarm is on. */
    private void refreshNextAlarm() {
        lastAlarmChip = System.currentTimeMillis();
        ClockAlarms.Alarm al = ClockAlarms.soonest(this);
        if (al == null) {
            nextAlarm.setVisibility(View.GONE);
            return;
        }
        String in = getString(R.string.clock_in,
                ClockAlarms.untilText(ClockAlarms.next(al, System.currentTimeMillis())));
        setIfChanged(nextAlarm, ClockAlarms.timeText(this, al) + "  \u00b7  " + in);
        if (nextAlarm.getVisibility() != View.VISIBLE) {
            Clay.inset(nextAlarm, ThemeHelper.resolve(this, R.attr.surface2), -1);
            nextAlarm.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        sVisible = false;
        super.onStop();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int openTab = intent.getIntExtra("tab", -1);
        if (openTab >= 0 && openTab < TABS) {
            selectTab(openTab);
        }
    }

    private static MainActivity sInstance;

    /** Called when timers/stopwatch were changed outside the activity (notification buttons). */
    public static void notifyExternalChange() {
        MainActivity a = sInstance;
        if (a != null && sVisible) {
            a.reloadFromStore();
        }
    }

    private void reloadFromStore() {
        timers = PrefsStore.loadTimers(this);
        projects = PrefsStore.loadProjects(this);
        if (currentTab == TAB_PROJECTS) {
            refreshProjects();
        }
        loadStopwatch();
        reconcileTimers();
        refreshTimers();
        refreshLaps();
        tickStopwatch();
        LiveNotifier.update(this);
    }

    private void loadStopwatch() {
        PrefsStore.Sw sw = PrefsStore.loadSw(this);
        swRunning = sw.running;
        swBase = sw.base;
        swAccum = sw.accum;
        swProject = sw.project;
        laps.clear();
        laps.addAll(sw.laps);
    }

    private void saveStopwatch() {
        // keep what isn't mirrored here (the pause count); a cleared run starts over
        PrefsStore.Sw sw = PrefsStore.loadSw(this);
        if (!swRunning && swAccum == 0) {
            sw.pauses = 0;
        }
        sw.laps.clear();
        sw.running = swRunning;
        sw.base = swBase;
        sw.accum = swAccum;
        sw.project = swProject;
        sw.laps.addAll(laps);
        PrefsStore.saveSw(this, sw);
        LiveNotifier.update(this);
    }

    @Override
    protected void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        handler.removeCallbacks(ticker);
        stopPreview();
        super.onDestroy();
    }

    /** Opens a placed widget's settings, or offers to add a widget when there's none. */
    private void widgetSettings() {
        final int[] ids = TimerWidget.ids(this);
        if (ids.length == 1) {
            openWidgetSettings(ids[0]);
        } else if (ids.length > 1) {
            String[] names = new String[ids.length];
            for (int i = 0; i < ids.length; i++) {
                names[i] = getString(R.string.w_n, i + 1);
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.w_which)
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            openWidgetSettings(ids[which]);
                        }
                    })
                    .show();
        } else {
            android.appwidget.AppWidgetManager mgr =
                    android.appwidget.AppWidgetManager.getInstance(this);
            if (mgr == null || !mgr.isRequestPinAppWidgetSupported() || !mgr.requestPinAppWidget(
                    new android.content.ComponentName(this, TimerWidget.class), null, null)) {
                Toast.makeText(this, R.string.w_pin_hint, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openWidgetSettings(int id) {
        Intent i = new Intent(this, WidgetConfigActivity.class);
        i.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(WidgetConfigActivity.EXTRA_EDIT, true);
        startActivity(i);
    }

    private LinearLayout histBadges;
    private TextView weeklyToggle;
    private LinearLayout histHeatmap;
    private LinearLayout histTags;
    private TextView sleepName;
    private TextView shieldStatus;
    private TextView shieldToggle;

    private static final int REQ_BACKUP = 41;
    private static final int REQ_EXPORT = 43;
    private static final int REQ_RESTORE = 42;
    private TextView backupSub;

    private void pick(Intent i, int req) {
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_TOUR) {
            if (result == RESULT_OK && data != null) {
                if (data.getBooleanExtra(OnboardingActivity.EXTRA_START_FOCUS, false)) {
                    selectTab(0);
                    quickStart(getString(R.string.quick_focus),
                            PrefsStore.getPomoFocus(this) * TimerOps.MINUTE);
                }
                if (data.getBooleanExtra(OnboardingActivity.EXTRA_RECREATE, false)) {
                    recreate();
                }
            }
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (req == REQ_EXPORT) {
            try {
                int n = Backup.writeCsv(this, data.getData());
                Toast.makeText(this, getString(R.string.exported, n), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
            }
        } else if (req == REQ_BACKUP) {
            try {
                Backup.write(this, data.getData());
                Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show();
            }
            refreshSettings();
        } else if (req == REQ_RESTORE) {
            final Backup.Snapshot snap;
            try {
                snap = Backup.read(this, data.getData());
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_backup, Toast.LENGTH_LONG).show();
                return;
            }
            String when = snap.created > 0 ? java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(new Date(snap.created)) : "?";
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restore_q)
                    .setMessage(getString(R.string.restore_msg, when))
                    .setPositiveButton(R.string.restore, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                Backup.restore(MainActivity.this, snap);
                                Toast.makeText(MainActivity.this, R.string.restored,
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, R.string.not_backup,
                                        Toast.LENGTH_LONG).show();
                            }
                            // theme, colour and language may have changed
                            recreate();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AlarmHelper.NOTIF_PERM_CODE) {
            refreshSettings();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", currentTab);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    public static String formatDuration(long millis) {
        long s = Math.max(0L, millis + 500L) / 1000L;
        long h = s / 3600L;
        long m = (s / 60L) % 60L;
        long sec = s % 60L;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String formatSw(long ms) {
        long cs = (ms / 10) % 100;
        long s = (ms / 1000) % 60;
        long m = (ms / 60000) % 60;
        long h = ms / 3600000L;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs);
        }
        return String.format(Locale.US, "%d:%02d.%02d", m, s, cs);
    }

    public static String formatSum(long ms) {
        long m = ms / 60000L;
        long h = m / 60L;
        m = m % 60L;
        if (h > 0) {
            return App.str(R.string.fmt_hm, h, m);
        }
        long s = (ms / 1000L) % 60L;
        if (m > 0) {
            return App.str(R.string.fmt_ms, m, s);
        }
        return App.str(R.string.fmt_s, s);
    }

    private android.view.animation.Animation slide(float from, float to, boolean in) {
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        set.addAnimation(new android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_SELF, from,
                android.view.animation.Animation.RELATIVE_TO_SELF, to,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0));
        set.addAnimation(new android.view.animation.AlphaAnimation(in ? 0f : 1f, in ? 1f : 0f));
        set.setDuration(in ? 240 : 160);
        set.setInterpolator(new android.view.animation.DecelerateInterpolator());
        return set;
    }

    private void selectTab(int i) {
        int prev = flipper.getDisplayedChild();
        if (prev != i && flipper.getWidth() > 0) {
            float dir = i > prev ? 1f : -1f;
            flipper.setInAnimation(slide(0.06f * dir, 0f, true));
            flipper.setOutAnimation(slide(0f, -0.06f * dir, false));
        } else {
            flipper.setInAnimation(null);
            flipper.setOutAnimation(null);
        }
        currentTab = i;
        flipper.setDisplayedChild(i);
        headerTitle.setText(TAB_TITLES[i]);
        btnNew.setVisibility(i == 0 || i == TAB_PROJECTS || i == TAB_HISTORY
                ? View.VISIBLE : View.INVISIBLE);
        btnAlarms.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        for (int j = 0; j < TABS; j++) {
            boolean sel = (j == i);
            tabLabs[j].setTextColor(sel ? accent : sub);
            tabIcons[j].setImageTintList(ColorStateList.valueOf(sel ? accent : sub));
            if (sel) {
                Clay.inset(tabBtns[j], ThemeHelper.resolve(this, R.attr.surface2), 24);
            } else {
                tabBtns[j].setBackground(null);
                tabBtns[j].setElevation(0);
            }
        }
        if (i == TAB_STOPWATCH) {
            buildSwProjects();
            tickStopwatch();
        } else if (i == TAB_PROJECTS) {
            refreshProjects();
        } else if (i == TAB_HISTORY) {
            refreshHistory();
        } else if (i == TAB_SETTINGS) {
            refreshSettings();
        }
    }

    private TimerItem findTimer(long id) {
        for (TimerItem it : timers) {
            if (it.id == id) {
                return it;
            }
        }
        return null;
    }

    private void saveTimers() {
        PrefsStore.saveTimers(this, timers);
        LiveNotifier.update(this);
        TimerWidget.refresh(this);
        QuickTile.refresh(this);
    }

    private void reconcileTimers() {
        long now = System.currentTimeMillis();
        List<Long> due = new ArrayList<Long>();
        for (TimerItem it : timers) {
            if (it.state == TimerItem.RUNNING && it.deadline <= now) {
                due.add(it.id);
            }
        }
        for (long id : due) {
            TimerOps.handleDeadline(this, id);
        }
    }

    private void tickTimers() {
        long now = System.currentTimeMillis();
        boolean due = false;
        for (TimerCard h : cards) {
            TimerItem it = h.item;
            if (it.state == TimerItem.RUNNING) {
                it.remaining = it.deadline - now;
                if (it.remaining <= 0) {
                    due = true;
                } else {
                    updateTimerCard(h);
                }
            }
        }
        if (due && sVisible) {
            reconcileTimers();
        }
        if (now - lastWidgetPush > 1000) {
            lastWidgetPush = now;
            TimerWidget.refresh(this);
        }
    }

    private void startItem(TimerItem it) {
        NotificationHelper.cancelAlarm(this, it.id);
        TimerOps.start(this, it);
        if (it.state != TimerItem.RUNNING) {
            Toast.makeText(this, R.string.set_time_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AlarmHelper.canSchedule(this)) {
            AlarmHelper.openExactAlarmSettings(this);
            Toast.makeText(this, R.string.enable_exact,
                    Toast.LENGTH_LONG).show();
        }
        saveTimers();
        refreshTimers();
    }

    private void pauseItem(TimerItem it) {
        if (TimerOps.strictLocked(this, it)) {
            showStrictDialog(it);
            return;
        }
        TimerOps.pause(this, it);
        saveTimers();
        refreshTimers();
    }

    private void resetItem(TimerItem it) {
        if (TimerOps.strictLocked(this, it)) {
            showStrictDialog(it);
            return;
        }
        NotificationHelper.cancelAlarm(this, it.id);
        long before = System.currentTimeMillis();
        TimerOps.reset(this, it);
        saveTimers();
        refreshTimers();
        // stopping early still logs the time studied; offer a note for it
        askNote(before, null);
    }

    /** Offers a note for a session logged since `since` (title null = "Add a note"). */
    private void askNote(long since, String title) {
        PrefsStore.Hist h = NoteDialog.justLogged(this, since);
        if (h == null) {
            return;
        }
        NoteDialog.show(this, h, title, new Runnable() {
            @Override
            public void run() {
                if (currentTab == TAB_HISTORY) {
                    refreshHistory();
                }
            }
        });
    }

    private void plusItem(TimerItem it) {
        TimerOps.addMinute(this, it);
        saveTimers();
        refreshTimers();
    }

    private void deleteItem(TimerItem it) {
        if (TimerOps.strictLocked(this, it)) {
            showStrictDialog(it);
            return;
        }
        TimerOps.logPartial(this, it);
        AlarmHelper.cancel(this, it.id);
        NotificationHelper.cancelAlarm(this, it.id);
        timers.remove(it);
        saveTimers();
        refreshTimers();
    }

    private void refreshTimers() {
        Collections.sort(timers, new Comparator<TimerItem>() {
            @Override
            public int compare(TimerItem a, TimerItem b) {
                if (a.pinned != b.pinned) {
                    return a.pinned ? -1 : 1;
                }
                if (a.id == b.id) {
                    return 0;
                }
                return a.id < b.id ? -1 : 1;
            }
        });
        projects = PrefsStore.loadProjects(this);
        buildFilterRow();
        timersBox.removeAllViews();
        cards.clear();
        int shown = 0;
        for (TimerItem t : timers) {
            if (filterProject >= 0 && t.projectId != filterProject) {
                continue;
            }
            timersBox.addView(buildTimerCard(t, shown++));
        }
        timersEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        buildQuickRow();
    }

    // ------------------------------------------------------------ quick start

    private void buildQuickRow() {
        quickRow.removeAllViews();
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int surface = ThemeHelper.resolve(this, R.attr.surface);

        TextView pomo = clayPill(getString(R.string.pomodoro), surface, main, true);
        quickIcon(pomo, R.drawable.ic_tab_timer);
        pomo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickPomodoro();
            }
        });
        quickRow.addView(pomo, quickLp());

        // recent plain timers from History, then a few sensible defaults
        List<String> names = new ArrayList<String>();
        List<Long> lens = new ArrayList<Long>();
        for (PrefsStore.Hist h : PrefsStore.loadHistory(this)) {
            if (names.size() >= 4) {
                break;
            }
            // only real timers with whole-minute lengths (not stopwatch runs or stopped ones)
            if (h.name.contains(" \u00b7 ") || h.millis < TimerOps.MINUTE
                    || h.millis % TimerOps.MINUTE != 0
                    || h.name.equals(getString(R.string.stopwatch))) {
                continue;
            }
            boolean dup = false;
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(h.name) && lens.get(i) == h.millis) {
                    dup = true;
                }
            }
            if (!dup) {
                names.add(h.name);
                lens.add(h.millis);
            }
        }
        long[] defaults = {5 * TimerOps.MINUTE, 10 * TimerOps.MINUTE, 25 * TimerOps.MINUTE};
        for (long d : defaults) {
            if (names.size() >= 4) {
                break;
            }
            if (!lens.contains(d)) {
                names.add(getString(R.string.duration_timer, formatDuration(d)));
                lens.add(d);
            }
        }
        for (int i = 0; i < names.size(); i++) {
            final String n = names.get(i);
            final long ms = lens.get(i);
            String label = n.equals(getString(R.string.duration_timer, formatDuration(ms)))
                    ? formatDuration(ms) : n + "  " + formatDuration(ms);
            TextView c = clayPill(label, surface, main, true);
            quickIcon(c, R.drawable.ic_play);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    quickStart(n, ms);
                }
            });
            quickRow.addView(c, quickLp());
        }
    }

    private LinearLayout.LayoutParams quickLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(10));
        return lp;
    }

    private void quickIcon(TextView t, int icon) {
        android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(ThemeHelper.resolve(this, R.attr.accent));
        d.setBounds(0, 0, dp(14), dp(14));
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(dp(6));
        t.setGravity(Gravity.CENTER_VERTICAL);
        Clay.text(t, 800, 14, ThemeHelper.resolve(this, R.attr.textMain));
        t.setPaddingRelative(dp(14), dp(9), dp(16), dp(10));
    }

    private boolean roomForOneMore() {
        if (timers.size() >= MAX_TIMERS) {
            Toast.makeText(this, getString(R.string.max_timers, MAX_TIMERS),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void quickStart(String name, long ms) {
        for (TimerItem it : timers) {
            if (!it.isSequence() && it.name.equals(name) && it.total == ms) {
                if (it.state != TimerItem.RUNNING) {
                    TimerOps.reset(this, it);
                    startItem(it);
                }
                return;
            }
        }
        if (!roomForOneMore()) {
            return;
        }
        TimerItem it = new TimerItem(System.currentTimeMillis(), name, ms);
        it.projectId = Math.max(0, filterProject);
        timers.add(it);
        startItem(it);
    }

    private void quickPomodoro() {
        for (TimerItem it : timers) {
            if (it.isSequence() && it.name.equals(getString(R.string.pomodoro))) {
                if (it.state != TimerItem.RUNNING) {
                    startItem(it);
                }
                return;
            }
        }
        if (!roomForOneMore()) {
            return;
        }
        TimerItem it = new TimerItem(System.currentTimeMillis(), getString(R.string.pomodoro), 0);
        it.projectId = Math.max(0, filterProject);
        int rounds = PrefsStore.getPomoRounds(this);
        for (int r = 0; r < rounds; r++) {
            it.steps.add(new TimerItem.Step(getString(R.string.focus_step),
                    PrefsStore.getPomoFocus(this) * TimerOps.MINUTE,
                    false));
            boolean last = r == rounds - 1;
            it.steps.add(new TimerItem.Step(getString(last ? R.string.long_break : R.string.short_break),
                    (last ? PrefsStore.getPomoLong(this) : PrefsStore.getPomoShort(this))
                            * TimerOps.MINUTE, true));
        }
        it.rewind();
        timers.add(it);
        startItem(it);
    }

    private int borderlessBg() {
        TypedArray ta = obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackgroundBorderless});
        int res = ta.getResourceId(0, 0);
        ta.recycle();
        return res;
    }

    private static final int MAX_DOTS = 16;

    private void updateTimerCard(TimerCard h) {
        TimerItem it = h.item;
        // only touch views whose content actually changed, so a running timer
        // never forces the scrolling list to lay out again
        setIfChanged(h.tv, formatDuration(Math.max(0L, it.remaining)));
        long span = it.progressSpan();
        float frac = span <= 0 ? 0f
                : Math.max(0f, Math.min(1f, (float) it.remaining / (float) span));
        h.ring.setProgress(it.state == TimerItem.FINISHED ? 0f : frac);
        boolean running = it.state == TimerItem.RUNNING;
        if (h.lastState != it.state) {
            h.toggle.setImageResource(running ? R.drawable.ic_pause : R.drawable.ic_play);
            h.toggle.setContentDescription(getString(running ? R.string.pause : R.string.start));
            h.status.setTextColor(running ? ThemeHelper.resolve(this, R.attr.accent)
                    : ThemeHelper.resolve(this, R.attr.textSub));
            h.plus.setVisibility(running || it.state == TimerItem.PAUSED
                    ? View.VISIBLE : View.GONE);
            h.lastState = it.state;
        }

        String step = "";
        if (it.isSequence()) {
            step = it.currentStep().name;
            if (it.stepCount() > MAX_DOTS || h.dots == null) {
                step += " " + (it.position() + 1) + "/" + it.stepCount();
            }
            step += " \u00b7 ";
        }
        String status;
        if (running) {
            status = "\u25cf  " + step + getString(R.string.st_ends, clockFmt.format(new Date(it.deadline)));
        } else if (it.state == TimerItem.PAUSED) {
            status = step + getString(R.string.st_paused);
        } else if (it.state == TimerItem.FINISHED) {
            status = getString(R.string.st_done);
        } else if (it.isSequence()) {
            status = getString(R.string.st_ready_seq, it.stepCount(),
                    Projects.hours(it.plannedTotal()));
        } else {
            status = getString(R.string.st_ready);
        }
        setIfChanged(h.status, status);

        int pos = it.state == TimerItem.FINISHED ? it.stepCount() : it.position();
        if (h.dots != null && pos != h.lastPos) {
            h.lastPos = pos;
            int accent = ThemeHelper.resolve(this, R.attr.accent);
            int track = ThemeHelper.resolve(this, R.attr.trackC);
            for (int k = 0; k < h.dots.getChildCount(); k++) {
                View d = h.dots.getChildAt(k);
                boolean rest = it.steps.get(k % it.steps.size()).rest;
                int size = dp(rest ? 5 : 7);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
                lp.width = k == pos ? dp(18) : size;
                lp.height = size;
                d.setLayoutParams(lp);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(4));
                g.setColor(k <= pos ? accent : track);
                d.setBackground(g);
            }
        }
    }

    private static void setIfChanged(TextView t, CharSequence text) {
        if (!TextUtils.equals(t.getText(), text)) {
            t.setText(text);
        }
    }

    /** "Start a 25-min focus" and "New timer" under the empty Timers message. */
    private void addEmptyActions(LinearLayout box) {
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        TextView start = new TextView(this);
        start.setText(getString(R.string.start_focus_n, PrefsStore.getPomoFocus(this)));
        start.setGravity(Gravity.CENTER);
        Clay.text(start, 900, 16, 0xFFFFFFFF);
        Clay.style(start, accent, -1, 5, true);
        start.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        start.setCompoundDrawableTintList(ColorStateList.valueOf(0xFFFFFFFF));
        start.setCompoundDrawablePadding(dp(6));
        start.setPaddingRelative(dp(22), 0, dp(26), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(54));
        slp.topMargin = dp(22);
        box.addView(start, slp);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickStart(getString(R.string.quick_focus),
                        PrefsStore.getPomoFocus(MainActivity.this) * TimerOps.MINUTE);
            }
        });
        TextView make = new TextView(this);
        make.setText(R.string.new_timer);
        make.setGravity(Gravity.CENTER);
        Clay.text(make, 800, 14, main);
        make.setPaddingRelative(dp(18), dp(10), dp(18), dp(10));
        Clay.inset(make, surface2, -1);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(12);
        box.addView(make, mlp);
        make.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewTimerDialog();
            }
        });
    }

    private static final int REQ_TOUR = 44;

    /** New users get the welcome tour once (people already using the app don't). */
    private void showWelcomeIfNew() {
        if (PrefsStore.raw(this).getBoolean("welcome_seen", false)) {
            return;
        }
        PrefsStore.raw(this).edit().putBoolean("welcome_seen", true).apply();
        if (!timers.isEmpty() || !PrefsStore.loadHistory(this).isEmpty()) {
            return; // not new: already using the app
        }
        startActivityForResult(OnboardingActivity.intent(this), REQ_TOUR);
    }

    private TextView clayPill(String text, int fill, int textColor, boolean pressable) {
        TextView t = new TextView(this);
        t.setText(text);
        Clay.text(t, 800, 12, textColor);
        t.setPaddingRelative(dp(12), dp(5), dp(12), dp(6));
        Clay.style(t, fill, -1, pressable ? 4 : 0, pressable);
        return t;
    }

    private View buildTimerCard(final TimerItem it, int index) {
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int surface = ThemeHelper.resolve(this, R.attr.surface);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPaddingRelative(dp(22), dp(16), dp(16), dp(20));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(20);
        card.setLayoutParams(clp);
        Clay.style(card, surface, 30, 5, false);
        card.setClipToPadding(false);
        card.setClipChildren(false);
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showEditDialog(it);
                return true;
            }
        });

        // name row
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(it.name);
        Clay.text(name, 800, 16, main);
            name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (it.pinned) {
            android.graphics.drawable.Drawable pinIc = getDrawable(R.drawable.ic_pin).mutate();
            pinIc.setTint(ThemeHelper.resolve(this, R.attr.accent));
            pinIc.setBounds(0, 0, dp(14), dp(14));
            name.setCompoundDrawablesRelative(pinIc, null, null, null);
            name.setCompoundDrawablePadding(dp(6));
        }
        PrefsStore.Project proj = projectOf(it.projectId);
        if (proj != null) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(it.name);
            int st = sb.length();
            sb.append("  " + proj.name);
            sb.setSpan(new android.text.style.ForegroundColorSpan(sub), st, sb.length(), 0);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), st, sb.length(), 0);
            name.setText(sb);
            if (!it.pinned) {
                name.setCompoundDrawablesRelative(colorDot(Projects.color(this, proj.color), 10),
                        null, null, null);
                name.setCompoundDrawablePadding(dp(8));
            }
        }
        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more);
        more.setImageTintList(ColorStateList.valueOf(sub));
        more.setBackgroundResource(borderlessBg());
        more.setPaddingRelative(dp(8), dp(8), dp(8), dp(8));
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        more.setContentDescription(getString(R.string.more_options));
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimerMenu(it);
            }
        });
        top.addView(name);
        top.addView(more);
        card.addView(top);

        // time + ring with the play button inside
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setClipToPadding(false);
        mid.setClipChildren(false);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        left.setClipToPadding(false);
        left.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this);
        Clay.text(tv, 900, 44, main);
        tv.setFontFeatureSettings("tnum");
        tv.setIncludeFontPadding(false);
        left.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView status = new TextView(this);
        Clay.text(status, 700, 13, sub);
        status.setPaddingRelative(0, dp(4), 0, 0);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout dots = null;
        if (it.isSequence() && it.stepCount() <= MAX_DOTS) {
            dots = new LinearLayout(this);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            dots.setGravity(Gravity.CENTER_VERTICAL);
            for (int k = 0; k < it.stepCount(); k++) {
                View d = new View(this);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(7), dp(7));
                dl.setMarginEnd(dp(5));
                dots.addView(d, dl);
            }
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(12));
            dlp.topMargin = dp(10);
            left.addView(dots, dlp);
        }

        TextView plus = clayPill(getString(R.string.plus_min), surface, main, true);
        Clay.text(plus, 800, 13, main);
        plus.setPaddingRelative(dp(14), dp(6), dp(14), dp(7));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(12);
        plus.setLayoutParams(plp);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                plusItem(it);
            }
        });
        left.addView(plus);
        mid.addView(left);

        FrameLayout ringBox = new FrameLayout(this);
        ringBox.setClipChildren(false);
        ringBox.setLayoutParams(new LinearLayout.LayoutParams(dp(96), dp(96)));
        RingView ring = new RingView(this);
        ring.setRing(9, 3);
        ring.setColors(ThemeHelper.resolve(this, R.attr.trackC),
                ThemeHelper.resolve(this, R.attr.accent),
                ThemeHelper.resolve(this, R.attr.accent2));
        ringBox.addView(ring, new FrameLayout.LayoutParams(dp(96), dp(96)));
        ImageView toggle = new ImageView(this);
        toggle.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        toggle.setPaddingRelative(dp(17), dp(17), dp(17), dp(17));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(dp(56), dp(56));
        tlp.gravity = Gravity.CENTER;
        ringBox.addView(toggle, tlp);
        Clay.style(toggle, ThemeHelper.resolve(this, R.attr.accent), -1, 4, true);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (it.state == TimerItem.RUNNING) {
                    pauseItem(it);
                } else {
                    startItem(it);
                }
            }
        });
        mid.addView(ringBox);
        card.addView(mid);

        TimerCard h = new TimerCard();
        h.item = it;
        h.card = card;
        h.tv = tv;
        h.status = status;
        h.ring = ring;
        h.toggle = toggle;
        h.plus = plus;
        h.dots = dots;
        cards.add(h);
        updateTimerCard(h);
        return card;
    }

    private void showTimerMenu(final TimerItem it) {
        String[] opts = {getString(R.string.reset), getString(R.string.edit),
                getString(it.pinned ? R.string.unpin : R.string.pin_to_top), getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            resetItem(it);
                        } else if (which == 1) {
                            showEditDialog(it);
                        } else if (which == 2) {
                            it.pinned = !it.pinned;
                            saveTimers();
                            refreshTimers();
                        } else {
                            confirmDelete(it);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final TimerItem it) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_timer_q)
                .setMessage(getString(R.string.remove_q, android.text.BidiFormatter.getInstance().unicodeWrap(it.name)))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        deleteItem(it);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showNewTimerDialog() {
        if (!roomForOneMore()) {
            return;
        }
        TimerSheet.show(this, null, Math.max(0, filterProject), new TimerSheet.Callback() {
            @Override
            public void onDone(String name, long ms, List<TimerItem.Step> steps, int rounds,
                               long projectId, boolean start) {
                if (!roomForOneMore()) {
                    return;
                }
                TimerItem it = new TimerItem(System.currentTimeMillis(), name, ms);
                it.steps.addAll(steps);
                it.rounds = Math.max(1, rounds);
                it.projectId = projectId;
                it.rewind();
                timers.add(it);
                if (start) {
                    startItem(it);
                } else {
                    saveTimers();
                    refreshTimers();
                }
            }
        });
    }

    /**
     * Strict mode said no: keep going, or hold "Give up" for 3 seconds to end the
     * session without counting it.
     */
    private void showStrictDialog(final TimerItem it) {
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int bad = ThemeHelper.resolve(this, R.attr.badC);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        String at = android.text.format.DateFormat.getTimeFormat(this)
                .format(new Date(it.deadline));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(dp(24), dp(4), dp(24), dp(4));
        TextView msg = new TextView(this);
        msg.setText(getString(R.string.strict_dialog_msg, at));
        Clay.text(msg, 600, 15, main);
        msg.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        box.addView(msg);
        final TextView hold = new TextView(this);
        hold.setText(R.string.strict_hold);
        hold.setGravity(Gravity.CENTER);
        Clay.text(hold, 800, 15, bad);
        Clay.inset(hold, surface2, -1);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        hl.topMargin = dp(18);
        box.addView(hold, hl);
        TextView note = new TextView(this);
        note.setText(R.string.strict_no_count);
        Clay.text(note, 600, 12, sub);
        note.setGravity(Gravity.CENTER);
        note.setPaddingRelative(0, dp(6), 0, 0);
        box.addView(note);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.strict_title)
                .setView(box)
                .setPositiveButton(R.string.strict_keep, null)
                .show();
        // holding the button counts down 3, 2, 1; letting go cancels
        final int[] left = {3};
        final Runnable count = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    dlg.dismiss();
                    NotificationHelper.cancelAlarm(MainActivity.this, it.id);
                    TimerOps.giveUp(MainActivity.this, it);
                    saveTimers();
                    refreshTimers();
                    Toast.makeText(MainActivity.this, R.string.strict_gave_up,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                hold.setText(getString(R.string.strict_holding, left[0]));
                handler.postDelayed(this, 1000);
            }
        };
        hold.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    left[0] = 3;
                    hold.setText(getString(R.string.strict_holding, 3));
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    handler.postDelayed(count, 1000);
                } else if (e.getAction() == android.view.MotionEvent.ACTION_UP
                        || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(count);
                    if (dlg.isShowing()) {
                        hold.setText(R.string.strict_hold);
                    }
                }
                return true;
            }
        });
    }

    private void showEditDialog(final TimerItem it) {
        if (TimerOps.strictLocked(this, it)) {
            showStrictDialog(it);
            return;
        }
        TimerSheet.show(this, it, it.projectId, new TimerSheet.Callback() {
            @Override
            public void onDone(String name, long ms, List<TimerItem.Step> steps, int rounds,
                               long projectId, boolean start) {
                TimerOps.logPartial(MainActivity.this, it);
                AlarmHelper.cancel(MainActivity.this, it.id);
                it.projectId = projectId;
                NotificationHelper.cancelAlarm(MainActivity.this, it.id);
                it.name = name;
                it.steps.clear();
                it.steps.addAll(steps);
                it.rounds = Math.max(1, rounds);
                if (steps.isEmpty()) {
                    it.total = ms;
                }
                it.rewind();
                saveTimers();
                refreshTimers();
            }
        });
    }

    // ------------------------------------------------------------ projects

    private PrefsStore.Project projectOf(long id) {
        for (PrefsStore.Project p : projects) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    private android.graphics.drawable.Drawable colorDot(int color, int sizeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return g;
    }

    /** "All / Math / Physics" chips above the timers; hidden until a project exists. */
    private void buildFilterRow() {
        filterRow.removeAllViews();
        filterScroll.setVisibility(projects.isEmpty() ? View.GONE : View.VISIBLE);
        if (projectOf(filterProject) == null) {
            filterProject = -1;
        }
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        for (int i = -1; i < projects.size(); i++) {
            final long id = i < 0 ? -1 : projects.get(i).id;
            boolean sel = id == filterProject;
            TextView c = new TextView(this);
            c.setText(i < 0 ? getString(R.string.all) : projects.get(i).name);
            Clay.text(c, 800, 14, sel ? main : sub);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPaddingRelative(dp(14), dp(7), dp(16), dp(8));
            if (i >= 0) {
                c.setCompoundDrawablesRelative(colorDot(Projects.color(this, projects.get(i).color), 10),
                        null, null, null);
                c.setCompoundDrawablePadding(dp(7));
            }
            if (sel) {
                Clay.inset(c, ThemeHelper.resolve(this, R.attr.surface2), -1);
            } else {
                c.setBackground(null);
            }
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    filterProject = id;
                    refreshTimers();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(4));
            filterRow.addView(c, lp);
        }
    }

    private void refreshProjects() {
        projects = PrefsStore.loadProjects(this);
        projBox.removeAllViews();
        projEmpty.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
        List<PrefsStore.Hist> history = PrefsStore.loadHistory(this);
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int surface = ThemeHelper.resolve(this, R.attr.surface);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        for (final PrefsStore.Project p : projects) {
            Projects.Stats st = Projects.stats(history, p.id);
            int pc = Projects.color(this, p.color);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPaddingRelative(dp(22), dp(18), dp(22), dp(20));
            card.setClipChildren(false);
            Clay.style(card, surface, 28, 5, true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(ProjectActivity.intent(MainActivity.this, p.id));
                }
            });

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(p.name);
            Clay.text(name, 900, 19, main);
            name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setCompoundDrawablesRelative(colorDot(pc, 12), null, null, null);
            name.setCompoundDrawablePadding(dp(10));
            top.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            ImageView go = new ImageView(this);
            go.setImageResource(R.drawable.ic_chevron);
            go.setImageTintList(ColorStateList.valueOf(sub));
            top.addView(go, new LinearLayout.LayoutParams(dp(20), dp(20)));
            card.addView(top);

            LinearLayout nums = new LinearLayout(this);
            nums.setPaddingRelative(0, dp(14), 0, 0);
            String[][] vals = {{getString(R.string.today), Projects.hours(st.today)},
                    {getString(R.string.this_week), Projects.hours(st.week)},
                    {getString(R.string.total), Projects.hours(st.total)}};
            for (String[] v : vals) {
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView l = new TextView(this);
                l.setText(v[0]);
                Clay.text(l, 700, 12, sub);
                TextView n = new TextView(this);
                n.setText(v[1]);
                Clay.text(n, 900, 18, main);
                n.setFontFeatureSettings("tnum");
                col.addView(l);
                col.addView(n);
                nums.addView(col, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            card.addView(nums);

            if (p.goalMin > 0) {
                long goalMs = p.goalMin * 60_000L;
                LinearLayout track = new LinearLayout(this);
                track.setPaddingRelative(dp(3), dp(3), dp(3), dp(3));
                track.setWeightSum(1f);
                Clay.inset(track, surface2, -1);
                View fill = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setColor(pc);
                g.setCornerRadius(dp(5));
                fill.setBackground(g);
                track.addView(fill, new LinearLayout.LayoutParams(0, dp(8),
                        Math.max(0.02f, Math.min(1f, st.week / (float) goalMs))));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(14));
                tl.topMargin = dp(16);
                card.addView(track, tl);
                TextView gt = new TextView(this);
                gt.setText(getString(st.week >= goalMs ? R.string.goal_reached : R.string.goal_progress,
                        Projects.hours(st.week), Projects.hours(goalMs)));
                Clay.text(gt, 600, 12, sub);
                gt.setPaddingRelative(0, dp(6), 0, 0);
                card.addView(gt);
            }

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(18);
            projBox.addView(card, clp);
        }
    }

    private long swCurrent() {
        if (swRunning) {
            return swAccum + Math.max(0L, System.currentTimeMillis() - swBase);
        }
        return swAccum;
    }

    /** "12:34" big with ".56" smaller and softer after it. */
    private CharSequence swBig(long ms) {
        String full = formatSw(ms);
        int dot = full.lastIndexOf('.');
        android.text.SpannableString sp = new android.text.SpannableString(full);
        sp.setSpan(new android.text.style.RelativeSizeSpan(0.5f), dot, full.length(), 0);
        sp.setSpan(new android.text.style.ForegroundColorSpan(
                ThemeHelper.resolve(this, R.attr.textSub)), dot, full.length(), 0);
        return sp;
    }

    private void tickStopwatch() {
        long cur = swCurrent();
        setIfChanged(swTime, swBig(cur));
        long lapStart = laps.isEmpty() ? -1 : laps.get(laps.size() - 1);
        swDial.setState(cur, lapStart, swRunning);
        if (lapStart >= 0) {
            setIfChanged(swLapTime, getString(R.string.sw_lap_live, laps.size() + 1,
                    formatSw(cur - lapStart)));
            swLapTime.setVisibility(View.VISIBLE);
        } else {
            swLapTime.setVisibility(View.INVISIBLE);
        }
        int state = swRunning ? 1 : (cur > 0 ? 2 : 0);
        if (swShownRunning != state) {
            swShownRunning = state;
            swToggle.setImageResource(swRunning ? R.drawable.ic_pause : R.drawable.ic_play);
            int label = swRunning ? R.string.pause : (cur > 0 ? R.string.resume : R.string.start);
            swToggle.setContentDescription(getString(label));
            swToggleLabel.setText(label);
            swLapBtn.setAlpha(swRunning ? 1f : 0.45f);
            swLapLabel.setAlpha(swRunning ? 1f : 0.45f);
            // status pill: coloured dot + word
            int sub = ThemeHelper.resolve(this, R.attr.textSub);
            int dotColor = swRunning ? ThemeHelper.resolve(this, R.attr.goodC)
                    : (cur > 0 ? ThemeHelper.resolve(this, R.attr.accent2) : sub);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(dotColor);
            dot.setSize(dp(8), dp(8));
            swSub.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            swSub.setText(swRunning ? R.string.running : (cur > 0 ? R.string.st_paused : R.string.st_ready));
            Clay.inset(swSub, ThemeHelper.resolve(this, R.attr.surface2), -1);
        }
        // a run of a minute or more is saved to History by this button
        setIfChanged(swResetLabel, getString(cur >= 60_000L ? R.string.w_finish : R.string.reset));
    }

    private void toggleStopwatch() {
        if (swRunning) {
            swAccum += Math.max(0L, System.currentTimeMillis() - swBase);
            swRunning = false;
            PrefsStore.Sw sw = PrefsStore.loadSw(this);
            sw.pauses++;
            PrefsStore.saveSw(this, sw);
        } else {
            swBase = System.currentTimeMillis();
            swRunning = true;
        }
        saveStopwatch();
        tickStopwatch();
    }

    private void resetStopwatch() {
        // a stopwatch run of a minute or more is logged as a study session
        PrefsStore.Sw sw = PrefsStore.loadSw(this);
        long t = sw.current(System.currentTimeMillis());
        long before = System.currentTimeMillis();
        if (PrefsStore.logStopwatch(this, sw, System.currentTimeMillis(),
                getString(R.string.stopwatch))) {
            PrefsStore.Project p = projectOf(swProject);
            String msg = p != null
                    ? getString(R.string.logged_to, Projects.hours(t),
                            android.text.BidiFormatter.getInstance().unicodeWrap(p.name))
                    : getString(R.string.logged, Projects.hours(t));
            askNote(before, msg);
        }
        swRunning = false;
        swAccum = 0;
        laps.clear();
        saveStopwatch();
        refreshLaps();
        tickStopwatch();
    }

    private void addLap() {
        long total = swCurrent();
        if (total <= 0) {
            return;
        }
        laps.add(total);
        saveStopwatch();
        refreshLaps();
    }

    private void addDivider(LinearLayout box, int insetDp) {
        View div = new View(this);
        div.setBackgroundColor(ThemeHelper.resolve(this, R.attr.strokeC));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.round(1.5f * getResources().getDisplayMetrics().density));
        lp.setMarginStart(dp(insetDp));
        lp.setMarginEnd(dp(18));
        div.setLayoutParams(lp);
        box.addView(div);
    }

    private TextView tag(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        Clay.text(t, 700, 12, color);
        return t;
    }

    private void refreshLaps() {
        lapsBox.removeAllViews();
        lapsEmpty.setVisibility(laps.isEmpty() ? View.VISIBLE : View.GONE);
        lapsCard.setVisibility(laps.isEmpty() ? View.GONE : View.VISIBLE);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        int good = ThemeHelper.resolve(this, R.attr.goodC);
        int bad = ThemeHelper.resolve(this, R.attr.badC);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        long[] splits = new long[laps.size()];
        long prev = 0;
        int best = -1;
        int worst = -1;
        for (int i = 0; i < laps.size(); i++) {
            splits[i] = laps.get(i) - prev;
            prev = laps.get(i);
            if (best < 0 || splits[i] < splits[best]) {
                best = i;
            }
            if (worst < 0 || splits[i] > splits[worst]) {
                worst = i;
            }
        }
        if (laps.isEmpty()) {
            return;
        }
        // header: "Laps 3"  ...  "Best 0:12.30 \u00b7 Avg 0:14.10"
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPaddingRelative(dp(20), dp(8), dp(20), dp(8));
        TextView title = new TextView(this);
        title.setText(getString(R.string.laps_title, laps.size()));
        Clay.text(title, 900, 15, main);
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (laps.size() >= 2) {
            TextView avg = new TextView(this);
            avg.setText(getString(R.string.sw_best_avg, formatSw(splits[best]),
                    formatSw(laps.get(laps.size() - 1) / laps.size())));
            Clay.text(avg, 700, 12, sub);
            avg.setFontFeatureSettings("tnum");
            head.addView(avg);
        }
        lapsBox.addView(head);
        boolean mark = laps.size() >= 3;
        for (int i = laps.size() - 1; i >= 0; i--) {
            addDivider(lapsBox, 20);
            int tone = mark && i == best ? good : (mark && i == worst ? bad : accent);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(16), dp(10), dp(20), dp(10));
            // lap number in a small round badge
            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setGravity(Gravity.CENTER);
            Clay.text(num, 900, 13, tone == accent ? main : tone);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(Clay.mix(surface2, tone, 0.18f));
            num.setBackground(badge);
            row.addView(num, new LinearLayout.LayoutParams(dp(32), dp(32)));
            // split time with a bar showing how long it was next to the slowest lap
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPaddingRelative(dp(12), 0, dp(12), 0);
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView sp = new TextView(this);
            sp.setText(formatSw(splits[i]));
            Clay.text(sp, 800, 15, main);
            sp.setFontFeatureSettings("tnum");
            top.addView(sp);
            if (mark && (i == best || i == worst)) {
                TextView t = tag(getString(i == best ? R.string.fastest : R.string.slowest), tone);
                t.setPaddingRelative(dp(8), 0, 0, 0);
                top.addView(t);
            }
            mid.addView(top);
            FrameLayout track = new FrameLayout(this);
            GradientDrawable tg = new GradientDrawable();
            tg.setColor(surface2);
            tg.setCornerRadius(dp(3));
            track.setBackground(tg);
            View bar = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(tone);
            bg.setCornerRadius(dp(3));
            bar.setBackground(bg);
            track.addView(bar);
            final View fBar = bar;
            final float frac = Math.max(0.04f, splits[i] / (float) splits[worst]);
            track.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                        int ol, int ot, int or, int ob) {
                    int w = Math.round((r - l) * frac);
                    if (fBar.getLayoutParams().width != w) {
                        fBar.setLayoutParams(new FrameLayout.LayoutParams(w,
                                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));
                    }
                }
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
            tlp.topMargin = dp(6);
            mid.addView(track, tlp);
            row.addView(mid, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            // total time when the lap ended
            TextView tot = new TextView(this);
            tot.setText(formatSw(laps.get(i)));
            Clay.text(tot, 700, 13, sub);
            tot.setFontFeatureSettings("tnum");
            row.addView(tot);
            lapsBox.addView(row);
        }
    }

    /** Most recent sessions listed under the stats (older ones still count in totals). */
    private static final int RECENT_ROWS = 60;

    private void refreshHistory() {
        projects = PrefsStore.loadProjects(this);
        final List<PrefsStore.Hist> list = PrefsStore.loadHistory(this);
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        Projects.Stats all = Projects.stats(list, -1);
        int streak = Projects.streak(list);
        boolean noHistory = list.isEmpty();
        findViewById(R.id.histChartCard).setVisibility(noHistory ? View.GONE : View.VISIBLE);
        histHeatmap.setVisibility(noHistory ? View.GONE : View.VISIBLE);
        histBreakdown.setVisibility(noHistory ? View.GONE : View.VISIBLE);
        histToday.setText(Projects.hours(all.today));
        histWeek.setText(Projects.hours(all.week));
        histCount.setText(getResources().getQuantityString(R.plurals.days, streak, streak));

        // ---- stacked 7-day chart: one series per project (fixed order), then "no project"
        int n = projects.size();
        long[][] segs = new long[7][n + 1];
        long[] weekBy = new long[n + 1];
        long first = Projects.startOfToday() - 6 * Projects.DAY;
        for (PrefsStore.Hist h : list) {
            if (h.ts < first) {
                continue;
            }
            int day = (int) ((h.ts - first) / Projects.DAY);
            if (day < 0 || day > 6) {
                continue;
            }
            int k = n;
            for (int i = 0; i < n; i++) {
                if (projects.get(i).id == h.project) {
                    k = i;
                }
            }
            segs[day][k] += h.millis;
            weekBy[k] += h.millis;
        }
        int[] colors = new int[n + 1];
        for (int i = 0; i < n; i++) {
            colors[i] = Projects.color(this, projects.get(i).color);
        }
        colors[n] = ThemeHelper.resolve(this, R.attr.textSub);
        String[] days = new String[7];
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(first);
        SimpleDateFormat dn = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            days[i] = i == 6 ? getString(R.string.today) : dn.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        histChart.removeAllViews();
        BarChartView chart = new BarChartView(this);
        chart.setStacked(segs, colors, days);
        histChart.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // legend (>= 2 series): every series named next to its color, with its week total
        histLegend.removeAllViews();
        int series = 0;
        for (long v : weekBy) {
            if (v > 0) {
                series++;
            }
        }
        if (series >= 2) {
            LinearLayout row = null;
            int inRow = 0;
            for (int k = 0; k <= n; k++) {
                if (weekBy[k] <= 0) {
                    continue;
                }
                if (row == null || inRow == 2) {
                    row = new LinearLayout(this);
                    histLegend.addView(row);
                    inRow = 0;
                }
                TextView t = new TextView(this);
                String label = k < n ? projects.get(k).name : getString(R.string.no_project);
                // isolate the name so a Latin name doesn't pull the number to its side in RTL
                t.setText(android.text.BidiFormatter.getInstance().unicodeWrap(label) + "  "
                        + Projects.hours(weekBy[k]));
                Clay.text(t, 700, 12, sub);
                t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setCompoundDrawablesRelative(colorDot(colors[k], 8), null, null, null);
                t.setCompoundDrawablePadding(dp(6));
                t.setPaddingRelative(0, dp(3), dp(8), dp(3));
                row.addView(t, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                inRow++;
            }
        }

        // ---- this week by project, largest first, with share bars
        histBreakdown.removeAllViews();
        TextView bt = new TextView(this);
        bt.setText(R.string.this_week_by_project);
        Clay.text(bt, 800, 15, main);
        histBreakdown.addView(bt);
        long weekTotal = 0;
        for (long v : weekBy) {
            weekTotal += v;
        }
        List<Integer> order = new ArrayList<Integer>();
        for (int k = 0; k <= n; k++) {
            if (weekBy[k] > 0) {
                order.add(k);
            }
        }
        final long[] wb = weekBy;
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Long.compare(wb[b], wb[a]);
            }
        });
        if (order.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.no_study_this_week);
            Clay.text(none, 600, 14, sub);
            none.setPaddingRelative(0, dp(8), 0, 0);
            histBreakdown.addView(none);
        }
        for (int k : order) {
            String label = k < n ? projects.get(k).name : getString(R.string.no_project);
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPaddingRelative(0, dp(12), 0, dp(4));
            TextView nm = new TextView(this);
            nm.setText(label);
            Clay.text(nm, 800, 14, main);
            nm.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nm.setCompoundDrawablesRelative(colorDot(colors[k], 10), null, null, null);
            nm.setCompoundDrawablePadding(dp(8));
            r.addView(nm, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView hv = new TextView(this);
            String pct = java.text.NumberFormat.getPercentInstance().format(
                    weekBy[k] / (double) weekTotal);
            hv.setText(Projects.hours(weekBy[k]) + "  \u00b7  " + pct);
            Clay.text(hv, 700, 13, sub);
            hv.setFontFeatureSettings("tnum");
            r.addView(hv);
            histBreakdown.addView(r);
            LinearLayout track = new LinearLayout(this);
            track.setPaddingRelative(dp(2), dp(2), dp(2), dp(2));
            track.setWeightSum(1f);
            Clay.inset(track, ThemeHelper.resolve(this, R.attr.surface2), -1);
            View fill = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(colors[k]);
            g.setCornerRadius(dp(4));
            fill.setBackground(g);
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(6),
                    Math.max(0.03f, weekBy[k] / (float) weekTotal)));
            histBreakdown.addView(track, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
        }
        if (!order.isEmpty()) {
            int best = 0;
            for (int i = 1; i < 7; i++) {
                if (all.days[i] > all.days[best]) {
                    best = i;
                }
            }
            TextView bd = new TextView(this);
            bd.setText(getString(R.string.best_day, days[best], Projects.hours(all.days[best])));
            Clay.text(bd, 600, 13, sub);
            bd.setPaddingRelative(0, dp(14), 0, 0);
            histBreakdown.addView(bd);
        }
        // focus: this week's average and the part of the day that goes best
        int avg = Insights.averageScore(list, Projects.startOfWeek());
        if (avg >= 0) {
            TextView fs = new TextView(this);
            String txt = getString(R.string.focus_week, avg);
            int[] bestPart = Insights.bestPartOfDay(list);
            if (bestPart != null) {
                txt += "  \u00b7  " + getString(R.string.focus_best,
                        getString(Insights.PART_NAMES[bestPart[0]]), bestPart[1]);
            }
            fs.setText(txt);
            Clay.text(fs, 600, 13, sub);
            fs.setPaddingRelative(0, dp(6), 0, 0);
            histBreakdown.addView(fs);
        }
        refreshHeatmap(list, main, sub);
        refreshTags(list, main, sub);
        refreshBadges(list, main, sub);

        // ---- recent sessions; tap one to edit or delete it
        histBox.removeAllViews();
        histEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        histBox.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        btnClearHist.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        int shown = 0;
        for (final PrefsStore.Hist h : list) {
            if (shown++ >= RECENT_ROWS) {
                break;
            }
            if (shown > 1) {
                addDivider(histBox, 20);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(20), dp(14), dp(20), dp(14));
            row.setBackgroundResource(R.drawable.row_bg);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SessionDialog.show(MainActivity.this, h, new Runnable() {
                        @Override
                        public void run() {
                            refreshHistory();
                        }
                    });
                }
            });
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView nm = new TextView(this);
            nm.setText(h.name);
            Clay.text(nm, 800, 15, main);
            nm.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView t = new TextView(this);
            Clay.text(t, 600, 12, sub);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            PrefsStore.Project hp = projectOf(h.project);
            if (hp != null) {
                t.setText(hp.name + "  \u00b7  " + df.format(new Date(h.ts))
                        + scoreSuffix(h));
                t.setCompoundDrawablesRelative(colorDot(Projects.color(this, hp.color), 8),
                        null, null, null);
                t.setCompoundDrawablePadding(dp(6));
            } else {
                t.setText(df.format(new Date(h.ts)) + scoreSuffix(h));
            }
            mid.addView(nm);
            mid.addView(t);
            if (!h.note.isEmpty()) {
                mid.addView(noteLine(h.note, sub));
            }
            TextView d = new TextView(this);
            d.setText(Projects.hours(h.millis));
            Clay.text(d, 800, 15, main);
            d.setFontFeatureSettings("tnum");
            row.addView(mid);
            row.addView(d);
            histBox.addView(row);
        }
    }

    // ------------------------------------------------------------ year heatmap & tags

    /** Month shown in the History calendar (any moment in it); 0 = this month. */
    private long calMonth;

    private void refreshHeatmap(final List<PrefsStore.Hist> list, final int main, final int sub) {
        histHeatmap.removeAllViews();
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        int surface = ThemeHelper.resolve(this, R.attr.surface);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);

        final HeatmapView cal = new HeatmapView(this);
        cal.setColors(surface2, accent, surface, main, sub);
        cal.setMonth(calMonth == 0 ? System.currentTimeMillis() : calMonth, list);
        cal.setPaddingRelative(0, dp(12), 0, dp(4));

        // \u2039  October 2026  \u203a
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView prev = monthArrow(R.drawable.ic_chevron, main, surface2);
        prev.setRotation(180f);
        ImageView next = monthArrow(R.drawable.ic_chevron, main, surface2);
        TextView title = new TextView(this);
        title.setText(new java.text.SimpleDateFormat(android.text.format.DateFormat
                .getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy"), Locale.getDefault())
                .format(new Date(cal.monthStart())));
        Clay.text(title, 800, 16, main);
        title.setGravity(Gravity.CENTER);
        head.addView(prev, new LinearLayout.LayoutParams(dp(34), dp(34)));
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(next, new LinearLayout.LayoutParams(dp(34), dp(34)));
        boolean isCurrent = cal.monthStart() > System.currentTimeMillis() - 31 * Projects.DAY
                && cal.monthStart() <= System.currentTimeMillis();
        next.setAlpha(isCurrent ? 0.3f : 1f);
        next.setEnabled(!isCurrent);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calMonth = cal.monthStart() - Projects.DAY;
                refreshHeatmap(list, main, sub);
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long n = cal.monthStart() + 32 * Projects.DAY;
                calMonth = n > System.currentTimeMillis() ? 0 : n;
                refreshHeatmap(list, main, sub);
            }
        });
        histHeatmap.addView(head);

        TextView summary = new TextView(this);
        summary.setText(Projects.hours(cal.monthTotal()) + "  \u00b7  " + getResources()
                .getQuantityString(R.plurals.days_studied, cal.daysStudied(), cal.daysStudied()));
        Clay.text(summary, 700, 13, sub);
        summary.setGravity(Gravity.CENTER);
        summary.setPaddingRelative(0, dp(2), 0, 0);
        histHeatmap.addView(summary);
        histHeatmap.addView(cal);

        final TextView info = new TextView(this);
        Clay.text(info, 700, 13, main);
        info.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        final java.text.DateFormat df = new java.text.SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"),
                Locale.getDefault());
        info.setText(R.string.cal_tap_hint);
        cal.setListener(new HeatmapView.OnDayListener() {
            @Override
            public void onDay(long dayStart, long millis) {
                info.setText(millis < 60_000L
                        ? getString(R.string.day_none, df.format(new Date(dayStart)))
                        : getString(R.string.day_total, df.format(new Date(dayStart)),
                        Projects.hours(millis)));
            }
        });

        // tapped day on the left, "Less \u25aa\u25aa\u25aa\u25aa\u25aa More" on the right
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(info, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView less = new TextView(this);
        less.setText(R.string.less);
        Clay.text(less, 600, 11, sub);
        bottom.addView(less);
        int[] shades = {surface2, Clay.mix(surface, accent, 0.3f), Clay.mix(surface, accent, 0.55f),
                Clay.mix(surface, accent, 0.8f), accent};
        for (int s : shades) {
            View sq = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(s);
            g.setCornerRadius(dp(2));
            sq.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(10), dp(10));
            lp.setMarginStart(dp(3));
            bottom.addView(sq, lp);
        }
        TextView more = new TextView(this);
        more.setText(R.string.more);
        Clay.text(more, 600, 11, sub);
        more.setPaddingRelative(dp(4), 0, 0, 0);
        bottom.addView(more);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        histHeatmap.addView(bottom, blp);
    }

    private ImageView monthArrow(int icon, int color, int fill) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        Clay.inset(iv, fill, -1);
        return iv;
    }

    private void refreshTags(List<PrefsStore.Hist> list, int main, int sub) {
        histTags.removeAllViews();
        List<java.util.Map.Entry<String, Long>> tags = Tags.totals(list, Projects.startOfWeek());
        histTags.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
        if (tags.isEmpty()) {
            return;
        }
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        TextView title = new TextView(this);
        title.setText(R.string.tags_week);
        Clay.text(title, 800, 15, main);
        histTags.addView(title);
        long max = tags.get(0).getValue();
        int shown = 0;
        for (java.util.Map.Entry<String, Long> e : tags) {
            if (shown++ >= 8) {
                break;
            }
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPaddingRelative(0, dp(10), 0, dp(4));
            TextView name = new TextView(this);
            name.setText(e.getKey());
            Clay.text(name, 800, 14, main);
            name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            line.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = new TextView(this);
            t.setText(Projects.hours(e.getValue()));
            Clay.text(t, 700, 13, sub);
            line.addView(t);
            histTags.addView(line);
            LinearLayout track = new LinearLayout(this);
            track.setPaddingRelative(dp(2), dp(2), dp(2), dp(2));
            Clay.inset(track, surface2, -1);
            View fill = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(accent);
            g.setCornerRadius(dp(4));
            fill.setBackground(g);
            float frac = Math.max(0.04f, e.getValue() / (float) max);
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(5), frac));
            if (frac < 1f) {
                track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(5), 1f - frac));
            }
            histTags.addView(track);
        }
    }

    // ------------------------------------------------------------ sleep timer & shield

    private static final int[] SLEEP_CHOICES = {0, 15, 30, 60};

    private String sleepLabel(int min) {
        return min == 0 ? getString(R.string.music_after_off) : getString(R.string.min_n, min);
    }

    private void showSleepDialog() {
        String[] names = new String[SLEEP_CHOICES.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = sleepLabel(SLEEP_CHOICES[i]);
            if (SLEEP_CHOICES[i] == PrefsStore.getSleepMin(this)) {
                sel = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.music_after)
                .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        PrefsStore.setSleepMin(MainActivity.this, SLEEP_CHOICES[which]);
                        d.dismiss();
                        refreshFocusSettings();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the system screen for the first permission the shield still needs. */
    private boolean askShieldPermission() {
        if (!Shield.usageAllowed(this)) {
            Toast.makeText(this, R.string.shield_need_usage, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception e) {
                AlarmHelper.openAppSettings(this);
            }
            return true;
        }
        if (!Shield.overlayAllowed(this)) {
            Toast.makeText(this, R.string.shield_need_overlay, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                AlarmHelper.openAppSettings(this);
            }
            return true;
        }
        return false;
    }

    private void toggleShield() {
        // while it's on but still missing a permission, the button leads to that permission
        if (PrefsStore.getShield(this) && askShieldPermission()) {
            return;
        }
        boolean on = !PrefsStore.getShield(this);
        PrefsStore.setShield(this, on);
        if (on) {
            askShieldPermission();
        }
        refreshFocusSettings();
    }

    /** Which apps the shield guards (guarded ones listed first). */
    private void showShieldApps() {
        if (PrefsStore.getShield(this) && askShieldPermission()) {
            return;
        }
        final java.util.Set<String> chosen = new java.util.HashSet<String>(Shield.chosen(this));
        List<Shield.App> apps = Shield.launchable(this);
        final List<Shield.App> ordered = new ArrayList<Shield.App>();
        for (Shield.App a : apps) {
            if (chosen.contains(a.pkg)) {
                ordered.add(a);
            }
        }
        for (Shield.App a : apps) {
            if (!chosen.contains(a.pkg)) {
                ordered.add(a);
            }
        }
        String[] names = new String[ordered.size()];
        boolean[] checked = new boolean[ordered.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = ordered.get(i).label;
            checked[i] = chosen.contains(ordered.get(i).pkg);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.shield_apps)
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean isChecked) {
                        if (isChecked) {
                            chosen.add(ordered.get(which).pkg);
                        } else {
                            chosen.remove(ordered.get(which).pkg);
                        }
                    }
                })
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        PrefsStore.setShieldApps(MainActivity.this, chosen);
                        refreshFocusSettings();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshShieldRow() {
        boolean on = PrefsStore.getShield(this);
        String status;
        if (!on) {
            status = getString(R.string.shield_off_sub);
        } else if (!Shield.usageAllowed(this)) {
            status = getString(R.string.shield_need_usage);
        } else if (!Shield.overlayAllowed(this)) {
            status = getString(R.string.shield_need_overlay);
        } else {
            int n = Shield.chosen(this).size();
            status = getResources().getQuantityString(R.plurals.apps_guarded, n, n);
        }
        shieldStatus.setText(status);
        shieldToggle.setText(on ? R.string.on : R.string.off);
        if (on) {
            Clay.style(shieldToggle, ThemeHelper.resolve(this, R.attr.accent), -1, 3, false);
            shieldToggle.setTextColor(0xFFFFFFFF);
        } else {
            Clay.inset(shieldToggle, ThemeHelper.resolve(this, R.attr.surface2), -1);
            shieldToggle.setTextColor(ThemeHelper.resolve(this, R.attr.textSub));
        }
        sleepName.setText(sleepLabel(PrefsStore.getSleepMin(this)));
    }

    /** "  \u00b7  Focus 90" after a session's date, when it has a score. */
    private String scoreSuffix(PrefsStore.Hist h) {
        return h.score < 0 ? "" : "  \u00b7  " + getString(R.string.focus_n, h.score);
    }

    /** The achievements card: every badge, earned ones in colour, others with progress. */
    private void refreshBadges(List<PrefsStore.Hist> list, int main, int sub) {
        histBadges.removeAllViews();
        final int[] progress = Achievements.progress(list);
        int earned = 0;
        for (int i = 0; i < progress.length; i++) {
            if (progress[i] >= Achievements.ALL[i].target) {
                earned++;
            }
        }
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPaddingRelative(dp(4), 0, dp(4), dp(10));
        TextView title = new TextView(this);
        title.setText(R.string.achievements);
        Clay.text(title, 800, 15, main);
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = new TextView(this);
        count.setText(getString(R.string.n_of_m, earned, Achievements.ALL.length));
        Clay.text(count, 700, 13, sub);
        head.addView(count);
        histBadges.addView(head);

        int surface2 = ThemeHelper.resolve(this, R.attr.surface2);
        // earned badges wear the theme's accent colour
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        LinearLayout row = null;
        for (int i = 0; i < Achievements.ALL.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setPaddingRelative(0, dp(4), 0, dp(4));
                histBadges.addView(row);
            }
            final Achievements.Badge b = Achievements.ALL[i];
            final boolean got = progress[i] >= b.target;
            final int value = progress[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPaddingRelative(dp(2), dp(4), dp(2), dp(6));
            cell.setBackgroundResource(R.drawable.row_bg);
            ImageView icon = new ImageView(this);
            icon.setImageResource(b.icon);
            icon.setPadding(dp(13), dp(13), dp(13), dp(13));
            if (got) {
                Clay.style(icon, accent, -1, 4, false);
                icon.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            } else {
                Clay.inset(icon, surface2, -1);
                icon.setImageTintList(ColorStateList.valueOf(sub));
                icon.setImageAlpha(110);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(50), dp(50));
            ilp.bottomMargin = dp(6);
            cell.addView(icon, ilp);
            TextView name = new TextView(this);
            name.setText(b.name);
            Clay.text(name, 800, 11, got ? main : sub);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            cell.addView(name);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(b.name)
                            .setMessage(getString(b.desc) + "\n\n" + (got
                                    ? getString(R.string.unlocked)
                                    : Achievements.progressText(MainActivity.this, b, value)))
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }
            });
            row.addView(cell, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        // keep the last row's cells the same width as the others
        while (row != null && row.getChildCount() < 4) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    /** A session's note, quoted under its date. */
    private TextView noteLine(String note, int color) {
        TextView n = new TextView(this);
        n.setText(android.text.BidiFormatter.getInstance().unicodeWrap(note));
        Clay.text(n, 600, 13, color);
        n.setTypeface(n.getTypeface(), android.graphics.Typeface.ITALIC);
        n.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setPaddingRelative(0, dp(3), 0, 0);
        return n;
    }

    private void addSession() {
        SessionDialog.show(this, null, new Runnable() {
            @Override
            public void run() {
                refreshHistory();
            }
        });
    }

    // ------------------------------------------------------------ stopwatch project

    private void buildSwProjects() {
        swProjRow.removeAllViews();
        projects = PrefsStore.loadProjects(this);
        swProjScroll.setVisibility(projects.isEmpty() ? View.GONE : View.VISIBLE);
        if (projectOf(swProject) == null) {
            swProject = 0;
        }
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        for (int i = -1; i < projects.size(); i++) {
            final long id = i < 0 ? 0 : projects.get(i).id;
            boolean sel = id == swProject;
            TextView c = new TextView(this);
            c.setText(i < 0 ? getString(R.string.no_project) : projects.get(i).name);
            Clay.text(c, 800, 13, sel ? main : sub);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPaddingRelative(dp(12), dp(7), dp(14), dp(8));
            if (i >= 0) {
                c.setCompoundDrawablesRelative(colorDot(Projects.color(this,
                        projects.get(i).color), 10), null, null, null);
                c.setCompoundDrawablePadding(dp(7));
            }
            if (sel) {
                Clay.inset(c, ThemeHelper.resolve(this, R.attr.surface2), -1);
            } else {
                c.setBackground(null);
            }
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    swProject = id;
                    saveStopwatch();
                    buildSwProjects();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(4));
            swProjRow.addView(c, lp);
        }
    }

    // ------------------------------------------------------------ focus settings

    private void toggleDnd() {
        if (!Focus.dndAllowed(this)) {
            Toast.makeText(this, R.string.dnd_grant, Toast.LENGTH_LONG).show();
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Exception e) {
                AlarmHelper.openAppSettings(this);
            }
            return;
        }
        PrefsStore.setDnd(this, !PrefsStore.getDnd(this));
        Focus.sync(this);
        refreshSettings();
    }

    private void showFocusSoundDialog() {
        // listed in Focus.ORDER (grouped by kind), saved by sound id
        String[] names = new String[Focus.ORDER.length];
        int checked = 0;
        final int[] pick = {PrefsStore.getFocusSound(this)};
        for (int i = 0; i < names.length; i++) {
            names[i] = Focus.name(this, Focus.ORDER[i]);
            if (Focus.ORDER[i] == pick[0]) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.focus_sound)
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pick[0] = Focus.ORDER[which];
                        Focus.preview(MainActivity.this, pick[0]);
                    }
                })
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        PrefsStore.setFocusSound(MainActivity.this, pick[0]);
                        Focus.preview(MainActivity.this, Focus.SOUND_OFF);
                        refreshSettings();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Focus.preview(MainActivity.this, Focus.SOUND_OFF);
                    }
                })
                .show();
    }

    private int currentLangIndex() {
        String cur = App.currentLang(this);
        for (int i = 1; i < App.LANGS.length; i++) {
            if (App.LANGS[i].equalsIgnoreCase(cur)
                    || (!cur.isEmpty() && App.LANGS[i].startsWith(cur.split("-")[0])
                    && !App.LANGS[i].contains("-"))) {
                return i;
            }
        }
        return 0;
    }

    private void showLanguageDialog() {
        String[] names = new String[App.LANGS.length];
        names[0] = getString(R.string.lang_system);
        for (int i = 1; i < names.length; i++) {
            names[i] = App.LANG_NAMES[i];
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(names, currentLangIndex(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                App.setLanguage(MainActivity.this, App.LANGS[which]);
                                if (Build.VERSION.SDK_INT < 33) {
                                    recreate();
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshFocusSettings() {
        refreshShieldRow();
        boolean weekly = PrefsStore.getWeeklyReport(this);
        weeklyToggle.setText(weekly ? R.string.on : R.string.off);
        if (weekly) {
            Clay.style(weeklyToggle, ThemeHelper.resolve(this, R.attr.accent), -1, 3, false);
            weeklyToggle.setTextColor(0xFFFFFFFF);
        } else {
            Clay.inset(weeklyToggle, ThemeHelper.resolve(this, R.attr.surface2), -1);
            weeklyToggle.setTextColor(ThemeHelper.resolve(this, R.attr.textSub));
        }
        long lb = PrefsStore.getLastBackup(this);
        backupSub.setText(lb <= 0 ? getString(R.string.backup_sub)
                : getString(R.string.backup_last, java.text.DateFormat.getDateInstance(
                        java.text.DateFormat.MEDIUM).format(new Date(lb))));
        int li = currentLangIndex();
        langName.setText(li == 0 ? getString(R.string.lang_system) : App.LANG_NAMES[li]);
        boolean allowed = Focus.dndAllowed(this);
        boolean on = allowed && PrefsStore.getDnd(this);
        dndStatus.setText(!allowed ? R.string.dnd_needs_access
                : on ? R.string.dnd_on : R.string.dnd_off);
        dndToggle.setText(on ? R.string.on : R.string.off);
        if (on) {
            Clay.style(dndToggle, ThemeHelper.resolve(this, R.attr.accent), -1, 3, false);
            dndToggle.setTextColor(0xFFFFFFFF);
        } else {
            Clay.inset(dndToggle, ThemeHelper.resolve(this, R.attr.surface2), -1);
            dndToggle.setTextColor(ThemeHelper.resolve(this, R.attr.textSub));
        }
        focusSoundName.setText(Focus.name(this, PrefsStore.getFocusSound(this)));
        boolean strict = PrefsStore.getStrict(this);
        strictStatus.setText(strict ? R.string.strict_on_sub : R.string.strict_off_sub);
        strictToggle.setText(strict ? R.string.on : R.string.off);
        if (strict) {
            Clay.style(strictToggle, ThemeHelper.resolve(this, R.attr.accent), -1, 3, false);
            strictToggle.setTextColor(0xFFFFFFFF);
        } else {
            Clay.inset(strictToggle, ThemeHelper.resolve(this, R.attr.surface2), -1);
            strictToggle.setTextColor(ThemeHelper.resolve(this, R.attr.textSub));
        }
    }

    /** Strict mode can't be switched off in the middle of a strict session. */
    private void toggleStrict() {
        boolean on = PrefsStore.getStrict(this);
        if (on && TimerOps.anyStrictLocked(this)) {
            Toast.makeText(this, R.string.strict_locked_setting, Toast.LENGTH_LONG).show();
            return;
        }
        PrefsStore.setStrict(this, !on);
        LiveNotifier.update(this);
        refreshSettings();
    }

    private void buildAccentRow() {
        accentRow.removeAllViews();
        for (int i = 0; i < Clay.ACCENTS.length; i++) {
            final int acc = i;
            ImageView dot = new ImageView(this);
            dot.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            dot.setPaddingRelative(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
            lp.setMarginStart(dp(8));
            dot.setLayoutParams(lp);
            Clay.style(dot, Clay.ACCENTS[i], -1, 2, true);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (PrefsStore.getAcc(MainActivity.this) != acc) {
                        PrefsStore.setAcc(MainActivity.this, acc);
                        recreate();
                    }
                }
            });
            accentRow.addView(dot);
        }
    }

    private void refreshSettings() {
        refreshFocusSettings();
        int mode = PrefsStore.getMode(this);
        int acc = PrefsStore.getAcc(this);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        boolean light = mode == ThemeHelper.MODE_LIGHT;
        TextView[] segs = {segLight, segDark};
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 0) == light;
            if (sel) {
                Clay.style(segs[i], ThemeHelper.resolve(this, R.attr.surface), -1, 2, false);
            } else {
                segs[i].setBackground(null);
                segs[i].setElevation(0);
            }
            segs[i].setTextColor(sel ? ThemeHelper.resolve(this, R.attr.textMain) : sub);
        }
        for (int i = 0; i < accentRow.getChildCount(); i++) {
            ImageView dot = (ImageView) accentRow.getChildAt(i);
            boolean sel = i == acc;
            dot.setImageResource(sel ? R.drawable.ic_check : 0);
            dot.setScaleX(sel ? 1.18f : 1f);
            dot.setScaleY(sel ? 1.18f : 1f);
        }
        String sn = PrefsStore.getSoundName(this);
        soundName.setText(sn.isEmpty() ? getString(R.string.system_default) : sn);
        android.app.NotificationManager nmgr =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean fullScreenOk = Build.VERSION.SDK_INT < 34 || nmgr == null
                || nmgr.canUseFullScreenIntent();
        if (AlarmHelper.canSchedule(this) && fullScreenOk) {
            alarmStatus.setText(R.string.alarms_on);
            btnAlarmPerm.setVisibility(View.GONE);
        } else if (AlarmHelper.canSchedule(this)) {
            alarmStatus.setText(R.string.alarms_fullscreen);
            btnAlarmPerm.setText(R.string.allow);
            btnAlarmPerm.setVisibility(View.VISIBLE);
        } else {
            alarmStatus.setText(R.string.alarms_off);
            btnAlarmPerm.setText(R.string.enable);
            btnAlarmPerm.setVisibility(View.VISIBLE);
        }
        if (AlarmHelper.hasNotifPermission(this)) {
            notifStatus.setText(R.string.on);
            btnNotifPerm.setVisibility(View.GONE);
        } else {
            notifStatus.setText(R.string.notif_off);
            btnNotifPerm.setVisibility(View.VISIBLE);
        }
    }

    private void showSoundDialog() {
        stopPreview();
        final List<String> names = new ArrayList<String>();
        final List<String> uris = new ArrayList<String>();
        names.add(getString(R.string.system_default));
        uris.add("");
        try {
            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(RingtoneManager.TYPE_ALARM);
            Cursor cur = rm.getCursor();
            while (cur.moveToNext() && names.size() < 31) {
                names.add(cur.getString(RingtoneManager.TITLE_COLUMN_INDEX));
                uris.add(rm.getRingtoneUri(cur.getPosition()).toString());
            }
        } catch (Exception ignored) {
        }
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.alarm_sound)
                .setItems(names.toArray(new String[0]),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                PrefsStore.setSound(MainActivity.this,
                                        uris.get(which), which == 0 ? "" : names.get(which));
                                NotificationHelper.applySound(MainActivity.this);
                                playPreview(uris.get(which));
                                refreshSettings();
                            }
                        })
                .create();
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                stopPreview();
            }
        });
        dlg.show();
    }

    private void playPreview(String uriStr) {
        stopPreview();
        try {
            Uri u = (uriStr == null || uriStr.length() == 0)
                    ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    : Uri.parse(uriStr);
            previewRingtone = RingtoneManager.getRingtone(this, u);
            if (previewRingtone != null) {
                previewRingtone.play();
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPreview() {
        if (previewRingtone != null) {
            try {
                previewRingtone.stop();
            } catch (Exception ignored) {
            }
            previewRingtone = null;
        }
    }
}
