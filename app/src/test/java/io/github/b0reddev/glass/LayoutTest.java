package io.github.b0reddev.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

/** The layout backup text: round trip, and tolerance for foreign or broken input. */
public class LayoutTest {
    @Test
    public void roundTrip() {
        String rows = "fav\tcom.a\nfav\tcom.b\nstreaming\tcom.c\n";
        HashSet<String> hidden = new HashSet<>(Arrays.asList("com.z", "com.y"));
        Layout.Parsed p = Layout.parse(Layout.format("dark", "wallhaven", rows, hidden));
        assertEquals("dark", p.theme);
        assertEquals("wallhaven", p.wallpaper);
        assertEquals(rows, p.rows);
        assertEquals(hidden, p.hidden);
    }

    @Test
    public void missingHeaderIsNull() {
        assertNull(Layout.parse("theme\tdark\nrow\tfav\tcom.a\n"));
    }

    @Test
    public void skipsMalformedAndUnknownLines() {
        Layout.Parsed p = Layout.parse("glass-layout 1\nrow\tfav\nwhat\tever\nrow\tfav\tcom.a\nhidden\ntheme\tauto\n");
        assertEquals("fav\tcom.a\n", p.rows);
        assertEquals("auto", p.theme);
        assertEquals(new HashSet<String>(), p.hidden);
    }

    @Test
    public void emptyValuesRoundTrip() {
        Layout.Parsed p = Layout.parse(Layout.format(null, null, "", new HashSet<>()));
        assertNull(p.theme);
        assertNull(p.wallpaper);
        assertEquals("", p.rows);
        assertEquals(new HashSet<String>(), p.hidden);
    }
}
