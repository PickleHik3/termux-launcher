package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.WallHalfShape;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

/**
 * What a page wears, as the Dress resolver answers it today (ADR 0008, before ADR 0009's shared
 * layout lands): a Home page wears the Home layout without the status bar or the extra keys, in
 * Home's own Style, Corners, Margin and Dock design; a window wears the Workspace's layout whole.
 */
public class DressResolverTest {

    private static final WallHalfShape WORKSPACE = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);
    private static final WallHalfShape HOME = WallHalfShape.of(LayoutStyle.FLOATING, 24, 16);

    /** What the store holds, as a test sets it. */
    private static final class Stored implements DressResolver.Source {
        boolean minimal;
        DockStyle homeDockStyle = DockStyle.GLASS_TRAY;
        WallHalfShape workspace = WORKSPACE;
        WallHalfShape home = HOME;

        @Override public boolean minimal() { return minimal; }
        @NonNull @Override public DockStyle homeDockStyle() { return homeDockStyle; }
        @NonNull @Override public WallHalfShape shape(boolean home) {
            return home ? this.home : workspace;
        }
    }

    private final Stored stored = new Stored();
    private final DressResolver dresses = new DressResolver(stored);

    private Dress home() {
        return dresses.dressOf(PaneWallPage.WIDGETS);
    }

    private Dress terminal() {
        return dresses.dressOf(PaneWallPage.TERMINAL);
    }

    @Test
    public void aHomePageWearsTheHomeLayout() {
        assertEquals(LayoutVariant.HOME, home().layout());
    }

    /** Moved from the Layout editor's Home filter test: the status bar is not Home's to place. */
    @Test
    public void aHomePageLeavesOutTheStatusBarAndTheExtraKeys() {
        assertFalse(home().wears(Element.STATUS));
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(home().wears(Element.APPS));
        assertTrue(home().wears(Element.AZ));
    }

    @Test
    public void aWindowWearsTheWorkspaceLayoutWhole() {
        for (PaneWallPage window : new PaneWallPage[] {PaneWallPage.TERMINAL, PaneWallPage.DISPLAY}) {
            Dress dress = dresses.dressOf(window);
            assertEquals(LayoutVariant.NORMAL, dress.layout());
            for (Element element : Element.values())
                assertTrue(window + " wears " + element, dress.wears(element));
        }
    }

    @Test
    public void minimalModeDressesTheWindowsAndLeavesHomeAlone() {
        stored.minimal = true;
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
        assertEquals(LayoutVariant.MINIMAL, dresses.dressOf(PaneWallPage.DISPLAY).layout());
        assertEquals(LayoutVariant.HOME, home().layout());
        assertFalse(home().wears(Element.STATUS));
    }

    @Test
    public void eachHalfWearsItsOwnStyleCornersAndMargin() {
        assertEquals(HOME, home().shape());
        assertEquals(WORKSPACE, terminal().shape());
        assertEquals(WORKSPACE, dresses.dressOf(PaneWallPage.DISPLAY).shape());
    }

    @Test
    public void homeWearsThePickedDockDesignAndTheWorkspaceTheTray() {
        stored.homeDockStyle = DockStyle.LENS_BEADS;
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
        assertEquals(DockStyle.GLASS_TRAY, terminal().dockDesign());
    }

    @Test
    public void aDesignNotBuiltYetDressesHomeAsTheTray() {
        for (DockStyle style : DockStyle.values()) {
            stored.homeDockStyle = style;
            assertEquals(DockStyle.effectiveStyle(style), home().dockDesign());
        }
    }

    @Test
    public void onlyHomesDockDesignIsPicked() {
        assertTrue(home().picksDockDesign());
        assertFalse(terminal().picksDockDesign());
        stored.minimal = true;
        assertFalse(terminal().picksDockDesign());
    }

    @Test
    public void theSearchFieldAndTheDrawerTileComeWithTheirDesigns() {
        stored.homeDockStyle = DockStyle.PROMPT_LINE;
        assertTrue(home().searchField());
        assertFalse(home().drawerTile());

        stored.homeDockStyle = DockStyle.TASKBAR;
        assertTrue(home().drawerTile());
        assertFalse(home().searchField());

        stored.homeDockStyle = DockStyle.GLASS_TRAY;
        assertFalse(home().searchField());
        assertFalse(home().drawerTile());
        assertFalse(terminal().searchField());
        assertFalse(terminal().drawerTile());
    }

    @Test
    public void twoWindowsDressAlike() {
        assertTrue(dresses.dressAlike(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        stored.minimal = true;
        assertTrue(dresses.dressAlike(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        assertTrue(dresses.dressAlike(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS));
    }

    @Test
    public void homeAndAWindowNeverDressAlikeEvenInTheSameShape() {
        stored.home = WORKSPACE;
        assertFalse(dresses.dressAlike(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL));
        assertNotEquals(home(), terminal());
    }

    @Test
    public void aDressFollowsTheStoreAsItChanges() {
        Dress before = terminal();
        stored.minimal = true;
        assertNotEquals(before, terminal());
        stored.minimal = false;
        assertEquals(before, terminal());
    }

    /** Moved from the Home chrome policy's test: a settle redresses only between halves apart. */
    @Test
    public void aCrossingChangesTheShapeWornOnlyWhileTheHalvesDiffer() {
        assertNotEquals(home().shape(), terminal().shape());
        assertEquals("window to window", terminal().shape(),
            dresses.dressOf(PaneWallPage.DISPLAY).shape());
        stored.home = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);
        assertEquals("a Home that still follows the Workspace changes nothing",
            terminal().shape(), home().shape());
        stored.home = WallHalfShape.of(LayoutStyle.DOCKED, 12, 9);
        assertNotEquals("one value apart is enough", terminal().shape(), home().shape());
    }

    @Test
    public void theLayoutIsReadAloneAsTheDressWearsIt() {
        for (boolean minimal : new boolean[] {false, true}) {
            stored.minimal = minimal;
            for (PaneWallPage page : PaneWallPage.values())
                assertEquals(page + (minimal ? " minimal" : ""), dresses.dressOf(page).layout(),
                    dresses.layoutOf(page));
        }
    }

    /** One rule for what the Home layout leaves out, asked by page or by layout. */
    @Test
    public void aLayoutWearsWhatItsPagesWear() {
        for (Element element : Element.values()) {
            assertEquals(element.name(), home().wears(element),
                DressResolver.wornOn(LayoutVariant.HOME, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.NORMAL, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.MINIMAL, element));
            for (LayoutVariant layout : LayoutVariant.values())
                assertEquals(DressResolver.wornOn(layout, element), layout.offers(element));
        }
    }

    @Test
    public void onlyTheHomeLayoutDrawsThePickedDockDesign() {
        assertTrue(DressResolver.picksDockDesign(LayoutVariant.HOME));
        assertFalse(DressResolver.picksDockDesign(LayoutVariant.NORMAL));
        assertFalse(DressResolver.picksDockDesign(LayoutVariant.MINIMAL));
        assertEquals(DockStyle.LENS_BEADS,
            DressResolver.dockDesignOn(LayoutVariant.HOME, DockStyle.LENS_BEADS));
        assertEquals(DockStyle.GLASS_TRAY, DressResolver.dockDesignOn(LayoutVariant.HOME, null));
        assertEquals(DockStyle.GLASS_TRAY,
            DressResolver.dockDesignOn(LayoutVariant.NORMAL, DockStyle.LENS_BEADS));
    }
}
