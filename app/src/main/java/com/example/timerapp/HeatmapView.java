package com.example.timerapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.List;

/**
 * One month as a calendar (Monday first): each day is a rounded square with its date,
 * filled darker the more was studied. Tapping a day reports it through the listener.
 */
public class HeatmapView extends View {

    /** Minutes at which a day reaches each darker step. */
    public static final int[] STEPS = {1, 30, 60, 120};

    public interface OnDayListener {
        void onDay(long dayStart, long millis);
    }

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private final float gap;
    private final float headH;
    private int[] levels = new int[5];
    private int textMain;
    private int textSub;
    /** First day of the shown month, local midnight. */
    private long monthStart;
    private int daysInMonth;
    /** 0 = Monday ... 6 = Sunday, for the 1st. */
    private int offset;
    private long[] days = new long[31];
    private int today = -1;
    private int selected = -1;
    private OnDayListener listener;

    public HeatmapView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        gap = 5 * d;
        headH = 22 * d;
        float sp = c.getResources().getDisplayMetrics().scaledDensity;
        num.setTextSize(12 * sp);
        num.setTypeface(Clay.font(c));
        num.setTextAlign(Paint.Align.CENTER);
        head.setTextSize(11 * sp);
        head.setTypeface(Clay.font(c));
        head.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * d);
    }

    public void setColors(int empty, int accent, int surface, int main, int sub) {
        levels = new int[]{empty, Clay.mix(surface, accent, 0.3f), Clay.mix(surface, accent, 0.55f),
                Clay.mix(surface, accent, 0.8f), accent};
        textMain = main;
        textSub = sub;
        head.setColor(sub);
        ring.setColor(main);
        invalidate();
    }

    public void setListener(OnDayListener l) {
        listener = l;
    }

    /** Shows the month containing `anyDay`, filled from the history. */
    public void setMonth(long anyDay, List<PrefsStore.Hist> history) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(anyDay);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        monthStart = cal.getTimeInMillis();
        daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        long firstKey = Insights.dayKey(monthStart);
        days = new long[daysInMonth];
        for (PrefsStore.Hist h : history) {
            long i = Insights.dayKey(h.ts) - firstKey;
            if (i >= 0 && i < daysInMonth) {
                days[(int) i] += h.millis;
            }
        }
        long t = Insights.dayKey(System.currentTimeMillis()) - firstKey;
        today = t >= 0 && t < daysInMonth ? (int) t : -1;
        selected = -1;
        requestLayout();
        invalidate();
    }

    public long monthStart() {
        return monthStart;
    }

    /** Total studied in the shown month. */
    public long monthTotal() {
        long s = 0;
        for (long v : days) {
            s += v;
        }
        return s;
    }

    /** Days in the shown month with at least a minute of study. */
    public int daysStudied() {
        int n = 0;
        for (long v : days) {
            if (v >= 60_000L) {
                n++;
            }
        }
        return n;
    }

    public static int level(long ms) {
        long m = ms / 60_000L;
        int l = 0;
        for (int i = 0; i < STEPS.length; i++) {
            if (m >= STEPS[i]) {
                l = i + 1;
            }
        }
        return l;
    }

    private int rows() {
        return (offset + daysInMonth + 6) / 7;
    }

    private float cellSize() {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        return (w - 6 * gap) / 7f;
    }

    private boolean rtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /** Left edge of a column (0 = Monday), mirrored in right-to-left languages. */
    private float colX(int col) {
        int c = rtl() ? 6 - col : col;
        return getPaddingLeft() + c * (cellSize() + gap);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float cs = (w - getPaddingLeft() - getPaddingRight() - 6 * gap) / 7f;
        // squares, but never taller than needed on wide screens
        cs = Math.min(cs, 46 * d);
        int rows = Math.max(5, rows());
        int h = (int) (getPaddingTop() + getPaddingBottom() + headH + rows * cs + (rows - 1) * gap);
        setMeasuredDimension(w, h);
    }

    private float rowY(int row) {
        float cs = Math.min(cellSize(), 46 * d);
        return getPaddingTop() + headH + row * (cs + gap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cs = cellSize();
        float ch = Math.min(cs, 46 * d);
        // weekday initials, Monday first
        String[] wd = new DateFormatSymbols().getShortWeekdays();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        for (int col = 0; col < 7; col++) {
            String s = wd[order[col]];
            if (s.length() > 3) {
                s = s.substring(0, 3);
            }
            canvas.drawText(s, colX(col) + cs / 2, getPaddingTop() + 13 * d, head);
        }
        float radius = 8 * d;
        Paint.FontMetrics fm = num.getFontMetrics();
        for (int i = 0; i < daysInMonth; i++) {
            int pos = offset + i;
            int col = pos % 7;
            int row = pos / 7;
            float x = colX(col);
            float y = rowY(row);
            r.set(x, y, x + cs, y + ch);
            boolean future = today >= 0 && i > today
                    || monthStart > System.currentTimeMillis();
            int lvl = future ? 0 : level(days[i]);
            cell.setColor(levels[lvl]);
            cell.setAlpha(future ? 110 : 255);
            canvas.drawRoundRect(r, radius, radius, cell);
            num.setColor(lvl >= 3 ? 0xFFFFFFFF : (future ? textSub : textMain));
            num.setAlpha(future ? 140 : 255);
            canvas.drawText(String.valueOf(i + 1), r.centerX(),
                    r.centerY() - (fm.ascent + fm.descent) / 2, num);
            if (i == selected || (selected < 0 && i == today)) {
                r.inset(-1.5f * d, -1.5f * d);
                canvas.drawRoundRect(r, radius + d, radius + d, ring);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float cs = cellSize();
            float ch = Math.min(cs, 46 * d);
            int col = (int) ((e.getX() - getPaddingLeft()) / (cs + gap));
            int row = (int) ((e.getY() - getPaddingTop() - headH) / (ch + gap));
            if (rtl()) {
                col = 6 - col;
            }
            int i = row * 7 + col - offset;
            if (col >= 0 && col < 7 && row >= 0 && i >= 0 && i < daysInMonth) {
                selected = i;
                invalidate();
                performClick();
                if (listener != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(monthStart);
                    cal.add(Calendar.DAY_OF_MONTH, i);
                    listener.onDay(cal.getTimeInMillis(), days[i]);
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
