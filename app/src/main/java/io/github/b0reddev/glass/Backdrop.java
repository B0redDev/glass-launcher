package io.github.b0reddev.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;

/**
 * The wallpaper layer: a procedural aurora, washed with a blurred copy of the focused app's art
 * (the Top Shelf ambience), plus a tiny blurred and saturated copy of itself that glass panels
 * sample instead of doing a real backdrop blur. Redrawn only when the ambience or dim changes.
 */
final class Backdrop extends View {
    static final int GW = 96, GH = 54;
    private static final float AMBIENT = 0.85f;

    /** What glass panels show through themselves: this layer at 1/20 scale, blurred, saturated. */
    final Bitmap glass = Bitmap.createBitmap(GW, GH, Bitmap.Config.ARGB_8888);
    private final Bitmap small = Bitmap.createBitmap(GW, GH, Bitmap.Config.ARGB_8888);
    private final Canvas glassCanvas = new Canvas(glass), smallCanvas = new Canvas(small);
    private final int[] px = new int[GW * GH], tmp = new int[GW * GH];
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint vivid = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint(Paint.DITHER_FLAG);
    private final RectF full = new RectF(), smallRect = new RectF(0, 0, GW, GH);
    private final ArrayList<View> clients = new ArrayList<>();
    private Bitmap wall, from, to;
    private float mix = 1f, dim;
    private ValueAnimator fade;

    Backdrop(Context c) {
        super(c);
        vivid.setColorFilter(saturation(1.6f));
    }

    void addClient(View v) {
        clients.add(v);
    }

    void removeClient(View v) {
        clients.remove(v);
    }

    void setAmbient(Bitmap b) {
        if (b == to) return;
        from = to;
        to = b;
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(0f, 1f).setDuration(450);
        fade.addUpdateListener(a -> {
            mix = (float) a.getAnimatedValue();
            changed();
        });
        fade.start();
    }

    /** 0..1, darkens the layer when the grid scrolls over it. Glass panels are hidden by then. */
    void setDim(float d) {
        if (d == dim) return;
        dim = d;
        invalidate();
    }

    private void changed() {
        rebuildGlass();
        invalidate();
        for (int i = 0; i < clients.size(); i++) clients.get(i).invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        full.set(0, 0, w, h);
        wall = aurora(Math.max(1, w / 4), Math.max(1, h / 4));
        scrim.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x66000000, 0x1A000000, 0x33000000, 0xB3000000},
                new float[]{0f, 0.3f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        changed();
    }

    @Override
    protected void onDraw(Canvas c) {
        paint(c, full);
        if (dim > 0) c.drawColor((int) (dim * 0.55f * 255) << 24);
    }

    private void paint(Canvas c, RectF r) {
        if (wall == null) return;
        c.drawBitmap(wall, null, r, bmp);
        if (from != null) {
            // Fading to no ambience (an input is focused): the old one fades out instead of being covered.
            bmp.setAlpha((int) (255 * AMBIENT * (to == null ? 1 - mix : 1)));
            c.drawBitmap(from, null, r, bmp);
        }
        if (to != null) {
            bmp.setAlpha((int) (255 * AMBIENT * mix));
            c.drawBitmap(to, null, r, bmp);
        }
        bmp.setAlpha(255);
        c.save();
        c.scale(r.width() / full.width(), r.height() / full.height());
        c.drawRect(full, scrim);
        c.restore();
    }

    private void rebuildGlass() {
        if (wall == null) return;
        paint(smallCanvas, smallRect);
        glass.eraseColor(0);
        glassCanvas.drawBitmap(small, 0, 0, vivid);
        glass.getPixels(px, 0, GW, 0, 0, GW, GH);
        blur(px, tmp, GW, GH, 3);
        blur(px, tmp, GW, GH, 3);
        glass.setPixels(px, 0, GW, 0, 0, GW, GH);
    }

    /** The ambience for one app: its art at 32x18, saturated and blurred; drawn full screen it is a soft wash. */
    static Bitmap ambient(Bitmap art) {
        final int w = 32, h = 18;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF10131C);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(saturation(1.35f));
        float s = Math.max((float) w / art.getWidth(), (float) h / art.getHeight());
        RectF dst = new RectF(0, 0, art.getWidth() * s, art.getHeight() * s);
        dst.offset((w - dst.width()) / 2, (h - dst.height()) / 2);
        c.drawBitmap(art, null, dst, p);
        int[] px = new int[w * h], t = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        blur(px, t, w, h, 2);
        blur(px, t, w, h, 2);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    static Bitmap aurora(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF070B1A);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        blob(c, p, w * .15f, h * .10f, w * .65f, 0x8C2F5BFF);
        blob(c, p, w * .88f, h * .22f, w * .55f, 0x737B3FE4);
        blob(c, p, w * .62f, h * 1.05f, w * .60f, 0x4D00A6A6);
        blob(c, p, w * .02f, h * .98f, w * .45f, 0x40C2338A);
        return b;
    }

    private static void blob(Canvas c, Paint p, float x, float y, float r, int color) {
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    static ColorMatrixColorFilter saturation(float s) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(s);
        return new ColorMatrixColorFilter(m);
    }

    /** Separable box blur, horizontal into t then vertical back into p. Sized for tiny bitmaps only. */
    static void blur(int[] p, int[] t, int w, int h, int r) {
        box(p, t, w, h, r, true);
        box(t, p, w, h, r, false);
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = 0, rr = 0, g = 0, b = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horizontal ? Math.min(w - 1, Math.max(0, x + k)) : x;
                    int yy = horizontal ? y : Math.min(h - 1, Math.max(0, y + k));
                    int c = in[yy * w + xx];
                    a += c >>> 24;
                    rr += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
                out[y * w + x] = (a / n) << 24 | (rr / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
    }
}
