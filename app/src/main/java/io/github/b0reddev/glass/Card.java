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

/**
 * One Top Shelf item, as tvOS draws a sectioned row: 16:9 artwork with the playback progress inside its bottom
 * edge, and a label (episode, time left) only while it has focus. Focus looks like an app tile's: a lift and
 * gloss in the light theme, the ring in the dark one, around the artwork only.
 */
@SuppressLint("ViewConstructor")
final class Card extends View {
    final Watching.Item item;
    Bitmap ambient;
    private Bitmap art;
    private final int w, artH;
    private final float radius;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final String line;
    private float glow, sheen = -1f;
    private ValueAnimator focusAnim;

    /** The view is the artwork; the focused label is drawn under it, in the row's margin (rows don't clip). */
    Card(Context c, Watching.Item item, int w, int artH) {
        super(c);
        this.item = item;
        this.w = w;
        this.artH = artH;
        radius = Ui.dp(12);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setContentDescription(item.line.isEmpty() ? item.title : item.title + ", " + item.line);
        setPivotX(w / 2f);
        setPivotY(artH / 2f);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, Card.this.w, Card.this.artH, radius);
            }
        });
        base.setShader(new LinearGradient(0, 0, 0, artH, 0xFF2C3242, 0xFF191C26, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, artH * 0.55f, 0, artH, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Ui.dp(1));
        rim.setShader(new LinearGradient(0, 0, 0, artH, 0xFFFFFFFF, 0x40FFFFFF, Shader.TileMode.CLAMP));
        gloss.setShader(new LinearGradient(0, 0, 0, artH * 0.55f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, w * 0.45f, artH * 0.25f,
                new int[]{0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(2.5f));
        label.setTypeface(Ui.medium);
        label.setTextSize(13 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, c.getResources().getDisplayMetrics()));
        line = TextUtils.ellipsize(item.line, label, w, TextUtils.TruncateAt.END).toString();
    }

    void setArt(Bitmap b, Bitmap amb) {
        art = b;
        ambient = amb;
        artPaint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        m.setScale((float) w / b.getWidth(), (float) artH / b.getHeight());
        artPaint.getShader().setLocalMatrix(m);
        invalidate();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(w, artH);
    }

    @Override
    protected void onFocusChanged(boolean f, int dir, Rect prev) {
        super.onFocusChanged(f, dir, prev);
        float s = f ? Theme.current.tileScale : 1f;
        animate().scaleX(s).scaleY(s).translationZ(f ? Ui.dp(14) : 0).setInterpolator(Ui.FOCUS).setDuration(420).start();
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
        Theme t = Theme.current;
        r.set(0, 0, w, artH);
        if (art == null) {
            base.setAlpha(140);
            c.drawRoundRect(r, radius, radius, base);
            base.setAlpha(255);
        } else {
            c.drawRoundRect(r, radius, radius, artPaint);
        }
        if (item.progress > 0) {
            // tvOS's playback progress: a thin bar inside the bottom edge, over a soft shade so it reads on any picture
            c.drawRoundRect(r, radius, radius, shade);
            float inset = Ui.dp(12), bh = Ui.dp(3), y = artH - Ui.dp(10) - bh, end = w - inset;
            bar.setColor(0x4DFFFFFF);
            c.drawRoundRect(inset, y, end, y + bh, bh / 2, bh / 2, bar);
            bar.setColor(0xFFFFFFFF);
            c.drawRoundRect(inset, y, inset + Math.max(bh, (end - inset) * item.progress), y + bh, bh / 2, bh / 2, bar);
        }
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
            float rh = ring.getStrokeWidth() / 2, out = Ui.dp(3) + rh, rr = radius + out;
            ring.setColor(t.ring);
            c.drawRoundRect(-out, -out, w + out, artH + out, rr, rr, ring);
        }
        if (glow > 0 && !line.isEmpty()) {
            int color = t.label();
            label.setColor(color);
            label.setAlpha((int) ((color >>> 24) * glow));
            c.drawText(line, 0, artH + Ui.dp(8) - label.ascent(), label);
        }
    }
}
