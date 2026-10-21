package io.github.b0reddev.glass;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A theme choice's thumbnail in the settings panel: its wallpaper (Automatic: light and dark split on a slant),
 * a sketch of the rows, and a check when it's the saved choice.
 */
@SuppressLint("ViewConstructor")
final class Swatch extends View {
    final String choice;
    private final int w = Ui.dp(104), h = Ui.dp(58.5f);
    private final float radius = Ui.dp(10), u = w / (float) Ui.dp(138);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path slant = new Path();
    private final Drawable check;
    private final RectF r = new RectF();

    Swatch(Context c, String choice) {
        super(c);
        this.choice = choice;
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        shade(light, Theme.LIGHT);
        shade(dark, Theme.DARK);
        slant.moveTo(w * 0.58f, 0);
        slant.lineTo(w, 0);
        slant.lineTo(w, h);
        slant.lineTo(w * 0.42f, h);
        slant.close();
        stroke.setStyle(Paint.Style.STROKE);
        check = c.getDrawable(R.drawable.ic_check).mutate();
        check.setTint(GlassFrame.DARK);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
    }

    private void shade(Paint p, Theme t) {
        BitmapShader s = new BitmapShader(t.wallpaper(w / 2, h / 2), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(2, 2);
        s.setLocalMatrix(m);
        p.setShader(s);
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
        c.drawRoundRect(r, radius, radius, Theme.DARK.id.equals(choice) ? dark : light);
        if (Theme.AUTO.equals(choice)) {
            c.save();
            c.clipPath(slant);
            c.drawRoundRect(r, radius, radius, dark);
            c.restore();
        }
        // Rows: three favourites, then two rows of smaller tiles.
        float x0 = 10 * u, y = 12 * u, gap = 4 * u;
        paint.setColor(0xBFFFFFFF);
        for (int i = 0; i < 3; i++) bar(c, x0 + i * (31 * u + gap), y, 31 * u, 17 * u);
        paint.setColor(0x80FFFFFF);
        for (int row = 0; row < 2; row++) {
            float ry = y + 22 * u + row * 17 * u;
            for (int i = 0; i < 4; i++) bar(c, x0 + i * (22 * u + gap), ry, 22 * u, 12 * u);
        }
        stroke.setStrokeWidth(Ui.dp(1));
        stroke.setColor(0x26FFFFFF);
        r.inset(Ui.dp(0.5f), Ui.dp(0.5f));
        c.drawRoundRect(r, radius, radius, stroke);
        if (choice.equals(Theme.choice)) {
            float cx = w - Ui.dp(13), cy = Ui.dp(13);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, Ui.dp(8), paint);
            int half = Ui.dp(5.5f);
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
        c.drawRoundRect(x, y, x + bw, y + bh, 3 * u, 3 * u, paint);
    }
}
