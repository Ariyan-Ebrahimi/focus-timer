package com.example.timerapp;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/**
 * Clay progress dial: a soft pushed-in groove, a rounded gradient arc and a puffy
 * white knob riding on the leading edge. The disc behind it is the view background.
 */
public class RingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grooveShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 1f;
    private float stroke;
    private float inset;
    private final float density;
    private int colorStart;
    private int colorEnd;

    public RingView(Context context) {
        this(context, null);
    }

    public RingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        stroke = 18 * density;
        inset = 22 * density;
        colorStart = ThemeHelper.resolve(context, R.attr.accent);
        colorEnd = ThemeHelper.resolve(context, R.attr.accent2);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ThemeHelper.resolve(context, R.attr.trackC));
        grooveShade.setStyle(Paint.Style.STROKE);
        grooveShade.setColor(Color.argb(40, 30, 20, 80));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        knobPaint.setColor(Color.WHITE);
        knobShadow.setColor(Color.argb(70, 30, 20, 80));
        applyWidths();
    }

    private void applyWidths() {
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);
        grooveShade.setStrokeWidth(stroke * 0.35f);
        grooveShade.setMaskFilter(new BlurMaskFilter(stroke * 0.25f, BlurMaskFilter.Blur.NORMAL));
        knobShadow.setMaskFilter(new BlurMaskFilter(stroke * 0.3f, BlurMaskFilter.Blur.NORMAL));
    }

    /** Ring thickness and its distance from the view edge, in dp. */
    public void setRing(float strokeDp, float insetDp) {
        stroke = strokeDp * density;
        inset = insetDp * density;
        applyWidths();
        layoutOval(getWidth(), getHeight());
        invalidate();
    }

    public void setColors(int track, int start, int end) {
        trackPaint.setColor(track);
        colorStart = start;
        colorEnd = end;
        buildShader();
        invalidate();
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    private void layoutOval(int w, int h) {
        float pad = inset + stroke / 2f;
        oval.set(pad, pad, w - pad, h - pad);
        buildShader();
    }

    private void buildShader() {
        if (oval.isEmpty()) {
            return;
        }
        SweepGradient g = new SweepGradient(oval.centerX(), oval.centerY(),
                new int[] {colorStart, colorEnd, colorStart}, null);
        Matrix m = new Matrix();
        m.setRotate(-90, oval.centerX(), oval.centerY());
        g.setLocalMatrix(m);
        progressPaint.setShader(g);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutOval(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = oval.centerX();
        float cy = oval.centerY();
        float r = oval.width() / 2f;
        canvas.drawArc(oval, 0, 360, false, trackPaint);
        // soft shade along the inner top of the groove makes it read as pushed in
        float s = stroke * 0.18f;
        canvas.save();
        canvas.translate(s, s);
        canvas.drawArc(oval, 180, 90, false, grooveShade);
        canvas.restore();
        if (progress <= 0) {
            return;
        }
        canvas.drawArc(oval, -90, Math.max(0.5f, 360 * progress), false, progressPaint);
        double a = Math.toRadians(-90 + 360 * progress);
        float kx = cx + r * (float) Math.cos(a);
        float ky = cy + r * (float) Math.sin(a);
        float kr = stroke * 0.42f;
        canvas.drawCircle(kx, ky + kr * 0.35f, kr, knobShadow);
        canvas.drawCircle(kx, ky, kr, knobPaint);
    }
}
