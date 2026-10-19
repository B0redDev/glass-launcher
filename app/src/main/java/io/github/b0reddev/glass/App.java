package io.github.b0reddev.glass;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An installed TV app, the row (category) it sits in, and the saved order / hidden set the rows follow. */
final class App {
    /** Row ids, top to bottom. The inputs row is not a category: it always sits after the first one. */
    static final String FAV = "fav", STREAMING = "streaming", TOOLS = "tools", GAMES = "games", OTHER = "other";
    static final List<String> CATS = Arrays.asList(FAV, STREAMING, TOOLS, GAMES, OTHER);

    /** Where known apps start out. Anything else goes by its declared category, then to "other". */
    private static final Map<String, String> START = new HashMap<>();

    static {
        for (String p : new String[]{"com.crunchyroll.crunchyroid", "com.disney.disneyplus", "com.netflix.ninja",
                "com.stremio.one", "it.rainet.androidtv"}) START.put(p, FAV);
        for (String p : new String[]{"it.mediaset.infinitytv", "com.nuvio.tv", "org.smarttube.beta", "com.sportzx.live",
                "com.nst.iptvsmarterstvbox", "com.mitv.livetv"}) START.put(p, STREAMING);
        for (String p : new String[]{"com.alphainventor.filemanager", "com.esaba.downloader", "org.localsend.localsend_app",
                "ch.protonvpn.android", "com.android.vending", "com.brouken.player"}) START.put(p, TOOLS);
        START.put("com.limelight", GAMES);
    }

    final String pkg, label;
    final ComponentName component;
    final ActivityInfo info;
    String cat;
    boolean hidden;
    Tile tile;

    private App(ResolveInfo ri, PackageManager pm) {
        info = ri.activityInfo;
        pkg = info.packageName;
        component = new ComponentName(pkg, info.name);
        label = String.valueOf(ri.loadLabel(pm)).trim();
    }

    Intent launchIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    /** All TV apps in saved row order; apps not saved yet go at the end of their starting row, alphabetically. */
    static List<App> load(Context c, SharedPreferences prefs) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        HashMap<String, App> byPkg = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(c.getPackageName()) && !byPkg.containsKey(p)) byPkg.put(p, new App(ri, pm));
        }
        Set<String> hidden = prefs.getStringSet("hidden", Collections.emptySet());
        ArrayList<App> out = new ArrayList<>();
        for (String line : prefs.getString("rows", "").split("\n")) {
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String cat = line.substring(0, tab);
            App a = byPkg.remove(line.substring(tab + 1));
            if (a == null || !CATS.contains(cat)) continue;
            a.cat = cat;
            out.add(a);
        }
        ArrayList<App> rest = new ArrayList<>(byPkg.values());
        Collator col = Collator.getInstance();
        rest.sort((x, y) -> col.compare(x.label, y.label));
        for (App a : rest) {
            a.cat = startingRow(a);
            out.add(a);
        }
        for (App a : out) a.hidden = hidden.contains(a.pkg);
        return out;
    }

    private static String startingRow(App a) {
        String known = START.get(a.pkg);
        if (known != null) return known;
        switch (a.info.applicationInfo.category) {
            case ApplicationInfo.CATEGORY_VIDEO: return STREAMING;
            case ApplicationInfo.CATEGORY_GAME: return GAMES;
            default: return OTHER;
        }
    }

    static void save(SharedPreferences prefs, List<App> apps) {
        StringBuilder rows = new StringBuilder();
        HashSet<String> hidden = new HashSet<>();
        for (App a : apps) {
            rows.append(a.cat).append('\t').append(a.pkg).append('\n');
            if (a.hidden) hidden.add(a.pkg);
        }
        prefs.edit().putString("rows", rows.toString()).putStringSet("hidden", hidden).remove("order").apply();
    }

    /** Banner, or the icon when there is none, rendered once at focused size; plus its ambience. */
    static final class Art {
        Bitmap bitmap, ambient;
        boolean banner;
    }

    /** Runs off the main thread. */
    static Art art(PackageManager pm, ActivityInfo info, int w, int h) {
        Art a = new Art();
        Drawable d = info.loadBanner(pm);
        a.banner = d != null;
        if (!a.banner) d = info.loadIcon(pm);
        int bw = a.banner ? w : h;
        a.bitmap = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, bw, h);
        d.draw(new Canvas(a.bitmap));
        a.ambient = Backdrop.ambient(a.bitmap);
        return a;
    }
}
