package io.github.b0reddev.glass;

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
 * Liquid Glass panel: shows the backdrop's blurred copy at its own position, the theme's fill and a
 * rim brighter at the top edge. Focused, it becomes a bright platter with dark content (light theme,
 * as tvOS buttons do) or gets a blue ring (dark theme).
 */
class GlassFrame extends LinearLayout {
    static final int LIGHT = 0xFFFFFFFF, DARK = 0xFF111318;

    private final Backdrop backdrop;
    private final float radius;
    private final boolean sample;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final int[] loc = new int[2], bloc = new int[2];
    private boolean panel, bright;
    private Theme rimTheme;

    /** @param sample false for rows inside a panel: they only draw their fill and focus, the panel is the glass. */
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
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(2.5f));
    }

    /** Menus and the settings panel: a denser fill than pills and cards. */
    void setPanel() {
        panel = true;
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
        rimTheme = null;
    }

    @Override
    protected void onDraw(Canvas c) {
        Theme t = Theme.current;
        float half = rim.getStrokeWidth() / 2;
        r.set(half, half, getWidth() - half, getHeight() - half);
        if (sample && !bright) {
            // Untransformed position: the parent's window position plus our offset (minus its scroll, for
            // cards in a scrolled row), so a focus zoom magnifies the glass a little instead of sliding it.
            View p = (View) getParent();
            p.getLocationInWindow(loc);
            backdrop.getLocationInWindow(bloc);
            float sx = (float) backdrop.getWidth() / Backdrop.GW, sy = (float) backdrop.getHeight() / Backdrop.GH;
            m.setScale(sx, sy);
            m.postTranslate(bloc[0] - loc[0] - getLeft() + p.getScrollX(), bloc[1] - loc[1] - getTop() + p.getScrollY());
            glass.getShader().setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, glass);
        }
        if (bright) {
            fill.setColor(0xF2FFFFFF);
            c.drawRoundRect(r, radius, radius, fill);
            return;
        }
        int f = !sample ? t.rowFill : panel ? t.panelFill : t.glassFill;
        if (f != 0) {
            fill.setColor(f);
            c.drawRoundRect(r, radius, radius, fill);
        }
        if (sample) {
            if (rimTheme != t) {
                rimTheme = t;
                rim.setShader(new LinearGradient(0, 0, 0, getHeight(), t.rimTop, t.rimBottom, Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, radius, radius, rim);
        }
        if (t.ring != 0 && isFocused()) {
            fill.setColor(t.ringFill());
            c.drawRoundRect(r, radius, radius, fill);
            ring.setColor(t.ring);
            drawRing(c, this, ring, radius, sample);
        }
    }

    /**
     * Dark theme focus: a ring 3 dp outside the view (pills, cards, tiles: their parents don't clip
     * children), or on its edge for rows inside a panel, which does clip.
     */
    static void drawRing(Canvas c, View v, Paint ring, float radius, boolean outside) {
        float half = ring.getStrokeWidth() / 2, out = outside ? Ui.dp(3) + half : -half;
        float rr = radius + out;
        c.drawRoundRect(-out, -out, v.getWidth() + out, v.getHeight() + out, rr, rr, ring);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        boolean zoom = Theme.current.ring == 0;
        bright = focused && zoom;
        recolor(this, bright ? DARK : LIGHT);
        if (sample) {
            float s = bright ? 1.08f : 1f;
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
