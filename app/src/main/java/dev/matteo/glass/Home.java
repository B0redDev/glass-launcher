package dev.matteo.glass;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * tvOS 26 style home: Top Shelf ambience and title, a 5-column grid of banners, glass pills for
 * inputs, network and settings, and a long-press menu to move or hide apps.
 */
public final class Home extends Activity {
    private static final int COLS = 5;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Backdrop backdrop;
    private ScrollView scroll;
    private Grid grid;
    private TextView heroTitle, wifiText;
    private ImageView wifiIcon;
    private LinearLayout topBar;
    private GlassFrame inputsPill, hint, panel;
    private FrameLayout overlay;
    private View scrim, menuAnchor;
    private List<App> apps = new ArrayList<>();
    private App last, moving;
    private boolean showHidden, resumed;
    private ValueAnimator scrollAnim, wiggle;
    private int tileW, tileH, pitch, shelf, side, scrollTarget;
    private volatile int artGen;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (moving == null) reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        side = Ui.dp(48);
        int gap = Ui.dp(24);
        tileW = (dm.widthPixels - 2 * side - (COLS - 1) * gap) / COLS;
        tileH = tileW * 9 / 16;
        pitch = tileH + Ui.dp(46);
        shelf = Math.round(dm.heightPixels * 0.56f);

        FrameLayout root = new FrameLayout(this);
        backdrop = new Backdrop(this);
        root.addView(backdrop, MATCH, MATCH);

