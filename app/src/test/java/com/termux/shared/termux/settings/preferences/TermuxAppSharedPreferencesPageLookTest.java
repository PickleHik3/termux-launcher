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
        // The dock's opacity cell; <cell>@<page> is its value on a page.
        dockOpacity = LookGrid.key(LookGrid.Property.OPACITY, LookGrid.Element.DOCK,
            LookGrid.NO_PAGE);
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
        String key = LookGrid.key(LookGrid.Property.OPACITY, LookGrid.Element.TERMINAL,
            LookGrid.NO_PAGE);
        store.edit().putInt(key, 80).putInt(PageLookKeys.pageKey(key, T), 30).commit();
        // The Terminal page's own value reaches the Terminal kind's cell through step 17.
        foldPageCellsThroughStep17(store, -1, T);
        assertEquals(30, prefs.getTerminalBackgroundOpacity());
        assertEquals(30, prefs.getSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, T));
    }

    /**
     * The layouts upgrade's look fold (step 17, ADR 0020 §2) over the test store: the Start-in
     * Home page's and the Terminal page's @<page> cells become their kinds' cells.
     */
    private static void foldPageCellsThroughStep17(android.content.SharedPreferences store,
                                                   int homePageId, int terminalPageId) {
        android.content.SharedPreferences.Editor editor = store.edit();
        for (java.util.Map.Entry<String, Object> cell : LookGrid.foldedPageCells(store.getAll(),
                homePageId, terminalPageId).entrySet())
            editor.putInt(cell.getKey(), ((Number) cell.getValue()).intValue());
        editor.commit();
    }

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        LookGrid.install(null, null);
    }
}
