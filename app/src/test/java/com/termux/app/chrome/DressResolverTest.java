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
import com.termux.app.place.WallShape;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;
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

    private static final WallShape WORKSPACE = WallShape.of(LayoutStyle.DOCKED, 12, 8);

    /** The Home page in front and the Terminal page, as the Source names them. */
    private static final int HOME_ID = 2;
    private static final int TERMINAL_ID = 1;
    /** The Home page in front's own layout: what the old Home layout switch stood for. */
    private static final LayoutVariant HOME_PAGE = LayoutVariant.HOME;

    /** What the store holds, as a test sets it. */
    private static final class Stored implements DressResolver.Source {
        boolean minimal;
        boolean homeLayout;
        final java.util.Map<LayoutVariant, DockStyle> designs = new java.util.HashMap<>();
        final java.util.Map<LayoutVariant, Boolean> searchFields = new java.util.HashMap<>();
        final java.util.Map<LayoutVariant, Boolean> drawerTiles = new java.util.HashMap<>();
        WallShape workspace = WORKSPACE;
        /** Kind visibility as a test sets it; what is absent is Home without the extra keys. */
        final java.util.Map<String, Boolean> kindShown = new java.util.HashMap<>();

        Stored() {
            for (LayoutVariant layout : LayoutVariant.values()) {
                designs.put(layout, DockStyle.GLASS_TRAY);
                // Home's own toggles stay unset until a test sets them: a Home dock nobody
                // edited reads the shared layout's, as the store does.
                if (layout == LayoutVariant.HOME) continue;
                searchFields.put(layout, false);
                drawerTiles.put(layout, false);
            }
        }

        @Override public boolean minimal() { return minimal; }
        /** Whether the Terminal page holds a page layout of its own. */
        boolean terminalLayout;
        int homeInFront = HOME_ID;
        final java.util.Set<Integer> otherPageLayouts = new java.util.HashSet<>();

        @Override public int pageIdOf(@NonNull PaneWallPage page) {
            return page == PaneWallPage.WIDGETS ? homeInFront : TERMINAL_ID;
        }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant layout) {
            return designs.getOrDefault(layout, DockStyle.GLASS_TRAY);
        }
        @Override public boolean searchField(@NonNull LayoutVariant layout) {
            return searchFields.getOrDefault(layout, false);
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant layout) {
            return drawerTiles.getOrDefault(layout, false);
        }
        @NonNull @Override public WallShape shape() {
            return workspace;
        }
        @Override public boolean shows(@NonNull PageKind kind, @NonNull Element element) {
            Boolean set = kindShown.get(kind + "." + element);
            if (set != null) return set;
            return kind != PageKind.HOME || element != Element.EXTRA_KEYS;
        }
        /** A page layout's own visibility as a test sets it; absent follows the kind. */
        final java.util.Map<String, Boolean> layoutShown = new java.util.HashMap<>();
        @Override public boolean layoutShows(@NonNull LayoutVariant layout, @NonNull PageKind kind,
                                             @NonNull Element element) {
            Boolean set = layout.isPage() ? layoutShown.get(layout + "." + element) : null;
            return set != null ? set : shows(kind, element);
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


    /** A Display page is of the Display kind, so the Display kind's visibility is read (C2). */
    @Test
    public void aDisplayPageIsOfTheDisplayKind() {
        assertEquals(com.termux.app.wall.WallPages.PageKind.DISPLAY,
            DressResolver.kindOf(PaneWallPage.DISPLAY));
        assertEquals(com.termux.app.wall.WallPages.PageKind.TERMINAL,
            DressResolver.kindOf(PaneWallPage.TERMINAL));
        assertEquals(com.termux.app.wall.WallPages.PageKind.HOME,
            DressResolver.kindOf(PaneWallPage.WIDGETS));
    }

    /** The bar on Home (#90): Home wears the status bar, and still not the extra keys. */
    @Test
    public void homeWearsTheStatusBarStillNotTheExtraKeys() {
        assertTrue(home().wears(Element.STATUS));
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(dresses.wears(PaneWallPage.WIDGETS, Element.STATUS));
        assertFalse(dresses.wears(PaneWallPage.WIDGETS, Element.EXTRA_KEYS));
    }

    /** With the Home layout on too: the bar is an element of Home's own layout, offered there. */
    @Test
    public void homeWearsTheStatusBarOnItsOwnLayoutToo() {
        stored.homeLayout = true;
        assertEquals(HOME_PAGE, home().layout());
        assertTrue(home().wears(Element.STATUS));
        assertFalse(home().wears(Element.EXTRA_KEYS));
        assertTrue(DressResolver.wornOn(HOME_PAGE, Element.STATUS));
        assertTrue(HOME_PAGE.offers(Element.STATUS));
        assertFalse(HOME_PAGE.offers(Element.EXTRA_KEYS));
    }








    // ------------------------------------------------------------------ the Home layout on

    @Test
    public void homeLayoutOnHomeWearsItsOwnLayoutInTheWallsShape() {
        stored.homeLayout = true;
        assertEquals(HOME_PAGE, home().layout());
        assertEquals(WORKSPACE, home().shape());
        assertEquals(WORKSPACE, terminal().shape());
        assertEquals(LayoutVariant.NORMAL, terminal().layout());
    }

    @Test
    public void aHomeLayoutAndMinimalModeDoNotMeet() {
        stored.homeLayout = true;
        stored.minimal = true;
        assertEquals(HOME_PAGE, home().layout());
    }

    @Test
    public void aHomeLayoutStaysAsSetInMinimalMode() {
        stored.homeLayout = true;
        stored.minimal = true;
        assertEquals(HOME_PAGE, home().layout());
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
    }

    @Test
    public void aHomeLayoutDrawsItsOwnDockDesign() {
        stored.homeLayout = true;
        stored.designs.put(HOME_PAGE, DockStyle.LENS_BEADS);
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.WALL_STRIP);
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
        assertEquals("a window keeps the Workspace's", DockStyle.WALL_STRIP,
            terminal().dockDesign());
    }

    @Test
    public void aHomeLayoutNeverDressesLikeAWindowButInTheSameShape() {
        stored.homeLayout = true;
        assertEquals(home().shape(), terminal().shape());
        assertNotEquals(home(), terminal());
    }

    /** The shape is wall-wide: a crossing never changes it, whatever layout Home wears. */
    @Test
    public void aCrossingNeverChangesTheShapeWorn() {
        assertEquals(terminal().shape(), home().shape());
        stored.homeLayout = true;
        assertEquals(terminal().shape(), home().shape());
        assertEquals("window to window", terminal().shape(),
            dresses.dressOf(PaneWallPage.DISPLAY).shape());
    }

    // ------------------------------------------------------------------ both ways


    @Test
    public void aDesignNotBuiltYetDressesHomeAsTheTray() {
        for (boolean homeLayout : new boolean[] {false, true}) {
            stored.homeLayout = homeLayout;
            for (DockStyle style : DockStyle.values()) {
                for (LayoutVariant layout : LayoutVariant.values()) stored.designs.put(layout, style);
                stored.designs.put(HOME_PAGE, style);
                assertEquals(DockStyle.effectiveStyle(style), home().dockDesign());
            }
        }
    }


    @Test
    public void theSearchFieldGoesWithEveryDesign() {
        for (DockStyle style : DockStyle.CHOICES) {
            stored.designs.put(LayoutVariant.NORMAL, style);
            for (boolean on : new boolean[] {false, true}) {
                stored.searchFields.put(LayoutVariant.NORMAL, on);
                stored.searchFields.put(LayoutVariant.HOME, on);
                assertEquals(style.name(), style, terminal().dockDesign());
                assertEquals(style.name(), on, terminal().searchField());
                assertEquals(style.name(), on, home().searchField());
            }
        }
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
        stored.designs.put(HOME_PAGE, DockStyle.WALL_STRIP);
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
                dresses.wears(PaneWallPage.WIDGETS, element));
            assertTrue(dresses.wears(PaneWallPage.TERMINAL, element));
            assertEquals(element.name(), home().wears(element),
                DressResolver.wornOn(HOME_PAGE, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.NORMAL, element));
            assertTrue(DressResolver.wornOn(LayoutVariant.MINIMAL, element));
            for (LayoutVariant layout : LayoutVariant.values())
                assertEquals(DressResolver.wornOn(layout, element), layout.offers(element));
        }
    }


    // ------------------------------------------------------------------ per-kind visibility

    @Test
    public void aPageWearsWhatItsKindShows() {
        stored.kindShown.put("TERMINAL.APPS", false);
        stored.kindShown.put("TERMINAL.AZ", false);
        stored.kindShown.put("HOME.EXTRA_KEYS", true);
        stored.kindShown.put("HOME.APPS", false);
        Dress terminal = dresses.dressOf(PaneWallPage.TERMINAL);
        assertFalse(terminal.wears(Element.APPS));
        assertFalse(terminal.wears(Element.AZ));
        assertTrue(terminal.wears(Element.EXTRA_KEYS));
        assertTrue(terminal.wears(Element.STATUS));
        assertTrue(home().wears(Element.EXTRA_KEYS));
        assertFalse(home().wears(Element.APPS));
        assertTrue(home().wears(Element.AZ));
        assertFalse(dresses.wears(PaneWallPage.WIDGETS, Element.APPS));
        assertTrue(dresses.wears(PaneWallPage.TERMINAL, Element.EXTRA_KEYS));
    }

    @Test
    public void minimalModeIsStillTheWindowsAloneAndKindsDoNotChangeIt() {
        stored.minimal = true;
        stored.kindShown.put("TERMINAL.APPS", false);
        assertEquals(LayoutVariant.MINIMAL, dresses.layoutOf(PaneWallPage.TERMINAL));
        assertEquals(LayoutVariant.HOME, dresses.layoutOf(PaneWallPage.WIDGETS));
        assertTrue(home().wears(Element.APPS));
    }

    @Test
    public void aResolverOverNoStoreWearsTheKindDefaults() {
        DressResolver empty = DressResolver.over(() -> null);
        assertTrue(empty.dressOf(PaneWallPage.WIDGETS).wears(Element.APPS));
        assertTrue(empty.dressOf(PaneWallPage.WIDGETS).wears(Element.AZ));
        assertFalse(empty.dressOf(PaneWallPage.WIDGETS).wears(Element.EXTRA_KEYS));
        assertTrue(empty.dressOf(PaneWallPage.TERMINAL).wears(Element.EXTRA_KEYS));
        assertFalse(empty.dressOf(PaneWallPage.TERMINAL).wears(Element.APPS));
        assertTrue(empty.dressOf(PaneWallPage.TERMINAL).wears(Element.STATUS));
    }

    // ------------------------------------------------------------------ page layouts (ADR 0015 §3)

    @Test
    public void aHomePageWithoutAPageLayoutWearsTheNormalLayout() {
        assertSame(LayoutVariant.HOME, dresses.layoutOf(PaneWallPage.WIDGETS));
        stored.homeLayout = true;
        stored.homeInFront = 3;
        assertSame("page 2's layout is not page 3's", LayoutVariant.HOME,
            dresses.layoutOf(PaneWallPage.WIDGETS));
    }


    @Test
    public void theTerminalPageWearsItsPageLayoutUnlessMinimal() {
        assertSame(LayoutVariant.NORMAL, dresses.layoutOf(PaneWallPage.TERMINAL));
        stored.terminalLayout = true;
        LayoutVariant own = dresses.layoutOf(PaneWallPage.TERMINAL);
        assertSame(LayoutVariant.NORMAL, own);
        assertFalse(own.isHomeKind());
        assertTrue(own.offers(Element.EXTRA_KEYS));
        stored.minimal = true;
        assertSame(LayoutVariant.MINIMAL, dresses.layoutOf(PaneWallPage.TERMINAL));
    }


    @Test
    public void aPageLayoutsDesignIsWhatThePageDraws() {
        stored.terminalLayout = true;
        stored.designs.put(LayoutVariant.NORMAL, DockStyle.LENS_BEADS);
        assertEquals(DockStyle.effectiveStyle(DockStyle.LENS_BEADS),
            dresses.dockDesignOf(PaneWallPage.TERMINAL));
        assertEquals(DockStyle.effectiveStyle(DockStyle.GLASS_TRAY),
            dresses.dockDesignOf(PaneWallPage.WIDGETS));
    }

    // ------------------------------------------------- one layout per page kind (ADR 0020 §1)

    private Dress display() {
        return dresses.dressOf(PaneWallPage.DISPLAY);
    }

    @Test
    public void eachPageWearsItsKindsLayout() {
        assertEquals(LayoutVariant.HOME, home().layout());
        assertEquals("the Terminal's layout", LayoutVariant.NORMAL, terminal().layout());
        assertEquals(LayoutVariant.DISPLAY, display().layout());
        assertFalse("Home leaves the extra keys out", home().wears(Element.EXTRA_KEYS));
        assertTrue(home().wears(Element.APPS));
        assertEquals(WORKSPACE, home().shape());
    }

    @Test
    public void minimalDressesBothWindowsMinimalAndLeavesHomeOnItsOwn() {
        stored.minimal = true;
        assertEquals(LayoutVariant.MINIMAL, terminal().layout());
        assertEquals(LayoutVariant.MINIMAL, display().layout());
        assertEquals(LayoutVariant.HOME, home().layout());
    }

    @Test
    public void everyHomePageWearsHomesLayoutWhateverItsId() {
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        Dress inFront = home();
        Dress beside = dresses.dressOf(com.termux.app.wall.PageRef.of(PaneWallPage.WIDGETS, 9));
        assertEquals(LayoutVariant.HOME, beside.layout());
        assertEquals("a swipe between Home pages moves nothing", inFront, beside);
        stored.minimal = true;
        assertEquals(inFront, home());
    }

    @Test
    public void eachLayoutDrawsItsOwnDockDesign() {
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        assertEquals(DockStyle.LENS_BEADS, home().dockDesign());
        assertEquals(DockStyle.LENS_BEADS, dresses.dockDesignOf(PaneWallPage.WIDGETS));
        assertEquals("the Terminal keeps its own", DockStyle.GLASS_TRAY, terminal().dockDesign());
        assertEquals(DockStyle.GLASS_TRAY, display().dockDesign());

        stored.designs.put(LayoutVariant.DISPLAY, DockStyle.LENS_BEADS);
        assertEquals(DockStyle.LENS_BEADS, display().dockDesign());
        assertEquals("the Display's edit leaves the Terminal", DockStyle.GLASS_TRAY,
            terminal().dockDesign());

        stored.minimal = true;
        stored.designs.put(LayoutVariant.MINIMAL, DockStyle.LENS_BEADS);
        assertEquals("minimal is the windows' alone", DockStyle.LENS_BEADS, terminal().dockDesign());
        stored.designs.put(LayoutVariant.HOME, DockStyle.GLASS_TRAY);
        assertEquals(DockStyle.GLASS_TRAY, home().dockDesign());
    }

    @Test
    public void theSearchFieldAndDrawerTileAreEachLayoutsOwn() {
        stored.searchFields.put(LayoutVariant.HOME, true);
        stored.drawerTiles.put(LayoutVariant.NORMAL, true);
        assertTrue(home().searchField());
        assertTrue(dresses.searchFieldOf(PaneWallPage.WIDGETS));
        assertFalse(terminal().searchField());
        assertFalse(display().searchField());
        assertTrue(terminal().drawerTile());
        assertFalse(home().drawerTile());
        assertFalse(display().drawerTile());
        assertNotEquals("a field on one layout only sets them apart", home(), terminal());
    }

    @Test
    public void aDressFollowsTheStoreAsItChanges() {
        Dress before = home();
        stored.designs.put(LayoutVariant.HOME, DockStyle.LENS_BEADS);
        assertNotEquals(before, home());
        stored.kindShown.put(PageKind.HOME + "." + Element.EXTRA_KEYS, true);
        assertTrue(home().wears(Element.EXTRA_KEYS));
    }

    @Test
    public void aResolverOverNoStoreYetDressesEveryKindByItsLayout() {
        DressResolver empty = new DressResolver(new Stored());
        assertSame(LayoutVariant.HOME, empty.layoutOf(PaneWallPage.WIDGETS));
        assertSame(LayoutVariant.NORMAL, empty.layoutOf(PaneWallPage.TERMINAL));
        assertSame(LayoutVariant.DISPLAY, empty.layoutOf(PaneWallPage.DISPLAY));
    }

    // ---- the Assistant page (ADR 0022) ---------------------------------------------------------

    private void assistantShowsAsShipped() {
        for (Element element : Element.values())
            stored.kindShown.put(PageKind.ASSISTANT + "." + element,
                com.termux.app.place.PlaceLayoutStore.kindShowsByDefault(PageKind.ASSISTANT,
                    element));
    }

    @Test
    public void theAssistantPageIsOfTheAssistantKind() {
        assertEquals(PageKind.ASSISTANT, DressResolver.kindOf(PaneWallPage.ASSISTANT));
    }

    @Test
    public void theAssistantPageWearsTheStatusBarAndNoDockExtraKeysOrWindowBar() {
        assistantShowsAsShipped();
        Dress assistant = dresses.dressOf(PaneWallPage.ASSISTANT);
        assertTrue(assistant.wears(Element.STATUS));
        for (Element element : Element.values()) {
            if (element == Element.STATUS) continue;
            assertFalse(element + " is off by default", assistant.wears(element));
        }
    }

    @Test
    public void minimalModeDoesNotApplyToTheAssistantPage() {
        assistantShowsAsShipped();
        stored.minimal = true;
        assertEquals(LayoutVariant.ASSISTANT, dresses.layoutOf(PaneWallPage.ASSISTANT));
        assertTrue(dresses.wears(PaneWallPage.ASSISTANT, Element.STATUS));
    }
}
