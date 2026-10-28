package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Page layouts (ADR 0015 §3, #172 A2b): a page may hold a layout of its own, keyed by its page
 * id, seeded from what it wears; version 16 turns the old Home layout into page layouts on every
 * Home page.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLayoutPageLayoutTest {

    private static final PlaceOrientation PORTRAIT = PlaceOrientation.PORTRAIT;

    private SharedPreferences prefs;
    private TermuxAppSharedPreferences launcher;

    @Before
    public void setUp() {
        Application app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("place-layout-page-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        launcher = new TermuxAppSharedPreferences(app, prefs, null);
    }

    private PlaceLayoutStore store() {
        return KindFixtures.dockOnTheTerminal(new PlaceLayoutStore(launcher));
    }

    /** The pages on screen, as the launcher would tell the store. */
    private static PlaceLayoutStore.ShownPages pages(final int homeInFront, final int terminal,
                                                     final List<Integer> homes) {
        return new PlaceLayoutStore.ShownPages() {
            @Override public int homePageInFront() { return homeInFront; }
            @Override public int terminalPage() { return terminal; }
            @NonNull @Override public List<Integer> homePageIds() { return homes; }
        };
    }

    private static LayoutVariant home(int id) {
        return LayoutVariant.page(id, PageKind.HOME);
    }

    @Test
    public void aPageLayoutIsInternedAndKeptUnderItsPageId() {
        assertSame(home(2), home(2));
        assertEquals("layout.page2.portrait.apps_row",
            PlaceLayoutStore.layoutKey(home(2), PORTRAIT, "apps_row"));
        assertEquals("place.page_layout.2", PlaceLayoutStore.pageLayoutKey(2));
        assertTrue(PlaceLayoutStore.sizesLinked(home(2), LayoutVariant.NORMAL));
        assertFalse(PlaceLayoutStore.sizesLinked(home(2), LayoutVariant.MINIMAL));
    }

    @Test
    public void withNoPageLayoutsEveryPageWearsWhatItWoreBefore() {
        PlaceLayoutStore store = store();
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.WIDGETS));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.TERMINAL));
    }

    @Test
    public void turnedOnAPageLayoutIsSeededFromWhatThePageWore() {
        PlaceLayoutStore store = store();
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        store.setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
        store.setDockStyle(DockStyle.LENS_BEADS);

        store.setPageLayout(2, PageKind.HOME, true);

        assertTrue(store.hasPageLayout(2));
        assertSame(home(2), store.variantFor(PaneWallPage.WIDGETS));
        PlaceLayoutStore own = store.forVariant(home(2));
        assertEquals(Edge.BOTTOM, own.statusBarEdge(PORTRAIT));
        assertEquals(DockStyle.LENS_BEADS, own.dockStyle());
    }

    @Test
    public void anEditOnOnePagesLayoutLeavesTheOthersAndTheSharedOneAlone() {
        PlaceLayoutStore store = store();
        store.setStatusBarEdge(PORTRAIT, Edge.TOP);
        store.setPageLayout(2, PageKind.HOME, true);
        store.setPageLayout(3, PageKind.HOME, true);

        store.forVariant(home(2)).setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
        store.forVariant(home(2)).setDockStyle(DockStyle.LENS_BEADS);

        assertEquals(Edge.BOTTOM, store.forVariant(home(2)).statusBarEdge(PORTRAIT));
        assertEquals(Edge.TOP, store.forVariant(home(3)).statusBarEdge(PORTRAIT));
        assertEquals(Edge.TOP, store.statusBarEdge(PORTRAIT));
        assertEquals(DockStyle.LENS_BEADS, store.forVariant(home(2)).dockStyle());
        assertFalse(store.forVariant(home(3)).dockStyle() == DockStyle.LENS_BEADS);
    }

    @Test
    public void theSizesStayLinkedToTheSharedLayout() {
        PlaceLayoutStore store = store();
        store.setPageLayout(2, PageKind.HOME, true);
        store.forVariant(home(2)).setDockHeightScale(PORTRAIT, 1.25f);
        assertEquals(1.25f, store.dockHeightScale(PORTRAIT), 0f);
        store.setDockHeightScale(PORTRAIT, 0.75f);
        assertEquals(0.75f, store.forVariant(home(2)).dockHeightScale(PORTRAIT), 0f);
        assertFalse("no dead copy of a linked size",
            prefs.contains(PlaceLayoutStore.layoutKey(home(2), PORTRAIT, "dock_height")));
    }

    @Test
    public void droppedAPageLayoutsKeysAndFlagAreGone() {
        PlaceLayoutStore store = store();
        store.setPageLayout(2, PageKind.HOME, true);
        store.forVariant(home(2)).setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
        store.forVariant(home(2)).setDockStyle(DockStyle.LENS_BEADS);
        store.setPageLayout(3, PageKind.HOME, true);

        store.dropPageLayout(2, PageKind.HOME);

        assertFalse(store.hasPageLayout(2));
        for (String key : prefs.getAll().keySet())
            assertFalse(key, key.startsWith("layout.page2.") || key.equals("place.page_layout.2"));
        assertTrue("another page's layout stays", store.hasPageLayout(3));
    }

    // ------------------------------------------------------------------ version 16

    /** An install at version 15 with the Home layout on, Home's arrangement set by {@code home}. */
    private void upgradeFromHomeLayout(boolean differ) {
        PlaceLayoutStore before = store();
        before.setStatusBarEdge(PORTRAIT, Edge.TOP);
        before.setHomeLayout(true);
        if (differ) {
            before.forVariant(LayoutVariant.HOME).setStatusBarEdge(PORTRAIT, Edge.BOTTOM);
            before.forVariant(LayoutVariant.HOME).setDockStyle(DockStyle.LENS_BEADS);
        }
        prefs.edit().putInt(PlaceLayoutStore.KEY_MIGRATED, 15).commit();
    }

    @Test
    public void aDifferingHomeLayoutBecomesAPageLayoutOnEveryHomePage() {
        upgradeFromHomeLayout(true);

        PlaceLayoutStore store = new PlaceLayoutStore(launcher);
        assertEquals(PlaceLayoutStore.MIGRATION_VERSION, prefs.getInt(PlaceLayoutStore.KEY_MIGRATED, 0));
        assertFalse("the Home layout switch goes off", store.hasHomeLayout());
        // The wall names its Home pages after the store migrates.
        assertFalse(store.hasPageLayout(2));
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));

        for (int id : new int[] {2, 3}) {
            assertTrue("page " + id, store.hasPageLayout(id));
            PlaceLayoutStore own = store.forVariant(home(id));
            assertEquals(Edge.BOTTOM, own.statusBarEdge(PORTRAIT));
            assertEquals(DockStyle.LENS_BEADS, own.dockStyle());
        }
        assertSame(home(2), store.variantFor(PaneWallPage.WIDGETS));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.TERMINAL));
        assertEquals(Edge.TOP, store.statusBarEdge(PORTRAIT));
        assertFalse(store.hasHomeLayoutToAdopt());
    }

    @Test
    public void anEqualHomeLayoutOnlyTurnsTheSwitchOff() {
        upgradeFromHomeLayout(false);

        PlaceLayoutStore store = new PlaceLayoutStore(launcher);
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));

        assertFalse(store.hasHomeLayout());
        assertFalse(store.hasHomeLayoutToAdopt());
        assertFalse(store.hasPageLayout(2));
        assertFalse(store.hasPageLayout(3));
        assertSame(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.WIDGETS));
    }

    @Test
    public void theCopyWaitsForHomePagesAndRunsOnce() {
        upgradeFromHomeLayout(true);
        PlaceLayoutStore store = new PlaceLayoutStore(launcher);

        store.adoptHomeLayout(Collections.<Integer>emptyList());
        assertTrue("no Home pages yet: still to come", store.hasHomeLayoutToAdopt());

        store.adoptHomeLayout(Arrays.asList(2));
        store.forVariant(home(2)).setStatusBarEdge(PORTRAIT, Edge.TOP);
        // Re-running, and building the store again, changes nothing.
        store.adoptHomeLayout(Arrays.asList(2, 3));
        new PlaceLayoutStore(launcher).setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        assertEquals(Edge.TOP, store.forVariant(home(2)).statusBarEdge(PORTRAIT));
        assertFalse(store.hasPageLayout(3));
    }

    // ------------------------------------------------------------------ a page's own visibility

    @Test
    public void hidingOnOnePagesLayoutLeavesTheKindAndOtherPagesAlone() {
        PlaceLayoutStore store = store();
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        store.setPageLayout(2, PageKind.HOME, true);
        store.setPageLayout(3, PageKind.HOME, true);
        PlaceLayoutStore own = store.forVariant(home(2));
        assertFalse(own.resolve(PORTRAIT).slot(Element.STATUS).hidden);

        own.setSlot(PORTRAIT, Element.STATUS, own.slot(PORTRAIT, Element.STATUS).withHidden(true));

        assertTrue(own.resolve(PORTRAIT).slot(Element.STATUS).hidden);
        assertTrue(store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.STATUS).hidden);
        assertTrue("the Home kind still shows it",
            store.kindShows(PageKind.HOME, PORTRAIT, Element.STATUS));
        assertFalse("page 3 still shows it",
            store.forVariant(home(3)).resolve(PORTRAIT).slot(Element.STATUS).hidden);
    }

    @Test
    public void aSeededPageLayoutShowsExactlyWhatTheKindShowed() {
        PlaceLayoutStore store = store();
        store.setKindShows(PageKind.HOME, PORTRAIT, Element.AZ, false);
        store.setKindShows(PageKind.HOME, PlaceOrientation.LANDSCAPE, Element.STATUS, false);
        // A hidden flag the shared layout still holds from before the kinds is not what shows.
        store.setPageLayout(2, PageKind.HOME, true);

        PlaceLayoutStore own = store.forVariant(home(2));
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (Element element : Element.values()) {
                assertEquals(orientation + " " + element,
                    store.kindShows(PageKind.HOME, orientation, element),
                    !own.resolve(orientation).slot(element).hidden);
            }
        }
    }

    @Test
    public void kindEditsDoNotReachAPageThatHoldsItsOwnLayout() {
        PlaceLayoutStore store = store();
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        store.setPageLayout(2, PageKind.HOME, true);

        store.setKindShows(PageKind.HOME, PORTRAIT, Element.APPS, false);

        assertFalse("page 2 keeps its own",
            store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.APPS).hidden);
        store.setShownPages(pages(3, 1, Arrays.asList(2, 3)));
        assertTrue("page 3 wears the kind's",
            store.resolve(PORTRAIT, PaneWallPage.WIDGETS).slot(Element.APPS).hidden);
    }

    /**
     * A page named by its id resolves for that page (#173 slice E): Home page 3's own layout
     * while page 2, on the shared layout, is in front, so a crossing between the two holds each
     * end's own arrangement. With no id said, or for the page in front, it reads as before.
     */
    /**
     * A Display page's own layout is of the Display kind: it seeds from what Display pages show,
     * not the Terminal's, and links its sizes as a Terminal page's does.
     */
    @Test
    public void aDisplayPagesLayoutSeedsFromTheDisplayKind() {
        PlaceLayoutStore store = store();
        store.setKindShows(PageKind.TERMINAL, PORTRAIT, Element.APPS, true);
        store.setKindShows(PageKind.DISPLAY, PORTRAIT, Element.APPS, false);
        store.setPageLayout(5, PageKind.DISPLAY, true);
        LayoutVariant display = LayoutVariant.page(5, PageKind.DISPLAY);
        assertEquals(PageKind.DISPLAY, display.kind());
        assertTrue(display.linksSizes());
        assertTrue(display.holdsOwnVisibility());
        assertTrue("seeded from the Display kind",
            store.forVariant(display).slot(PORTRAIT, Element.APPS).hidden);
    }

    @Test
    public void aPageNamedByItsIdResolvesItsOwnLayoutWhileAnotherIsInFront() {
        PlaceLayoutStore store = store();
        store.setShownPages(pages(2, 1, Arrays.asList(2, 3)));
        store.setStatusBarEdge(PORTRAIT, Edge.TOP);
        store.setPageLayout(3, PageKind.HOME, true);
        store.forVariant(home(3)).setStatusBarEdge(PORTRAIT, Edge.BOTTOM);

        com.termux.app.wall.PageRef page2 = com.termux.app.wall.PageRef.of(PaneWallPage.WIDGETS, 2);
        com.termux.app.wall.PageRef page3 = com.termux.app.wall.PageRef.of(PaneWallPage.WIDGETS, 3);
        assertSame(LayoutVariant.NORMAL, store.variantFor(PaneWallPage.WIDGETS));
        assertSame(LayoutVariant.NORMAL, store.variantFor(page2));
        assertSame(home(3), store.variantFor(page3));
        assertSame(LayoutVariant.NORMAL, store.variantFor(com.termux.app.wall.PageRef.HOME));

        ResolvedArrangement three = ResolvedArrangement.resolve(store, PORTRAIT, page3);
        assertSame(home(3), three.variant());
        assertEquals(page3, three.page());
        assertEquals(Edge.BOTTOM, three.layout().slot(Element.STATUS).edge);
        ResolvedArrangement two = ResolvedArrangement.resolve(store, PORTRAIT, page2);
        assertSame(LayoutVariant.NORMAL, two.variant());
        assertEquals(Edge.TOP, two.layout().slot(Element.STATUS).edge);
    }
}
