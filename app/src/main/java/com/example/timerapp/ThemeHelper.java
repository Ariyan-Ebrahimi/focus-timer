package com.example.timerapp;

import android.content.Context;
import android.util.TypedValue;

public final class ThemeHelper {

    public static final int MODE_LIGHT = 0;
    public static final int MODE_DARK = 1;

    public static final int ACC_BLUE = 0;
    public static final int ACC_TEAL = 1;
    public static final int ACC_PURPLE = 2;
    public static final int ACC_ORANGE = 3;

    public static final int[] ACC_DOTS = {
            R.drawable.dot_blue, R.drawable.dot_teal,
            R.drawable.dot_purple, R.drawable.dot_orange
    };

    public static final int[] ACC_DOTS_SEL = {
            R.drawable.dot_blue_sel, R.drawable.dot_teal_sel,
            R.drawable.dot_purple_sel, R.drawable.dot_orange_sel
    };

    private ThemeHelper() {
    }

    public static int themeRes(int mode, int acc) {
        if (mode == MODE_DARK) {
            switch (acc) {
                case ACC_TEAL:
                    return R.style.AppDark_Teal;
                case ACC_PURPLE:
                    return R.style.AppDark_Purple;
                case ACC_ORANGE:
                    return R.style.AppDark_Orange;
                default:
                    return R.style.AppDark_Blue;
            }
        }
        switch (acc) {
            case ACC_TEAL:
                return R.style.AppLight_Teal;
            case ACC_PURPLE:
                return R.style.AppLight_Purple;
            case ACC_ORANGE:
                return R.style.AppLight_Orange;
            default:
                return R.style.AppLight_Blue;
        }
    }

    public static int resolve(Context c, int attr) {
        TypedValue v = new TypedValue();
        c.getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }
}
