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
    static final Theme LIGHT = new Theme("light", 0.85f, true, 0x99FFFFFF, 0xB3FFFFFF,
            0x1AFFFFFF, 0x29FFFFFF, 0, 0x8CFFFFFF, 0x14FFFFFF, Tile.FOCUSED, 0);
    static final Theme DARK = new Theme("dark", 0f, false, 0xFF8B9099, 0xFF9AA0A8,
            0x0FFFFFFF, 0xB816181E, 0x0DFFFFFF, 0x1FFFFFFF, 0x14FFFFFF, 1.05f, 0xFF4EA0E8);
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

    private Theme(String id, float ambient, boolean scrim, int overline, int label, int glassFill, int panelFill,
                  int rowFill, int rimTop, int rimBottom, float tileScale, int ring) {
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
    }

    Bitmap wallpaper(int w, int h) {
        return scrim ? Backdrop.aurora(w, h) : Backdrop.night(w, h);
    }

    /** The ring's own fill, under it: the ring colour at 16%. */
    int ringFill() {
        return (ring & 0x00FFFFFF) | 0x29000000;
    }

    static void load(Context c) {
        current = DARK.id.equals(prefs(c).getString("theme", LIGHT.id)) ? DARK : LIGHT;
    }

    static void set(Context c, Theme t) {
        current = t;
        prefs(c).edit().putString("theme", t.id).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("glass", Context.MODE_PRIVATE);
    }
}
