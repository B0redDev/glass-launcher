package dev.matteo.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Liquid Glass panel: shows the backdrop's blurred copy at its own position, a faint white tint and a
 * light rim brighter at the top edge. Focusable panels (pills, menu rows) turn into a bright platter
 * with dark content when focused, as tvOS buttons do.
 */
class GlassFrame extends LinearLayout {
    static final int LIGHT = 0xFFFFFFFF, DARK = 0xFF111318;

    private final Backdrop backdrop;
    private final float radius;
    private final boolean sample;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final int[] loc = new int[2], bloc = new int[2];
    private float tint = 0.10f;
    private boolean bright;

    /** @param sample false for rows inside a panel: they only draw the focus platter, the panel is the glass. */
    GlassFrame(Context c, Backdrop backdrop, float radius, boolean sample) {
        super(c);
        this.backdrop = backdrop;
        this.radius = radius;
        this.sample = sample;
        setWillNotDraw(false);
        setDefaultFocusHighlightEnabled(false);
        glass.setShader(new BitmapShader(backdrop.glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(1));
    }

    void setTint(float t) {
        tint = t;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sample) backdrop.addClient(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        backdrop.removeClient(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rim.setShader(new LinearGradient(0, 0, 0, h, 0x8CFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float half = rim.getStrokeWidth() / 2;
        r.set(half, half, getWidth() - half, getHeight() - half);
        if (sample && !bright) {
            // Untransformed position: the parent's window position plus our offset, so a focus zoom
            // magnifies the glass a little instead of sliding it.
            ((View) getParent()).getLocationInWindow(loc);
            backdrop.getLocationInWindow(bloc);
            float sx = (float) backdrop.getWidth() / Backdrop.GW, sy = (float) backdrop.getHeight() / Backdrop.GH;
            m.setScale(sx, sy);
            m.postTranslate(bloc[0] - loc[0] - getLeft(), bloc[1] - loc[1] - getTop());
            glass.getShader().setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, glass);
        }
        if (bright) {
            fill.setColor(0xF2FFFFFF);
            c.drawRoundRect(r, radius, radius, fill);
        } else if (tint > 0) {
            fill.setColor((int) (tint * 255) << 24 | 0xFFFFFF);
            c.drawRoundRect(r, radius, radius, fill);
            if (sample) c.drawRoundRect(r, radius, radius, rim);
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        bright = focused;
        recolor(this, focused ? DARK : LIGHT);
        if (sample) {
            float s = focused ? 1.08f : 1f;
            animate().scaleX(s).scaleY(s).setInterpolator(Ui.FOCUS).setDuration(380).start();
        }
        invalidate();
    }

    private static void recolor(ViewGroup g, int color) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.setTextColor((color & 0x00FFFFFF) | (t.getCurrentTextColor() & 0xFF000000));
            } else if (v instanceof ImageView) {
                ((ImageView) v).setImageTintList(ColorStateList.valueOf(color));
            } else if (v instanceof ViewGroup) {
                recolor((ViewGroup) v, color);
            }
        }
    }
}
