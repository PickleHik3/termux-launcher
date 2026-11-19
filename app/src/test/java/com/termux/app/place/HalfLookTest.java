package com.termux.app.place;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The look each half dresses its chrome in (ADR 0013), and which half a surface wears on a frame:
 * a stack look that differs is a dress that differs, so the crossing changes it where none of the
 * stack shows.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HalfLookTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("half-look-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
    }

    private static final WallShape SHAPE = WallShape.of(LayoutStyle.DOCKED, -1, 8);

    @Test
    public void untouchedHalves_lookAlike() {
        assertEquals(HalfLook.read(preferences, TermuxAppSharedPreferences.PAGE_WALL),
            HalfLook.read(preferences, TermuxAppSharedPreferences.terminalPageId()));
    }

    /** The chrome wears one look on every page (ADR 0031): a page names no look of its own. */
    @Test
    public void bothHalvesWearOneLook() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId(), 5);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            TermuxAppSharedPreferences.terminalPageId(), 90);
        preferences.setInAppKeyboardBackdropOpacity(TermuxAppSharedPreferences.terminalPageId(), 40);
        HalfLook home = HalfLook.read(preferences, TermuxAppSharedPreferences.PAGE_WALL);
        HalfLook terminal = HalfLook.read(preferences, TermuxAppSharedPreferences.terminalPageId());

        assertTrue(home.stackEquals(terminal));
        assertTrue(home.statusEquals(terminal));
        assertEquals(SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, home),
            SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, terminal));
    }

    /**
     * A stack look apart is a dress apart, but not a structure apart: the crossing blends its
     * glass instead of dipping the stack (amendment 2026-11-04). A shape or a size apart is.
     */
    @Test
    public void stackLookAlone_keepsTheStacksStructure() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId(), 5);
        HalfLook home = HalfLook.read(preferences, TermuxAppSharedPreferences.PAGE_WALL);
        HalfLook terminal = HalfLook.read(preferences, TermuxAppSharedPreferences.terminalPageId());
        SeamDress.StackDress a = SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, home);
        assertTrue(a.structureEquals(SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, terminal)));
        assertFalse(a.structureEquals(SeamDress.StackDress.of(
            WallShape.of(LayoutStyle.DOCKED, -1, 16), 1f, 1f, 0, home)));
        assertFalse(a.structureEquals(SeamDress.StackDress.of(SHAPE, 0.8f, 1f, 0, home)));
    }

    @Test
    public void lookHalf_followsTheDressEachSurfaceWears() {
        PageKind home = PageKind.HOME;
        PageKind window = PageKind.TERMINAL;
        // At rest, every surface wears its committed half's look.
        for (SeamDress.Surface surface : SeamDress.Surface.values()) {
            assertEquals(PageKind.HOME, SeamDress.lookPage(surface, home, home, false));
            assertEquals(PageKind.TERMINAL, SeamDress.lookPage(surface, window, window, false));
        }
        // Mid-slide from Home: the stack wears the half whose rows it stands, the status bar the
        // Terminal's, the frame the committed half's.
        assertEquals(PageKind.TERMINAL,
            SeamDress.lookPage(SeamDress.Surface.STACK, home, window, true));
        assertEquals(PageKind.HOME,
            SeamDress.lookPage(SeamDress.Surface.STACK, home, home, true));
        assertEquals(PageKind.TERMINAL,
            SeamDress.lookPage(SeamDress.Surface.STATUS, home, home, true));
        assertEquals(PageKind.HOME,
            SeamDress.lookPage(SeamDress.Surface.FRAME, home, window, true));
    }

    @Test
    public void halfOf_namesThePagesHalf() {
        assertEquals(PageKind.HOME, SeamDress.screenOf(PageKind.HOME));
        assertEquals(PageKind.TERMINAL, SeamDress.screenOf(PageKind.TERMINAL));
    }

}
