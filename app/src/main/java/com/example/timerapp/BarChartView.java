package com.example.timerapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Seven-day study chart: thin bars with rounded data ends on a flat baseline, either one
 * series (a project) or stacked by project with 2dp gaps between segments. Today's total
 * is labeled; tapping or dragging across a day shows its total.
 */
public class BarChartView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float d;
    private long[] data = new long[7];
    /** Per day, per series values (stacked bottom-up). */
    private long[][] segs = new long[7][0];
    private int[] colors = new int[0];
    private String[] days = new String[7];
    private int selected = 6;

    public BarChartView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        base.setColor(ThemeHelper.resolve(c, R.attr.strokeC));
        base.setStrokeWidth(1.5f * d);
        label.setColor(ThemeHelper.resolve(c, R.attr.textSub));
        label.setTextSize(12 * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Clay.font(c));
        value.setColor(ThemeHelper.resolve(c, R.attr.textMain));
        value.setTextSize(13 * d);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Clay.font(c));
        value.setFakeBoldText(true);
        tipBg.setColor(ThemeHelper.resolve(c, R.attr.surface2));
        setMinimumHeight(Math.round(180 * d));
    }

    public void setData(long[] data, String[] days, int color) {
        long[][] s = new long[7][1];
        for (int i = 0; i < 7; i++) {
            s[i][0] = data[i];
        }
        setStacked(s, new int[] {color}, days);
    }

    /** segs[day][series]; colors[series] in the fixed project order. */
    public void setStacked(long[][] segs, int[] colors, String[] days) {
        this.segs = segs;
        this.colors = colors;
        this.days = days;
        data = new long[7];
        for (int i = 0; i < 7; i++) {
            for (long v : segs[i]) {
                data[i] += v;
            }
        }
        selected = 6;
        setContentDescription(describe());
        invalidate();
    }

    private String describe() {
        StringBuilder sb = new StringBuilder(getContext().getString(R.string.chart_desc));
        sb.append(' ');
        for (int i = 0; i < 7; i++) {
            sb.append(days[i]).append(' ').append(Projects.hours(data[i]))
                    .append(i < 6 ? ", " : "");
        }
        return sb.toString();
    }

    /** Right-to-left languages read time from right to left: today sits on the left. */
    private boolean rtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    private int slotAt(float x) {
        float slot = getWidth() / 7f;
        int s = Math.max(0, Math.min(6, (int) (x / slot)));
        return rtl() ? 6 - s : s;
    }

    private float centerOf(int day, float slot) {
        int pos = rtl() ? 6 - day : day;
        return slot * pos + slot / 2f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            int s = slotAt(e.getX());
            if (s != selected) {
                selected = s;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float labelH = 26 * d;
        float topPad = 34 * d;
        float baseY = h - labelH;
        float plotH = baseY - topPad;
        long max = 0;
        for (long v : data) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            max = 60 * 60_000L;
        }
        float slot = w / 7f;
        float barW = Math.min(slot * 0.46f, 30 * d);
        float rad = 4 * d;

        for (int i = 0; i < 7; i++) {
            float cx = centerOf(i, slot);
            float bh = data[i] <= 0 ? 0 : Math.max(rad * 1.5f, plotH * data[i] / (float) max);
            if (bh > 0) {
                int top = -1;
                for (int k = 0; k < segs[i].length; k++) {
                    if (segs[i][k] > 0) {
                        top = k;
                    }
                }
                float gap = 2 * d;
                float y = baseY;
                for (int k = 0; k <= top; k++) {
                    if (segs[i][k] <= 0) {
                        continue;
                    }
                    float sh = bh * segs[i][k] / (float) data[i];
                    float y0 = k == top ? baseY - bh : y - sh;
                    // segments are separated by a 2dp gap; only the top one gets the
                    // rounded data end, everything stays square on the baseline
                    float bottom = y == baseY ? y : y - gap;
                    if (bottom - y0 < 1) {
                        y -= sh;
                        continue;
                    }
                    r.set(cx - barW / 2f, y0, cx + barW / 2f, bottom);
                    path.reset();
                    float tr = k == top ? rad : 0;
                    path.addRoundRect(r, new float[] {tr, tr, tr, tr, 0, 0, 0, 0},
                            Path.Direction.CW);
                    bar.setColor(colors[Math.min(k, colors.length - 1)]);
                    bar.setAlpha(i == selected || selected < 0 ? 255 : 150);
                    canvas.drawPath(path, bar);
                    y -= sh;
                }
            }
            label.setFakeBoldText(i == selected);
            label.setColor(i == selected ? ThemeHelper.resolve(getContext(), R.attr.textMain)
                    : ThemeHelper.resolve(getContext(), R.attr.textSub));
            canvas.drawText(days[i] == null ? "" : days[i], cx, h - 6 * d, label);
        }
        canvas.drawLine(0, baseY, w, baseY, base);

        // value label for the selected day (today by default)
        if (selected >= 0) {
            float cx = centerOf(selected, slot);
            float bh = data[selected] <= 0 ? 0
                    : Math.max(rad * 1.5f, plotH * data[selected] / (float) max);
            String txt = Projects.hours(data[selected]);
            float tw = value.measureText(txt) + 16 * d;
            float ty = baseY - bh - 10 * d;
            float left = Math.max(0, Math.min(w - tw, cx - tw / 2f));
            r.set(left, ty - 20 * d, left + tw, ty + 4 * d);
            canvas.drawRoundRect(r, 10 * d, 10 * d, tipBg);
            canvas.drawText(txt, r.centerX(), ty - 3 * d, value);
        }
    }
}
