package com.example.timerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.LruCache;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * Claymorphism toolkit: puffy surfaces (soft gradient, blurred inner highlight and
 * shade) plus tinted elevation shadows, pastel palette and the Nunito font.
 */
public final class Clay {

    private static final int[] PASTEL_LIGHT = {
            0xFFE6E1FF, 0xFFFFE4DA, 0xFFD5F5E9, 0xFFDCEBFF, 0xFFFFF2C7, 0xFFFFE0EE};
    private static final int[] PASTEL_DARK = {
            0xFF3A3570, 0xFF4B3029, 0xFF1F4638, 0xFF22395C, 0xFF4A4224, 0xFF4A2740};
    /** Accent colors in the order of the accent setting (light-mode values). */
    public static final int[] ACCENTS = {0xFF7C6CF0, 0xFFFF8A65, 0xFF2FBF8F, 0xFF4A9DF5};

    private static Typeface nunito;

    private Clay() {
    }

    // ---------------------------------------------------------------- palette

    public static boolean isDark(Context c) {
        return PrefsStore.getMode(c) == ThemeHelper.MODE_DARK;
    }

    public static int pastel(Context c, int i) {
        int[] p = isDark(c) ? PASTEL_DARK : PASTEL_LIGHT;
        return p[((i % p.length) + p.length) % p.length];
    }

    public static int accentColor(Context c) {
        int a = PrefsStore.getAcc(c);
        return ACCENTS[a >= 0 && a < ACCENTS.length ? a : 0];
    }

    static int mix(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    // ---------------------------------------------------------------- type

    public static Typeface font(Context c) {
        if (nunito == null) {
            nunito = c.getResources().getFont(R.font.nunito);
        }
        return nunito;
    }

    /** Applies Nunito at the given weight (200-1000). */
    public static void text(TextView t, int weight, float sp, int color) {
        t.setTypeface(font(t.getContext()));
        t.setFontVariationSettings("'wght' " + weight);
        // reading order from the text itself (English names stay intact in Persian/Arabic)
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        t.setTextSize(sp);
        t.setTextColor(color);
    }

    // ---------------------------------------------------------------- surfaces

    /** Rendered clay surfaces, shared across views and capped at ~24 MB. */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 << 20) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** A puffy clay surface. radiusDp < 0 means fully rounded (pill / circle). */
    public static class ClayDrawable extends Drawable {
        private final int fill;
        private final float radiusDp;
        private final boolean concave;
        private final float density;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final Path ring = new Path();
        private final RectF rect = new RectF();
        private float radius;
        private float depth;

