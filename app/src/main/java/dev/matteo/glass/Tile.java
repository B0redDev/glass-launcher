package dev.matteo.glass;

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

/** One app: its banner with rounded corners, a light rim, and on focus a lift, a gloss sweep and its name. */
@SuppressLint("ViewConstructor")
final class Tile extends View {
    static final float FOCUSED = 1.12f, LIFTED = 1.2f;

    final App app;
    Bitmap ambient;
    private Bitmap art;
    private boolean banner, lifted;
    private final int w, h;
    private final float radius;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), icon = new RectF();
    private final Matrix m = new Matrix();
    private final String name, smallName;
    private float labelAlpha, sheen = -1f;
    private ValueAnimator focusAnim;

    Tile(Context c, App app, int w, int h) {
        super(c);
        this.app = app;
        this.w = w;
        this.h = h;
        radius = Ui.dp(12);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, w, h, radius);
            }
        });
        base.setShader(new LinearGradient(0, 0, 0, h, 0xFF2C3242, 0xFF191C26, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(1));
        rim.setShader(new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP));
        gloss.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, w * 0.45f, h * 0.25f,
                new int[]{0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, c.getResources().getDisplayMetrics());
        label.setTypeface(Ui.medium);
        label.setTextSize(13 * sp);
        label.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setShadowLayer(Ui.dp(6), 0, Ui.dp(1), 0x99000000);
        small.set(label);
        small.setTextSize(11 * sp);
        small.clearShadowLayer();
        small.setColor(0xD9FFFFFF);
        name = TextUtils.ellipsize(app.label, label, w * 1.3f, TextUtils.TruncateAt.END).toString();
        smallName = TextUtils.ellipsize(app.label, small, w * 0.85f, TextUtils.TruncateAt.END).toString();
    }

    void setArt(Bitmap b, boolean isBanner, Bitmap amb) {
        art = b;
        banner = isBanner;
        ambient = amb;
        if (isBanner) {
            BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            m.setScale((float) w / b.getWidth(), (float) h / b.getHeight());
            s.setLocalMatrix(m);
            artPaint.setShader(s);
        } else {
            float s = h * 0.46f;
            icon.set((w - s) / 2, h * 0.16f, (w + s) / 2, h * 0.16f + s);
        }
        invalidate();
    }

    void setLifted(boolean l) {
        lifted = l;
        float s = l ? LIFTED : (isFocused() ? FOCUSED : 1f);
        animate().scaleX(s).scaleY(s).translationZ(Ui.dp(l ? 28 : (isFocused() ? 14 : 0)))
                .setInterpolator(Ui.FOCUS).setDuration(420).start();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(w, h + Ui.dp(34));
    }

    @Override
    protected void onFocusChanged(boolean f, int dir, Rect prev) {
        super.onFocusChanged(f, dir, prev);
        if (!lifted) {
            float s = f ? FOCUSED : 1f;
            animate().scaleX(s).scaleY(s).translationZ(f ? Ui.dp(14) : 0)
                    .setInterpolator(Ui.FOCUS).setDuration(420).start();
        }
        if (focusAnim != null) focusAnim.cancel();
        final float start = labelAlpha;
        focusAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(f ? 800 : 180);
        focusAnim.setInterpolator(new DecelerateInterpolator());
        focusAnim.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            labelAlpha = f ? Math.min(1f, start + t * 4 * (1 - start)) : start * (1 - t);
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
        if (labelAlpha > 0) {
            gloss.setAlpha((int) (255 * labelAlpha));
            c.drawRoundRect(r, radius, radius, gloss);
        }
        if (sheen >= 0) {
            m.setTranslate(-w * 0.6f + sheen * w * 2.2f, 0);
            sheenPaint.getShader().setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, sheenPaint);
        }
        float half = rim.getStrokeWidth() / 2;
        r.inset(half, half);
        rim.setAlpha(38 + (int) (120 * labelAlpha));
        c.drawRoundRect(r, radius, radius, rim);
        if (labelAlpha > 0) {
            label.setAlpha((int) (255 * labelAlpha));
            c.drawText(name, w / 2f, h + Ui.dp(26), label);
        }
    }
}
