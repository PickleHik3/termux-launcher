package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class X11AppScaleEnvTest {

    private static void check(String scale, String gdk, String dpiScale, String qt, int dpi) {
        X11AppScaleEnv e = X11AppScaleEnv.of(scale);
        assertEquals(scale, gdk, e.env.get("GDK_SCALE"));
        assertEquals(scale, dpiScale, e.env.get("GDK_DPI_SCALE"));
        assertEquals(scale, qt, e.env.get("QT_SCALE_FACTOR"));
        assertEquals(scale, dpi, e.xftDpi);
    }

    @Test public void everyScale() {
        check("1", "1", "1", "1", 96);
        check("1.25", "1", "1.25", "1.25", 120);
        check("1.5", "1", "1.5", "1.5", 144);
        check("2", "2", "1", "2", 192);
        check("2.5", "2", "1.25", "2.5", 240);
        check("3", "3", "1", "3", 288);
    }

    @Test public void followAndInvalidAreEmpty() {
        for (String s : new String[] {null, "", "1.75", "abc", "0"}) {
            X11AppScaleEnv e = X11AppScaleEnv.of(s);
            assertTrue(e.env.isEmpty());
            assertEquals(0, e.xftDpi);
        }
    }
}
