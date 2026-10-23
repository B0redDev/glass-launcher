package io.github.b0reddev.glass;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What the Top Shelf shows for an app: the programs it published to Android's Watch Next row, then the
 * programs of its own preview channel when that channel is a "continue watching" one (Stremio, Nuvio publish
 * theirs that way). Reading other apps' rows needs READ_TV_LISTINGS; without it the provider returns nothing.
 */
final class Watching {
    static final int MAX = 12;
    // The provider refuses selections and sort orders from apps like Glass, so browsable and recency are read too.
    private static final String[] COLUMNS = {"package_name", "title", "season_display_number",
            "episode_display_number", "poster_art_uri", "poster_art_aspect_ratio", "thumbnail_uri", "intent_uri",
            "duration_millis", "last_playback_position_millis", "browsable", "last_engagement_time_utc_millis"};

    static final class Item {
        final String pkg, title, line, image, intent;
        final float progress;

        Item(String pkg, String title, String line, String image, String intent, float progress) {
            this.pkg = pkg;
            this.title = title;
            this.line = line;
            this.image = image;
            this.intent = intent;
            this.progress = progress;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Item)) return false;
            Item i = (Item) o;
            return pkg.equals(i.pkg) && title.equals(i.title) && line.equals(i.line) && Objects.equals(image, i.image)
                    && Objects.equals(intent, i.intent) && progress == i.progress;
        }

        @Override
        public int hashCode() {
            return Objects.hash(pkg, title, line, intent);
        }
    }

    /** Per package, in the app's own order (most recent first). Only packages in {@code pkgs}. Off the main thread. */
    static Map<String, List<Item>> load(Context c, Collection<String> pkgs) {
        HashMap<String, List<Item>> out = new HashMap<>();
        ContentResolver cr = c.getContentResolver();
        read(c, cr, TvContract.WatchNextPrograms.CONTENT_URI, true, pkgs, out);
        // ponytail: channels are matched by name ("Continue Watching", "Continua a guardare"...); a per-app
        // channel choice would replace the guess if an app names its row differently.
        try (Cursor ch = cr.query(TvContract.Channels.CONTENT_URI, new String[]{"_id", "package_name", "display_name"},
                null, null, null)) {
            while (ch != null && ch.moveToNext()) {
                String name = ch.getString(2);
                if (pkgs.contains(ch.getString(1)) && name != null && name.toLowerCase(Locale.ROOT).contains("contin")) {
                    read(c, cr, TvContract.buildPreviewProgramsUriForChannel(ch.getLong(0)), false, pkgs, out);
                }
            }
        } catch (RuntimeException e) {
            Log.w("Glass", "top shelf channels: " + e); // no permission, or no TV provider: no shelf
        }
        return out;
    }

    /** @param byRecency Watch Next: most recently engaged first; a channel keeps its app's own order */
    private static void read(Context c, ContentResolver cr, Uri uri, boolean byRecency, Collection<String> pkgs,
                             Map<String, List<Item>> out) {
        ArrayList<Item> items = new ArrayList<>();
        ArrayList<Long> when = new ArrayList<>();
        try (Cursor k = cr.query(uri, COLUMNS, null, null, null)) {
            while (k != null && k.moveToNext()) {
                String pkg = k.getString(0), title = k.getString(1), intent = k.getString(7);
                if (!pkgs.contains(pkg) || title == null || intent == null || k.getInt(10) == 0) continue;
                long duration = k.getLong(8), position = k.getLong(9);
                if (duration > 0 && position >= duration * 0.95) continue; // watched to the end
                String se = episode(c, k.isNull(2) ? null : k.getString(2), k.isNull(3) ? null : k.getString(3));
                long left = duration > 0 && position > 0 ? (duration - position) / 60_000 : 0;
                String line = se;
                if (left >= 1) {
                    String min = c.getString(R.string.watching_left, left);
                    line = se == null ? min : se + " · " + min;
                }
                items.add(new Item(pkg, title, line == null ? "" : line, image(k), intent,
                        duration > 0 ? Math.min(1f, (float) position / duration) : 0f));
                when.add(k.getLong(11));
            }
        } catch (RuntimeException e) {
            Log.w("Glass", "top shelf " + uri + ": " + e);
        }
        Integer[] order = new Integer[items.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        if (byRecency) Arrays.sort(order, (a, b) -> Long.compare(when.get(b), when.get(a)));
        for (int i : order) {
            Item item = items.get(i);
            List<Item> list = out.get(item.pkg);
            if (list == null) out.put(item.pkg, list = new ArrayList<>());
            if (list.size() < MAX && !list.contains(item)) list.add(item);
        }
    }

    private static String episode(Context c, String season, String episode) {
        if (episode == null || episode.isEmpty()) return null;
        return season == null || season.isEmpty() ? "E" + episode : c.getString(R.string.watching_episode, season, episode);
    }

    /** A landscape picture: the poster when it is 16:9, 3:2 or 4:3 (or says nothing), else the thumbnail. */
    private static String image(Cursor k) {
        String poster = k.getString(4), thumb = k.getString(6);
        boolean wide = k.isNull(5) || k.getInt(5) <= TvContract.PreviewPrograms.ASPECT_RATIO_4_3;
        return wide || thumb == null ? poster : thumb;
    }

    /** The artwork at w×h, centre-cropped; https or content URIs. Null if it can't be had. Off the main thread. */
    static Bitmap art(Context c, String uri, int w, int h) {
        if (uri == null) return null;
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                data = Wallpaper.read(uri, 4_000_000);
            } else {
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(uri))) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
            return Wallpaper.decode(data, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if (out.size() > 4_000_000) throw new java.io.IOException("too large");
        }
        return out.toByteArray();
    }
}
