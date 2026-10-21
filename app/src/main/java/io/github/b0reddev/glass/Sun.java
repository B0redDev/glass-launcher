package io.github.b0reddev.glass;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Sunrise and sunset for the automatic theme, worked out on the TV: the almanac algorithm (zenith 90.833°,
 * about a minute off), for the main city of the TV's time zone. No location permission, no network.
 */
final class Sun {
    private static double[] where;
    private static String whereZone;

    private Sun() {}

    /** True between sunrise and sunset at the TV's location. */
    static boolean isDay(Context c, long now) {
        double[] p = where(c);
        return isDay(p[0], p[1], now, TimeZone.getDefault());
    }

    /** The next sunrise or sunset at the TV's location. */
    static long nextChange(Context c, long now) {
        double[] p = where(c);
        return nextChange(p[0], p[1], now, TimeZone.getDefault());
    }

    static boolean isDay(double lat, double lon, long now, TimeZone tz) {
        long[] d = riseSet(lat, lon, now, tz);
        return d[0] <= now && now < d[1];
    }

    /** The next sunrise or sunset after {@code now}; on days without either, a midnight, to look again. */
    static long nextChange(double lat, double lon, long now, TimeZone tz) {
        long[] today = riseSet(lat, lon, now, tz), tomorrow = riseSet(lat, lon, today[2], tz);
        for (long t : new long[]{today[0], today[1], tomorrow[0], tomorrow[1]}) if (t > now) return t;
        return tomorrow[2];
    }

    /** Latitude and longitude of the time zone's main city, from IANA's zone.tab (res/raw/zones.txt). */
    static double[] where(Context c) {
        TimeZone tz = TimeZone.getDefault();
        if (tz.getID().equals(whereZone)) return where;
        whereZone = tz.getID();
        String id = tz.getID() + " ";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(R.raw.zones)))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.startsWith(id)) continue;
                String[] f = line.split(" ");
                return where = new double[]{Double.parseDouble(f[1]), Double.parseDouble(f[2])};
            }
        } catch (IOException ignored) {
            // falls through to the guess
        }
        // Unknown zone (e.g. plain UTC): the offset gives the longitude, a mid-northern latitude the rest.
        return where = new double[]{40, tz.getRawOffset() / 3_600_000.0 * 15};
    }

    /**
     * Sunrise, sunset and the next midnight, in epoch ms, for the local day containing {@code when}. A day the sun
     * never sets gives sunrise at midnight and sunset at the next one; a day it never rises, both at the next one.
     */
    static long[] riseSet(double lat, double lon, long when, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(when);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long midnight = c.getTimeInMillis();
        int n = c.get(Calendar.DAY_OF_YEAR);
        c.add(Calendar.DAY_OF_YEAR, 1);
        long next = c.getTimeInMillis();
        double offset = tz.getOffset(midnight + 12 * 3_600_000L) / 3_600_000.0;
        double rise = event(n, lat, lon, true), set = event(n, lat, lon, false);
        if (rise == Double.NEGATIVE_INFINITY || set == Double.NEGATIVE_INFINITY) return new long[]{midnight, next, next};
        if (Double.isInfinite(rise) || Double.isInfinite(set)) return new long[]{next, next, next};
        return new long[]{midnight + hours(rise + offset), midnight + hours(set + offset), next};
    }

    private static long hours(double h) {
        h = ((h % 24) + 24) % 24;
        return Math.round(h * 3_600_000);
    }

    /**
     * Time of sunrise or sunset in UT hours (not wrapped to 0..24) on day {@code n} of the year; minus infinity when
     * the sun stays up all day, plus infinity when it stays down.
     */
    private static double event(int n, double lat, double lon, boolean rise) {
        double lngHour = lon / 15;
        double t = n + ((rise ? 6 : 18) - lngHour) / 24;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))));
        ra = (ra + Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90) / 15;
        double sinDec = 0.39782 * sin(l), cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(90.833) - sinDec * sin(lat)) / (cosDec * cos(lat));
        if (cosH < -1) return Double.NEGATIVE_INFINITY;
        if (cosH > 1) return Double.POSITIVE_INFINITY;
        double h = Math.toDegrees(Math.acos(cosH));
        h = (rise ? 360 - h : h) / 15;
        return h + ra - 0.06571 * t - 6.622 - lngHour;
    }

    private static double norm(double d) {
        return ((d % 360) + 360) % 360;
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }
}
