package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.WallHalfShape;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;

import java.util.EnumMap;

/**
 * What a page wears, as the Dress resolver answers it (ADR 0009, build step 1). One layout dresses
 * both halves: with the Home layout off, a Home page wears the Workspace's normal layout (minimal
 * mode is the windows' alone) less the extra keys, in the Workspace's Style,
 * Corners and Margin, its dock in the Dock design that layout holds. With the Home layout on, a
 * Home page wears its own copy and its own shape, as ADR 0008 had it. A window wears the
 * Workspace's layout whole, its dock in the Dock design that layout holds (#93), the minimal
 * layout's own while minimal mode is on.
 */
public class DressResolverTest {

    private static final WallHalfShape WORKSPACE = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);
    private static final WallHalfShape HOME = WallHalfShape.of(LayoutStyle.FLOATING, 24, 16);

    /** What the store holds, as a test sets it. */
    private static final class Stored implements DressResolver.Source {
        boolean minimal;
        boolean homeLayout;
        final EnumMap<LayoutVariant, DockStyle> designs = new EnumMap<>(LayoutVariant.class);
        final EnumMap<LayoutVariant, Boolean> searchFields = new EnumMap<>(LayoutVariant.class);
        final EnumMap<LayoutVariant, Boolean> drawerTiles = new EnumMap<>(LayoutVariant.class);
        WallHalfShape workspace = WORKSPACE;
        WallHalfShape home = HOME;

        Stored() {
            for (LayoutVariant layout : LayoutVariant.values()) {
                designs.put(layout, DockStyle.GLASS_TRAY);
                searchFields.put(layout, false);
                drawerTiles.put(layout, false);
            }
        }

        @Override public boolean minimal() { return minimal; }
        @Override public boolean homeLayout() { return homeLayout; }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant layout) {
            return designs.get(layout);
        }
        @Override public boolean searchField(@NonNull LayoutVariant layout) {
            return searchFields.get(layout);
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant layout) {
            return drawerTiles.get(layout);
        }
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

    // ------------------------------------------------------------------ one layout (Home layout off)

    @Test
    public void homeLayoutOffHomeWearsTheNormalLayoutLessTheExtraKeysInTheWorkspacesShape() {
        assertEquals(LayoutVariant.NORMAL, home().layout());
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(home().wears(Element.APPS));
        assertTrue(home().wears(Element.AZ));
        assertEquals(WORKSPACE, home().shape());
    }

    /** The bar on Home (#90): Home wears the status bar, and still not the extra keys. */
    @Test
    public void homeWearsTheStatusBarStillNotTheExtraKeys() {
        assertTrue(home().wears(Element.STATUS));
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(DressResolver.wornBy(PaneWallPage.WIDGETS, Element.STATUS));
        assertFalse(DressResolver.wornBy(PaneWallPage.WIDGETS, Element.EXTRA_KEYS));
    }

    /** With the Home layout on too: the bar is an element of Home's own layout, offered there. */
    @Test
    public void homeWearsTheStatusBarOnItsOwnLayoutToo() {
        stored.homeLayout = true;
        assertEquals(LayoutVariant.HOME, home().layout());
        assertTrue(home().wears(Element.STATUS));
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(DressResolver.wornOn(LayoutVariant.HOME, Element.STATUS));
        assertTrue(LayoutVariant.HOME.offers(Element.STATUS));
        assertFalse(LayoutVariant.HOME.offers(Element.EXTRA_KEYS));
    }

    /**
     * The bar stands still on the swipe: with the Home layout off, Home and a window differ only
     * by the extra keys, so the bar's layout, shape and the bar itself are the same on both.
     */
    @Test
    public void homeAndAWindowWearTheSameBarOnOneLayout() {
        Dress home = home();
        Dress window = terminal();
        assertEquals(window.layout(), home.layout());
        assertEquals(window.shape(), home.shape());
        assertEquals(window.wears(Element.STATUS), home.wears(Element.STATUS));
        for (Element element : Element.values()) {
            if (element != Element.EXTRA_KEYS)
                assertEquals(element.name(), window.wears(element), home.wears(element));
        }
    }

    @Test
    public void minimalOnDressesTheWindowsMinimalAndLeavesHomeNormal() {
        stored.minimal = true;
        assertEquals(LayoutVariant.NORMAL, home().layout());
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
        assertEquals(LayoutVariant.MINIMAL, dresses.dressOf(PaneWallPage.DISPLAY).layout());
        assertEquals(WORKSPACE, home().shape());
    }

    @Test
    public void homeOnTheSharedLayoutDrawsThatLayoutsDockDesign() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        stored.designs.put(LayoutVariant.MINIMAL, DockStyle.WALL_STRIP);
        stored.designs.put(LayoutVariant.HOME, DockStyle.GLASS_TRAY);
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
        stored.minimal = true;
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
    }

    /** #93: the Workspace's dock no longer keeps the tray; it wears what its layout holds. */
    @Test
    public void aWindowWearsItsLayoutsDesign() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        assertEquals(DockStyle.LENS_BEADS, terminal().dockDesign());
        assertEquals(DockStyle.LENS_BEADS, dresses.dressOf(PaneWallPage.DISPLAY).dockDesign());
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.WALL_STRIP);
        assertEquals(DockStyle.WALL_STRIP, terminal().dockDesign());
    }

    @Test
    public void theMinimalLayoutCarriesItsOwnDesign() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        stored.designs.put(LayoutVariant.MINIMAL, DockStyle.WALL_STRIP);
        stored.minimal = true;
        assertEquals(DockStyle.WALL_STRIP, terminal().dockDesign());
        assertEquals(DockStyle.WALL_STRIP, dresses.dockDesignOf(PaneWallPage.TERMINAL));
        assertEquals("minimal is the windows' alone", DockStyle.LENS_BEADS, home().dockDesign());
    }

    /**
     * One layout, minimal off: Home and a window draw one design, so the dock's slab is the same
     * spec on both and a swipe across the seam does not dress the dock again.
     */
    @Test
    public void homeAndAWindowOnOneLayoutDrawOneDesign() {
        for (DockStyle style : DockStyle.values()) {
            stored.designs.put(LayoutVariant.NORMAL, style);
            stored.designs.put(LayoutVariant.HOME, DockStyle.GLASS_TRAY);
            assertEquals(style.name(), dresses.dockDesignOf(PaneWallPage.WIDGETS),
                dresses.dockDesignOf(PaneWallPage.TERMINAL));
            assertSame(style.name(),
                DockSurfaceSpec.forStyle(dresses.dockDesignOf(PaneWallPage.WIDGETS)),
                DockSurfaceSpec.forStyle(dresses.dockDesignOf(PaneWallPage.TERMINAL)));
        }
    }

    @Test
    public void homeAndAWindowOnOneLayoutShareTheLayoutAndTheShape() {
        stored.homeLayout = false;
        assertEquals(terminal().layout(), home().layout());
        assertEquals("the stack is not re-dressed on the swipe",
            terminal().shape(), home().shape());
        stored.minimal = true;
        assertEquals("minimal is the windows' alone", LayoutVariant.NORMAL, home().layout());
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
        assertEquals(terminal().shape(), home().shape());
    }

    // ------------------------------------------------------------------ the Home layout on

    @Test
    public void homeLayoutOnHomeWearsItsOwnLayoutAndShape() {
        stored.homeLayout = true;
        assertEquals(LayoutVariant.HOME, home().layout());
        assertEquals(HOME, home().shape());
        assertEquals(WORKSPACE, terminal().shape());
        assertEquals(LayoutVariant.NORMAL, terminal().layout());
    }

    @Test
    public void aHomeLayoutAndMinimalModeDoNotMeet() {
        stored.homeLayout = true;
        stored.minimal = true;
        assertEquals(LayoutVariant.HOME, home().layout());
    }

    @Test
    public void aHomeLayoutStaysAsSetInMinimalMode() {
        stored.homeLayout = true;
        stored.minimal = true;
        assertEquals(LayoutVariant.HOME, home().layout());
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
    }

    @Test
    public void aHomeLayoutDrawsItsOwnDockDesign() {
        stored.homeLayout = true;
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.WALL_STRIP);
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
        assertEquals("a window keeps the Workspace's", DockStyle.WALL_STRIP,
            terminal().dockDesign());
    }

    @Test
    public void aHomeLayoutInAnotherShapeNeverDressesLikeAWindow() {
        stored.homeLayout = true;
        assertNotEquals(home().shape(), terminal().shape());
        assertFalse(dresses.dressAlike(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL));
    }

    /** Moved from the Home chrome policy's test: a settle redresses only between halves apart. */
    @Test
    public void aCrossingChangesTheShapeWornOnlyWhileAHomeLayoutSetsItApart() {
        assertEquals("one layout: Home's own values are not read",
            terminal().shape(), home().shape());
        stored.homeLayout = true;
        assertNotEquals(home().shape(), terminal().shape());
        assertEquals("window to window", terminal().shape(),
            dresses.dressOf(PaneWallPage.DISPLAY).shape());
        stored.home = WallHalfShape.of(LayoutStyle.DOCKED, 12, 8);
        assertEquals("a Home that still follows the Workspace changes nothing",
            terminal().shape(), home().shape());
        stored.home = WallHalfShape.of(LayoutStyle.DOCKED, 12, 9);
        assertNotEquals("one value apart is enough", terminal().shape(), home().shape());
    }

    // ------------------------------------------------------------------ both ways

    @Test
    public void aWindowWearsTheWorkspaceLayoutWhole() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (PaneWallPage window
                    : new PaneWallPage[] {PaneWallPage.TERMINAL, PaneWallPage.DISPLAY}) {
                Dress dress = dresses.dressOf(window);
                assertEquals(LayoutVariant.NORMAL, dress.layout());
                assertEquals(WORKSPACE, dress.shape());
                for (Element element : Element.values())
                    assertTrue(window + " wears " + element, dress.wears(element));
            }
        }
    }

    @Test
    public void aDesignNotBuiltYetDressesHomeAsTheTray() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (DockStyle style : DockStyle.values()) {
                for (LayoutVariant layout : LayoutVariant.values()) stored.designs.put(layout, style);
                assertEquals(DockStyle.effectiveStyle(style), home().dockDesign());
            }
        }
    }

    /** The Layout editor offers the picker wherever the dock is selected, a window's page too. */
    @Test
    public void thePickerIsOfferedOnAWindow() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (boolean minimal : new boolean[] {false, true}) {
                stored.minimal = minimal;
                for (PaneWallPage page : PaneWallPage.values()) {
                    assertTrue(page.name(), dresses.dressOf(page).picksDockDesign());
                    assertTrue(page.name(), DressResolver.picksDockDesign(page));
                }
            }
        }
    }

    /** The Search field is a toggle of its own, with any design (#96). */
    @Test
    public void searchFieldOnWithBeadsTheDressHasBoth() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        stored.searchFields.put(LayoutVariant.NORMAL, true);
        for (Dress dress : new Dress[] {home(), terminal()}) {
            assertEquals(DockStyle.LENS_BEADS, dress.dockDesign());
            assertTrue(dress.searchField());
        }
        assertTrue(dresses.searchFieldOf(PaneWallPage.WIDGETS));
        assertTrue(dresses.searchFieldOf(PaneWallPage.DISPLAY));
    }

    @Test
    public void theSearchFieldGoesWithEveryDesign() {
        for (DockStyle style : DockStyle.CHOICES) {
            stored.designs.put(LayoutVariant.NORMAL, style);
            for (boolean on : new boolean[] {false, true}) {
                stored.searchFields.put(LayoutVariant.NORMAL, on);
                assertEquals(style.name(), style, terminal().dockDesign());
                assertEquals(style.name(), on, terminal().searchField());
                assertEquals(style.name(), on, home().searchField());
            }
        }
    }

    @Test
    public void theSearchFieldIsEachLayoutsOwn() {
        stored.searchFields.put(LayoutVariant.NORMAL, true);
        stored.minimal = true;
        assertFalse("the minimal layout holds its own", terminal().searchField());
        assertTrue("Home wears the normal layout", home().searchField());
        stored.minimal = false;
        stored.homeLayout = true;
        assertFalse("a Home layout holds its own", home().searchField());
        assertFalse(dresses.searchFieldOf(PaneWallPage.WIDGETS));
        assertTrue(terminal().searchField());
    }

    @Test
    public void theSearchFieldAloneChangesTheDress() {
        Dress without = terminal();
        stored.searchFields.put(LayoutVariant.NORMAL, true);
        assertNotEquals(without, terminal());
        stored.homeLayout = true;
        stored.home = WORKSPACE;
        stored.designs.put(LayoutVariant.HOME, DockStyle.GLASS_TRAY);
        assertNotEquals("a field on one half only sets them apart",
            dresses.dressOf(PaneWallPage.WIDGETS).searchField(), terminal().searchField());
    }

    /**
     * A stray Prompt line or Taskbar (an id the migration has not reached) draws the tray, and
     * neither brings its old toggle: the field and the tile are the stored toggles' (#96, #97).
     */
    @Test
    public void aStrayPromptLineOrTaskbarDrawsTheTrayWithNoToggleOfItsOwn() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.PROMPT_LINE);
        assertEquals(DockStyle.GLASS_TRAY, home().dockDesign());
        assertFalse("the field is the stored toggle's", home().searchField());
        assertFalse(home().drawerTile());

        stored.designs.put(LayoutVariant.NORMAL, DockStyle.TASKBAR);
        assertEquals(DockStyle.GLASS_TRAY, home().dockDesign());
        assertEquals(DockStyle.GLASS_TRAY, terminal().dockDesign());
        assertFalse("the tile is the stored toggle's", home().drawerTile());
        assertFalse(home().searchField());
    }

    // ------------------------------------------------------------------ the Drawer tile (#97)

    /** The Drawer tile is the layout's toggle: both halves wear it on one layout. */
    @Test
    public void theDrawerTileIsTheLayoutsToggleOnBothHalves() {
        stored.drawerTiles.put(LayoutVariant.NORMAL, true);
        for (Dress dress : new Dress[] {home(), terminal()}) {
            assertTrue(dress.drawerTile());
            assertEquals(DockStyle.GLASS_TRAY, dress.dockDesign());
        }
        assertTrue(dresses.drawerTileOf(PaneWallPage.WIDGETS));
        assertTrue(dresses.drawerTileOf(PaneWallPage.TERMINAL));
        assertTrue(dresses.drawerTileOf(PaneWallPage.DISPLAY));
    }

    @Test
    public void theDrawerTileGoesWithEveryDesignAndBesideTheSearchField() {
        for (DockStyle style : DockStyle.CHOICES) {
            stored.designs.put(LayoutVariant.NORMAL, style);
            for (boolean tile : new boolean[] {false, true}) {
                stored.drawerTiles.put(LayoutVariant.NORMAL, tile);
                for (boolean field : new boolean[] {false, true}) {
                    stored.searchFields.put(LayoutVariant.NORMAL, field);
                    for (Dress dress : new Dress[] {home(), terminal()}) {
                        assertEquals(style.name(), style, dress.dockDesign());
                        assertEquals(style.name(), tile, dress.drawerTile());
                        assertEquals(style.name(), field, dress.searchField());
                    }
                }
            }
        }
    }

    @Test
    public void theDrawerTileIsEachLayoutsOwn() {
        stored.drawerTiles.put(LayoutVariant.NORMAL, true);
        stored.minimal = true;
        assertFalse("the minimal layout holds its own", terminal().drawerTile());
        assertTrue("Home wears the normal layout", home().drawerTile());
        stored.minimal = false;
        stored.homeLayout = true;
        assertFalse("a Home layout holds its own", home().drawerTile());
        assertFalse(dresses.drawerTileOf(PaneWallPage.WIDGETS));
        assertTrue(terminal().drawerTile());
        stored.drawerTiles.put(LayoutVariant.HOME, true);
        assertTrue(dresses.drawerTileOf(PaneWallPage.WIDGETS));
    }

    @Test
    public void theDrawerTileAloneChangesTheDress() {
        Dress without = terminal();
        stored.drawerTiles.put(LayoutVariant.NORMAL, true);
        assertNotEquals(without, terminal());
        assertNotEquals(without.hashCode(), terminal().hashCode());
        stored.drawerTiles.put(LayoutVariant.NORMAL, false);
        assertEquals(without, terminal());
    }

    @Test
    public void twoWindowsDressAlike() {
        assertTrue(dresses.dressAlike(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        stored.minimal = true;
        assertTrue(dresses.dressAlike(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY));
        assertTrue(dresses.dressAlike(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS));
    }

    @Test
    public void aDressFollowsTheStoreAsItChanges() {
        Dress before = home();
        Dress window = terminal();
        stored.minimal = true;
        assertEquals(before, home());
        assertNotEquals(window, terminal());
        stored.minimal = false;
        assertEquals(window, terminal());
        stored.homeLayout = true;
        assertNotEquals(before, home());
    }

    @Test
    public void theLayoutIsReadAloneAsTheDressWearsIt() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (boolean minimal : new boolean[] {false, true}) {
                stored.minimal = minimal;
                for (PaneWallPage page : PaneWallPage.values())
                    assertEquals(page + (minimal ? " minimal" : "") + (homeLayout ? " own" : ""),
                        dresses.dressOf(page).layout(), dresses.layoutOf(page));
            }
        }
    }

    @Test
    public void theDockDesignAloneMatchesTheWholeDress() {
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        stored.designs.put(LayoutVariant.HOME, DockStyle.WALL_STRIP);
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (PaneWallPage page : PaneWallPage.values())
                assertEquals(page.name(), dresses.dressOf(page).dockDesign(),
                    dresses.dockDesignOf(page));
        }
    }

    /** One rule for what Home leaves out, asked by page or by the Home layout. */
    @Test
    public void aLayoutAndAPageWearWhatTheDressWears() {
        for (Element element : Element.values()) {
            assertEquals(element.name(), home().wears(element),
                DressResolver.wornBy(PaneWallPage.WIDGETS, element));
            assertTrue(DressResolver.wornBy(PaneWallPage.TERMINAL, element));
            assertEquals(element.name(), home().wears(element),
                DressResolver.wornOn(LayoutVariant.HOME, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.NORMAL, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.MINIMAL, element));
            for (LayoutVariant layout : LayoutVariant.values())
                assertEquals(DressResolver.wornOn(layout, element), layout.offers(element));
        }
    }

    @Test
    public void thePickedDesignIsDrawnOnEveryPage() {
        for (PaneWallPage page : PaneWallPage.values()) {
            assertEquals(page.name(), DockStyle.LENS_BEADS,
                DressResolver.dockDesignOn(page, DockStyle.LENS_BEADS));
            assertEquals(page.name(), DockStyle.GLASS_TRAY, DressResolver.dockDesignOn(page, null));
        }
        // A layout named without its page draws what it holds, whichever half wears it.
        for (LayoutVariant layout : LayoutVariant.values())
            assertEquals(layout.name(), DockStyle.WALL_STRIP,
                DressResolver.dockDesignOn(layout, DockStyle.WALL_STRIP));
    }
}
