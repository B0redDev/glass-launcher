package io.github.b0reddev.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;

/**
 * One horizontal row: its name above, fixed-size cells, and its own horizontal scroll that keeps the
 * focused cell on screen. Reordering slides every moved cell to its new place.
 */
final class Row extends ViewGroup {
    /** Category id ({@link App#CATS}), or null for the inputs row. */
    final String cat;
    final String title;
    final int cellW, cellH;
    private final int gap, labelH, padX, below;
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final HashMap<View, int[]> before = new HashMap<>();
    private float labelAlpha = 1f;
    private ValueAnimator labelAnim, scrollAnim;

    Row(Context c, String cat, String title, int cellW, int cellH, int gap) {
        super(c);
        this.cat = cat;
        this.title = title;
        this.cellW = cellW;
        this.cellH = cellH;
        this.gap = gap;
        padX = Ui.dp(48);
        labelH = Ui.dp(22);
        below = Ui.dp(24) + Math.round(cellH * (Tile.FOCUSED - 1) / 2);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        label.setTypeface(Ui.medium);
        label.setTextSize(13 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, c.getResources().getDisplayMetrics()));
    }

    /** Distance from the row's top to its cells: where the cells sit when the row is the focused one. */
    int cellsTop() {
        return labelH;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), ch = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
        setMeasuredDimension(MeasureSpec.getSize(ws), labelH + cellH + below);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int x = padX + i * (cellW + gap);
            v.layout(x, labelH, x + v.getMeasuredWidth(), labelH + v.getMeasuredHeight());
            int[] old = before.get(v);
            if (old != null && old[0] != x) {
                v.setTranslationX(old[0] - x);
                v.animate().translationX(0).setInterpolator(Ui.FOCUS).setDuration(450).start();
            }
        }
        before.clear();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (labelAlpha <= 0 || getChildCount() == 0) return; // an empty Top Shelf keeps its room, not its name
        int color = Theme.current.label();
        label.setColor(color);
        label.setAlpha((int) ((color >>> 24) * labelAlpha));
        c.drawText(title, padX + getScrollX(), labelH - Ui.dp(9), label); // the name doesn't scroll with the cells
    }

    /** The focused row hides its name: the title above already says it. */
    void showLabel(boolean show) {
        float target = show ? 1f : 0f;
        if (labelAnim != null) labelAnim.cancel();
        if (labelAlpha == target) return;
        labelAnim = ValueAnimator.ofFloat(labelAlpha, target).setDuration(200);
        labelAnim.addUpdateListener(a -> {
            labelAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        labelAnim.start();
    }

    /** Scrolls the row so the child is fully inside the side margins. */
    void reveal(View child) {
        if (getWidth() == 0) return; // not laid out yet: it starts scrolled to 0, which shows the first cells
        int left = padX + indexOfChild(child) * (cellW + gap), right = left + cellW;
        int target = getScrollX();
        if (right - target > getWidth() - padX) target = right - (getWidth() - padX);
        if (left - target < padX) target = left - padX;
        scrollToX(Math.max(0, target));
    }

    void scrollToX(int x) {
        if (scrollAnim != null) scrollAnim.cancel();
        if (x == getScrollX()) return;
        scrollAnim = ValueAnimator.ofInt(getScrollX(), x).setDuration(520);
        scrollAnim.setInterpolator(Ui.SCROLL);
        scrollAnim.addUpdateListener(a -> scrollTo((int) a.getAnimatedValue(), 0));
        scrollAnim.start();
    }

    /** Moves the child at {@code from} to {@code to} without detaching it from the window, so it keeps focus. */
    void move(int from, int to) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            before.put(v, new int[]{v.getLeft()});
        }
        View v = getChildAt(from);
        detachViewFromParent(from);
        attachViewToParent(v, to, v.getLayoutParams());
        requestLayout();
        invalidate();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(cellW, cellH);
    }
}
