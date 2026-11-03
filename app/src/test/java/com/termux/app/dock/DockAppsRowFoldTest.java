package com.termux.app.dock;

import com.termux.app.place.Element;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The pinned icons hold their size while the extra-keys row folds in over the keyboard (ADR 0009):
 * the keys are the Workspace's own, so they never make the apps row a shared one. The row is the
 * same band on Home and in a window, and the dock grows by the keys' row instead.
 */
public class DockAppsRowFoldTest {

    private static final float[] DENSITIES = {1f, 2f, 2.625f, 2.75f, 3f, 3.5f};
    private static final boolean[] STYLES = {false, true};

    private static List<Element> stack(Element... bottomOutermostFirst) {
        return Arrays.asList(bottomOutermostFirst);
    }

    private static DockLayout layout(List<Element> bottom, boolean capsule, float scale,
                                     float density) {
        return DockLayoutPolicy.compute(DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(true)
            .capsule(capsule)
            .appsRowEnabledPref(bottom.contains(Element.APPS))
            .azRowEnabledPref(bottom.contains(Element.AZ))
            .appsRowAlone(DockLayoutPolicy.appsRowAloneOnDock(bottom))
            .appsRowNextToCanvas(!bottom.isEmpty()
                && bottom.get(bottom.size() - 1) == Element.APPS)
            .rowOverAz(AccessoryStackLayoutPolicy.rowOverAz(bottom))
            .rowUnderAz(AccessoryStackLayoutPolicy.rowUnderAz(bottom))
            .density(density)
            .barHeightScale(scale)
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .build());
    }

    private static float[] sizes(boolean capsule) {
        return new float[] {DockLayoutPolicy.minUsefulScale(), 2.18f,
            DockLayoutPolicy.maxUsefulScale(capsule)};
    }

    @Test
    public void theExtraKeysNeverMakeTheRowShared() {
        assertTrue(DockLayoutPolicy.appsRowAloneOnDock(stack(Element.APPS)));
        assertTrue(DockLayoutPolicy.appsRowAloneOnDock(stack(Element.EXTRA_KEYS, Element.APPS)));
        assertTrue(DockLayoutPolicy.appsRowAloneOnDock(stack(Element.APPS, Element.EXTRA_KEYS)));
        // A status bar outermost keeps its own glass and was never on the sheet.
        assertTrue(DockLayoutPolicy.appsRowAloneOnDock(
            stack(Element.EXTRA_KEYS, Element.APPS, Element.STATUS)));
    }

    @Test
    public void anyOtherBandStillShares() {
        assertFalse(DockLayoutPolicy.appsRowAloneOnDock(stack(Element.AZ, Element.APPS)));
        assertFalse(DockLayoutPolicy.appsRowAloneOnDock(
            stack(Element.EXTRA_KEYS, Element.AZ, Element.APPS)));
        assertFalse(DockLayoutPolicy.appsRowAloneOnDock(
            stack(Element.STATUS, Element.EXTRA_KEYS, Element.APPS)));
        assertFalse(DockLayoutPolicy.appsRowAloneOnDock(stack(Element.EXTRA_KEYS)));
        assertFalse(DockLayoutPolicy.appsRowAloneOnDock(stack()));
    }

    /**
     * Home's dock and a window's, with and without the letters: the same icon and the same apps
     * band on both halves, at every size, density and style, and the window's dock is Home's plus
     * the keys' row and nothing else.
     */
    @Test
    public void theIconsHoldTheirSizeAndTheDockGrowsByTheKeys() {
        List<List<Element>> homes = Arrays.asList(
            stack(Element.APPS), stack(Element.AZ, Element.APPS));
        List<List<Element>> windows = Arrays.asList(
            stack(Element.EXTRA_KEYS, Element.APPS),
            stack(Element.EXTRA_KEYS, Element.AZ, Element.APPS));
        for (float density : DENSITIES) {
            int keysPx = DockLayoutPolicy.baseToolbarHeightPx(density);
            for (boolean capsule : STYLES) {
                for (float scale : sizes(capsule)) {
                    for (int i = 0; i < homes.size(); i++) {
                        String at = (capsule ? "Floating" : "Docked") + " scale " + scale
                            + " density " + density + " " + windows.get(i);
                        DockLayout home = layout(homes.get(i), capsule, scale, density);
                        DockLayout window = layout(windows.get(i), capsule, scale, density);
                        assertEquals(at, home.appsRowIconPx, window.appsRowIconPx);
                        assertEquals(at, home.appsRowBandPx, window.appsRowBandPx);
                        assertEquals(at, home.appsBarHeightPx, window.appsBarHeightPx);
                        // The letters' crown and chin follow their own neighbours (Home's rim
                        // chin); the apps row is what this pins.
                        if (i == 0) {
                            assertEquals(at, home.combinedHeight(0, false) + keysPx,
                                window.combinedHeight(keysPx, true));
                        }
                    }
                }
            }
        }
    }

    /**
     * Pong (420dpi) in a window with the letters off, at min, the shipped default and max, in px:
     * the apps band is Home's lone band. It was the shared band before, 36, 55 and 71dp Docked and
     * 40, 55 and 70dp Floating; it is 45, 64 and 81dp Docked and 46, 62 and 80dp Floating.
     */
    @Test
    public void pongsWindowAppsBandIsHomes() {
        float density = 2.625f;
        int[] docked = {118, 168, 212};
        int[] floating = {122, 162, 210};
        List<Element> window = stack(Element.EXTRA_KEYS, Element.APPS);
        for (boolean capsule : STYLES) {
            float[] scales = sizes(capsule);
            for (int i = 0; i < scales.length; i++) {
                assertEquals((capsule ? "Floating " : "Docked ") + scales[i],
                    (capsule ? floating : docked)[i],
                    layout(window, capsule, scales[i], density).appsRowBandPx);
            }
        }
    }
}
