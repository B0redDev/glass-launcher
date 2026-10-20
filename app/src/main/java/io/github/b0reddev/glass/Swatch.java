package io.github.b0reddev.glass;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A theme's thumbnail in the settings panel: its wallpaper, a sketch of the rows, a check when it's the active one. */
@SuppressLint("ViewConstructor")
final class Swatch extends View {
    final Theme theme;
    private final int w = Ui.dp(138), h = Ui.dp(77.6f);
    private final float radius = Ui.dp(12);
    private final Paint wall = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable check;
    private final RectF r = new RectF();

    Swatch(Context c, Theme theme) {
        super(c);
        this.theme = theme;
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        Bitmap b = theme.wallpaper(w / 2, h / 2);
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(2, 2);
        s.setLocalMatrix(m);
        wall.setShader(s);
        stroke.setStyle(Paint.Style.STROKE);
        check = c.getDrawable(R.drawable.ic_check).mutate();
        check.setTint(GlassFrame.DARK);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onFocusChanged(boolean f, int dir, Rect prev) {
        super.onFocusChanged(f, dir, prev);
        float s = f && Theme.current.ring == 0 ? 1.06f : 1f;
        animate().scaleX(s).scaleY(s).setInterpolator(Ui.FOCUS).setDuration(380).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        r.set(0, 0, w, h);
        c.drawRoundRect(r, radius, radius, wall);
        // Rows: three favourites, then two rows of smaller tiles.
        float x0 = Ui.dp(10), y = Ui.dp(12), gap = Ui.dp(4);
        paint.setColor(0xBFFFFFFF);
        for (int i = 0; i < 3; i++) bar(c, x0 + i * (Ui.dp(31) + gap), y, Ui.dp(31), Ui.dp(17));
        paint.setColor(0x80FFFFFF);
        for (int row = 0; row < 2; row++) {
            float ry = y + Ui.dp(17) + Ui.dp(5) + row * (Ui.dp(12) + Ui.dp(5));
            for (int i = 0; i < 4; i++) bar(c, x0 + i * (Ui.dp(22) + gap), ry, Ui.dp(22), Ui.dp(12));
        }
        stroke.setStrokeWidth(Ui.dp(1));
        stroke.setColor(0x26FFFFFF);
        r.inset(Ui.dp(0.5f), Ui.dp(0.5f));
        c.drawRoundRect(r, radius, radius, stroke);
        if (Theme.current == theme) {
            float cx = w - Ui.dp(16), cy = Ui.dp(16);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, Ui.dp(10), paint);
            int half = Ui.dp(7);
            check.setBounds((int) cx - half, (int) cy - half, (int) cx + half, (int) cy + half);
            check.draw(c);
        }
        if (!isFocused()) return;
        Theme t = Theme.current;
        if (t.ring != 0) {
            stroke.setStrokeWidth(Ui.dp(2.5f));
            stroke.setColor(t.ring);
            GlassFrame.drawRing(c, this, stroke, radius, true);
        } else {
            stroke.setStrokeWidth(Ui.dp(2));
            stroke.setColor(0xF2FFFFFF);
            float o = Ui.dp(1);
            c.drawRoundRect(-o, -o, w + o, h + o, radius + o, radius + o, stroke);
        }
    }

    private void bar(Canvas c, float x, float y, float bw, float bh) {
        c.drawRoundRect(x, y, x + bw, y + bh, Ui.dp(3), Ui.dp(3), paint);
    }
}
