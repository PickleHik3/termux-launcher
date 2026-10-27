package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The resolved arrangement (#141) for every Style × Dock design × half × orientation, read from a
 * real store the way the activity reads it: where the Elements stand and the shapes the
 * 2026-10-23 consistency audit expects (#139, Testing Decisions).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ResolvedArrangementTest {

    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs =
            app.getSharedPreferences("resolved-arrangement-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    /**
     * A store dressed in {@code style} and {@code design} on both screens' layouts, which start
     * identical (editor-redesign §1).
     */
    private PlaceLayoutStore dressed(LayoutStyle style, DockStyle design) {
        PlaceLayoutStore store = KindFixtures.dockOnTheTerminal(new PlaceLayoutStore(launcher));
        launcher.setLayoutStyle(style);
        store.forVariant(LayoutVariant.NORMAL).setDockStyle(design);
        store.forVariant(LayoutVariant.HOME).setDockStyle(design);
        return store;
    }

    @Test
    public void everyCellOfTheAuditResolvesTheElementsAndShapesItExpects() {
        for (LayoutStyle style : LayoutStyle.values()) {
            for (DockStyle design : DockStyle.CHOICES) {
                PlaceLayoutStore store = dressed(style, design);
                for (boolean home : new boolean[]{true, false}) {
                    for (PlaceOrientation orientation : PlaceOrientation.values()) {
                        String cell = style + " " + design + (home ? " Home " : " Workspace ")
                            + orientation;
                        ResolvedArrangement a =
                            ResolvedArrangement.resolve(store, orientation, home);

                        assertEquals(cell, home, a.home());
                        assertEquals(cell, orientation, a.orientation());
                        LayoutVariant variant = home ? LayoutVariant.HOME : LayoutVariant.NORMAL;
                        assertEquals(cell, variant, a.variant());
                        assertEquals(cell, style, a.layoutStyle());
                        assertEquals(cell, style == LayoutStyle.FLOATING, a.floating());
                        assertEquals(cell, design, a.design());

                        // The extra keys are the Workspace's under every design and Style, and
                        // Home never wears them.
                        assertEquals(cell, !home, a.extraKeysShown());
                        // The pinned apps and the index stand on both halves.
                        assertTrue(cell, a.appsShown());
                        assertTrue(cell, a.azIndexShown());
                        // Portrait lies the apps down along the bottom; landscape stands a rail.
                        boolean landscape = orientation == PlaceOrientation.LANDSCAPE;
                        assertEquals(cell, landscape, a.appsRailShown());
                        assertEquals(cell, !landscape, a.appsRowShown());

                        // The Docked frame ends above Beads, with no glass under the pill;
                        // Floating has no frame to run down at all.
                        boolean runs = style == LayoutStyle.DOCKED
                            && design != DockStyle.LENS_BEADS;
                        assertEquals(cell, runs, a.frameRunsToBottom());

                        assertPredicatesArePlaceChromePolicys(cell, a);
                        assertEquals(cell, a, ResolvedArrangement.resolve(store, orientation,
                            variant, home, style));
                    }
                }
            }
        }
    }

    @Test
    public void theFrameRunsToTheBottomOnlyDockedOffBeads() {
        PlaceLayout layout = KindFixtures.dockOnTheTerminal(new PlaceLayoutStore(launcher)).resolve(PlaceOrientation.PORTRAIT);
        for (DockStyle design : DockStyle.CHOICES) {
            assertFalse(design.toString(), arranged(layout, LayoutStyle.FLOATING, design)
                .frameRunsToBottom());
        }
        assertFalse(arranged(layout, LayoutStyle.DOCKED, DockStyle.LENS_BEADS).frameRunsToBottom());
        assertTrue(arranged(layout, LayoutStyle.DOCKED, DockStyle.GLASS_TRAY).frameRunsToBottom());
        assertTrue(arranged(layout, LayoutStyle.DOCKED, DockStyle.WALL_STRIP).frameRunsToBottom());
    }

    @Test
    public void aHomeLayoutGivesHomeItsOwnDesignAndLeavesTheWorkspacesAlone() {
        PlaceLayoutStore store = dressed(LayoutStyle.DOCKED, DockStyle.GLASS_TRAY);
        store.setHomeLayout(true);
        store.forVariant(LayoutVariant.HOME).setDockStyle(DockStyle.LENS_BEADS);
        ResolvedArrangement home =
            ResolvedArrangement.resolve(store, PlaceOrientation.PORTRAIT, true);
        ResolvedArrangement workspace =
            ResolvedArrangement.resolve(store, PlaceOrientation.PORTRAIT, false);
        assertEquals(LayoutVariant.HOME, home.variant());
        assertEquals(DockStyle.LENS_BEADS, home.design());
        assertFalse(home.frameRunsToBottom());
        assertFalse("the Home layout has no extra keys", home.extraKeysShown());
        assertEquals(LayoutVariant.NORMAL, workspace.variant());
        assertEquals(DockStyle.GLASS_TRAY, workspace.design());
        assertTrue(workspace.frameRunsToBottom());
        assertTrue(workspace.extraKeysShown());
        // One Style across the wall (#84).
        assertEquals(home.floating(), workspace.floating());
    }

    @Test
    public void standingAnotherLayoutKeepsTheDressAndAnswersForThatLayout() {
        PlaceLayoutStore store = dressed(LayoutStyle.FLOATING, DockStyle.WALL_STRIP);
        ResolvedArrangement workspace =
            ResolvedArrangement.resolve(store, PlaceOrientation.PORTRAIT, false);
        assertSame(workspace, workspace.standing(workspace.layout()));
        PlaceLayout seam = ResolvedArrangement.resolve(store, PlaceOrientation.PORTRAIT, true)
            .layout();
        ResolvedArrangement standing = workspace.standing(seam);
        assertSame(seam, standing.layout());
        assertEquals(workspace.layoutStyle(), standing.layoutStyle());
        assertEquals(workspace.design(), standing.design());
        assertEquals(workspace.home(), standing.home());
        assertFalse("Home's layout leaves the extra keys out", standing.extraKeysShown());
        assertNotEquals(workspace, standing);
        assertPredicatesArePlaceChromePolicys("standing", standing);
    }

    @Test
    public void aPredicateFollowsTheLayoutWhereAnElementWasMoved() {
        PlaceLayoutStore store = dressed(LayoutStyle.DOCKED, DockStyle.GLASS_TRAY);
        store.setSlot(PlaceOrientation.PORTRAIT, Element.EXTRA_KEYS,
            store.slot(PlaceOrientation.PORTRAIT, Element.EXTRA_KEYS).withEdge(Edge.RIGHT));
        ResolvedArrangement a =
            ResolvedArrangement.resolve(store, PlaceOrientation.PORTRAIT, false);
        assertTrue(a.extraKeysShown());
        assertTrue(a.extraKeysColumnShown());
        assertFalse(a.extraKeysRowShown());
        assertEquals(Edge.RIGHT, a.extraKeysEdge());
        assertPredicatesArePlaceChromePolicys("column", a);
    }

    private static ResolvedArrangement arranged(PlaceLayout layout, LayoutStyle style,
                                                DockStyle design) {
        return ResolvedArrangement.of(layout, style, design, false, false,
            PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL, false);
    }

    private static void assertPredicatesArePlaceChromePolicys(String cell, ResolvedArrangement a) {
        PlaceLayout l = a.layout();
        assertEquals(cell, PlaceChromePolicy.appsShown(l), a.appsShown());
        assertEquals(cell, PlaceChromePolicy.appsEdge(l), a.appsEdge());
        assertEquals(cell, PlaceChromePolicy.appsRowShown(l), a.appsRowShown());
        assertEquals(cell, PlaceChromePolicy.appsRailShown(l), a.appsRailShown());
        assertEquals(cell, PlaceChromePolicy.azRowShown(l), a.azRowShown());
        assertEquals(cell, PlaceChromePolicy.azIndexShown(l), a.azIndexShown());
        assertEquals(cell, PlaceChromePolicy.azBarEdge(l), a.azBarEdge());
        assertEquals(cell, PlaceChromePolicy.azRowOnDock(l), a.azRowOnDock());
        assertEquals(cell, PlaceChromePolicy.extraKeysShown(l), a.extraKeysShown());
        assertEquals(cell, PlaceChromePolicy.extraKeysEdge(l), a.extraKeysEdge());
        assertEquals(cell, PlaceChromePolicy.extraKeysRowShown(l), a.extraKeysRowShown());
        assertEquals(cell, PlaceChromePolicy.extraKeysColumnShown(l), a.extraKeysColumnShown());
        assertEquals(cell, PlaceChromePolicy.dockShown(l), a.dockShown());
    }
}
