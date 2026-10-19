package io.github.b0reddev.glass;

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
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
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
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * tvOS 26 style home in rows: Favourites, the TV's inputs, then one row per category. The focused row
 * always sits under the title; Move reorders within a row (←/→) and moves an app between rows (↑/↓).
 */
public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    /** Where the focused row's cells sit, under the title. */
    private static final float FOCUS_Y = 116;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HashMap<String, Row> rows = new HashMap<>();
    private final HashMap<String, TextView> inputStatus = new HashMap<>();
    private SharedPreferences prefs;
    private TvInputManager tim;
    private Backdrop backdrop;
    private ScrollView scroll;
    private LinearLayout content, topBar;
    private Row sources;
    private TextView overline, title;
    private GlassFrame netPill;
    private ImageView netIcon;
    private GlassFrame hint, panel;
    private FrameLayout overlay;
    private View scrim, menuAnchor, lastFocus;
    private List<App> apps = new ArrayList<>();
    private App moving;
    private boolean showHidden, resumed, relocating;
    private ValueAnimator scrollAnim, wiggle;
    private int favW, favH, scrollTarget;
    private volatile int artGen;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (moving == null) reload();
        }
    };

    private final TvInputManager.TvInputCallback inputs = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            TextView s = inputStatus.get(id);
            if (s != null) setStatus(s, state);
        }

        @Override
        public void onInputAdded(String id) {
            buildSources();
        }

        @Override
        public void onInputRemoved(String id) {
            buildSources();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int side = Ui.dp(48);
        favW = Ui.dp(200);
        favH = Ui.dp(112.5f);

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
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        // The first row's cells start at FOCUS_Y; the bottom padding lets the last row scroll up there too.
        content.setPadding(0, Ui.dp(FOCUS_Y) - Ui.dp(22), 0, dm.heightPixels);
        int appW = Ui.dp(150), appH = Ui.dp(84.4f), gap = Ui.dp(16);
        addRow(App.FAV, R.string.row_fav, favW, favH, Ui.dp(20));
        sources = new Row(this, null, getString(R.string.row_sources), Ui.dp(150), Ui.dp(56), gap);
        content.addView(sources, MATCH, WRAP);
        addRow(App.STREAMING, R.string.row_streaming, appW, appH, gap);
        addRow(App.TOOLS, R.string.row_tools, appW, appH, gap);
        addRow(App.GAMES, R.string.row_games, appW, appH, gap);
        addRow(App.OTHER, R.string.row_other, appW, appH, gap);
        scroll.addView(content, MATCH, WRAP);
        root.addView(scroll, MATCH, MATCH);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        overline = Ui.text(this, Ui.medium, 12, 0x99FFFFFF);
        overline.setLetterSpacing(0.14f);
        overline.setAllCaps(true);
        title = Ui.text(this, Ui.semibold, 34, 0xFFFFFFFF);
        titleBox.addView(overline);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
        tl.topMargin = Ui.dp(6);
        titleBox.addView(title, tl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
        bl.leftMargin = side;
        bl.topMargin = Ui.dp(30);
        root.addView(titleBox, bl);

        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topBar.setPadding(side, Ui.dp(28), side, 0);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        TextClock time = Ui.style(new TextClock(this), Ui.light, 24, 0xFFFFFFFF);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.rightMargin = Ui.dp(8);
        topBar.addView(time, cl);
        // Icon only: Wi-Fi, Ethernet or offline. The state's name is kept for screen readers.
        netPill = pill(R.drawable.ic_wifi, null,
                () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)));
        netIcon = (ImageView) netPill.getChildAt(0);
        topBar.addView(netPill);
        topBar.addView(pill(R.drawable.ic_settings, null, () -> start(new Intent(Settings.ACTION_SETTINGS))));
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
        tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputs, main);
        buildSources();
        reload();
    }

    private void addRow(String cat, int name, int w, int h, int gap) {
        Row r = new Row(this, cat, getString(name), w, h, gap);
        rows.put(cat, r);
        content.addView(r, MATCH, WRAP);
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
        if (tim != null) tim.unregisterCallback(inputs);
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (moving != null) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: if (down) moveWithin(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) moveWithin(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (down) moveAcross(-1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (down) moveAcross(1); return true;
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
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && topBar.hasFocus() && lastFocus != null && lastFocus.isAttachedToWindow()) {
            lastFocus.requestFocus();
            return true;
        }
        if (down && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) && panel == null) {
            // Up/down always lands on the next row, on the cell closest across; Android's geometric search
            // would skip a short row (Giochi has one app) for a better-aligned cell further down.
            View f = getCurrentFocus();
            if (f != null && f.getParent() instanceof Row) {
                Row next = adjacentRow((Row) f.getParent(), k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                if (next != null) {
                    nearest(next, f).requestFocus();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) return true; // last row: stay
                // first row going up: the default search reaches the top bar
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- apps and rows ----

    private void reload() {
        String focus = lastFocus instanceof Tile ? ((Tile) lastFocus).app.pkg : null;
        apps = App.load(this, prefs);
        for (App a : apps) a.tile = tile(a);
        rebuildRows(focus);
        loadArt(++artGen);
    }

    private Tile tile(App a) {
        Row r = rows.get(a.cat);
        Tile t = new Tile(this, a, r.cellW, r.cellH);
        t.setOnClickListener(v -> launch(v, a.launchIntent()));
        t.setOnLongClickListener(v -> {
            appMenu(a);
            return true;
        });
        t.setOnFocusChangeListener((v, f) -> {
            if (f && !relocating) focusedTile(t);
        });
        return t;
    }

    private void rebuildRows(String focusPkg) {
        for (String cat : App.CATS) rows.get(cat).removeAllViews();
        Tile target = null;
        for (App a : apps) {
            if (a.hidden && !showHidden) continue;
            Row r = rows.get(a.cat);
            a.tile.setSize(r.cellW, r.cellH);
            a.tile.setAlpha(a.hidden ? 0.35f : 1f);
            r.addView(a.tile);
            if (a.pkg.equals(focusPkg)) target = a.tile;
        }
        for (String cat : App.CATS) {
            Row r = rows.get(cat);
            r.setVisibility(r.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }
        if (panel != null) return;
        View f = target != null ? target : firstCell();
        if (f != null) f.requestFocus();
    }

    private Row adjacentRow(Row r, int dir) {
        for (int i = content.indexOfChild(r) + dir; i >= 0 && i < content.getChildCount(); i += dir) {
            Row x = (Row) content.getChildAt(i);
            if (x.getVisibility() == View.VISIBLE && x.getChildCount() > 0) return x;
        }
        return null;
    }

    /** The cell of {@code row} whose on-screen centre is horizontally closest to {@code from}'s. */
    private View nearest(Row row, View from) {
        int[] l = new int[2];
        from.getLocationInWindow(l);
        float cx = l[0] + from.getWidth() * from.getScaleX() / 2;
        row.getLocationInWindow(l);
        View best = row.getChildAt(0);
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            float d = Math.abs(l[0] + c.getLeft() - row.getScrollX() + c.getWidth() / 2f - cx);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private View firstCell() {
        for (int i = 0; i < content.getChildCount(); i++) {
            Row r = (Row) content.getChildAt(i);
            if (r.getVisibility() == View.VISIBLE && r.getChildCount() > 0) return r.getChildAt(0);
        }
        return null;
    }

    private void loadArt(int gen) {
        final List<App> list = apps;
        final PackageManager pm = getPackageManager();
        final int w = Math.round(favW * Tile.FOCUSED), h = Math.round(favH * Tile.FOCUSED);
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
                    if (lastFocus == a.tile) backdrop.setAmbient(art.ambient);
                });
            }
        }, "art").start();
    }

    private void focusedTile(Tile t) {
        lastFocus = t;
        setTitle(rows.get(t.app.cat).title, t.app.label);
        if (t.ambient != null) backdrop.setAmbient(t.ambient);
        focusRow((Row) t.getParent(), t);
    }

    private void focusedCard(View card, CharSequence name) {
        lastFocus = card;
        setTitle(sources.title, name);
        backdrop.setAmbient(null);
        focusRow(sources, card);
    }

    private void setTitle(CharSequence row, CharSequence name) {
        overline.setText(row);
        if (name.toString().contentEquals(title.getText())) return;
        title.setText(name);
        title.setAlpha(0f);
        title.setTranslationY(Ui.dp(8));
        title.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** The focused row moves under the title; rows above it go away behind the title, the ones below show their names. */
    private void focusRow(Row r, View child) {
        r.reveal(child);
        boolean seen = false, firstVisible = true, rowIsFirst = false;
        for (int i = 0; i < content.getChildCount(); i++) {
            Row x = (Row) content.getChildAt(i);
            if (x.getVisibility() != View.VISIBLE) continue;
            if (x == r) {
                seen = true;
                rowIsFirst = firstVisible;
            }
            firstVisible = false;
            x.animate().alpha(seen ? 1f : 0f).setDuration(200).start();
            x.showLabel(x != r);
        }
        topBar.setDescendantFocusability(rowIsFirst ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (content.isLaidOut()) scrollToRow(r);
        else content.post(() -> scrollToRow(r));
    }

    private void scrollToRow(Row r) {
        int y = Math.max(0, r.getTop() + r.cellsTop() - Ui.dp(FOCUS_Y));
        if (y == scrollTarget && (scroll.getScrollY() == y || (scrollAnim != null && scrollAnim.isRunning()))) return;
        scrollTarget = y;
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = ValueAnimator.ofInt(scroll.getScrollY(), y).setDuration(520);
        scrollAnim.setInterpolator(Ui.SCROLL);
        scrollAnim.addUpdateListener(v -> scroll.scrollTo(0, (int) v.getAnimatedValue()));
        scrollAnim.start();
    }

    /** Clock and pills fade out and the backdrop dims as the rows scroll up. */
    private void chrome(int y) {
        if (moving == null) topBar.setAlpha(barAlpha(y));
        backdrop.setDim(Math.min(1f, y / (float) Ui.dp(260)) * 0.5f);
    }

    private float barAlpha(int y) {
        return Math.max(0f, 1f - y / (float) Ui.dp(140));
    }

    private void toTop() {
        View first = firstCell();
        if (first == null) return;
        ((Row) first.getParent()).scrollToX(0);
        first.requestFocus();
    }

    private void launch(View v, Intent i) {
        try {
            startActivity(i, ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle());
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // uninstalled or disabled in the meantime; the package broadcast will refresh the rows
        }
    }

    // ---- move and hide ----

    private void appMenu(App a) {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new Item(R.drawable.ic_open_with, getString(R.string.move), () -> startMove(a)));
        items.add(new Item(a.hidden ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                getString(a.hidden ? R.string.unhide : R.string.hide), () -> setHidden(a, !a.hidden)));
        items.add(new Item(R.drawable.ic_info, getString(R.string.app_info),
                () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)))));
        int n = 0;
        for (App x : apps) if (x.hidden) n++;
        if (n > 0) {
            items.add(new Item(showHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility,
                    showHidden ? getString(R.string.hide_hidden) : getString(R.string.show_hidden, n), () -> {
                        showHidden = !showHidden;
                        rebuildRows(a.pkg);
                    }));
        }
        openMenu(a.tile, a.tile.tileHeight() * Tile.FOCUSED, items);
    }

    private void setHidden(App a, boolean h) {
        a.hidden = h;
        App.save(prefs, apps);
        boolean any = false;
        for (App x : apps) any |= x.hidden;
        if (!any) showHidden = false;
        String focus = a.pkg;
        if (h && !showHidden) {
            Row r = (Row) a.tile.getParent();
            int i = r.indexOfChild(a.tile);
            View next = r.getChildAt(i + 1 < r.getChildCount() ? i + 1 : i - 1);
            focus = next instanceof Tile ? ((Tile) next).app.pkg : null;
        }
        rebuildRows(focus);
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
            int n = 0;
            for (String cat : App.CATS) {
                Row r = rows.get(cat);
                for (int i = 0; i < r.getChildCount(); i++, n++) {
                    View c = r.getChildAt(i);
                    c.setRotation(c == a.tile ? 0f : 1.2f * (float) Math.sin(p + n * 1.9f));
                }
            }
        });
        wiggle.start();
    }

    private void moveWithin(int d) {
        Tile t = moving.tile;
        Row r = (Row) t.getParent();
        int from = r.indexOfChild(t), to = from + d;
        if (to < 0 || to >= r.getChildCount()) return;
        App target = ((Tile) r.getChildAt(to)).app;
        apps.remove(moving);
        apps.add(apps.indexOf(target) + (d > 0 ? 1 : 0), moving);
        r.move(from, to);
        r.reveal(t);
    }

    /** Moves the app to the next category row up or down, under the column it left (or at the end of a shorter row). */
    private void moveAcross(int d) {
        Tile t = moving.tile;
        int ci = App.CATS.indexOf(moving.cat) + d;
        if (ci < 0 || ci >= App.CATS.size()) return;
        Row from = (Row) t.getParent(), to = rows.get(App.CATS.get(ci));
        int at = Math.min(from.indexOfChild(t), to.getChildCount());
        App before = at < to.getChildCount() ? ((Tile) to.getChildAt(at)).app : null;
        apps.remove(moving);
        moving.cat = to.cat;
        if (before != null) apps.add(apps.indexOf(before), moving);
        else apps.add(moving); // rows filter by category, so the end of the list is the end of the row

        int[] was = new int[2];
        t.getLocationInWindow(was);
        relocating = true; // removing the focused view hands focus around; none of that is a real focus change
        from.removeView(t);
        t.setSize(to.cellW, to.cellH);
        to.addView(t, at);
        to.setVisibility(View.VISIBLE);
        if (from.getChildCount() == 0) from.setVisibility(View.GONE);
        t.requestFocus();
        relocating = false;
        focusedTile(t);
        t.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                t.getViewTreeObserver().removeOnPreDrawListener(this);
                int[] now = new int[2];
                t.getLocationInWindow(now);
                t.setTranslationX(was[0] - now[0]);
                t.setTranslationY(was[1] - now[1]);
                t.animate().translationX(0).translationY(0).setInterpolator(Ui.FOCUS).setDuration(450).start();
                return true;
            }
        });
    }

    private void endMove() {
        Tile t = moving.tile;
        moving = null;
        wiggle.cancel();
        for (String cat : App.CATS) {
            Row r = rows.get(cat);
            for (int i = 0; i < r.getChildCount(); i++) r.getChildAt(i).setRotation(0f);
        }
        t.setLifted(false);
        App.save(prefs, apps);
        hint.animate().alpha(0f).setDuration(200).withEndAction(() -> hint.setVisibility(View.GONE)).start();
        topBar.animate().alpha(barAlpha(scroll.getScrollY())).setDuration(200).start();
    }

    // ---- inputs row and network ----

    private void buildSources() {
        String focusedId = sources.hasFocus() ? (String) sources.findFocus().getTag() : null;
        sources.removeAllViews();
        inputStatus.clear();
        if (tim != null) {
            ArrayList<TvInputInfo> inputs = new ArrayList<>(), tuners = new ArrayList<>();
            for (TvInputInfo in : tim.getTvInputList()) {
                if (in.getParentId() != null || in.isHidden(this)) continue;
                if (isPhysical(in.getType())) inputs.add(in);
                else if (in.getType() == TvInputInfo.TYPE_TUNER) tuners.add(in);
            }
            inputs.sort((x, y) -> label(x).toString().compareTo(label(y).toString()));
            for (TvInputInfo in : inputs) sources.addView(card(in, label(in), false));
            // Firmwares register AirPlay and the dead analogue tuner as tuners too; the broadcast tuner is
            // the one that can record. The same VIEW intent on it makes Live TV switch to the aerial.
            ArrayList<TvInputInfo> broadcast = new ArrayList<>();
            for (TvInputInfo in : tuners) if (in.canRecord()) broadcast.add(in);
            if (!broadcast.isEmpty()) tuners = broadcast;
            for (TvInputInfo in : tuners) {
                sources.addView(card(in, tuners.size() == 1 ? getString(R.string.live_tv) : label(in), true));
            }
        }
        sources.setVisibility(sources.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        if (focusedId != null) {
            View again = sources.findViewWithTag(focusedId);
            (again != null ? again : firstCell()).requestFocus();
        }
    }

    private GlassFrame card(TvInputInfo in, CharSequence name, boolean tuner) {
        GlassFrame c = new GlassFrame(this, backdrop, Ui.dp(14), true);
        c.setTag(in.getId());
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(14), 0, Ui.dp(10), 0);
        c.setFocusable(true);
        c.setOnClickListener(v -> start(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()))));
        c.setOnFocusChangeListener((v, f) -> {
            if (f && !relocating) focusedCard(c, name);
        });
        ImageView iv = new ImageView(this);
        iv.setImageResource(tuner ? R.drawable.ic_tv : R.drawable.ic_input);
        iv.setImageTintList(ColorStateList.valueOf(GlassFrame.LIGHT));
        c.addView(iv, Ui.dp(20), Ui.dp(20));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, Ui.medium, 15, GlassFrame.LIGHT);
        n.setText(name);
        TextView s = Ui.text(this, Ui.medium, 12, 0x99FFFFFF);
        if (tuner) s.setText(R.string.antenna);
        else {
            inputStatus.put(in.getId(), s);
            setStatus(s, tim.getInputState(in.getId()));
        }
        texts.addView(n);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
        sl.topMargin = Ui.dp(2);
        texts.addView(s, sl);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
        tl.leftMargin = Ui.dp(12);
        c.addView(texts, tl);
        return c;
    }

    private void setStatus(TextView s, int state) {
        boolean on = state == TvInputManager.INPUT_STATE_CONNECTED, standby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
        s.setText(on ? R.string.input_connected : standby ? R.string.input_standby : R.string.input_disconnected);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(on ? 0xFF34D399 : standby ? 0xFFFCD34D : 0x59FFFFFF);
        dot.setSize(Ui.dp(7), Ui.dp(7));
        s.setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        s.setCompoundDrawablePadding(Ui.dp(5));
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
        int icon, name;
        if (nc == null) {
            icon = R.drawable.ic_wifi_off;
            name = R.string.offline;
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            icon = R.drawable.ic_lan;
            name = R.string.ethernet;
        } else {
            icon = R.drawable.ic_wifi;
            name = R.string.wifi;
        }
        netIcon.setImageResource(icon);
        netPill.setContentDescription(getString(name));
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
        final CharSequence text;
        final Runnable run;

        Item(int icon, CharSequence text, Runnable run) {
            this.icon = icon;
            this.text = text;
            this.run = run;
        }
    }

    /** A glass menu under (or above) the anchor; everything else stops taking focus until it closes. */
    private void openMenu(View anchor, float anchorH, List<Item> items) {
        closeMenu();
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
            TextView t = Ui.text(this, Ui.medium, 16, GlassFrame.LIGHT);
            t.setText(it.text);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
            tl.leftMargin = Ui.dp(12);
            row.addView(t, tl);
            panel.addView(row, MATCH, WRAP);
        }

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
        int sw = backdrop.getWidth(), sh = backdrop.getHeight(); // the overlay is GONE until now, so 0x0
        int side = Ui.dp(48);
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        float aw = anchor.getWidth() * anchor.getScaleX();
        int x = Math.max(side, Math.min(sw - side - pw, Math.round(l[0] + aw / 2 - pw / 2f)));
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
        // Focusing the anchor again runs focusRow, which also restores whether the top bar can take focus.
        if (menuAnchor != null && menuAnchor.isAttachedToWindow()) menuAnchor.requestFocus();
    }
}
