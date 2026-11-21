package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class X11AppSettingsTest {

    @Test public void pasteShortcutRoundTripsAndDropsGarbage() {
        Map<String, X11AppSettings.Overrides> in = new LinkedHashMap<>();
        in.put("a", new X11AppSettings.Overrides(null, null, null, "ctrl_shift_v"));
        in.put("b", new X11AppSettings.Overrides(null, null, null, "bogus"));
        Map<String, X11AppSettings.Overrides> out = X11AppSettings.parse(X11AppSettings.encode(in));
        assertEquals(1, out.size());
        assertEquals("ctrl_shift_v", out.get("a").pasteShortcut);
    }

    @Test public void roundTrips() {
        Map<String, X11AppSettings.Overrides> in = new LinkedHashMap<>();
        in.put("firefox.desktop", new X11AppSettings.Overrides("2", "1.5"));
        in.put("distro:debian:gimp.desktop", new X11AppSettings.Overrides(null, "2"));
        Map<String, X11AppSettings.Overrides> out = X11AppSettings.parse(X11AppSettings.encode(in));
        assertEquals(in, out);
    }

    @Test public void garbageReadsAsEmpty() {
        assertTrue(X11AppSettings.parse(null).isEmpty());
        assertTrue(X11AppSettings.parse("").isEmpty());
        assertTrue(X11AppSettings.parse("not json").isEmpty());
        assertTrue(X11AppSettings.parse("[1,2]").isEmpty());
        assertTrue(X11AppSettings.parse("{\"a\": 5, \"b\": \"x\"}").isEmpty());
    }

    @Test public void invalidValuesAreDropped() {
        Map<String, X11AppSettings.Overrides> out = X11AppSettings.parse(
            "{\"a\":{\"touch_mode\":\"9\",\"scale\":\"1.5\"},\"b\":{\"touch_mode\":\"3\",\"scale\":\"7\"},"
                + "\"c\":{\"touch_mode\":\"9\"}}");
        assertNull(out.get("a").touchMode);
        assertEquals("1.5", out.get("a").scale);
        assertEquals("3", out.get("b").touchMode);
        assertNull(out.get("b").scale);
        assertFalse(out.containsKey("c"));
    }

    @Test public void emptyEntriesAreOmitted() {
        Map<String, X11AppSettings.Overrides> in = new LinkedHashMap<>();
        in.put("a", new X11AppSettings.Overrides(null, null));
        in.put("b", new X11AppSettings.Overrides("bogus", "bogus"));
        assertEquals("{}", X11AppSettings.encode(in));
    }

    @Test public void validators() {
        assertTrue(X11AppSettings.isTouchMode("1"));
        assertTrue(X11AppSettings.isTouchMode("3"));
        assertFalse(X11AppSettings.isTouchMode("4"));
        assertFalse(X11AppSettings.isTouchMode(null));
        for (String s : X11AppSettings.SCALES) assertTrue(X11AppSettings.isScale(s));
        assertFalse(X11AppSettings.isScale("1.0"));
        assertFalse(X11AppSettings.isScale(null));
    }
}
