package dev.matteo.glass;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/** Shared metrics, fonts and motion. Everything is in dp of a 960x540 dp TV screen. */
final class Ui {
    static float density;
    static Typeface light, semibold, medium;

    static void init(Context c) {
        if (light != null) return;
        Resources r = c.getResources();
        density = r.getDisplayMetrics().density;
        light = r.getFont(R.font.display_light);
        semibold = r.getFont(R.font.display_semibold);
        medium = r.getFont(R.font.text_medium);
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, Typeface face, float sp, int color) {
        return style(new TextView(c), face, sp, color);
    }

    static <T extends TextView> T style(T t, Typeface face, float sp, int color) {
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    /** Damped spring over the animation's duration: zeta below 1 overshoots a little, like tvOS focus. */
    static final class Spring implements TimeInterpolator {
        private static final float W = 10f;
        private final float z, wd;

        Spring(float zeta) {
            z = zeta;
            wd = (float) (W * Math.sqrt(1 - zeta * zeta));
        }

        @Override
        public float getInterpolation(float t) {
            if (t >= 1f) return 1f;
            double e = Math.exp(-z * W * t);
            return (float) (1 - e * (Math.cos(wd * t) + (z * W / wd) * Math.sin(wd * t)));
        }
    }

    static final TimeInterpolator FOCUS = new Spring(0.7f);
    static final TimeInterpolator SCROLL = new Spring(0.9f);

    private Ui() {}
}
