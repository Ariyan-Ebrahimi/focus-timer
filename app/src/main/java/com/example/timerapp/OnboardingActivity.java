package com.example.timerapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The welcome tour for new users: says hello, shows what the app can do and how to use
 * each part, then lets them pick a look and start their first focus. Swipe or use
 * Next / Back; Skip ends it any time. It can be replayed from Settings.
 */
public class OnboardingActivity extends Activity {

    static final String EXTRA_START_FOCUS = "start_focus";
    static final String EXTRA_RECREATE = "recreate";
    private static final int PAGES = 6;

    private int page;
    private boolean changedLook;
    private FrameLayout stage;
    private LinearLayout dots;
    private TextView back;
    private TextView next;
    private TextView skip;
    private int main;
    private int sub;
    private int surface;
    private int surface2;
    private int accent;
    private GestureDetector swipe;

    public static Intent intent(Context c) {
        return new Intent(c, OnboardingActivity.class);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(App.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        setTheme(ThemeHelper.themeRes(PrefsStore.getMode(this), PrefsStore.getAcc(this)));
        super.onCreate(state);
        if (state != null) {
            page = state.getInt("page", 0);
            changedLook = state.getBoolean("changed", false);
        }
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);
        root.setClipChildren(false);

        // Skip, top end
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        top.setPaddingRelative(dp(20), dp(12), dp(16), 0);
        skip = new TextView(this);
        skip.setText(R.string.tour_skip);
        Clay.text(skip, 800, 15, sub);
        skip.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));
        top.addView(skip);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        // Back ............ Next
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        nav.setPaddingRelative(dp(24), dp(10), dp(24), dp(24));
        back = new TextView(this);
        back.setText(R.string.tour_back);
        back.setGravity(Gravity.CENTER);
        Clay.text(back, 800, 16, main);
        Clay.style(back, surface, -1, 4, true);
        nav.addView(back, new LinearLayout.LayoutParams(0, dp(56), 1f));
        View gap = new View(this);
        nav.addView(gap, new LinearLayout.LayoutParams(dp(14), 1));
        next = new TextView(this);
        next.setGravity(Gravity.CENTER);
        Clay.text(next, 900, 17, 0xFFFFFFFF);
        Clay.style(next, accent, -1, 6, true);
        nav.addView(next, new LinearLayout.LayoutParams(0, dp(56), 1.6f));
        root.addView(nav);
        setContentView(root);

        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                done(false);
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go(page - 1);
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page == PAGES - 1) {
                    done(false);
                } else {
                    go(page + 1);
                }
            }
        });
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(vx) < 600) {
                    return false;
                }
                boolean rtl = getWindow().getDecorView().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL;
                boolean forward = rtl ? vx > 0 : vx < 0;
                go(page + (forward ? 1 : -1));
                return true;
            }
        });
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
        show(page, 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipe != null) {
            swipe.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
        out.putBoolean("changed", changedLook);
    }

    @Override
    public void onBackPressed() {
        if (page > 0) {
            go(page - 1);
        } else {
            done(false);
        }
    }

    private void go(int to) {
        if (to < 0 || to >= PAGES || to == page) {
            return;
        }
        int dir = to > page ? 1 : -1;
        page = to;
        show(page, dir);
    }

    /** Ends the tour; the main screen can start a focus or reload a new look. */
    private void done(boolean startFocus) {
        Intent r = new Intent();
        r.putExtra(EXTRA_START_FOCUS, startFocus);
        r.putExtra(EXTRA_RECREATE, changedLook);
        setResult(RESULT_OK, r);
        finish();
    }

    // ------------------------------------------------------------------ pages

    private void show(int p, int dir) {
        View v = build(p);
        final View old = stage.getChildCount() > 0 ? stage.getChildAt(0) : null;
        stage.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (dir != 0) {
            boolean rtl = stage.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            float shift = dp(60) * dir * (rtl ? -1 : 1);
            v.setAlpha(0f);
            v.setTranslationX(shift);
            v.animate().alpha(1f).translationX(0).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
            if (old != null) {
                old.animate().alpha(0f).translationX(-shift).setDuration(200)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                stage.removeView(old);
                            }
                        }).start();
            }
        } else if (old != null) {
            stage.removeView(old);
        }
        // dots
        dots.removeAllViews();
        for (int i = 0; i < PAGES; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4));
            g.setColor(i == p ? accent : Clay.mix(surface2, sub, 0.35f));
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(i == p ? 22 : 8), dp(8));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(d, lp);
        }
        back.setVisibility(p == 0 ? View.INVISIBLE : View.VISIBLE);
        skip.setVisibility(p == PAGES - 1 ? View.INVISIBLE : View.VISIBLE);
        next.setText(p == 0 ? R.string.tour_start : p == PAGES - 1 ? R.string.tour_explore
                : R.string.tour_next);
    }

    private View build(int p) {
        switch (p) {
            case 0:
                return hello();
            case 1:
                return feature(R.drawable.ic_tab_timer, R.string.tour_t1, R.string.tour_d1,
                        new int[]{R.string.tour_s1a, R.string.tour_s1b, R.string.tour_s1c});
            case 2:
                return feature(R.drawable.ic_tab_proj, R.string.tour_t2, R.string.tour_d2,
                        new int[]{R.string.tour_s2a, R.string.tour_s2b, R.string.tour_s2c});
            case 3:
                return feature(R.drawable.ic_music, R.string.tour_t3, R.string.tour_d3,
                        new int[]{R.string.tour_s3a, R.string.tour_s3b, R.string.tour_s3c});
            case 4:
                return feature(R.drawable.ic_alarm, R.string.tour_t4, R.string.tour_d4,
                        new int[]{R.string.tour_s4a, R.string.tour_s4b, R.string.tour_s4c});
            default:
                return ready();
        }
    }

    private ScrollView scrollPage(LinearLayout body) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(false);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPaddingRelative(dp(28), dp(8), dp(28), dp(16));
        s.addView(body);
        return s;
    }

    private TextView text(CharSequence s, int weight, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        Clay.text(t, weight, sp, color);
        return t;
    }

    /** A clay disc with the page's icon, floating gently. */
    private View art(int icon) {
        FrameLayout disc = new FrameLayout(this);
        disc.setClipChildren(false);
        Clay.style(disc, surface, -1, 8, false);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(accent));
        disc.addView(iv, new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
        ObjectAnimator f = ObjectAnimator.ofFloat(disc, View.TRANSLATION_Y, -dp(5), dp(5));
        f.setDuration(1800);
        f.setRepeatCount(ValueAnimator.INFINITE);
        f.setRepeatMode(ValueAnimator.REVERSE);
        f.start();
        return disc;
    }

    private View hello() {
        LinearLayout b = new LinearLayout(this);
        ScrollView s = scrollPage(b);
        b.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(120), dp(120));
        b.addView(logo, ll);
        ObjectAnimator f = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, -dp(5), dp(5));
        f.setDuration(1800);
        f.setRepeatCount(ValueAnimator.INFINITE);
        f.setRepeatMode(ValueAnimator.REVERSE);
        f.start();
        TextView hi = text(getString(R.string.tour_hello), 900, 34, main);
        hi.setPaddingRelative(0, dp(24), 0, 0);
        b.addView(hi);
        TextView w = text(getString(R.string.tour_hello_sub), 600, 17, sub);
        w.setPaddingRelative(0, dp(10), 0, 0);
        b.addView(w);
        // what's inside, as small chips
        int[] chips = {R.string.tour_c1, R.string.tour_c2, R.string.tour_c3, R.string.tour_c4,
                R.string.tour_c5, R.string.tour_c6};
        LinearLayout row = null;
        for (int i = 0; i < chips.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rl.topMargin = dp(i == 0 ? 26 : 8);
                b.addView(row, rl);
            }
            TextView c = text(getString(chips[i]), 800, 13, main);
            c.setSingleLine(true);
            c.setPaddingRelative(dp(12), dp(7), dp(12), dp(7));
            Clay.inset(c, surface2, -1);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.setMargins(dp(4), 0, dp(4), 0);
            row.addView(c, cl);
        }
        // language, so people can switch before reading on
        TextView lang = text("", 800, 15, accent);
        int li = langIndex();
        lang.setText(getString(R.string.language) + ": "
                + (li == 0 ? getString(R.string.lang_system) : App.LANG_NAMES[li]) + "  \u203a");
        lang.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_globe, 0, 0, 0);
        lang.setCompoundDrawableTintList(ColorStateList.valueOf(accent));
        lang.setCompoundDrawablePadding(dp(8));
        lang.setPaddingRelative(dp(18), dp(10), dp(18), dp(10));
        Clay.style(lang, surface, -1, 3, true);
        LinearLayout.LayoutParams lgl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lgl.topMargin = dp(28);
        b.addView(lang, lgl);
        lang.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLanguage();
            }
        });
        return s;
    }

    /** Title, a short line, then numbered "how to" steps. */
    private View feature(int icon, int title, int desc, int[] steps) {
        LinearLayout b = new LinearLayout(this);
        ScrollView s = scrollPage(b);
        b.addView(art(icon), new LinearLayout.LayoutParams(dp(150), dp(150)));
        TextView t = text(getString(title), 900, 28, main);
        t.setPaddingRelative(0, dp(26), 0, 0);
        b.addView(t);
        TextView d = text(getString(desc), 600, 16, sub);
        d.setPaddingRelative(0, dp(8), 0, 0);
        b.addView(d);
        TextView how = text(getString(R.string.tour_how), 800, 13, accent);
        how.setPaddingRelative(0, dp(24), 0, dp(6));
        b.addView(how);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPaddingRelative(dp(18), dp(12), dp(18), dp(12));
        Clay.style(card, surface, 24, 5, false);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(0, dp(8), 0, dp(8));
            TextView n = text(String.valueOf(i + 1), 900, 14, 0xFFFFFFFF);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(accent);
            n.setBackground(g);
            row.addView(n, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView st = text(getString(steps[i]), 700, 15, main);
            st.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            st.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            st.setPaddingRelative(dp(14), 0, 0, 0);
            row.addView(st, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }
        b.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    /** Pick a look, then start the first focus or look around. */
    private View ready() {
        LinearLayout b = new LinearLayout(this);
        ScrollView s = scrollPage(b);
        b.addView(art(R.drawable.ic_star), new LinearLayout.LayoutParams(dp(130), dp(130)));
        TextView t = text(getString(R.string.tour_t5), 900, 28, main);
        t.setPaddingRelative(0, dp(22), 0, 0);
        b.addView(t);
        TextView d = text(getString(R.string.tour_d5), 600, 16, sub);
        d.setPaddingRelative(0, dp(8), 0, 0);
        b.addView(d);

        // look: light / dark, and the four colors
        TextView lookLabel = text(getString(R.string.tour_look), 800, 13, accent);
        lookLabel.setPaddingRelative(0, dp(22), 0, dp(8));
        b.addView(lookLabel);
        LinearLayout look = new LinearLayout(this);
        look.setGravity(Gravity.CENTER_VERTICAL);
        look.setPaddingRelative(dp(16), dp(12), dp(16), dp(12));
        Clay.style(look, surface, 24, 5, false);
        final int mode = PrefsStore.getMode(this);
        String[] modes = {getString(R.string.light), getString(R.string.dark)};
        int[] modeIds = {ThemeHelper.MODE_LIGHT, ThemeHelper.MODE_DARK};
        for (int i = 0; i < 2; i++) {
            final int m = modeIds[i];
            TextView mt = text(modes[i], 800, 14, m == mode ? 0xFFFFFFFF : main);
            mt.setPaddingRelative(dp(14), dp(8), dp(14), dp(8));
            if (m == mode) {
                Clay.style(mt, accent, -1, 2, false);
            } else {
                Clay.inset(mt, surface2, -1);
            }
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.setMarginEnd(dp(6));
            look.addView(mt, ml);
            mt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (PrefsStore.getMode(OnboardingActivity.this) != m) {
                        PrefsStore.setMode(OnboardingActivity.this, m);
                        changedLook = true;
                        recreate();
                    }
                }
            });
        }
        look.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        int acc = PrefsStore.getAcc(this);
        for (int i = 0; i < Clay.ACCENTS.length; i++) {
            final int a = i;
            ImageView dot = new ImageView(this);
            if (i == acc) {
                dot.setImageResource(R.drawable.ic_check);
            }
            dot.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            dot.setPaddingRelative(dp(6), dp(6), dp(6), dp(6));
            Clay.style(dot, Clay.ACCENTS[i], -1, 2, true);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(28), dp(28));
            dl.setMarginStart(dp(6));
            look.addView(dot, dl);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (PrefsStore.getAcc(OnboardingActivity.this) != a) {
                        PrefsStore.setAcc(OnboardingActivity.this, a);
                        changedLook = true;
                        recreate();
                    }
                }
            });
        }
        b.addView(look, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // the first focus, right now
        TextView go = text(getString(R.string.start_focus_n, PrefsStore.getPomoFocus(this)),
                900, 17, 0xFFFFFFFF);
        go.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        go.setCompoundDrawableTintList(ColorStateList.valueOf(0xFFFFFFFF));
        go.setCompoundDrawablePadding(dp(8));
        go.setPaddingRelative(dp(24), 0, dp(28), 0);
        Clay.style(go, Clay.mix(accent, 0xFF000000, 0.08f), -1, 6, true);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        gl.topMargin = dp(24);
        b.addView(go, gl);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                done(true);
            }
        });
        TextView again = text(getString(R.string.tour_again), 600, 13, sub);
        again.setPaddingRelative(0, dp(16), 0, 0);
        b.addView(again);
        return s;
    }

    // ------------------------------------------------------------------ language

    private int langIndex() {
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

    private void pickLanguage() {
        String[] names = new String[App.LANGS.length];
        names[0] = getString(R.string.lang_system);
        for (int i = 1; i < names.length; i++) {
            names[i] = App.LANG_NAMES[i];
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(names, langIndex(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        App.setLanguage(OnboardingActivity.this, App.LANGS[which]);
                        changedLook = true;
                        if (Build.VERSION.SDK_INT < 33) {
                            recreate();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
