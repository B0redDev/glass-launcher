package io.github.b0reddev.glass;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/** Sun times against the astral library (2026), within two minutes; and days without sunrise or sunset. */
public class SunTest {
    private static final long MINUTE = 60_000;

    @Test
    public void matchesReferenceTimes() {
        check("Europe/Rome", 41.90, 12.48, 2026, 10, 21, "07:29", "18:19");       // standard time
        check("Europe/Rome", 41.90, 12.48, 2026, 6, 21, "05:35", "20:48");        // summer time
        check("Asia/Tokyo", 35.65, 139.74, 2026, 1, 15, "06:50", "16:50");        // sunrise is the day before in UT
        check("America/Los_Angeles", 34.05, -118.24, 2026, 3, 9, "07:11", "18:55"); // west, the day after a DST switch
    }

    @Test
    public void polarDayAndNight() {
        TimeZone tz = TimeZone.getTimeZone("Arctic/Longyearbyen");
        long noon = at(tz, 2026, 6, 21, 12, 0);
        long[] summer = Sun.riseSet(78.00, 16.00, noon, tz);
        assertEquals(at(tz, 2026, 6, 21, 0, 0), summer[0]);   // light from midnight...
        assertEquals(summer[2], summer[1]);                    // ...to the next one
        long[] winter = Sun.riseSet(78.00, 16.00, at(tz, 2026, 12, 21, 12, 0), tz);
        assertEquals(winter[2], winter[0]);                    // dark all day
        assertEquals(winter[2], winter[1]);
    }

    @Test
    public void nextChangeIsTheNextSunriseOrSunset() {
        TimeZone tz = TimeZone.getTimeZone("Europe/Rome");
        long early = at(tz, 2026, 10, 21, 5, 0), noon = at(tz, 2026, 10, 21, 12, 0), late = at(tz, 2026, 10, 21, 21, 0);
        assertEquals(false, Sun.isDay(41.90, 12.48, early, tz));
        assertEquals(true, Sun.isDay(41.90, 12.48, noon, tz));
        assertEquals(false, Sun.isDay(41.90, 12.48, late, tz));
        assertNear("before sunrise", at(tz, 2026, 10, 21, "07:29"), Sun.nextChange(41.90, 12.48, early, tz));
        assertNear("after sunrise", at(tz, 2026, 10, 21, "18:19"), Sun.nextChange(41.90, 12.48, noon, tz));
        assertNear("after sunset", at(tz, 2026, 10, 22, "07:30"), Sun.nextChange(41.90, 12.48, late, tz)); // not midnight
        TimeZone arctic = TimeZone.getTimeZone("Arctic/Longyearbyen");
        long midsummer = at(arctic, 2026, 6, 21, 12, 0);
        assertEquals(true, Sun.isDay(78.00, 16.00, midsummer, arctic));
        assertEquals(at(arctic, 2026, 6, 22, 0, 0), Sun.nextChange(78.00, 16.00, midsummer, arctic)); // look again at midnight
    }

    private static void check(String zone, double lat, double lon, int y, int mo, int d, String rise, String set) {
        TimeZone tz = TimeZone.getTimeZone(zone);
        long[] t = Sun.riseSet(lat, lon, at(tz, y, mo, d, 12, 0), tz);
        assertNear(zone + " sunrise", at(tz, y, mo, d, rise), t[0]);
        assertNear(zone + " sunset", at(tz, y, mo, d, set), t[1]);
    }

    private static void assertNear(String what, long expected, long actual) {
        assertEquals(what + " off by " + (actual - expected) / MINUTE + " min", 0, (actual - expected) / (2 * MINUTE + 1));
    }

    private static long at(TimeZone tz, int y, int mo, int d, String hhmm) {
        return at(tz, y, mo, d, Integer.parseInt(hhmm.substring(0, 2)), Integer.parseInt(hhmm.substring(3)));
    }

    private static long at(TimeZone tz, int y, int mo, int d, int h, int m) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(y, mo - 1, d, h, m);
        return c.getTimeInMillis();
    }
}