        public ClayDrawable(Context c, int fill, float radiusDp, boolean concave) {
            this.fill = fill;
            this.radiusDp = radiusDp;
            this.concave = concave;
            this.density = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            rect.set(b);
            float minSide = Math.min(rect.width(), rect.height());
            radius = radiusDp < 0 ? minSide / 2f : Math.min(radiusDp * density, minSide / 2f);
            depth = Math.max(2f * density, Math.min(7f * density, minSide * 0.09f));
            shape.reset();
            shape.addRoundRect(rect, radius, radius, Path.Direction.CW);

            int top = concave ? mix(fill, Color.BLACK, 0.04f) : mix(fill, Color.WHITE, 0.10f);
            int bottom = concave ? mix(fill, Color.WHITE, 0.05f) : mix(fill, Color.BLACK, 0.03f);
            fillPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    top, bottom, Shader.TileMode.CLAMP));
            shadePaint.setMaskFilter(new BlurMaskFilter(depth * 1.4f, BlurMaskFilter.Blur.NORMAL));
        }

        private void inner(Canvas canvas, float dx, float dy, int color) {
            ring.reset();
            ring.setFillType(Path.FillType.EVEN_ODD);
            ring.addRect(rect.left - rect.width(), rect.top - rect.height(),
                    rect.right + rect.width(), rect.bottom + rect.height(), Path.Direction.CW);
            ring.addRoundRect(new RectF(rect.left + dx, rect.top + dy,
                    rect.right + dx, rect.bottom + dy), radius, radius, Path.Direction.CW);
            shadePaint.setColor(color);
            canvas.drawPath(ring, shadePaint);
        }

        @Override
        public void draw(Canvas canvas) {
            if (rect.isEmpty()) {
                return;
            }
            // Blurred inner shading is costly to redraw every frame (e.g. while scrolling),
            // so each surface is rendered once into a bitmap shared by identical surfaces.
            int w = (int) Math.ceil(rect.width());
            int h = (int) Math.ceil(rect.height());
            String key = fill + ":" + w + "x" + h + ":" + radius + ":" + concave;
            Bitmap bmp = CACHE.get(key);
            if (bmp == null) {
                try {
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError e) {
                    paint(canvas);
                    return;
                }
                Canvas c = new Canvas(bmp);
                c.translate(-rect.left, -rect.top);
                paint(c);
                CACHE.put(key, bmp);
            }
            canvas.drawBitmap(bmp, rect.left, rect.top, null);
        }

        private void paint(Canvas canvas) {
            canvas.drawPath(shape, fillPaint);
            boolean darkFill = luminance(fill) < 0.45f;
            int light = Color.argb(darkFill ? 28 : 210, 255, 255, 255);
            int dark = darkFill ? Color.argb(100, 0, 0, 0) : Color.argb(22, 20, 22, 40);
            canvas.save();
            canvas.clipPath(shape);
            if (concave) {
                inner(canvas, depth, depth, dark);
                inner(canvas, -depth * 0.7f, -depth * 0.7f, light);
            } else {
                inner(canvas, depth, depth, light);
                inner(canvas, -depth, -depth, dark);
            }
            canvas.restore();
        }

        @Override
        public void getOutline(Outline outline) {
            Rect b = getBounds();
            outline.setRoundRect(b.left, b.top, b.right, b.bottom, radius);
            outline.setAlpha(1f);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    /** Clay background + soft tinted shadow. Pressable surfaces sink in while touched. */
    public static void style(View v, int fill, float radiusDp, float elevationDp,
                             boolean pressable) {
        Context c = v.getContext();
        Drawable d = new ClayDrawable(c, fill, radiusDp, false);
        if (pressable) {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[] {android.R.attr.state_pressed},
                    new ClayDrawable(c, fill, radiusDp, true));
            s.addState(new int[0], d);
            d = s;
            v.setClickable(true);
            v.setOnTouchListener(new Squish());
        }
        v.setBackground(d);
        v.setElevation(dp(c, elevationDp));
        if (Build.VERSION.SDK_INT >= 28 && elevationDp > 0) {
            int sh = isDark(c) ? Color.BLACK : mix(fill, 0xFF1F2128, 0.7f);
            v.setOutlineSpotShadowColor(sh);
            v.setOutlineAmbientShadowColor(sh);
        }
    }

    /**
     * Tactile press: a soft squish once the touch is clearly a tap (not the start of a
     * scroll) and a light haptic tick when it is released as a tap.
     */
    private static class Squish implements View.OnTouchListener {
        private float base = 1f;
        private boolean squished;
        private View target;
        private final Runnable press = new Runnable() {
            @Override
            public void run() {
                if (target != null && target.isPressed()) {
                    squished = true;
                    target.animate().scaleX(base * 0.95f).scaleY(base * 0.95f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    target = v;
                    if (!squished) {
                        base = v.getScaleX();
                    }
                    v.removeCallbacks(press);
                    v.postDelayed(press, 70);
                    break;
                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(press);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    release(v);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(press);
                    release(v);
                    break;
                default:
                    break;
            }
            return false;
        }

        private void release(View v) {
            if (squished) {
                squished = false;
                v.animate().scaleX(base).scaleY(base).setDuration(240)
                        .setInterpolator(new OvershootInterpolator(2.2f)).start();
            }
        }
    }

    /** Pushed-in (concave) well, used for tracks, inputs and segmented controls. */
    public static void inset(View v, int fill, float radiusDp) {
        v.setBackground(new ClayDrawable(v.getContext(), fill, radiusDp, true));
        v.setElevation(0);
    }

    /**
     * Styles every view tagged "clay:FILL:RADIUS:ELEVATION[:p]" under root. FILL is one of
     * surface, surface2, accent, soft, inset, or pN (pastel N steps from the accent).
     */
    public static void applyTags(View root) {
        Object tag = root.getTag();
        if (tag instanceof String && ((String) tag).startsWith("clay:")) {
            String[] p = ((String) tag).split(":");
            Context c = root.getContext();
            String key = p[1];
            float r = Float.parseFloat(p[2]);
            float e = Float.parseFloat(p[3]);
            boolean press = p.length > 4 && "p".equals(p[4]);
            if ("inset".equals(key)) {
                inset(root, ThemeHelper.resolve(c, R.attr.surface2), r);
            } else {
                int fill;
                if ("accent".equals(key)) {
                    fill = ThemeHelper.resolve(c, R.attr.accent);
                } else if ("soft".equals(key)) {
                    fill = ThemeHelper.resolve(c, R.attr.accentSoft);
                } else if ("surface2".equals(key)) {
                    fill = ThemeHelper.resolve(c, R.attr.surface2);
                } else if (key.startsWith("p")) {
                    fill = pastel(c, PrefsStore.getAcc(c) + Integer.parseInt(key.substring(1)));
                } else {
                    fill = ThemeHelper.resolve(c, R.attr.surface);
                }
                style(root, fill, r, e, press);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTags(g.getChildAt(i));
            }
        }
    }
}
