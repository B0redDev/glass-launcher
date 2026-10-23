package io.github.b0reddev.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

/**
 * The two looks. Views read {@link #current} while drawing, so switching only needs a redraw.
 * Light: aurora and the focused app's colour, focus by zoom and a bright platter. Dark ("Notte",
 * after Nuvio OS): near-black with one cold light, no app colour, focus by a blue ring.
 */
final class Theme {
    static final Theme LIGHT = new Theme("light", 0.85f, true, 0xB3FFFFFF, 0xCCFFFFFF,
            0x1AFFFFFF, 0x1AFFFFFF, 0, 0x8CFFFFFF, 0x14FFFFFF, Tile.FOCUSED, 0, 0x40000000);
    static final Theme DARK = new Theme("dark", 0f, false, 0xFF8B9099, 0xFF9AA0A8,
            0x0FFFFFFF, 0xB816181E, 0x0DFFFFFF, 0x1FFFFFFF, 0x14FFFFFF, 1.05f, 0xFF4EA0E8, 0);
    /** The saved choice: {@link #LIGHT}'s or {@link #DARK}'s id, or this for Light by day and Dark from sunset. */
    static final String AUTO = "auto";
    static String choice = "light";
    static Theme current = LIGHT;

    final String id;
    /** Opacity of the focused app's colour wash over the wallpaper. */
    final float ambient;
    /** Light only: darkens the top and bottom of the wallpaper so text stays readable over the aurora. */
    final boolean scrim;
    /** Secondary text: the row name above the title, and row names. */
    final int overline, label;
    /** Fill over the sampled backdrop for pills and cards, for menus and panels, and for rows inside a panel. */
    final int glassFill, panelFill, rowFill;
    final int rimTop, rimBottom;
    final float tileScale;
    /** Focus ring colour; 0 means focus by zoom and a bright platter instead. */
    final int ring;
    /** Light only: a dark tint in the glass under pills, cards and panels, so their white text holds contrast. */
    final int glassVeil;

    private Theme(String id, float ambient, boolean scrim, int overline, int label, int glassFill, int panelFill,
                  int rowFill, int rimTop, int rimBottom, float tileScale, int ring, int glassVeil) {
        this.id = id;
        this.ambient = ambient;
        this.scrim = scrim;
        this.overline = overline;
        this.label = label;
        this.glassFill = glassFill;
        this.panelFill = panelFill;
        this.rowFill = rowFill;
        this.rimTop = rimTop;
        this.rimBottom = rimBottom;
        this.tileScale = tileScale;
        this.ring = ring;
        this.glassVeil = glassVeil;
    }

    Bitmap wallpaper(int w, int h) {
        return scrim ? Backdrop.aurora(w, h) : Backdrop.night(w, h);
    }

    /** The ring's own fill, under it: the ring colour at 16%. */
    int ringFill() {
        return (ring & 0x00FFFFFF) | 0x29000000;
    }

    static void load(Context c) {
        choice = prefs(c).getString("theme", LIGHT.id);
        resolve(c);
    }

    static void set(Context c, String choice) {
        Theme.choice = choice;
        prefs(c).edit().putString("theme", choice).apply();
        resolve(c);
    }

    /** Picks the theme for the choice and, for Automatic, the time of day. True if it changed. */
    static boolean resolve(Context c) {
        Theme t = AUTO.equals(choice) ? (Sun.isDay(c, System.currentTimeMillis()) ? LIGHT : DARK)
                : DARK.id.equals(choice) ? DARK : LIGHT;
        boolean changed = t != current;
        current = t;
        return changed;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("glass", Context.MODE_PRIVATE);
    }
}
