package com.example.timerapp;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Session tags are #words in a session's note (so they need no storage of their own). */
public final class Tags {

    private static final Pattern TAG = Pattern.compile("#[\\p{L}\\p{N}_]+");
    private static final int[] DEFAULTS = {R.string.tag_homework, R.string.tag_revision,
            R.string.tag_reading, R.string.tag_practice, R.string.tag_exam};

    private Tags() {
    }

    /** The tags in a note, lower-cased, without repeats. */
    public static Set<String> of(String note) {
        Set<String> out = new LinkedHashSet<String>();
        if (note == null) {
            return out;
        }
        Matcher m = TAG.matcher(note);
        while (m.find()) {
            out.add(m.group().toLowerCase());
        }
        return out;
    }

    /** "#" + a word with its spaces removed (tags are single words). */
    private static String tag(String word) {
        return "#" + word.replace(" ", "").toLowerCase();
    }

    /** Tags offered as chips: the built-in ones, then ones used recently. */
    public static List<String> suggestions(Activity a) {
        Set<String> out = new LinkedHashSet<String>();
        for (int id : DEFAULTS) {
            out.add(tag(a.getString(id)));
        }
        int seen = 0;
        for (PrefsStore.Hist h : PrefsStore.loadHistory(a)) {
            out.addAll(of(h.note));
            if (++seen > 300 || out.size() >= 14) {
                break;
            }
        }
        return new ArrayList<String>(out);
    }

    /** Time per tag since `since`, largest first. */
    public static List<Map.Entry<String, Long>> totals(List<PrefsStore.Hist> history,
                                                       long since) {
        Map<String, Long> sum = new LinkedHashMap<String, Long>();
        for (PrefsStore.Hist h : history) {
            if (h.ts < since) {
                continue;
            }
            for (String t : of(h.note)) {
                Long v = sum.get(t);
                sum.put(t, (v == null ? 0L : v) + h.millis);
            }
        }
        List<Map.Entry<String, Long>> out = new ArrayList<Map.Entry<String, Long>>(sum.entrySet());
        java.util.Collections.sort(out, new java.util.Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> x, Map.Entry<String, Long> y) {
                return y.getValue().compareTo(x.getValue());
            }
        });
        return out;
    }

    /** A row of tag chips under a note field; tapping one adds or removes it. */
    public static View chips(final Activity a, final EditText note) {
        final int main = ThemeHelper.resolve(a, R.attr.textMain);
        final int sub = ThemeHelper.resolve(a, R.attr.textSub);
        final int accent = ThemeHelper.resolve(a, R.attr.accent);
        final int surface2 = ThemeHelper.resolve(a, R.attr.surface2);
        final float d = a.getResources().getDisplayMetrics().density;
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        final LinearLayout row = new LinearLayout(a);
        row.setPaddingRelative(0, Math.round(10 * d), 0, Math.round(2 * d));
        hs.addView(row);
        final List<String> tags = suggestions(a);
        final Runnable paint = new Runnable() {
            @Override
            public void run() {
                Set<String> on = of(note.getText().toString());
                row.removeAllViews();
                for (final String t : tags) {
                    boolean sel = on.contains(t);
                    TextView chip = new TextView(a);
                    chip.setText(t);
                    chip.setGravity(Gravity.CENTER);
                    Clay.text(chip, 800, 13, sel ? 0xFFFFFFFF : sub);
                    chip.setPaddingRelative(Math.round(12 * d), Math.round(6 * d),
                            Math.round(12 * d), Math.round(7 * d));
                    if (sel) {
                        Clay.style(chip, accent, -1, 2, false);
                    } else {
                        Clay.inset(chip, surface2, -1);
                    }
                    final Runnable self = this;
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            toggle(note, t);
                            self.run();
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMarginEnd(Math.round(6 * d));
                    row.addView(chip, lp);
                }
            }
        };
        paint.run();
        // typing a #tag by hand lights its chip too
        note.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                paint.run();
            }
        });
        return hs;
    }

    private static void toggle(EditText note, String tag) {
        String s = note.getText().toString();
        if (of(s).contains(tag)) {
            // remove it (and the space before it)
            s = s.replaceAll("(?i)\\s*" + Pattern.quote(tag) + "(?![\\p{L}\\p{N}_])", "").trim();
        } else {
            s = (s.trim() + " " + tag).trim();
        }
        note.setText(s);
        note.setSelection(s.length());
    }
}
