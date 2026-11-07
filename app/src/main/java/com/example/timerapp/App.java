package com.example.timerapp;

import android.app.Application;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/** Holds the app context (for localized text in static helpers) and the language choice. */
public class App extends Application {

    /** Language tags offered in Settings, "" = follow the phone. */
    public static final String[] LANGS = {"", "en", "fa", "tr", "ar", "de", "es", "zh-CN",
            "ko", "pt", "fr"};
    /** Each language's name in its own language (shown that way in every UI language). */
    public static final String[] LANG_NAMES = {null, "English", "\u0641\u0627\u0631\u0633\u06cc",
            "T\u00fcrk\u00e7e", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629", "Deutsch",
            "Espa\u00f1ol", "\u7b80\u4f53\u4e2d\u6587", "\ud55c\uad6d\uc5b4", "Portugu\u00eas",
            "Fran\u00e7ais"};

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        WeeklyReport.schedule(this);
        Achievements.baseline(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(wrap(base));
    }

    /** Localized resources for code that has no Context at hand. */
    public static Context ctx() {
        return instance;
    }

    public static String str(int id, Object... args) {
        return instance.getString(id, args);
    }

    /** Applies the saved language on Android 12 and older (13+ uses the system setting). */
    public static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) {
            return base;
        }
        String tag = PrefsStore.getLang(base);
        if (tag.isEmpty()) {
            return base;
        }
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        Locale l = Locale.forLanguageTag(tag);
        Locale.setDefault(l);
        conf.setLocale(l);
        conf.setLayoutDirection(l);
        return base.createConfigurationContext(conf);
    }

    /** Switches the app language; activities restart in the new language. */
    public static void setLanguage(Context c, String tag) {
        PrefsStore.setLang(c, tag);
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = c.getSystemService(LocaleManager.class);
            if (lm != null) {
                lm.setApplicationLocales(tag.isEmpty() ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(tag));
            }
        }
    }

    /** The language currently in effect (from the system per-app setting on 13+). */
    public static String currentLang(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = c.getSystemService(LocaleManager.class);
            if (lm != null) {
                LocaleList l = lm.getApplicationLocales();
                return l.isEmpty() ? "" : l.get(0).toLanguageTag();
            }
        }
        return PrefsStore.getLang(c);
    }
}
