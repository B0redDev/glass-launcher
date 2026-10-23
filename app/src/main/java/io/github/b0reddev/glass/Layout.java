package io.github.b0reddev.glass;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A plain-text copy of the layout (theme, rows, hidden apps) in the app's external files directory, where ADB
 * can read it: install.sh saves it on the PC and puts it back after a reinstall, and Glass imports it on its
 * first start. Any new persistent setting must be added to format, parse and restoreIfEmpty.
 */
final class Layout {
    private static final String HEADER = "glass-layout 1";
    private static final String FILE = "layout.txt";

    private Layout() {}

    static final class Parsed {
        String theme;
        String rows = "";
        Set<String> hidden = new HashSet<>();
    }

    static String format(String theme, String rows, Set<String> hidden) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        if (theme != null) sb.append("theme\t").append(theme).append('\n');
        for (String line : rows.split("\n")) {
            if (!line.isEmpty()) sb.append("row\t").append(line).append('\n');
        }
        List<String> sorted = new ArrayList<>(hidden);
        Collections.sort(sorted);
        for (String pkg : sorted) sb.append("hidden\t").append(pkg).append('\n');
        return sb.toString();
    }

    /** Null unless the text starts with the header; malformed and unknown lines are skipped. */
    static Parsed parse(String text) {
        String[] lines = text.split("\n");
        if (!lines[0].equals(HEADER)) return null;
        Parsed p = new Parsed();
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t");
            if (f.length == 2 && f[0].equals("theme") && !f[1].isEmpty()) p.theme = f[1];
            else if (f.length == 3 && f[0].equals("row") && !f[1].isEmpty() && !f[2].isEmpty()) {
                rows.append(f[1]).append('\t').append(f[2]).append('\n');
            } else if (f.length == 2 && f[0].equals("hidden") && !f[1].isEmpty()) p.hidden.add(f[1]);
        }
        p.rows = rows.toString();
        return p;
    }

    static void backup(Context c, SharedPreferences prefs) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) return;
            String text = format(prefs.getString("theme", null), prefs.getString("rows", ""),
                    prefs.getStringSet("hidden", Collections.emptySet()));
            File tmp = new File(dir, FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            Files.move(tmp.toPath(), new File(dir, FILE).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // never break the launcher over its backup
        }
    }

    /** On a fresh install (no rows yet) imports the file install.sh put back, if any. */
    static void restoreIfEmpty(Context c) {
        try {
            SharedPreferences prefs = c.getSharedPreferences("glass", Context.MODE_PRIVATE);
            if (prefs.contains("rows")) return;
            File dir = c.getExternalFilesDir(null);
            if (dir == null) return;
            File f = new File(dir, FILE);
            if (!f.isFile()) return;
            Parsed p = parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (p == null) return;
            SharedPreferences.Editor e = prefs.edit().putString("rows", p.rows).putStringSet("hidden", p.hidden);
            if (p.theme != null) e.putString("theme", p.theme);
            e.commit();
        } catch (Exception e) {
            // a bad file just means no restore
        }
    }
}
