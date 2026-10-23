package io.github.b0reddev.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The opt-in daily wallpaper: one SFW landscape photo from wallhaven.cc, kept in the app's files directory.
 * The only code in Glass that uses the network. A failed fetch keeps the old image and never reaches the UI.
 */
final class Wallpaper {
    private static final String SEARCH = "https://wallhaven.cc/api/v1/search?categories=100&purity=100"
            + "&sorting=toplist&topRange=1M&atleast=1920x1080&ratios=16x9&q=landscape";
    private static final long DAY = 24 * 3600_000L, RETRY = 3600_000L;
    private static final int MAX_BYTES = 4_000_000;
    private static final AtomicBoolean busy = new AtomicBoolean();

    private Wallpaper() {}

    static boolean enabled(Context c) {
        return "wallhaven".equals(c.getSharedPreferences("glass", Context.MODE_PRIVATE).getString("wallpaper", "glass"));
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.img");
    }

    /**
     * When the setting is on and the last image is over a day old (and the last failure over an hour old),
     * fetches a new one on a background thread and posts {@code onNewImage} on the main thread once it is in
     * place. Removing {@code wall_time} and {@code wall_try} from the prefs forces a fetch.
     */
    static void refresh(Context c, Runnable onNewImage) {
        Context app = c.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("glass", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!enabled(app) || now - p.getLong("wall_time", 0) < DAY || now - p.getLong("wall_try", 0) < RETRY) return;
        if (!busy.compareAndSet(false, true)) return;
        p.edit().putLong("wall_try", now).apply();
        new Thread(() -> {
            try {
                if (fetch(app, p)) new Handler(Looper.getMainLooper()).post(onNewImage);
            } catch (Exception e) {
                Log.w("Glass", "wallpaper fetch failed: " + e);
            } finally {
                busy.set(false);
            }
        }, "wallpaper").start();
    }

    private static boolean fetch(Context c, SharedPreferences p) throws Exception {
        JSONArray data = new JSONObject(new String(read(SEARCH, 1_000_000), StandardCharsets.UTF_8)).getJSONArray("data");
        String last = p.getString("wall_id", "");
        List<JSONObject> ok = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            String type = o.optString("file_type");
            long size = o.optLong("file_size");
            if ("sfw".equals(o.optString("purity")) && size > 0 && size <= MAX_BYTES
                    && (type.equals("image/jpeg") || type.equals("image/png")) && !o.getString("id").equals(last)) {
                ok.add(o);
            }
        }
        if (ok.isEmpty()) return false;
        JSONObject pick = ok.get(new Random().nextInt(ok.size()));
        File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
        Files.write(tmp.toPath(), read(pick.getString("path"), MAX_BYTES));
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(tmp.getPath(), o);
        if (o.outWidth <= 0) return false; // not an image: the old one stays
        Files.move(tmp.toPath(), file(c).toPath(), StandardCopyOption.REPLACE_EXISTING);
        p.edit().putString("wall_id", pick.getString("id")).putLong("wall_time", System.currentTimeMillis()).apply();
        Log.i("Glass", "wallpaper " + pick.getString("id"));
        return true;
    }

    /** The whole body of a 200 answer, at most {@code max} bytes. Also used for the Top Shelf's artwork. */
    static byte[] read(String url, int max) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setConnectTimeout(10_000);
            h.setReadTimeout(30_000);
            h.setRequestProperty("User-Agent", "Glass-launcher (+https://github.com/B0redDev/glass-launcher)");
            if (h.getResponseCode() != 200) throw new IOException("HTTP " + h.getResponseCode() + " " + url);
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > max) throw new IOException("over " + max + " bytes: " + url);
                }
                return out.toByteArray();
            }
        } finally {
            h.disconnect();
        }
    }

    /**
     * The image at exactly w×h (centre-cropped to that shape), or null if it can't be read. Sub-sampled while
     * decoding so a 4K photo is never held at full size. Call off the main thread.
     */
    static Bitmap decode(File f, int w, int h) {
        return fit(o -> BitmapFactory.decodeFile(f.getPath(), o), w, h);
    }

    static Bitmap decode(byte[] data, int w, int h) {
        return fit(o -> BitmapFactory.decodeByteArray(data, 0, data.length, o), w, h);
    }

    private static Bitmap fit(Function<BitmapFactory.Options, Bitmap> source, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        source.apply(o);
        if (o.outWidth <= 0) return null;
        int s = 1;
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        Bitmap b = source.apply(o);
        if (b == null) return null;
        float k = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
        int cw = Math.min(b.getWidth(), Math.round(w / k)), ch = Math.min(b.getHeight(), Math.round(h / k));
        Bitmap crop = Bitmap.createBitmap(b, (b.getWidth() - cw) / 2, (b.getHeight() - ch) / 2, cw, ch);
        Bitmap out = Bitmap.createScaledBitmap(crop, w, h, true);
        if (crop != b) b.recycle();
        if (out != crop) crop.recycle();
        return out;
    }
}
