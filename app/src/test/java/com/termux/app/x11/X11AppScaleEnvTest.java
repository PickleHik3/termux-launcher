package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class X11AppScaleEnvTest {

    private static void check(String scale, String gdk, String dpiScale, String qt) {
        X11AppScaleEnv e = X11AppScaleEnv.of(scale);
        assertEquals(scale, gdk, e.env.get("GDK_SCALE"));
        assertEquals(scale, dpiScale, e.env.get("GDK_DPI_SCALE"));
        assertEquals(scale, qt, e.env.get("QT_SCALE_FACTOR"));
        assertEquals(scale, "0", e.env.get("QT_AUTO_SCREEN_SCALE_FACTOR"));
        assertEquals(scale, "1", e.env.get("QT_ENABLE_HIGHDPI_SCALING"));
    }

    @Test public void everyScale() {
        check("1", "1", "1", "1");
        check("1.25", "1", "1.25", "1.25");
        check("1.5", "1", "1.5", "1.5");
        check("2", "2", "1", "2");
        check("2.5", "2", "1.25", "2.5");
        check("3", "3", "1", "3");
    }

    @Test public void neverTouchesTheDisplaysDpi() {
        for (String scale : X11AppSettings.SCALES) {
            assertFalse(X11AppScaleEnv.of(scale).env.keySet().stream().anyMatch(k -> k.contains("XFT")));
        }
    }

    @Test public void everyVariableIsOnTheForwardedList() {
        for (String scale : X11AppSettings.SCALES) {
            assertEquals(X11AppLaunchEnv.SCALE_VARIABLES,
                new java.util.ArrayList<>(X11AppScaleEnv.of(scale).env.keySet()));
        }
    }

    @Test public void followAndInvalidAreEmpty() {
        for (String s : new String[] {null, "", "1.75", "abc", "0"}) {
            assertTrue(X11AppScaleEnv.of(s).env.isEmpty());
        }
    }
}
