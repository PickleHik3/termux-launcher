package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.look.TerminalPageIdFixture;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Page looks as the preferences read and write them (ADR 0015 §6): a write that names no page
 * shows on every page, and the canvas honours the Terminal page's own value.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxAppSharedPreferencesPageLookTest {

    private static final int T = TerminalPageIdFixture.TERMINAL_PAGE_ID;
    private static final int DISPLAY = 5;

    private SharedPreferences store;
    private TermuxAppSharedPreferences prefs;
    private String dockOpacity;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("page-look-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        prefs = TerminalPageIdFixture.give(new TermuxAppSharedPreferences(context, store, null));
        dockOpacity = TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.DOCK,
            SurfaceProperty.OPACITY);
    }

    /** L5: a write on every page drops every page's own value, a Display page's included. */
    @Test
    public void aWriteOnAllPagesDropsEveryPagesOverride() {
        store.edit().putInt(PageLookKeys.pageKey(dockOpacity, T), 70)
            .putInt(PageLookKeys.pageKey(dockOpacity, DISPLAY), 70).commit();
        prefs.setSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_ALL, 37);
        assertFalse(store.contains(PageLookKeys.pageKey(dockOpacity, DISPLAY)));
        assertFalse(store.contains(PageLookKeys.pageKey(dockOpacity, T)));
        assertEquals(37, prefs.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, DISPLAY));
        assertEquals(37, prefs.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, T));
    }

    /** L4: the Terminal's own value and no wall flag: every page reads the written number. */
    @Test
    public void aWriteOverATerminalOverrideIsNotLost() {
        store.edit().putInt(PageLookKeys.pageKey(dockOpacity, T), 70).commit();
        prefs.setSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_ALL, 37);
        assertEquals(37, prefs.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, T));
        assertEquals(37, prefs.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL));
    }

    /** L3: the terminal's ground reads the Terminal page's own value, as PageLook does. */
    @Test
    public void theCanvasHonoursTheTerminalPagesOverride() {
        String key = TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.CANVAS,
            SurfaceProperty.OPACITY);
        store.edit().putInt(key, 80).putInt(PageLookKeys.pageKey(key, T), 30).commit();
        assertEquals(30, prefs.getTerminalBackgroundOpacity());
        assertEquals(30, prefs.getSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, T));
    }
}
