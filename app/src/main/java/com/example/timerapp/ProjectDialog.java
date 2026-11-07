package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Create / edit a project: name, color and an optional weekly goal. */
public final class ProjectDialog {

    private ProjectDialog() {
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Returns false (with a message) when there is no free color slot for a new project. */
    public static boolean show(final Activity a, final PrefsStore.Project editing,
                               final Runnable onSaved) {
        final List<PrefsStore.Project> all = PrefsStore.loadProjects(a);
        int free = Projects.nextColor(all);
        if (editing == null && free < 0) {
            Toast.makeText(a, a.getString(R.string.max_projects, Projects.MAX),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        int main = ThemeHelper.resolve(a, R.attr.textMain);
        int sub = ThemeHelper.resolve(a, R.attr.textSub);
        int surface2 = ThemeHelper.resolve(a, R.attr.surface2);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(dp(a, 24), dp(a, 6), dp(a, 24), 0);
        root.setClipToPadding(false);

        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setHint(R.string.project_name_hint);
        Clay.text(name, 700, 16, main);
        name.setHintTextColor(sub);
        Clay.inset(name, surface2, 18);
        name.setPaddingRelative(dp(a, 18), dp(a, 13), dp(a, 18), dp(a, 13));
        if (editing != null) {
            name.setText(editing.name);
        }
        root.addView(name);

        final TextView cl = new TextView(a);
        Clay.text(cl, 800, 14, sub);
        cl.setPaddingRelative(0, dp(a, 18), 0, dp(a, 6));
        root.addView(cl);

        // 4 x 2 grid of puffy swatches; colors other projects already use are shown faded
        // (each project keeps a distinct color so they never get confused)
        final int[] chosen = {editing != null ? editing.color : free};
        final ImageView[] cells = new ImageView[Projects.MAX];
        final boolean[] taken = new boolean[Projects.MAX];
        final Runnable paint = new Runnable() {
            @Override
            public void run() {
                cl.setText(a.getString(R.string.color_named,
                        a.getString(Projects.COLOR_NAMES[chosen[0]])));
                for (int i = 0; i < cells.length; i++) {
                    boolean sel = i == chosen[0];
                    cells[i].setImageResource(sel ? R.drawable.ic_check : 0);
                    cells[i].animate().scaleX(sel ? 1.12f : 1f).scaleY(sel ? 1.12f : 1f)
                            .setDuration(160).start();
                    cells[i].setElevation(dp(a, sel ? 6 : taken[i] ? 0 : 3));
                }
            }
        };
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPaddingRelative(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 6));
        LinearLayout row = null;
        for (int slot = 0; slot < Projects.MAX; slot++) {
            if (slot % 4 == 0) {
                row = new LinearLayout(a);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rl.topMargin = slot == 0 ? 0 : dp(a, 14);
                grid.addView(row, rl);
            }
            for (PrefsStore.Project p : all) {
                if (p.color == slot && (editing == null || p.id != editing.id)) {
                    taken[slot] = true;
                }
            }
            final int s = slot;
            ImageView sw = new ImageView(a);
            sw.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            sw.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            sw.setPaddingRelative(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
            String cname = a.getString(Projects.COLOR_NAMES[slot]);
            sw.setContentDescription(taken[slot]
                    ? a.getString(R.string.color_taken, cname) : cname);
            Clay.style(sw, Projects.color(a, slot), -1, 3, !taken[slot]);
            if (taken[slot]) {
                sw.setAlpha(0.28f);
            } else {
                sw.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chosen[0] = s;
                        paint.run();
                    }
                });
            }
            cells[slot] = sw;
            FrameLayout cell = new FrameLayout(a);
            cell.setClipChildren(false);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(a, 44), dp(a, 44));
            fl.gravity = Gravity.CENTER;
            cell.addView(sw, fl);
            row.addView(cell, new LinearLayout.LayoutParams(0, dp(a, 54), 1f));
        }
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        paint.run();

        TextView gl = new TextView(a);
        gl.setText(R.string.weekly_goal);
        Clay.text(gl, 800, 14, sub);
        gl.setPaddingRelative(0, dp(a, 14), 0, dp(a, 4));
        root.addView(gl);
        LinearLayout goalRow = new LinearLayout(a);
        goalRow.setGravity(Gravity.CENTER_VERTICAL);
        goalRow.setClipToPadding(false);
        goalRow.setPaddingRelative(0, dp(a, 4), 0, dp(a, 8));
        final int[] goalH = {editing != null ? editing.goalMin / 60 : 0};
        final TextView gv = new TextView(a);
        Clay.text(gv, 900, 17, main);
        gv.setGravity(Gravity.CENTER);
        final Runnable showGoal = new Runnable() {
            @Override
            public void run() {
                gv.setText(goalH[0] == 0 ? a.getString(R.string.no_goal)
                        : a.getString(R.string.goal_per_week, goalH[0]));
            }
        };
        showGoal.run();
        View.OnClickListener step = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goalH[0] = Math.max(0, Math.min(60, goalH[0] + (v.getTag() == null ? 1 : -1)));
                showGoal.run();
            }
        };
        ImageView minus = round(a, R.drawable.ic_minus, main);
        minus.setTag("minus");
        minus.setOnClickListener(step);
        ImageView plus = round(a, R.drawable.ic_plus, main);
        plus.setOnClickListener(step);
        goalRow.addView(minus);
        goalRow.addView(gv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        goalRow.addView(plus);
        root.addView(goalRow);

        new AlertDialog.Builder(a)
                .setTitle(editing == null ? R.string.new_project : R.string.edit_project)
                .setView(root)
                .setPositiveButton(editing == null ? R.string.create : R.string.save,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String n = name.getText().toString().trim();
                                if (n.length() == 0) {
                                    Toast.makeText(a, R.string.name_project_first,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                List<PrefsStore.Project> list = PrefsStore.loadProjects(a);
                                if (editing == null) {
                                    list.add(new PrefsStore.Project(System.currentTimeMillis(),
                                            n, chosen[0], goalH[0] * 60));
                                } else {
                                    for (PrefsStore.Project p : list) {
                                        if (p.id == editing.id) {
                                            p.name = n;
                                            p.color = chosen[0];
                                            p.goalMin = goalH[0] * 60;
                                        }
                                    }
                                }
                                PrefsStore.saveProjects(a, list);
                                if (onSaved != null) {
                                    onSaved.run();
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
        return true;
    }

    private static ImageView round(Activity a, int icon, int tint) {
        ImageView b = new ImageView(a);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(tint));
        int p = dp(a, 11);
        b.setPaddingRelative(p, p, p, p);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
        Clay.style(b, ThemeHelper.resolve(a, R.attr.surface), -1, 3, true);
        return b;
    }
}
