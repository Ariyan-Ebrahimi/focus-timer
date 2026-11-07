package com.example.timerapp;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Stopwatch face: 60 ticks around the edge that light up as the seconds sweep past,
 * a knob on the current second, a thin inner ring for the minutes of the hour and a
 * small mark where the current lap began. The clay disc behind it is the background.
 */
public class StopwatchDial extends View {

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lapMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float d;
    private int off;
    private int offMajor;
    private int accent;
    private int accent2;
    /** Elapsed time shown, and where the current lap started (-1 = no laps). */
    private long ms;
    private long lapStart = -1;
    private boolean running;

    public StopwatchDial(Context c) {
        this(c, null);
    }

    public StopwatchDial(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        accent = ThemeHelper.resolve(c, R.attr.accent);
        accent2 = ThemeHelper.resolve(c, R.attr.accent2);
        off = ThemeHelper.resolve(c, R.attr.trackC);
        offMajor = Clay.mix(off, ThemeHelper.resolve(c, R.attr.textSub), 0.45f);
        tick.setStrokeCap(Paint.Cap.ROUND);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeCap(Paint.Cap.ROUND);
        inner.setStrokeWidth(5 * d);
        inner.setColor(accent2);
        innerTrack.setStyle(Paint.Style.STROKE);
        innerTrack.setStrokeWidth(5 * d);
        innerTrack.setColor(off);
        knob.setColor(Color.WHITE);
        knobShadow.setColor(Color.argb(80, 30, 20, 80));
        knobShadow.setMaskFilter(new BlurMaskFilter(3 * d, BlurMaskFilter.Blur.NORMAL));
        lapMark.setColor(accent2);
    }

    public void setState(long elapsed, long currentLapStart, boolean isRunning) {
        ms = elapsed;
        lapStart = currentLapStart;
        running = isRunning;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(s, s);
    }

    private static float angle(float fraction) {
        return (float) Math.toRadians(-90 + 360 * fraction);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = getWidth() / 2f - 20 * d;
        float sec = (ms % 60000L) / 1000f;
        int lit = ms > 0 ? (int) sec + 1 : 0;

        // 60 ticks; every fifth one is longer. Ticks already passed this minute glow.
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            float len = (major ? 12 : 7) * d;
            tick.setStrokeWidth((major ? 3.2f : 2.2f) * d);
            if (i < lit) {
                tick.setColor(Clay.mix(accent, accent2, i / 59f));
            } else {
                tick.setColor(major ? offMajor : off);
            }
            double a = angle(i / 60f);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            canvas.drawLine(cx + cos * outer, cy + sin * outer,
                    cx + cos * (outer - len), cy + sin * (outer - len), tick);
        }

        // inner ring: minutes of the current hour
        float ir = outer - 24 * d;
        oval.set(cx - ir, cy - ir, cx + ir, cy + ir);
        canvas.drawArc(oval, 0, 360, false, innerTrack);
        float minFrac = (ms % 3600000L) / 3600000f;
        if (minFrac > 0) {
            canvas.drawArc(oval, -90, Math.max(1f, 360 * minFrac), false, inner);
        }

        // where the current lap began, on the seconds track
        if (lapStart >= 0) {
            double a = angle((lapStart % 60000L) / 60000f);
            float r = outer + 7 * d;
            canvas.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                    3 * d, lapMark);
        }

        // knob riding on the current second (sits at 12 when ready)
        double a = angle(sec / 60f);
        float kr = outer - 6 * d;
        float kx = cx + (float) Math.cos(a) * kr;
        float ky = cy + (float) Math.sin(a) * kr;
        float size = 9 * d;
        canvas.drawCircle(kx, ky + 2 * d, size, knobShadow);
        knob.setColor(Color.WHITE);
        canvas.drawCircle(kx, ky, size, knob);
        knob.setColor(running || ms > 0 ? accent : offMajor);
        canvas.drawCircle(kx, ky, size * 0.45f, knob);
    }
}
