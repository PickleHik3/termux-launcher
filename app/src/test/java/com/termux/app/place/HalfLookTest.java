package com.termux.app.place;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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
    }

    private static final WallHalfShape SHAPE = WallHalfShape.of(LayoutStyle.DOCKED, -1, 8);

    @Test
    public void untouchedHalves_lookAlike() {
        assertEquals(HalfLook.read(preferences, WallHalf.HOME),
            HalfLook.read(preferences, WallHalf.WORKSPACE));
    }

    @Test
    public void stackLook_isPartOfTheStacksDress() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 5);
        HalfLook home = HalfLook.read(preferences, WallHalf.HOME);
        HalfLook workspace = HalfLook.read(preferences, WallHalf.WORKSPACE);

        assertFalse(home.stackEquals(workspace));
        assertNotEquals(SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, home),
            SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, workspace));
    }

    @Test
    public void statusLookAlone_leavesTheStacksDressAlike() {
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT, WallHalf.HOME,
            90);
        HalfLook home = HalfLook.read(preferences, WallHalf.HOME);
        HalfLook workspace = HalfLook.read(preferences, WallHalf.WORKSPACE);

        assertTrue(home.stackEquals(workspace));
        assertFalse(home.statusEquals(workspace));
        assertNotEquals(home, workspace);
        SeamDress.StackDress a = SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, home);
        SeamDress.StackDress b = SeamDress.StackDress.of(SHAPE, 1f, 1f, 0, workspace);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void keyboardsOwnFade_isPartOfTheStack() {
        preferences.setInAppKeyboardBackdropOpacity(WallHalf.WORKSPACE, 40);
        assertFalse(HalfLook.read(preferences, WallHalf.HOME)
            .stackEquals(HalfLook.read(preferences, WallHalf.WORKSPACE)));
    }

    @Test
    public void lookHalf_followsTheDressEachSurfaceWears() {
        PaneWallPage home = PaneWallPage.WIDGETS;
        PaneWallPage window = PaneWallPage.TERMINAL;
        // At rest, every surface wears its committed half's look.
        for (SeamDress.Surface surface : SeamDress.Surface.values()) {
            assertEquals(WallHalf.HOME, SeamDress.lookHalf(surface, home, home, false));
            assertEquals(WallHalf.WORKSPACE, SeamDress.lookHalf(surface, window, window, false));
        }
        // Mid-slide from Home: the stack wears the half whose rows it stands, the status bar the
        // Workspace's, the frame the committed half's.
        assertEquals(WallHalf.WORKSPACE,
            SeamDress.lookHalf(SeamDress.Surface.STACK, home, window, true));
        assertEquals(WallHalf.HOME,
            SeamDress.lookHalf(SeamDress.Surface.STACK, home, home, true));
        assertEquals(WallHalf.WORKSPACE,
            SeamDress.lookHalf(SeamDress.Surface.STATUS, home, home, true));
        assertEquals(WallHalf.HOME,
            SeamDress.lookHalf(SeamDress.Surface.FRAME, home, window, true));
    }

    @Test
    public void halfOf_namesThePagesHalf() {
        assertEquals(WallHalf.HOME, SeamDress.halfOf(PaneWallPage.WIDGETS));
        assertEquals(WallHalf.WORKSPACE, SeamDress.halfOf(PaneWallPage.TERMINAL));
    }
}
