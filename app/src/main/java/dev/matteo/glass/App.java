package dev.matteo.glass;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An installed TV app, and the saved order / hidden set that the grid follows. */
final class App {
    final String pkg, label;
    final ComponentName component;
    final ActivityInfo info;
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

    /** All TV apps in the saved order; apps the order doesn't know yet go at the end, alphabetically. */
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
        for (String p : prefs.getString("order", "").split("\n")) {
            App a = byPkg.remove(p);
            if (a != null) out.add(a);
        }
        ArrayList<App> rest = new ArrayList<>(byPkg.values());
        Collator col = Collator.getInstance();
        rest.sort((x, y) -> col.compare(x.label, y.label));
        out.addAll(rest);
        for (App a : out) a.hidden = hidden.contains(a.pkg);
        return out;
    }

    static void save(SharedPreferences prefs, List<App> apps) {
        StringBuilder order = new StringBuilder();
        HashSet<String> hidden = new HashSet<>();
        for (App a : apps) {
            order.append(a.pkg).append('\n');
            if (a.hidden) hidden.add(a.pkg);
        }
        prefs.edit().putString("order", order.toString()).putStringSet("hidden", hidden).apply();
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
