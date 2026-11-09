package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.SeamTravel;
import com.termux.app.place.Slot;

import org.junit.Test;

/**
 * The design a page's slab wears: its layout's while the layout shows the pinned apps, the plain
 * tray where it shows none (pong, round 9: a Workspace with only the extra keys resolved to Lens
 * beads, and a slide to Home's Peek glass re-dressed the slab at the landing).
 */
public class WornDockDesignTest {

    /** Home: the pinned apps with the A–Z row under them. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** The developer's Workspace: the extra keys alone, the dock's apps row turned off. */
    private static final PlaceLayout KEYS_ONLY = new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A Workspace with the apps and the extra keys. */
    private static final PlaceLayout APPS_AND_KEYS = new PlaceLayout(Edge.TOP,
        RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.BOTTOM,
        PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    @Test
    public void aPageWithoutTheAppsRowWearsThePlainTrayWhateverIsStored() {
        for (DockStyle stored : DockStyle.values())
            assertSame(stored.name(), WornDockDesign.NO_APPS_ROW,
                WornDockDesign.of(stored, KEYS_ONLY));
        assertSame(DockStyle.GLASS_TRAY, WornDockDesign.NO_APPS_ROW);
    }

    @Test
    public void aPageWithTheAppsRowWearsItsStoredDesign() {
        for (DockStyle stored : DockStyle.values()) {
            assertSame(stored, WornDockDesign.of(stored, HOME));
            assertSame(stored, WornDockDesign.of(stored, APPS_AND_KEYS));
        }
        assertSame("nothing stored", DockStyle.GLASS_TRAY, WornDockDesign.of(null, HOME));
    }

    @Test
    public void turningTheRowBackOnBringsThePickBack() {
        PlaceLayout off = APPS_AND_KEYS.withSlot(Element.APPS,
            Slot.on(Edge.BOTTOM, Element.APPS).withHidden(true));
        assertSame(DockStyle.GLASS_TRAY, WornDockDesign.of(DockStyle.LENS_BEADS, off));
        assertSame(DockStyle.LENS_BEADS, WornDockDesign.of(DockStyle.LENS_BEADS, APPS_AND_KEYS));
    }

    /**
     * What "none" draws on the extra-keys-only slab is what Lens beads drew there: no beads
     * (the row cannot wear them), the tray's soft rim, its shelf and its contact shadow. Peek
     * glass with no card is the tray's slab too ({@link DockSurfaceSpec#forCard}).
     */
    @Test
    public void theDockOffSlabLooksAsItDidUnderLensBeadsAndPeek() {
        DockSurfaceSpec none = DockSurfaceSpec.forStyle(WornDockDesign.NO_APPS_ROW);
        DockSurfaceSpec beads = DockSurfaceSpec.LENS_BEADS;
        assertEquals(DockSurfaceSpec.GLASS_TRAY.softRimDp, none.softRimDp, 0f);
        assertEquals(beads.shelf, none.shelf, 0f);
        assertEquals(beads.contactShadow, none.contactShadow);
        assertSame(none, DockSurfaceSpec.PEEK_GLASS.forCard(null));
        assertEquals(false, LensBeadGeometry.rowWearsBeads(KEYS_ONLY, false, false));
    }

    /**
     * Workspace (dock off) to Home and back: the crossing's two designs are the resolver's, so a
     * dock-off Workspace and Home's Peek glass have a design apart only where Home's stands; the
     * rows a dip brings in rise in their own design and the settle commits that same design.
     */
    @Test
    public void theCrossingsDesignsAreTheResolversAtRest() {
        DockStyle workspace = WornDockDesign.of(DockStyle.LENS_BEADS, KEYS_ONLY);
        DockStyle home = WornDockDesign.of(DockStyle.PEEK_GLASS, HOME);
        assertSame(DockStyle.GLASS_TRAY, workspace);
        assertSame(DockStyle.PEEK_GLASS, home);
        // The rows the dip brings in stand Home's apps row, so they wear Home's design, the one
        // the settle on Home resolves; back on the Workspace its slab is the plain tray again.
        PlaceLayout standingHome = SeamTravel.withDockOf(KEYS_ONLY, HOME);
        assertSame(home, WornDockDesign.of(DockStyle.PEEK_GLASS, standingHome));
        PlaceLayout standingWorkspace = SeamTravel.withDockOf(HOME, KEYS_ONLY);
        assertSame(workspace, WornDockDesign.of(DockStyle.LENS_BEADS, standingWorkspace));
    }
}
