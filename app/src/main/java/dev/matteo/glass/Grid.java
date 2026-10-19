package dev.matteo.glass;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;

/** Fixed-cell grid laid out by child order. Reordering slides every moved tile to its new cell. */
final class Grid extends ViewGroup {
    final int cols;
    private final int cellW, cellH, gapX, pitch;
    private final HashMap<View, int[]> before = new HashMap<>();

    Grid(Context c, int cols, int cellW, int cellH, int gapX, int pitch) {
        super(c);
        this.cols = cols;
        this.cellW = cellW;
        this.cellH = cellH;
        this.gapX = gapX;
        this.pitch = pitch;
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(cellW, cellH);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int n = getChildCount(), rows = (n + cols - 1) / cols;
        int cw = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), ch = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        for (int i = 0; i < n; i++) getChildAt(i).measure(cw, ch);
        setMeasuredDimension(getPaddingLeft() + cols * cellW + (cols - 1) * gapX + getPaddingRight(),
                getPaddingTop() + rows * pitch + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int x = getPaddingLeft() + (i % cols) * (cellW + gapX), y = getPaddingTop() + (i / cols) * pitch;
            v.layout(x, y, x + cellW, y + cellH);
            int[] old = before.get(v);
            if (old != null && (old[0] != x || old[1] != y)) {
                v.setTranslationX(old[0] - x);
                v.setTranslationY(old[1] - y);
                v.animate().translationX(0).translationY(0).setInterpolator(Ui.FOCUS).setDuration(450).start();
            }
        }
        before.clear();
    }

    /** Moves the child at {@code from} to {@code to} without detaching it from the window, so it keeps focus. */
    void move(int from, int to) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            before.put(v, new int[]{v.getLeft(), v.getTop()});
        }
        View v = getChildAt(from);
        detachViewFromParent(from);
        attachViewToParent(v, to, v.getLayoutParams());
        requestLayout();
        invalidate();
    }
}
