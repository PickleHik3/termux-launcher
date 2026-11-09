package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.LiftDropPolicy.Item;
import com.termux.app.launcher.widget.LiftDropPolicy.Place;
import com.termux.app.launcher.widget.LiftDropPolicy.Release;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** Which place takes which lifted item. */
public class LiftDropPolicyTest {

    @Test
    public void appsMoveBetweenHomeAndTheDock() {
        assertTrue(LiftDropPolicy.accepts(Item.HOME_APP, Place.DOCK));
        assertTrue(LiftDropPolicy.accepts(Item.DOCK_APP, Place.HOME));
        assertTrue("the dock reorders its own", LiftDropPolicy.accepts(Item.DOCK_APP, Place.DOCK));
        assertTrue(LiftDropPolicy.accepts(Item.HOME_APP, Place.HOME));
    }

    /** A Home folder is not a dock folder, and a shortcut is not a dock pin. */
    @Test
    public void whatTheDockCannotHoldItRefuses() {
        assertFalse(LiftDropPolicy.accepts(Item.HOME_FOLDER, Place.DOCK));
        assertFalse(LiftDropPolicy.accepts(Item.SHORTCUT, Place.DOCK));
    }

    @Test
    public void aShortcutLandsOnlyOnHome() {
        assertTrue(LiftDropPolicy.accepts(Item.SHORTCUT, Place.HOME));
        assertFalse(LiftDropPolicy.accepts(Item.SHORTCUT, Place.REMOVE));
        assertFalse(LiftDropPolicy.accepts(Item.SHORTCUT, Place.UNINSTALL));
        assertEquals(Collections.emptyList(), LiftDropPolicy.targetsFor(Item.SHORTCUT, true));
    }

    @Test
    public void removeTakesAnythingOffHomeOrTheDock() {
        assertTrue(LiftDropPolicy.accepts(Item.HOME_APP, Place.REMOVE));
        assertTrue(LiftDropPolicy.accepts(Item.HOME_FOLDER, Place.REMOVE));
        assertTrue(LiftDropPolicy.accepts(Item.DOCK_APP, Place.REMOVE));
    }

    @Test
    public void uninstallIsForAnAppNotAFolder() {
        assertTrue(LiftDropPolicy.accepts(Item.HOME_APP, Place.UNINSTALL));
        assertTrue(LiftDropPolicy.accepts(Item.DOCK_APP, Place.UNINSTALL));
        assertFalse(LiftDropPolicy.accepts(Item.HOME_FOLDER, Place.UNINSTALL));
    }

    @Test
    public void theTargetsShownFollowWhatIsInTheAir() {
        assertEquals(Arrays.asList(HomeDropTargets.Target.REMOVE, HomeDropTargets.Target.UNINSTALL),
            LiftDropPolicy.targetsFor(Item.DOCK_APP, true));
        assertEquals(Collections.singletonList(HomeDropTargets.Target.REMOVE),
            LiftDropPolicy.targetsFor(Item.DOCK_APP, false));
        assertEquals(Collections.singletonList(HomeDropTargets.Target.REMOVE),
            LiftDropPolicy.targetsFor(Item.HOME_FOLDER, true));
    }

    @Test
    public void aReleaseGoesToTheTargetThenTheDockThenHome() {
        assertEquals(Release.TARGET, LiftDropPolicy.release(false, true, true, true));
        assertEquals(Release.DOCK, LiftDropPolicy.release(false, false, true, true));
        assertEquals(Release.HOME, LiftDropPolicy.release(false, false, false, true));
    }

    /**
     * Let go at the screen's leftmost edge, beside the row and off Home's grid: Home, if it has
     * been following the finger, decides (and puts nothing down off its grid); otherwise the icon
     * goes back. Either way it is a release with an answer, never one that leaves the icon nowhere.
     */
    @Test
    public void aReleaseAtTheScreensEdgeGoesHomeOrBack() {
        assertEquals(Release.BACK, LiftDropPolicy.release(false, false, false, false));
        assertEquals(Release.HOME, LiftDropPolicy.release(false, false, false, true));
    }

    @Test
    public void aCancelAlwaysGoesBack() {
        assertEquals(Release.BACK, LiftDropPolicy.release(true, true, true, true));
        assertEquals(Release.BACK, LiftDropPolicy.release(true, false, false, false));
    }
}
