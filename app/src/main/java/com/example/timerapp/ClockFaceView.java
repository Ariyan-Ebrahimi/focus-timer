package com.example.timerapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A small analog clock face whose hands show a given hour and minute. */
public class ClockFaceView extends View {

    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hourHand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minHand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int hour;
    private int minute;

    public ClockFaceView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        face.setColor(ThemeHelper.resolve(c, R.attr.surface2));
        tick.setColor(ThemeHelper.resolve(c, R.attr.textSub));
        tick.setStrokeCap(Paint.Cap.ROUND);
        hourHand.setColor(ThemeHelper.resolve(c, R.attr.textMain));
        hourHand.setStrokeCap(Paint.Cap.ROUND);
        hourHand.setStrokeWidth(5 * d);
        minHand.setColor(ThemeHelper.resolve(c, R.attr.accent));
        minHand.setStrokeCap(Paint.Cap.ROUND);
        minHand.setStrokeWidth(3.5f * d);
        pin.setColor(ThemeHelper.resolve(c, R.attr.accent));
    }

    public void setTime(int h, int m) {
        hour = h;
        minute = m;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(s, s);
    }

    private void hand(Canvas c, float cx, float cy, float turns, float len, Paint p) {
        double a = Math.toRadians(turns * 360 - 90);
        c.drawLine(cx, cy, cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);
        c.drawCircle(cx, cy, r, face);
        for (int i = 0; i < 12; i++) {
            boolean major = i % 3 == 0;
            tick.setStrokeWidth((major ? 3f : 2f) * d);
            tick.setAlpha(major ? 255 : 120);
            double a = Math.toRadians(i * 30);
            float out = r - 7 * d;
            float in = out - (major ? 7 : 4) * d;
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, tick);
        }
        hand(c, cx, cy, ((hour % 12) + minute / 60f) / 12f, r * 0.45f, hourHand);
        hand(c, cx, cy, minute / 60f, r * 0.68f, minHand);
        c.drawCircle(cx, cy, 4.5f * d, pin);
    }
}
