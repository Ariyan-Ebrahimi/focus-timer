package com.example.timerapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Wake-up missions: small tasks that must be finished before an alarm turns off.
 * Each alarm keeps a list of them (with their own settings); they run one after another.
 */
public final class Missions {

    public static final int MATH = 0;
    public static final int SHAKE = 1;
    public static final int MEMORY = 2;
    public static final int TYPE = 3;
    public static final int DOT = 4;
    public static final int ORDER = 5;
    public static final int WALK = 6;
    public static final int COUNT = 7;

    static final int[] NAMES = {R.string.m_math, R.string.m_shake, R.string.m_memory,
            R.string.m_type, R.string.m_dot, R.string.m_order, R.string.m_walk};
    /** What the amount setting means for each mission (problems, shakes, tiles ...). */
    static final int[] AMOUNT_NAMES = {R.string.m_problems, R.string.m_shakes, R.string.m_tiles,
            0, R.string.m_taps, R.string.m_numbers, R.string.m_steps};
    /** The amounts offered for each mission. */
    static final int[][] AMOUNTS = {{1, 2, 3, 5}, {15, 30, 50, 80}, {4, 5, 6, 7}, {},
            {10, 20, 30}, {9, 16, 25}, {20, 50, 100}};
    static final int[] DEFAULT_AMOUNT = {3, 30, 5, 0, 20, 16, 50};
    static final int[] LEVELS = {R.string.m_easy, R.string.m_medium, R.string.m_hard};

    private Missions() {
    }

    public static final class Mission {
        public int type;
        /** Math difficulty: 0 easy, 1 medium, 2 hard. */
        public int level = 1;
        public int amount;
        /** The sentence for Type. */
        public String text = "";

        Mission copy() {
            Mission m = new Mission();
            m.type = type;
            m.level = level;
            m.amount = amount;
            m.text = text;
            return m;
        }
    }

    static Mission create(Context c, int type) {
        Mission m = new Mission();
        m.type = type;
        m.amount = DEFAULT_AMOUNT[type];
        if (type == TYPE) {
            m.text = c.getString(R.string.m_default_phrase);
        }
        return m;
    }

    static String name(Context c, int type) {
        return c.getString(NAMES[type]);
    }