        scroll = new ScrollView(this) {
            // Scrolling follows focus row by row with our own spring, never ScrollView's jumps.
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return 0;
            }

            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                return false;
            }
        };
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> chrome(y));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);

        FrameLayout hero = new FrameLayout(this);
        heroTitle = Ui.text(this, Ui.semibold, 40, 0xFFFFFFFF);
        heroTitle.setShadowLayer(Ui.dp(16), 0, Ui.dp(2), 0x66000000);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
        hl.leftMargin = side;
        hl.bottomMargin = Ui.dp(26);
        hero.addView(heroTitle, hl);
        content.addView(hero, MATCH, shelf);

        grid = new Grid(this, COLS, tileW, tileH + Ui.dp(34), gap, pitch);
        // Room below the last row so it can scroll up to the same spot as every other row.
        grid.setPadding(side, 0, side, Math.max(0, dm.heightPixels - Ui.dp(120) - pitch));
        content.addView(grid, MATCH, WRAP);
        scroll.addView(content, MATCH, WRAP);
        root.addView(scroll, MATCH, MATCH);

        topBar = new LinearLayout(this);
        topBar.setPadding(side, Ui.dp(28), side, 0);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = Ui.style(new TextClock(this), Ui.light, 38, 0xFFFFFFFF);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setFontFeatureSettings("tnum");
        TextClock date = Ui.style(new TextClock(this), Ui.medium, 14, 0xB3FFFFFF);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, WRAP);
        dl.topMargin = Ui.dp(6);
        clockBox.addView(time);
        clockBox.addView(date, dl);
        topBar.addView(clockBox, new LinearLayout.LayoutParams(0, WRAP, 1f));
        inputsPill = pill(R.drawable.ic_input, getString(R.string.inputs), this::inputsMenu);
        GlassFrame wifiPill = pill(R.drawable.ic_wifi, getString(R.string.wifi),
                () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)));
        wifiIcon = (ImageView) wifiPill.getChildAt(0);
        wifiText = (TextView) wifiPill.getChildAt(1);
        GlassFrame settingsPill = pill(R.drawable.ic_settings, null, () -> start(new Intent(Settings.ACTION_SETTINGS)));
        topBar.addView(inputsPill);
        topBar.addView(wifiPill);
        topBar.addView(settingsPill);
        root.addView(topBar, MATCH, WRAP);

        hint = new GlassFrame(this, backdrop, Ui.dp(20), true);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        TextView ht = Ui.text(this, Ui.medium, 14, 0xFFFFFFFF);
        ht.setText(R.string.move_hint);
        hint.addView(ht);
        hint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(WRAP, Ui.dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = Ui.dp(34);
        root.addView(hint, hp);

        overlay = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(0x73000000);
        overlay.addView(scrim, MATCH, MATCH);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, MATCH, MATCH);

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packages, f);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refreshNetwork();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: back to the top, as tvOS does. From another app: stay put.
        boolean wasHome = resumed;
        closeMenu();
        if (moving != null) endMove();
        if (wasHome) toTop();
    }

    @Override
    protected void onDestroy() {
        artGen++;
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (moving != null) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: if (down) moveBy(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) moveBy(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (down) moveBy(-COLS); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (down) moveBy(COLS); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (!down) endMove();
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            // A launcher never finishes on Back: close the menu, or return to the top row.
            if (!down) {
                if (panel != null) closeMenu();
                else toTop();
            }
            return true;
        }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && topBar.hasFocus() && last != null && last.tile.getParent() == grid) {
            last.tile.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- apps and grid ----

    private void reload() {
        String focus = last != null ? last.pkg : null;
        apps = App.load(this, prefs);
        for (App a : apps) a.tile = tile(a);
        rebuildGrid(focus);
        loadArt(++artGen);
    }

    private Tile tile(App a) {
        Tile t = new Tile(this, a, tileW, tileH);
        t.setOnClickListener(v -> launch(a, v));
        t.setOnLongClickListener(v -> {
            appMenu(a);
            return true;
        });
        t.setOnFocusChangeListener((v, f) -> {
            if (f) focused(a);
        });
        return t;
    }

    private void rebuildGrid(String focusPkg) {
        grid.removeAllViews();
        Tile target = null;
        for (App a : apps) {
            if (a.hidden && !showHidden) continue;
            a.tile.setAlpha(a.hidden ? 0.35f : 1f);
            grid.addView(a.tile);
            if (a.pkg.equals(focusPkg)) target = a.tile;
        }
        if (target == null && grid.getChildCount() > 0) target = (Tile) grid.getChildAt(0);
        if (target != null && panel == null) target.requestFocus();
    }

    private void loadArt(int gen) {
        final List<App> list = apps;
        final PackageManager pm = getPackageManager();
        final int w = Math.round(tileW * Tile.FOCUSED), h = Math.round(tileH * Tile.FOCUSED);
        new Thread(() -> {
            for (App a : list) {
                if (gen != artGen) return;
                App.Art art;
                try {
                    art = App.art(pm, a.info, w, h);
                } catch (RuntimeException e) {
                    continue; // an app whose resources fail to load keeps its placeholder
                }
                main.post(() -> {
                    a.tile.setArt(art.bitmap, art.banner, art.ambient);
                    if (a == last) backdrop.setAmbient(art.ambient);
                });
            }
        }, "art").start();
    }

    private void focused(App a) {
        last = a;
        if (!a.label.contentEquals(heroTitle.getText())) {
            heroTitle.setText(a.label);
            heroTitle.setAlpha(0f);
            heroTitle.setTranslationY(Ui.dp(8));
            heroTitle.animate().alpha(1f).translationY(0).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        if (a.tile.ambient != null) backdrop.setAmbient(a.tile.ambient);
        scrollToRow(grid.indexOfChild(a.tile) / COLS);
    }

    private void scrollToRow(int row) {
        topBar.setDescendantFocusability(row == 0 ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        int y = row == 0 ? 0 : shelf + row * pitch - Ui.dp(120);
        if (y == scrollTarget && (scroll.getScrollY() == y || (scrollAnim != null && scrollAnim.isRunning()))) return;
        scrollTarget = y;
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = ValueAnimator.ofInt(scroll.getScrollY(), y).setDuration(520);
        scrollAnim.setInterpolator(Ui.SCROLL);
        scrollAnim.addUpdateListener(v -> scroll.scrollTo(0, (int) v.getAnimatedValue()));
        scrollAnim.start();
    }

    /** Clock and pills fade out and the backdrop dims as the grid scrolls over the Top Shelf. */
    private void chrome(int y) {
        if (moving == null) topBar.setAlpha(barAlpha(y));
        backdrop.setDim(Math.min(1f, y / (float) Ui.dp(260)));
    }

    private float barAlpha(int y) {
        return Math.max(0f, 1f - y / (float) Ui.dp(140));
    }

    private void toTop() {
        if (last == null || last.tile.getParent() != grid) return;
        int i = grid.indexOfChild(last.tile);
        if (i >= COLS) grid.getChildAt(i % COLS).requestFocus();
    }

    private void launch(App a, View v) {
        ActivityOptions o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), tileH);
        try {
            startActivity(a.launchIntent(), o.toBundle());
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // uninstalled or disabled in the meantime; the package broadcast will refresh the grid
        }
    }

    // ---- move and hide ----

    private void appMenu(App a) {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new Item(R.drawable.ic_open_with, getString(R.string.move), null, () -> startMove(a)));
        items.add(new Item(a.hidden ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                getString(a.hidden ? R.string.unhide : R.string.hide), null, () -> setHidden(a, !a.hidden)));
        items.add(new Item(R.drawable.ic_info, getString(R.string.app_info), null,
                () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)))));
        int n = 0;
        for (App x : apps) if (x.hidden) n++;
        if (n > 0) {
            items.add(new Item(showHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility,
                    showHidden ? getString(R.string.hide_hidden) : getString(R.string.show_hidden, n), null, () -> {
                        showHidden = !showHidden;
                        rebuildGrid(a.pkg);
                    }));
        }
        openMenu(a.tile, tileH * Tile.FOCUSED, false, items);
    }

    private void setHidden(App a, boolean h) {
        a.hidden = h;
        App.save(prefs, apps);
        boolean any = false;
        for (App x : apps) any |= x.hidden;
        if (!any) showHidden = false;
        String focus = a.pkg;
        if (h && !showHidden) {
            int i = grid.indexOfChild(a.tile);
            View next = grid.getChildAt(i + 1 < grid.getChildCount() ? i + 1 : i - 1);
            focus = next instanceof Tile ? ((Tile) next).app.pkg : null;
        }
        rebuildGrid(focus);
    }

    private void startMove(App a) {
        moving = a;
        a.tile.requestFocus();
        a.tile.setLifted(true);
        hint.setVisibility(View.VISIBLE);
        hint.setAlpha(0f);
        hint.animate().alpha(1f).setDuration(200).start();
        topBar.animate().alpha(0f).setDuration(200).start(); // the hint takes the top centre
        wiggle = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI)).setDuration(700);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.setInterpolator(new LinearInterpolator());
        wiggle.addUpdateListener(v -> {
            float p = (float) v.getAnimatedValue();
            for (int i = 0; i < grid.getChildCount(); i++) {
                View c = grid.getChildAt(i);
                c.setRotation(c == a.tile ? 0f : 1.2f * (float) Math.sin(p + i * 1.9f));
            }
        });
        wiggle.start();
    }

    private void moveBy(int d) {
        Tile t = moving.tile;
        int from = grid.indexOfChild(t), to = from + d;
        if (to < 0 || to >= grid.getChildCount()) return;
        App target = ((Tile) grid.getChildAt(to)).app;
        apps.remove(moving);
        apps.add(apps.indexOf(target) + (d > 0 ? 1 : 0), moving);
        grid.move(from, to);
        scrollToRow(to / COLS);
    }

    private void endMove() {
        Tile t = moving.tile;
        moving = null;
        wiggle.cancel();
        for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setRotation(0f);
        t.setLifted(false);
        App.save(prefs, apps);
        hint.animate().alpha(0f).setDuration(200).withEndAction(() -> hint.setVisibility(View.GONE)).start();
        topBar.animate().alpha(barAlpha(scroll.getScrollY())).setDuration(200).start();
    }

    // ---- inputs and network ----

    private void inputsMenu() {
        ArrayList<Item> items = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return;
        ArrayList<TvInputInfo> inputs = new ArrayList<>(), tuners = new ArrayList<>();
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getParentId() != null || in.isHidden(this)) continue;
            if (isPhysical(in.getType())) inputs.add(in);
            else if (in.getType() == TvInputInfo.TYPE_TUNER) tuners.add(in);
        }
        inputs.sort((x, y) -> label(x).toString().compareTo(label(y).toString()));
        for (TvInputInfo in : inputs) {
            int s = tim.getInputState(in.getId());
            int sub = s == TvInputManager.INPUT_STATE_CONNECTED ? R.string.input_connected
                    : s == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? R.string.input_standby : R.string.input_disconnected;
            items.add(new Item(R.drawable.ic_input, label(in), getString(sub), () -> watch(in)));
        }
        // Firmwares register AirPlay and the dead analogue tuner as tuners too; the broadcast tuner is
        // the one that can record. The same VIEW intent on it makes Live TV switch to the aerial.
        ArrayList<TvInputInfo> broadcast = new ArrayList<>();
        for (TvInputInfo in : tuners) if (in.canRecord()) broadcast.add(in);
        if (!broadcast.isEmpty()) tuners = broadcast;
        for (TvInputInfo in : tuners) {
            items.add(new Item(R.drawable.ic_tv, tuners.size() == 1 ? getString(R.string.live_tv) : label(in), null, () -> watch(in)));
        }
        openMenu(inputsPill, inputsPill.getHeight(), true, items);
    }

    private void watch(TvInputInfo in) {
        start(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId())));
    }

    private static boolean isPhysical(int type) {
        return type == TvInputInfo.TYPE_HDMI || type == TvInputInfo.TYPE_COMPOSITE || type == TvInputInfo.TYPE_COMPONENT
                || type == TvInputInfo.TYPE_SVIDEO || type == TvInputInfo.TYPE_SCART || type == TvInputInfo.TYPE_VGA
                || type == TvInputInfo.TYPE_DVI || type == TvInputInfo.TYPE_DISPLAY_PORT;
    }

    private CharSequence label(TvInputInfo in) {
        CharSequence l = in.loadCustomLabel(this);
        return l == null || l.length() == 0 ? in.loadLabel(this) : l;
    }

    private void refreshNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null) {
            wifiIcon.setImageResource(R.drawable.ic_wifi_off);
            wifiText.setText(R.string.offline);
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            wifiIcon.setImageResource(R.drawable.ic_lan);
            wifiText.setText(R.string.ethernet);
        } else {
            wifiIcon.setImageResource(R.drawable.ic_wifi);
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            @SuppressWarnings("deprecation")
            String ssid = wm == null ? null : wm.getConnectionInfo().getSSID();
            boolean known = ssid != null && ssid.length() > 2 && ssid.startsWith("\"");
            wifiText.setText(known ? ssid.substring(1, ssid.length() - 1) : getString(R.string.wifi));
        }
    }

    private void start(Intent... candidates) {
        for (Intent i : candidates) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // try the next one
            }
        }
    }

    // ---- glass pills and menus ----

    private GlassFrame pill(int icon, String label, Runnable click) {
        int h = Ui.dp(40);
        GlassFrame p = new GlassFrame(this, backdrop, h / 2f, true);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setOnClickListener(v -> click.run());
        if (label != null) p.setPadding(Ui.dp(14), 0, Ui.dp(16), 0);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(GlassFrame.LIGHT));
        p.addView(iv, Ui.dp(20), Ui.dp(20));
        if (label != null) {
            TextView t = Ui.text(this, Ui.medium, 15, GlassFrame.LIGHT);
            t.setText(label);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
            tl.leftMargin = Ui.dp(8);
            p.addView(t, tl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(label == null ? h : WRAP, h);
        lp.leftMargin = Ui.dp(12);
        p.setLayoutParams(lp);
        return p;
    }

    private static final class Item {
        final int icon;
        final CharSequence text, sub;
        final Runnable run;

        Item(int icon, CharSequence text, CharSequence sub, Runnable run) {
            this.icon = icon;
            this.text = text;
            this.sub = sub;
            this.run = run;
        }
    }

    /** A glass menu under (or above) the anchor; everything else stops taking focus until it closes. */
    private void openMenu(View anchor, float anchorH, boolean alignRight, List<Item> items) {
        closeMenu();
        if (items.isEmpty()) return;
        menuAnchor = anchor;
        panel = new GlassFrame(this, backdrop, Ui.dp(22), true);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setTint(0.16f);
        panel.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        panel.setMinimumWidth(Ui.dp(240));
        for (Item it : items) {
            GlassFrame row = new GlassFrame(this, backdrop, Ui.dp(14), false);
            row.setTint(0f);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(18), Ui.dp(10));
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                closeMenu();
                it.run.run();
            });
            ImageView iv = new ImageView(this);
            iv.setImageResource(it.icon);
            iv.setImageTintList(ColorStateList.valueOf(GlassFrame.LIGHT));
            row.addView(iv, Ui.dp(20), Ui.dp(20));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(this, Ui.medium, 16, GlassFrame.LIGHT);
            t.setText(it.text);
            texts.addView(t);
            if (it.sub != null) {
                TextView s = Ui.text(this, Ui.medium, 12, 0x99FFFFFF);
                s.setText(it.sub);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
                sl.topMargin = Ui.dp(3);
                texts.addView(s, sl);
            }
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
            tl.leftMargin = Ui.dp(12);
            row.addView(texts, tl);
            panel.addView(row, MATCH, WRAP);
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
        int sw = backdrop.getWidth(), sh = backdrop.getHeight(); // the overlay is GONE until now, so 0x0
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        float aw = anchor.getWidth() * anchor.getScaleX();
        int x = Math.round(alignRight ? l[0] + aw - pw : l[0] + aw / 2 - pw / 2f);
        x = Math.max(side, Math.min(sw - side - pw, x));
        int y = Math.round(l[1] + anchorH + Ui.dp(14));
        boolean below = y + ph <= sh - Ui.dp(20);
        if (!below) y = Math.max(Ui.dp(20), l[1] - ph - Ui.dp(14));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = x;
        lp.topMargin = y;
        overlay.addView(panel, lp);
        overlay.setVisibility(View.VISIBLE);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        panel.setPivotX(pw / 2f);
        panel.setPivotY(below ? 0 : ph);
        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(Ui.FOCUS).setDuration(380).start();
        panel.getChildAt(0).requestFocus();
    }

    private void closeMenu() {
        if (panel == null) return;
        overlay.removeView(panel);
        panel = null;
        overlay.setVisibility(View.GONE);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        topBar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (menuAnchor != null && menuAnchor.isAttachedToWindow()) menuAnchor.requestFocus();
        if (last != null && last.tile.getParent() == grid && last.tile.hasFocus()) scrollToRow(grid.indexOfChild(last.tile) / COLS);
    }
}
