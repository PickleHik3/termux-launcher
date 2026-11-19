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
 * Looks as the preferences read and write them: the chrome wears one look on every page
 * (ADR 0031), whatever an older build left under a page's or a kind's key.
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

    /**
     * The status bar, the dock, the keyboard, the widgets and the sheets answer every look number
     * the same on a Home page as on the Terminal page, with an older build's per-kind and
     * per-page values for each of them still in the store (ADR 0031 §1).
     */
    @Test
    public void everyChromeSurfaceAnswersTheSameOnHomeAndTheTerminal() {
        int home = 9;
        SharedPreferences.Editor old = store.edit();
        for (LookGrid.Property property : LookGrid.Property.values()) {
            int value = Math.max(property.min, Math.min(property.max, property.min + 1));
            for (LookGrid.Kind kind : LookGrid.Kind.values()) {
                if (property != LookGrid.Property.VIBRANCY)
                    old.putInt(LookGrid.key(property, null, kind), value);
                if (!property.perElement) continue;
                for (LookGrid.Element element : LookGrid.Element.values())
                    old.putInt(LookGrid.key(property, element, kind), value);
            }
            old.putInt(LookGrid.key(property, null, home), value);
            old.putInt(LookGrid.key(property, null, T), value);
        }
        old.commit();
        for (SurfaceSlot slot : new SurfaceSlot[] {SurfaceSlot.STATUS, SurfaceSlot.DOCK,
                SurfaceSlot.KEYBOARD, SurfaceSlot.WIDGETS, SurfaceSlot.SHEETS}) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.hasSurfaceProperty(slot, property)) continue;
                assertEquals(slot + " " + property,
                    prefs.getSurfaceValue(slot, property, TermuxAppSharedPreferences.PAGE_WALL),
                    prefs.getSurfaceValue(slot, property, home));
                assertEquals(slot + " " + property,
                    prefs.getSurfaceValue(slot, property, home),
                    prefs.getSurfaceValue(slot, property, T));
            }
        }
        assertEquals(prefs.getAppBarOpacity(home), prefs.getAppBarOpacity(T));
        assertEquals(prefs.getStatusBarOpacity(home), prefs.getStatusBarOpacity(T));
        assertEquals(prefs.getInAppKeyboardBackgroundOpacity(home),
            prefs.getInAppKeyboardBackgroundOpacity(T));
        assertEquals(prefs.getExtraKeysBlurRadius(home), prefs.getExtraKeysBlurRadius(T));
        assertEquals(prefs.getDockTintStrength(home), prefs.getDockTintStrength(T));
    }

    /** One look on every page (ADR 0031): an older build's page values are never read. */
    @Test
    public void aPagesOldValueIsNeverRead() {
        store.edit().putInt(PageLookKeys.pageKey(dockOpacity, T), 70)
            .putInt(PageLookKeys.pageKey(dockOpacity, DISPLAY), 70).commit();
        prefs.setSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_ALL, 37);
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

    /** The terminal's ground reads the terminal's own value; a page's old one is not read. */
    @Test
    public void theCanvasReadsTheTerminalsOwnValue() {
        String key = LookGrid.key(LookGrid.Property.OPACITY, LookGrid.Element.TERMINAL,
            LookGrid.NO_PAGE);
        store.edit().putInt(key, 80).putInt(PageLookKeys.pageKey(key, T), 30).commit();
        assertEquals(80, prefs.getTerminalBackgroundOpacity());
        assertEquals(80, prefs.getSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, T));
    }

}
