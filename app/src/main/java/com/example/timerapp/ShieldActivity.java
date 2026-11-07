package com.example.timerapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** "Back to focus?" \u2014 shown over a guarded app while focusing. */
public class ShieldActivity extends Activity {

    private String pkg;

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
        Shield.clearNotice(this);
        pkg = getIntent().getStringExtra("pkg");
        String appName = pkg == null ? "" : Shield.label(this, pkg);

        int bg = ThemeHelper.resolve(this, R.attr.bg);
        int main = ThemeHelper.resolve(this, R.attr.textMain);
        int sub = ThemeHelper.resolve(this, R.attr.textSub);
        int surface = ThemeHelper.resolve(this, R.attr.surface);
        int accent = ThemeHelper.resolve(this, R.attr.accent);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(bg);
        root.setPaddingRelative(dp(28), dp(96), dp(28), dp(40));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        // the app they opened, softly faded, in a clay circle
        ImageView icon = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            icon.setImageDrawable(d);
        } catch (Exception e) {
            icon.setImageResource(R.drawable.ic_tab_timer);
        }
        icon.setPadding(dp(22), dp(22), dp(22), dp(22));
        icon.setImageAlpha(150);
        Clay.style(icon, surface, -1, 8, false);
        root.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = new TextView(this);
        title.setText(R.string.shield_q);
        Clay.text(title, 900, 30, main);
        title.setGravity(Gravity.CENTER);
        title.setPaddingRelative(0, dp(32), 0, dp(8));
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.shield_can_wait, appName) + "\n" + focusLine());
        Clay.text(body, 600, 16, sub);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        root.addView(body);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView back = new TextView(this);
        back.setText(R.string.shield_back);
        back.setGravity(Gravity.CENTER);
        Clay.text(back, 900, 19, 0xFFFFFFFF);
        Clay.style(back, accent, -1, 6, true);
        root.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToFocus();
            }
        });

        TextView allow = new TextView(this);
        allow.setText(getString(R.string.shield_allow, appName));
        allow.setGravity(Gravity.CENTER);
        Clay.text(allow, 700, 15, sub);
        allow.setPaddingRelative(dp(12), dp(18), dp(12), dp(6));
        root.addView(allow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        allow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pkg != null) {
                    Shield.allow(pkg);
                }
                finish();
            }
        });

        setContentView(root);
    }

    /** "18:24 left on Math" for a running timer, or "Studying Math" for the stopwatch. */
    private String focusLine() {
        long now = System.currentTimeMillis();
        for (TimerItem it : PrefsStore.loadTimers(this)) {
            if (it.state == TimerItem.RUNNING && it.deadline > now) {
                PrefsStore.Project p = PrefsStore.findProject(this, it.projectId);
                return getString(R.string.shield_left, MainActivity.formatDuration(it.deadline - now),
                        p != null ? p.name : it.name);
            }
        }
        PrefsStore.Sw sw = PrefsStore.loadSw(this);
        PrefsStore.Project p = PrefsStore.findProject(this, sw.project);
        return getString(R.string.shield_studying, p != null ? p.name
                : getString(R.string.stopwatch));
    }

    private void backToFocus() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        // going "back" would land in the guarded app: go back to focusing instead
        backToFocus();
    }
}
