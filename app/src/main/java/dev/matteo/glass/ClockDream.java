package dev.matteo.glass;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Random;

/** Screensaver: the launcher's clock over its dimmed aurora, moving once a minute so nothing burns in. */
public final class ClockDream extends DreamService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private LinearLayout box;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            box.animate().alpha(0f).setDuration(900).withEndAction(() -> {
                place();
                box.animate().alpha(1f).setDuration(900).start();
            }).start();
            main.postDelayed(this, 60_000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        Backdrop b = new Backdrop(this);
        b.setDim(0.6f);
        root.addView(b, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextClock time = Ui.style(new TextClock(this), Ui.light, 120, 0xFFFFFFFF);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setFontFeatureSettings("tnum");
        TextClock date = Ui.style(new TextClock(this), Ui.medium, 22, 0xB3FFFFFF);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Ui.dp(10);
        dl.leftMargin = Ui.dp(6);
        box.addView(time, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(date, dl);
        root.addView(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setContentView(root);
        box.post(this::place);
    }

    private void place() {
        View p = (View) box.getParent();
        int m = Ui.dp(48);
        box.setX(m + rnd.nextInt(Math.max(1, p.getWidth() - box.getWidth() - 2 * m)));
        box.setY(m + rnd.nextInt(Math.max(1, p.getHeight() - box.getHeight() - 2 * m)));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        main.postDelayed(drift, 60_000);
    }

    @Override
    public void onDreamingStopped() {
        main.removeCallbacks(drift);
        super.onDreamingStopped();
    }
}
