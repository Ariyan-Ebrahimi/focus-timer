package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;

/** Add a study session by hand, or edit / delete a logged one. */
public final class SessionDialog {

    private SessionDialog() {
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** editing == null adds a new session. onDone runs after any change. */
    public static void show(final Activity a, final PrefsStore.Hist editing,
                            final Runnable onDone) {
        final int main = ThemeHelper.resolve(a, R.attr.textMain);
        final int sub = ThemeHelper.resolve(a, R.attr.textSub);
        final int surface2 = ThemeHelper.resolve(a, R.attr.surface2);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(a, 24), dp(a, 6), dp(a, 24), 0);
        root.setClipToPadding(false);

        // project chips
        final long[] project = {editing != null ? editing.project : 0};
        final List<PrefsStore.Project> projects = PrefsStore.loadProjects(a);
        final LinearLayout chips = new LinearLayout(a);
        if (!projects.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chips);
            hs.setPaddingRelative(0, 0, 0, dp(a, 12));
            root.addView(hs);
        }
        final Runnable paintChips = new Runnable() {
            @Override
            public void run() {
                chips.removeAllViews();
                for (int i = -1; i < projects.size(); i++) {
                    final long id = i < 0 ? 0 : projects.get(i).id;
                    boolean sel = id == project[0];
                    TextView t = new TextView(a);
                    t.setText(i < 0 ? a.getString(R.string.no_project) : projects.get(i).name);
                    Clay.text(t, 800, 13, sel ? main : sub);
                    t.setGravity(Gravity.CENTER_VERTICAL);
                    t.setPaddingRelative(dp(a, 12), dp(a, 7), dp(a, 14), dp(a, 8));
                    if (i >= 0) {
                        GradientDrawable dot = new GradientDrawable();
                        dot.setShape(GradientDrawable.OVAL);
                        dot.setColor(Projects.color(a, projects.get(i).color));
                        dot.setBounds(0, 0, dp(a, 10), dp(a, 10));
                        t.setCompoundDrawablesRelative(dot, null, null, null);
                        t.setCompoundDrawablePadding(dp(a, 7));
                    }
                    if (sel) {
                        Clay.inset(t, surface2, -1);
                    } else {
                        t.setBackground(null);
                    }
                    final Runnable self = this;
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            project[0] = id;
                            self.run();
                        }
                    });
                    chips.addView(t);
                }
            }
        };
        paintChips.run();

        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setHint(R.string.session_name_hint);
        Clay.text(name, 700, 16, main);
        name.setHintTextColor(sub);
        Clay.inset(name, surface2, 18);
        name.setPaddingRelative(dp(a, 18), dp(a, 13), dp(a, 18), dp(a, 13));
        if (editing != null) {
            name.setText(editing.name);
        }
        // one text box is enough: the session keeps its name (or takes the project's),
        // and the box below says what was studied
        name.setVisibility(View.GONE);
        root.addView(name);

        final EditText note = new EditText(a);
        note.setSingleLine(true);
        note.setHint(R.string.session_name_hint);
        note.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Clay.text(note, 700, 16, main);
        note.setHintTextColor(sub);
        Clay.inset(note, surface2, 18);
        note.setPaddingRelative(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 12));
        if (editing != null) {
            note.setText(editing.note);
        }
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(note, nl);
        root.addView(Tags.chips(a, note));

        // duration
        long mins = editing != null ? editing.millis / 60_000L : 30;
        LinearLayout pr = new LinearLayout(a);
        pr.setGravity(Gravity.CENTER);
        final NumberPicker hh = picker(a, pr, a.getString(R.string.unit_hours), 23,
                (int) (mins / 60), main, sub);
        final NumberPicker mm = picker(a, pr, a.getString(R.string.unit_min), 59,
                (int) (mins % 60), main, sub);
        root.addView(pr);

        // date
        final Calendar when = Calendar.getInstance();
        if (editing != null) {
            when.setTimeInMillis(editing.ts);
        }
        final TextView date = new TextView(a);
        Clay.text(date, 800, 15, main);
        date.setGravity(Gravity.CENTER);
        date.setPaddingRelative(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        Clay.style(date, ThemeHelper.resolve(a, R.attr.surface), -1, 3, true);
        final Runnable showDate = new Runnable() {
            @Override
            public void run() {
                date.setText(a.getString(R.string.session_date,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(when.getTime())));
            }
        };
        showDate.run();
        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePickerDialog dp = new DatePickerDialog(a,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int y, int m, int d) {
                                when.set(y, m, d);
                                showDate.run();
                            }
                        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH),
                        when.get(Calendar.DAY_OF_MONTH));
                dp.getDatePicker().setMaxDate(System.currentTimeMillis());
                dp.show();
            }
        });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = dp(a, 4);
        dl.bottomMargin = dp(a, 8);
        root.addView(date, dl);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(editing == null ? R.string.add_session : R.string.edit_session)
                .setView(root)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        long ms = (hh.getValue() * 60L + mm.getValue()) * 60_000L;
                        if (ms <= 0) {
                            Toast.makeText(a, R.string.set_time_first, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String n = name.getText().toString().trim();
                        if (n.length() == 0) {
                            PrefsStore.Project p = PrefsStore.findProject(a, project[0]);
                            n = p != null ? p.name : a.getString(R.string.study);
                        }
                        long ts = when.getTimeInMillis();
                        if (ts > System.currentTimeMillis()) {
                            ts = System.currentTimeMillis();
                        }
                        PrefsStore.Hist h = new PrefsStore.Hist(ts, n, ms, project[0]);
                        h.note = note.getText().toString().trim();
                        if (editing != null && editing.millis / 60_000L == ms / 60_000L) {
                            // length unchanged: keep the exact time and the focus score
                            h.millis = editing.millis;
                            h.score = editing.score;
                        }
                        if (editing == null) {
                            PrefsStore.insertHistory(a, h);
                        } else {
                            PrefsStore.replaceHistory(a, editing.ts, h);
                        }
                        onDone.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null);
        if (editing != null) {
            b.setNeutralButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    PrefsStore.replaceHistory(a, editing.ts, null);
                    onDone.run();
                }
            });
        }
        b.show();
    }

    private static NumberPicker picker(Activity a, LinearLayout row, String label, int max,
                                       int value, int main, int sub) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPaddingRelative(dp(a, 12), dp(a, 8), dp(a, 12), 0);
        TextView t = new TextView(a);
        t.setText(label);
        Clay.text(t, 800, 12, sub);
        NumberPicker np = new NumberPicker(a);
        np.setMinValue(0);
        np.setMaxValue(max);
        np.setValue(value);
        if (Build.VERSION.SDK_INT >= 29) {
            np.setTextColor(main);
            np.setTextSize(dp(a, 22));
        }
        col.addView(t);
        col.addView(np);
        row.addView(col);
        return np;
    }
}
