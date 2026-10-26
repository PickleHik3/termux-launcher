package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HomeDropTargetsTest {

    // ---- which targets

    @Test
    public void anUninstallableAppShowsRemoveThenUninstall() {
        assertEquals(Arrays.asList(HomeDropTargets.Target.REMOVE, HomeDropTargets.Target.UNINSTALL),
            HomeDropTargets.targetsFor(false, true));
    }

    @Test
    public void anAppThatCannotBeUninstalledShowsOnlyRemove() {
        assertEquals(Collections.singletonList(HomeDropTargets.Target.REMOVE),
            HomeDropTargets.targetsFor(false, false));
    }

    /** A folder is removed from Home, never uninstalled: its apps stay installed. */
    @Test
    public void aFolderShowsOnlyRemove() {
        assertEquals(Collections.singletonList(HomeDropTargets.Target.REMOVE),
            HomeDropTargets.targetsFor(true, true));
    }

    @Test
    public void onlyAnInstalledUserAppOtherThanThisLauncherCanBeUninstalled() {
        assertTrue(HomeDropTargets.canUninstall(true, true, false, false));
        assertFalse("system app", HomeDropTargets.canUninstall(true, true, true, false));
        assertFalse("this launcher", HomeDropTargets.canUninstall(true, true, false, true));
        assertFalse("gone", HomeDropTargets.canUninstall(true, false, false, false));
        assertFalse("Linux app or launcher entry",
            HomeDropTargets.canUninstall(false, true, false, false));
    }

    // ---- where they sit

    private static final HomeDropTargets.Box BAND = new HomeDropTargets.Box(0, 0, 1000, 100);

    /** Two pills centred as a row with the gap between, each as tall as asked and centred in the band. */
    @Test
    public void twoPillsSitCentredInTheBand() {
        HomeDropTargets.Layout layout = HomeDropTargets.layout(BAND, new int[]{200, 300}, 40, 20);
        assertEquals(new HomeDropTargets.Box(240, 30, 440, 70), layout.pills.get(0));
        assertEquals(new HomeDropTargets.Box(460, 30, 760, 70), layout.pills.get(1));
    }

    /** A band shorter than a pill makes the pill as short as the band. */
    @Test
    public void aShortBandShortensThePills() {
        HomeDropTargets.Layout layout = HomeDropTargets.layout(
            new HomeDropTargets.Box(0, 50, 1000, 80), new int[]{200}, 40, 20);
        assertEquals(new HomeDropTargets.Box(400, 50, 600, 80), layout.pills.get(0));
    }

    /** A label too wide for its share is held to it, so the pills never run into each other. */
    @Test
    public void aWideLabelIsHeldToItsShare() {
        HomeDropTargets.Layout layout = HomeDropTargets.layout(BAND, new int[]{900, 900}, 40, 20);
        assertEquals(490, layout.pills.get(0).width());
        assertTrue(layout.pills.get(0).right <= layout.pills.get(1).left);
        assertTrue(layout.pills.get(1).right <= BAND.right);
    }

    // ---- which one the finger is over

    /** The band splits at the middle of the gap, and each half answers from its foot all the way up. */
    @Test
    public void theBandSplitsBetweenTheTargetsAtTheGap() {
        HomeDropTargets.Layout layout = HomeDropTargets.layout(BAND, new int[]{200, 300}, 40, 20);
        assertEquals(0, HomeDropTargets.hit(layout, 10, 90));
        assertEquals(0, HomeDropTargets.hit(layout, 449, 50));
        assertEquals(1, HomeDropTargets.hit(layout, 450, 50));
        assertEquals(1, HomeDropTargets.hit(layout, 999, -40));
        assertEquals(-1, HomeDropTargets.hit(layout, 500, 100));
        assertEquals(-1, HomeDropTargets.hit(layout, 1000, 50));
    }

    @Test
    public void oneTargetTakesTheWholeBand() {
        HomeDropTargets.Layout layout = HomeDropTargets.layout(BAND, new int[]{200}, 40, 20);
        assertEquals(0, HomeDropTargets.hit(layout, 0, 0));
        assertEquals(0, HomeDropTargets.hit(layout, 999, 99));
        assertEquals(-1, HomeDropTargets.hit(layout, 500, 140));
    }

    @Test
    public void noTargetsOrNoBandAnswerNothing() {
        assertEquals(-1, HomeDropTargets.hit(HomeDropTargets.layout(BAND, new int[0], 40, 20), 5, 5));
        assertEquals(-1, HomeDropTargets.hit(HomeDropTargets.layout(
            new HomeDropTargets.Box(0, 0, 0, 100), new int[]{100}, 40, 20), 0, 5));
    }

    /** Without status chrome along the top: the system bar's inset and 56dp under it. */
    @Test
    public void theFallbackBandRunsJustUnderTheSystemBar() {
        assertEquals(new HomeDropTargets.Box(0, 0, 1080, 72 + 112),
            HomeDropTargets.fallbackBand(0, 0, 1080, 72, 2f));
        assertEquals(new HomeDropTargets.Box(10, 5, 410, 5 + 56),
            HomeDropTargets.fallbackBand(10, 5, 400, -3, 1f));
    }

    @Test
    public void aPillIsItsPaddingGlyphAndLabelWide() {
        assertEquals(2 * 16 + 18 + 8 + 100, HomeDropTargets.contentWidth(100, 1f));
        assertEquals(2 * (2 * 16 + 18 + 8) + 50, HomeDropTargets.contentWidth(50, 2f));
    }
}