    /** "Math, Shake" for the alarm list. */
    static String names(Context c, List<Mission> list) {
        StringBuilder sb = new StringBuilder();
        for (Mission m : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name(c, m.type));
        }
        return sb.toString();
    }

    /** Walk needs a step sensor; phones without one don't offer it. */
    static boolean available(Context c, int type) {
        if (type != WALK) {
            return true;
        }
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null;
    }

    static boolean canCountSteps(Context c) {
        return available(c, WALK) && (Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED);
    }

    static JSONArray toJson(List<Mission> list) {
        JSONArray a = new JSONArray();
        try {
            for (Mission m : list) {
                JSONObject o = new JSONObject();
                o.put("t", m.type);
                o.put("l", m.level);
                o.put("n", m.amount);
                o.put("x", m.text);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        return a;
    }

    static List<Mission> fromJson(JSONArray a) {
        List<Mission> out = new ArrayList<Mission>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) {
                continue;
            }
            Mission m = new Mission();
            m.type = o.optInt("t", MATH);
            if (m.type < 0 || m.type >= COUNT) {
                continue;
            }
            m.level = o.optInt("l", 1);
            m.amount = o.optInt("n", DEFAULT_AMOUNT[m.type]);
            m.text = o.optString("x", "");
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ running a mission

    /** A mission on screen; stop() releases its sensors and timers. */
    public interface Run {
        View view();

        void stop();
    }

    /**
     * Builds mission `m`. `done` runs once it is finished; `active` runs whenever the
     * person is clearly working on it (touches, shakes, steps), to keep the alarm quiet.
     */
    static Run start(Activity a, Mission m, Runnable done, Runnable active) {
        switch (m.type) {
            case MATH:
                return new MathRun(a, m, done);
            case SHAKE:
                return new ShakeRun(a, m.amount, done, active, false);
            case MEMORY:
                return new MemoryRun(a, m, done);
            case TYPE:
                return new TypeRun(a, m, done);
            case DOT:
                return new DotRun(a, m, done);
            case ORDER:
                return new OrderRun(a, m, done);
            case WALK:
            default:
                // without the step sensor (or its permission) walking falls back to shaking
                return new ShakeRun(a, m.amount, done, active, canCountSteps(a));
        }
    }

    /** Shared look: colours, a title line, a progress line and clay buttons. */
    private abstract static class Base implements Run {
        final Activity a;
        final Runnable done;
        final LinearLayout root;
        final int main;
        final int sub;
        final int surface;
        final int surface2;
        final int accent;
        final int bad;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Random rnd = new Random();
        boolean finished;

        Base(Activity a, Runnable done) {
            this.a = a;
            this.done = done;
            main = ThemeHelper.resolve(a, R.attr.textMain);
            sub = ThemeHelper.resolve(a, R.attr.textSub);
            surface = ThemeHelper.resolve(a, R.attr.surface);
            surface2 = ThemeHelper.resolve(a, R.attr.surface2);
            accent = ThemeHelper.resolve(a, R.attr.accent);
            bad = ThemeHelper.resolve(a, R.attr.badC);
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
        }

        int dp(float v) {
            return Math.round(v * a.getResources().getDisplayMetrics().density);
        }

        TextView text(String s, int weight, float sp, int color) {
            TextView t = new TextView(a);
            t.setText(s);
            t.setGravity(Gravity.CENTER);
            Clay.text(t, weight, sp, color);
            return t;
        }

        void finish() {
            if (finished) {
                return;
            }
            finished = true;
            stop();
            done.run();
        }

        /** A little side-to-side shake for a wrong answer. */
        void wobble(View v) {
            v.animate().translationX(dp(10)).setDuration(50).withEndAction(new Runnable() {
                @Override
                public void run() {
                    v.animate().translationX(-dp(10)).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            v.animate().translationX(0).setDuration(50).start();
                        }
                    }).start();
                }
            }).start();
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        public void stop() {
            handler.removeCallbacksAndMessages(null);
        }
    }

    // ------------------------------------------------------------------ math

    private static final class MathRun extends Base {
        private final Mission m;
        private final TextView problem;
        private final TextView answer;
        private final TextView progress;
        private int solved;
        private int result;
        private String typed = "";

        MathRun(Activity a, Mission m, Runnable done) {
            super(a, done);
            this.m = m;
            progress = text("", 800, 14, sub);
            root.addView(progress);
            problem = text("", 900, 40, main);
            problem.setFontFeatureSettings("tnum");
            problem.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            problem.setPadding(0, dp(8), 0, dp(8));
            root.addView(problem);
            answer = text("", 900, 34, accent);
            answer.setFontFeatureSettings("tnum");
            answer.setMinWidth(dp(160));
            answer.setPadding(dp(20), dp(8), dp(20), dp(8));
            Clay.inset(answer, surface2, 20);
            root.addView(answer);
            String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"},
                    {"del", "0", "ok"}};
            for (String[] r : rows) {
                LinearLayout row = new LinearLayout(a);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                for (final String k : r) {
                    TextView key = text("del".equals(k) ? "\u232b" : "ok".equals(k) ? "\u2713" : k,
                            800, 22, "ok".equals(k) ? 0xFFFFFFFF : main);
                    Clay.style(key, "ok".equals(k) ? accent : surface, 20, 3, true);
                    key.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            press(k);
                        }
                    });
                    LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, dp(54), 1f);
                    kl.setMargins(dp(5), dp(5), dp(5), dp(5));
                    row.addView(key, kl);
                }
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (r[0].equals("1")) {
                    rl.topMargin = dp(14);
                }
                root.addView(row, rl);
            }
            next();
        }

        private void next() {
            int x;
            int y;
            int z;
            String q;
            if (m.level == 0) {
                x = 2 + rnd.nextInt(19);
                y = 2 + rnd.nextInt(19);
                if (rnd.nextBoolean() && x != y) {
                    result = Math.abs(x - y);
                    q = Math.max(x, y) + " \u2212 " + Math.min(x, y);
                } else {
                    result = x + y;
                    q = x + " + " + y;
                }
            } else if (m.level == 1) {
                if (rnd.nextBoolean()) {
                    x = 10 + rnd.nextInt(90);
                    y = 10 + rnd.nextInt(90);
                    result = x + y;
                    q = x + " + " + y;
                } else {
                    x = 3 + rnd.nextInt(10);
                    y = 3 + rnd.nextInt(7);
                    result = x * y;
                    q = x + " \u00d7 " + y;
                }
            } else {
                x = 11 + rnd.nextInt(9);
                y = 3 + rnd.nextInt(7);
                z = 10 + rnd.nextInt(90);
                result = x * y + z;
                q = x + " \u00d7 " + y + " + " + z;
            }
            problem.setText(q + " = ?");
            typed = "";
            answer.setText(" ");
            progress.setText((solved + 1) + " / " + Math.max(1, m.amount));
        }

        private void press(String k) {
            if ("del".equals(k)) {
                if (typed.length() > 0) {
                    typed = typed.substring(0, typed.length() - 1);
                }
            } else if ("ok".equals(k)) {
                if (typed.length() > 0 && Integer.parseInt(typed) == result) {
                    solved++;
                    if (solved >= Math.max(1, m.amount)) {
                        finish();
                        return;
                    }
                    next();
                    return;
                }
                wobble(answer);
                typed = "";
            } else if (typed.length() < 5) {
                typed += k;
            }
            answer.setText(typed.length() == 0 ? " " : typed);
        }
    }

    // ------------------------------------------------------------------ shake / walk

    private static final class ShakeRun extends Base implements SensorEventListener {
        private final int goal;
        private final Runnable active;
        private final boolean steps;
        private final SensorManager sm;
        private final RingView ring;
        private final TextView left;
        private int count;
        private long last;

        ShakeRun(Activity a, int goal, Runnable done, Runnable active, boolean steps) {
            super(a, done);
            this.goal = Math.max(1, goal);
            this.active = active;
            this.steps = steps;
            root.addView(text(a.getString(steps ? R.string.m_walk_now : R.string.m_shake_now),
                    900, 22, main));
            FrameLayout box = new FrameLayout(a);
            ring = new RingView(a);
            ring.setRing(14, 16);
            ring.setProgress(0f);
            Clay.style(ring, surface, -1, 6, false);
            box.addView(ring, new FrameLayout.LayoutParams(dp(220), dp(220)));
            left = text(String.valueOf(this.goal), 900, 52, main);
            left.setElevation(dp(12));
            box.addView(left, new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(220), dp(220));
            bl.topMargin = dp(20);
            root.addView(box, bl);
            sm = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
            Sensor s = sm == null ? null : sm.getDefaultSensor(
                    steps ? Sensor.TYPE_STEP_DETECTOR : Sensor.TYPE_ACCELEROMETER);
            if (s != null) {
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
            }
        }

        @Override
        public void onSensorChanged(SensorEvent e) {
            if (finished) {
                return;
            }
            if (!steps) {
                float x = e.values[0];
                float y = e.values[1];
                float z = e.values[2];
                double g = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
                long now = System.currentTimeMillis();
                if (g < 2.0 || now - last < 220) {
                    return;
                }
                last = now;
            }
            count++;
            active.run();
            left.setText(String.valueOf(Math.max(0, goal - count)));
            ring.setProgress(count / (float) goal);
            if (count >= goal) {
                finish();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void stop() {
            super.stop();
            if (sm != null) {
                sm.unregisterListener(this);
            }
        }
    }

    // ------------------------------------------------------------------ memory

    private static final class MemoryRun extends Base {
        private final int length;
        private final int side;
        private final TextView[] cells;
        private final TextView hint;
        private final List<Integer> pattern = new ArrayList<Integer>();
        private int pos;
        private boolean listening;

        MemoryRun(Activity a, Mission m, Runnable done) {
            super(a, done);
            length = Math.max(3, m.amount);
            side = length <= 5 ? 3 : 4;
            hint = text("", 800, 18, main);
            root.addView(hint);
            LinearLayout grid = new LinearLayout(a);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setClipChildren(false);
            grid.setClipToPadding(false);
            cells = new TextView[side * side];
            int size = side == 3 ? 84 : 66;
            for (int r = 0; r < side; r++) {
                LinearLayout row = new LinearLayout(a);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                for (int c = 0; c < side; c++) {
                    final int idx = r * side + c;
                    TextView cell = new TextView(a);
                    paint(cell, surface);
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            tap(idx);
                        }
                    });
                    cells[idx] = cell;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
                    lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                    row.addView(cell, lp);
                }
                grid.addView(row);
            }
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gl.topMargin = dp(18);
            root.addView(grid, gl);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    newPattern();
                }
            }, 600);
        }

        private void paint(TextView cell, int color) {
            Clay.style(cell, color, 18, 3, false);
        }

        private void newPattern() {
            pattern.clear();
            List<Integer> all = new ArrayList<Integer>();
            for (int i = 0; i < cells.length; i++) {
                all.add(i);
            }
            Collections.shuffle(all, rnd);
            pattern.addAll(all.subList(0, length));
            show();
        }

        /** Lights the pattern one tile at a time, then hands over. */
        private void show() {
            listening = false;
            pos = 0;
            hint.setText(R.string.m_watch);
            for (int i = 0; i < pattern.size(); i++) {
                final int cell = pattern.get(i);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        paint(cells[cell], accent);
                    }
                }, 300 + i * 700L);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        paint(cells[cell], surface);
                    }
                }, 300 + i * 700L + 500);
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    listening = true;
                    hint.setText(R.string.m_your_turn);
                }
            }, 300 + pattern.size() * 700L);
        }

        private void tap(final int idx) {
            if (!listening || finished) {
                return;
            }
            if (pattern.get(pos) == idx) {
                paint(cells[idx], accent);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        paint(cells[idx], surface);
                    }
                }, 250);
                pos++;
                if (pos >= pattern.size()) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 300);
                }
                return;
            }
            // wrong tile: flash it, then a fresh pattern
            listening = false;
            paint(cells[idx], bad);
            hint.setText(R.string.m_wrong);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    paint(cells[idx], surface);
                    newPattern();
                }
            }, 900);
        }
    }

    // ------------------------------------------------------------------ type a sentence

    private static final class TypeRun extends Base {
        TypeRun(Activity a, Mission m, Runnable done) {
            super(a, done);
            final String target = m.text.trim().length() > 0 ? m.text.trim()
                    : a.getString(R.string.m_default_phrase);
            root.addView(text(a.getString(R.string.m_type_hint), 800, 14, sub));
            TextView phrase = text("\u201c" + target + "\u201d", 900, 22, main);
            phrase.setPadding(dp(18), dp(14), dp(18), dp(14));
            Clay.style(phrase, surface, 20, 4, false);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.topMargin = dp(10);
            root.addView(phrase, pl);
            final EditText input = new EditText(a);
            input.setSingleLine(false);
            input.setMinLines(2);
            input.setGravity(Gravity.TOP | Gravity.START);
            Clay.text(input, 700, 18, main);
            input.setPadding(dp(16), dp(12), dp(16), dp(12));
            Clay.inset(input, surface2, 18);
            // no pasting: it has to be typed
            input.setLongClickable(false);
            input.setCustomInsertionActionModeCallback(noMenu());
            input.setCustomSelectionActionModeCallback(noMenu());
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            il.topMargin = dp(14);
            root.addView(input, il);
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int st, int c, int af) {
                }

                @Override
                public void onTextChanged(CharSequence s, int st, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (clean(s.toString()).equals(clean(target))) {
                        finish();
                    }
                }
            });
            input.requestFocus();
        }

        private static String clean(String s) {
            return s.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        }

        private static ActionMode.Callback noMenu() {
            return new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                }
            };
        }
    }

    // ------------------------------------------------------------------ catch the dot

    private static final class DotRun extends Base {
        private final int goal;
        private final FrameLayout field;
        private final View dot;
        private final TextView left;
        private int hits;
        private final Runnable hop = new Runnable() {
            @Override
            public void run() {
                move();
                handler.postDelayed(this, 1100);
            }
        };

        DotRun(Activity a, Mission m, Runnable done) {
            super(a, done);
            goal = Math.max(1, m.amount);
            root.addView(text(a.getString(R.string.m_tap_dot), 900, 22, main));
            left = text(String.valueOf(goal), 900, 18, accent);
            root.addView(left);
            field = new FrameLayout(a);
            Clay.inset(field, surface2, 24);
            dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(accent);
            dot.setBackground(g);
            dot.setElevation(dp(4));
            field.addView(dot, new FrameLayout.LayoutParams(dp(62), dp(62)));
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(330));
            fl.topMargin = dp(12);
            root.addView(field, fl);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hits++;
                    left.setText(String.valueOf(Math.max(0, goal - hits)));
                    if (hits >= goal) {
                        finish();
                        return;
                    }
                    handler.removeCallbacks(hop);
                    handler.post(hop);
                }
            });
            handler.postDelayed(hop, 200);
        }

        private void move() {
            int w = field.getWidth() - dot.getWidth();
            int h = field.getHeight() - dot.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            dot.animate().x(rnd.nextInt(w)).y(rnd.nextInt(h)).setDuration(180).start();
        }
    }

    // ------------------------------------------------------------------ number order

    private static final class OrderRun extends Base {
        private final int n;
        private final TextView[] cells;
        private final TextView hint;
        private int next = 1;

        OrderRun(Activity a, Mission m, Runnable done) {
            super(a, done);
            int side = m.amount >= 25 ? 5 : m.amount >= 16 ? 4 : 3;
            n = side * side;
            hint = text(a.getString(R.string.m_tap_order, n), 900, 20, main);
            root.addView(hint);
            List<Integer> nums = new ArrayList<Integer>();
            for (int i = 1; i <= n; i++) {
                nums.add(i);
            }
            Collections.shuffle(nums, rnd);
            cells = new TextView[n + 1];
            LinearLayout grid = new LinearLayout(a);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            grid.setClipChildren(false);
            grid.setClipToPadding(false);
            int size = side == 3 ? 84 : side == 4 ? 66 : 54;
            for (int r = 0; r < side; r++) {
                LinearLayout row = new LinearLayout(a);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                for (int c = 0; c < side; c++) {
                    final int value = nums.get(r * side + c);
                    final TextView cell = text(String.valueOf(value), 900, side == 5 ? 18 : 22, main);
                    Clay.style(cell, surface, 16, 3, true);
                    cells[value] = cell;
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            tap(value, cell);
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
                    lp.setMargins(dp(5), dp(5), dp(5), dp(5));
                    row.addView(cell, lp);
                }
                grid.addView(row);
            }
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gl.topMargin = dp(16);
            root.addView(grid, gl);
        }

        private void tap(int value, TextView cell) {
            if (finished || cell.getAlpha() < 0.5f) {
                return;
            }
            if (value == next) {
                cell.setAlpha(0.25f);
                next++;
                if (next > n) {
                    finish();
                }
                return;
            }
            // wrong number: start over
            wobble(cell);
            for (int i = 1; i <= n; i++) {
                cells[i].setAlpha(1f);
            }
            next = 1;
        }
    }
}
