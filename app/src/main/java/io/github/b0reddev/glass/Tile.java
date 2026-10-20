package io.github.b0reddev.glass;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

/** One app: its banner with rounded corners and a light rim; on focus a lift and a gloss sweep. */
@SuppressLint("ViewConstructor")
final class Tile extends View {
    static final float FOCUSED = 1.12f, LIFTED = 1.2f;

    final App app;
    Bitmap ambient;
    private Bitmap art;
    private boolean banner, lifted;
    private int w, h;
    private final float radius;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), icon = new RectF();
    private final Matrix m = new Matrix();
    private String smallName;
    private float glow, sheen = -1f;
    private ValueAnimator focusAnim;

    Tile(Context c, App app, int w, int h) {
        super(c);
        this.app = app;
        radius = Ui.dp(12);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, Tile.this.w, Tile.this.h, radius);
            }
        });
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(1));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(2.5f));
        small.setTypeface(Ui.medium);
        small.setTextSize(11 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, c.getResources().getDisplayMetrics()));
        small.setColor(0xD9FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        setSize(w, h);
    }

    int tileHeight() {
        return h;
    }

    /** Rows use different sizes; an app moved between them is resized in place. */
    void setSize(int w, int h) {
        if (w == this.w && h == this.h) return;
        this.w = w;
        this.h = h;
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        base.setShader(new LinearGradient(0, 0, 0, h, 0xFF2C3242, 0xFF191C26, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP));
        gloss.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, w * 0.45f, h * 0.25f,
                new int[]{0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        smallName = TextUtils.ellipsize(app.label, small, w * 0.85f, TextUtils.TruncateAt.END).toString();
        if (art != null) fitArt();
        invalidateOutline();
        requestLayout();
        invalidate();
    }

    void setArt(Bitmap b, boolean isBanner, Bitmap amb) {
        art = b;
        banner = isBanner;
        ambient = amb;
        if (isBanner) artPaint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        fitArt();
        invalidate();
    }

    private void fitArt() {
        if (banner) {
            m.setScale((float) w / art.getWidth(), (float) h / art.getHeight());
            artPaint.getShader().setLocalMatrix(m);
        } else {
            float s = h * 0.46f;
            icon.set((w - s) / 2, h * 0.16f, (w + s) / 2, h * 0.16f + s);
        }
    }

    void setLifted(boolean l) {
        lifted = l;
        float s = l ? LIFTED : (isFocused() ? Theme.current.tileScale : 1f);
        animate().scaleX(s).scaleY(s).translationZ(Ui.dp(l ? 28 : (isFocused() ? 14 : 0)))
                .setInterpolator(Ui.FOCUS).setDuration(420).start();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onFocusChanged(boolean f, int dir, Rect prev) {
        super.onFocusChanged(f, dir, prev);
        if (!lifted) {
            float s = f ? Theme.current.tileScale : 1f;
            animate().scaleX(s).scaleY(s).translationZ(f ? Ui.dp(14) : 0)
                    .setInterpolator(Ui.FOCUS).setDuration(420).start();
        }
        if (focusAnim != null) focusAnim.cancel();
        final float start = glow;
        focusAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(f ? 800 : 180);
        focusAnim.setInterpolator(new DecelerateInterpolator());
        focusAnim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            glow = f ? Math.min(1f, start + t * 4 * (1 - start)) : start * (1 - t);
            sheen = f && t < 1f ? t : -1f;
            invalidate();
        });
        focusAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        r.set(0, 0, w, h);
        if (art == null) {
            base.setAlpha(140);
            c.drawRoundRect(r, radius, radius, base);
            base.setAlpha(255);
        } else if (banner) {
            c.drawRoundRect(r, radius, radius, artPaint);
        } else {
            c.drawRoundRect(r, radius, radius, base);
            c.drawBitmap(art, null, icon, artPaint);
            c.drawText(smallName, w / 2f, h * 0.88f, small);
        }
        Theme t = Theme.current;
        boolean zoom = t.ring == 0;
        if (zoom && glow > 0) {
            gloss.setAlpha((int) (255 * glow));
            c.drawRoundRect(r, radius, radius, gloss);
        }
        if (zoom && sheen >= 0) {
            m.setTranslate(-w * 0.6f + sheen * w * 2.2f, 0);
            sheenPaint.getShader().setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, sheenPaint);
        }
        float half = rim.getStrokeWidth() / 2;
        r.inset(half, half);
        rim.setAlpha(38 + (zoom ? (int) (120 * glow) : 0));
        c.drawRoundRect(r, radius, radius, rim);
        if (!zoom && isFocused()) {
            ring.setColor(t.ring);
            GlassFrame.drawRing(c, this, ring, radius, true);
        }
    }
}
