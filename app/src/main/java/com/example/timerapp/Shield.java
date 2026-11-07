package com.example.timerapp;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Distraction shield: while focusing, opening one of the chosen apps brings up a
 * "back to focus?" screen. Checked every second by LiveService (which runs while
 * something is running).
 */
public final class Shield {

    /** Picked by default (when installed) the first time the shield is set up. */
    private static final String[] DEFAULT_APPS = {"com.instagram.android", "com.whatsapp",
            "com.google.android.youtube", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "org.telegram.messenger", "com.twitter.android", "com.snapchat.android",
            "com.pinterest", "com.facebook.katana", "com.reddit.frontpage",
            "com.supercell.clashroyale"};
    private static final String CHANNEL = "focus_shield";
    private static final long ALLOW_MS = 5 * 60_000L;

    private static String foreground;
    private static long lastQuery;
    private static long lastShown;
    private static final Map<String, Long> ALLOWED = new HashMap<String, Long>();

    private Shield() {
    }

    // ------------------------------------------------------------------ permissions

    public static boolean usageAllowed(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            return false;
        }
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean overlayAllowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    // ------------------------------------------------------------------ apps

    /** An app the shield can guard. */
    public static final class App {
        public final String pkg;
        public final String label;

        App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    /** Apps with a launcher icon (except this one), by name. */
    public static List<App> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<String>();
        List<App> out = new ArrayList<App>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            out.add(new App(pkg, String.valueOf(ri.loadLabel(pm))));
        }
        Collections.sort(out, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /** The guarded apps: the user's choice, or the installed defaults. */
    public static Set<String> chosen(Context c) {
        Set<String> s = PrefsStore.getShieldApps(c);
        if (s != null) {
            return s;
        }
        Set<String> out = new HashSet<String>();
        PackageManager pm = c.getPackageManager();
        for (String p : DEFAULT_APPS) {
            if (pm.getLaunchIntentForPackage(p) != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** "Use it for 5 minutes": lets this app through for a while. */
    public static void allow(String pkg) {
        ALLOWED.put(pkg, System.currentTimeMillis() + ALLOW_MS);
    }

    // ------------------------------------------------------------------ the check

    /** The app in front right now, from recent usage events (null if unknown). */
    private static String foregroundApp(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return foreground;
        }
        long now = System.currentTimeMillis();
        long from = lastQuery > 0 ? Math.min(lastQuery, now - 2000) : now - 60_000L;
        lastQuery = now;
        UsageEvents ev = usm.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e);
            // 1 = an activity came to the front (MOVE_TO_FOREGROUND / ACTIVITY_RESUMED)
            if (e.getEventType() == 1) {
                foreground = e.getPackageName();
            }
        }
        return foreground;
    }

    /** Called every second while something runs: shields a guarded app during focus. */
    public static void check(Context c) {
        try {
            if (!PrefsStore.getShield(c) || !usageAllowed(c)) {
                return;
            }
            String pkg = foregroundApp(c);
            if (pkg == null || pkg.equals(c.getPackageName()) || !Focus.focusing(c)
                    || !chosen(c).contains(pkg)) {
                return;
            }
            Long until = ALLOWED.get(pkg);
            long now = System.currentTimeMillis();
            if ((until != null && until > now) || now - lastShown < 4000) {
                return;
            }
            lastShown = now;
            show(c, pkg);
        } catch (Exception ignored) {
        }
    }

    private static void show(Context c, String pkg) {
        Intent i = new Intent(c, ShieldActivity.class);
        i.putExtra("pkg", pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // a heads-up notification too, in case the phone doesn't let the screen open
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    c.getString(R.string.shield_title), NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
            PendingIntent pi = PendingIntent.getActivity(c, 0x7400, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_timer)
                    .setColor(Clay.accentColor(c))
                    .setContentTitle(c.getString(R.string.shield_q))
                    .setContentText(c.getString(R.string.shield_can_wait, label(c, pkg)))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setTimeoutAfter(15_000L)
                    .build();
            nm.notify("shield", 1, n);
        }
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    public static void clearNotice(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel("shield", 1);
        }
    }
}
