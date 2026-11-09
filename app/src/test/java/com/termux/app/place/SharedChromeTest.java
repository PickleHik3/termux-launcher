package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.place.layout.SharedChromeFold;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The dock's design, search field and drawer tile and the status bar's edge and compact rest are
 * one value for every page, Home's (ADR 0023 §1): the one-time fold and the reads and writes
 * that follow it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SharedChromeTest {

    private static final String BAR = Element.STATUS.storageKey();
    private static final String P = PlaceOrientation.PORTRAIT.storageValue();

    private Application app;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = app.getSharedPreferences("shared-chrome", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
    }

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));
    }

    private static Layouts layouts(KindLayout home, KindLayout terminal) {
        return Layouts.shipped().withKind(LayoutKind.HOME, home)
            .withKind(LayoutKind.TERMINAL, terminal);
    }

    private static KindLayout kind(String design, boolean search, boolean tile, String bar,
                                   Boolean compact) {
        KindLayout out = KindLayout.EMPTY
            .withWide(LayoutsJson.DOCK_DESIGN, design)
            .withWide(LayoutsJson.SEARCH_FIELD, search)
            .withWide(LayoutsJson.DRAWER_TILE, tile);
        if (bar != null) out = out.with(PlaceOrientation.PORTRAIT, BAR, bar);
        if (compact != null)
            out = out.with(PlaceOrientation.PORTRAIT, LayoutsJson.STATUS_COMPACT, compact);
        return out;
    }

    @Test
    public void homesValueWinsWhereHomeAndTheWorkspaceDiffer() {
        Layouts folded = SharedChromeFold.fold(layouts(
            kind(DockStyle.LENS_BEADS.storageValue(), true, false, "bottom", true),
            kind(DockStyle.GLASS_TRAY.storageValue(), false, true, "top", false)));

        KindLayout home = folded.kind(LayoutKind.HOME);
        KindLayout terminal = folded.kind(LayoutKind.TERMINAL);
        assertEquals(DockStyle.LENS_BEADS.storageValue(), home.wide().string(LayoutsJson.DOCK_DESIGN));
        assertTrue(home.wide().bool(LayoutsJson.SEARCH_FIELD, false));
        assertFalse(home.wide().bool(LayoutsJson.DRAWER_TILE, true));
        assertEquals("bottom", home.portrait().string(LayoutsJson.STATUS_EDGE));
        assertTrue(home.portrait().bool(LayoutsJson.STATUS_COMPACT, false));
        for (String field : LayoutsJson.SHARED_WIDE_FIELDS)
            assertFalse(field, terminal.wide().has(field));
        assertFalse(terminal.portrait().has(LayoutsJson.STATUS_COMPACT));
        assertFalse(terminal.portrait().has(BAR));
    }

    @Test
    public void allEqualChangesNothingAnyoneSees() {
        String tray = DockStyle.GLASS_TRAY.storageValue();
        Layouts folded = SharedChromeFold.fold(layouts(
            kind(tray, false, false, "top", false), kind(tray, false, false, "top", false)));
        assertEquals(tray, folded.kind(LayoutKind.HOME).wide().string(LayoutsJson.DOCK_DESIGN));
        assertNull("the top is the shipped edge: nothing is written",
            folded.kind(LayoutKind.HOME).portrait().string(LayoutsJson.STATUS_EDGE));
    }

    @Test
    public void aPutAwayHomeBarKeepsItsEdgeAndItsMark() {
        KindLayout home = kind(DockStyle.GLASS_TRAY.storageValue(), false, false, "hidden", null)
            .with(PlaceOrientation.PORTRAIT, BAR + LayoutsJson.RESTORE_EDGE_SUFFIX, "bottom");
        Layouts folded = SharedChromeFold.fold(layouts(home, KindLayout.EMPTY));
        KindLayout out = folded.kind(LayoutKind.HOME);
        assertEquals("bottom", out.portrait().string(LayoutsJson.STATUS_EDGE));
        assertEquals("hidden", out.portrait().string(BAR));
        assertFalse(out.portrait().has(BAR + LayoutsJson.RESTORE_EDGE_SUFFIX));
    }

    @Test
    public void foldingAgainChangesNothing() {
        Layouts once = SharedChromeFold.fold(layouts(
            kind(DockStyle.LENS_BEADS.storageValue(), true, false, "bottom", true),
            kind(DockStyle.GLASS_TRAY.storageValue(), false, true, "top", false)));
        assertEquals(once, SharedChromeFold.fold(once));
    }

    @Test
    public void theStoreFoldsOnceBehindItsFlagAndEveryKindReadsHomes() {
        LayoutStore documents = LayoutStore.shared(app);
        documents.replaceAll(layouts(
            kind(DockStyle.LENS_BEADS.storageValue(), true, false, "bottom", true),
            kind(DockStyle.GLASS_TRAY.storageValue(), false, true, "top", false)));
        // Step 17 has already run for this install; the shared fold has not.
        prefs.edit().putInt(PlaceLayoutStore.KEY_MIGRATED, PlaceLayoutStore.MIGRATION_VERSION).commit();

        PlaceLayoutStore store = store();

        assertTrue(prefs.getBoolean(SharedChromeFold.KEY_MIGRATED, false));
        for (LayoutVariant variant : new LayoutVariant[]{LayoutVariant.HOME, LayoutVariant.NORMAL,
            LayoutVariant.MINIMAL}) {
            PlaceLayoutStore view = store.forVariant(variant);
            assertEquals(variant.toString(), DockStyle.LENS_BEADS, view.dockStyle());
            assertTrue(view.searchField());
            assertFalse(view.drawerTile());
            // The slot edge: a put-away bar (Terminal-minimal) still names the shared one.
            assertEquals(variant.toString(), Edge.BOTTOM,
                view.slot(PlaceOrientation.PORTRAIT, Element.STATUS).edge);
            assertTrue(view.isStatusCompact(PlaceOrientation.PORTRAIT));
        }
    }

    @Test
    public void aWriteFromAnyKindIsEveryKindsValue() {
        PlaceLayoutStore store = store();
        PlaceLayoutStore terminal = store.forVariant(LayoutVariant.NORMAL);
        terminal.setDockStyle(DockStyle.LENS_BEADS);
        terminal.setSearchField(true);
        terminal.setStatusBarEdge(PlaceOrientation.PORTRAIT, Edge.BOTTOM);
        terminal.setStatusCompact(PlaceOrientation.PORTRAIT, true);

        PlaceLayoutStore home = store.forVariant(LayoutVariant.HOME);
        assertEquals(DockStyle.LENS_BEADS, home.dockStyle());
        assertTrue(home.searchField());
        assertEquals(Edge.BOTTOM, home.statusBarEdge(PlaceOrientation.PORTRAIT));
        assertTrue(home.isStatusCompact(PlaceOrientation.PORTRAIT));
        assertEquals(Edge.BOTTOM, home.slot(PlaceOrientation.PORTRAIT, Element.STATUS).edge);
        assertEquals("the Terminal document holds no copy", null,
            LayoutStore.shared(app).current().kind(LayoutKind.TERMINAL)
                .wide().string(LayoutsJson.DOCK_DESIGN));
    }
}
