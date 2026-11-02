package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.List;

/**
 * A Look by card (appearance-cards-overhaul §1): Customization's reaches every page; Home's lands
 * in Home pages' all-glass cells alone, the Workspace's in Terminal and Display pages' together,
 * and their Reset returns those pages to Base.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class EditorScreenLookTest {

    private static final int SOLID = AppearanceLooks.stopForPresetId("solid");
    private static final int FROST = AppearanceLooks.stopForPresetId("frost");
    private static final int HOME_PAGE = 1;
    private static final int TERMINAL_PAGE =
        com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID;

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("editor-screen-look",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        SurfacePresets.applyLook(preferences, preset("frost"));
    }

    @Test
    public void aLookOnCustomizationReachesEveryPage() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PaneWallPage.TERMINAL,
            TERMINAL_PAGE);
        assertFalse("Customization stands on Global", model(editor).scopeOnPage());
        assertEquals(FROST, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);

        assertTrue("the wall wears Solid", SurfacePresets.wornOn(preferences, preset("solid"),
            PaneWallPage.WIDGETS));
        assertFalse(SurfacePresets.holdsPageLook(preferences, TERMINAL_PAGE));
        assertFalse(SurfacePresets.holdsPageLook(preferences, HOME_PAGE));
        assertEquals(SOLID, model(editor).value(Row.LOOK));
    }

    @Test
    public void aLookOnTheWorkspaceLandsOnTerminalAndDisplayPages() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL,
            TERMINAL_PAGE);
        tapLook(editor, SOLID);
        int[] solid = GlassRows.lookCell(SOLID);
        LookGrid.Property[] properties = {LookGrid.Property.FROST, LookGrid.Property.OPACITY,
            LookGrid.Property.TINT};
        LookGrid grid = preferences.lookGrid();
        for (LookGrid.Kind kind : new LookGrid.Kind[] {LookGrid.Kind.TERMINAL, LookGrid.Kind.DISPLAY})
            for (int i = 0; i < properties.length; i++)
                assertEquals(kind.key + " " + properties[i].key, properties[i].clamp(solid[i]),
                    grid.value(new LookGrid.Key(properties[i], null, kind)));
        assertFalse("Home pages are left", grid.has(LookGrid.Property.OPACITY, null, LookGrid.Kind.HOME));
        assertTrue("the wall still wears Frost",
            SurfacePresets.wornOn(preferences, preset("frost"), PaneWallPage.WIDGETS));
        assertEquals(SOLID, model(editor).value(Row.LOOK));

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertFalse(grid.has(LookGrid.Property.OPACITY, null, LookGrid.Kind.TERMINAL));
        assertFalse(grid.has(LookGrid.Property.OPACITY, null, LookGrid.Kind.DISPLAY));
        assertEquals("the Workspace follows Base again", FROST, model(editor).value(Row.LOOK));
    }

    @Test
    public void aLookOnHomeLandsOnHomePagesAlone() {
        SurfaceEditorController editor = open(EditorCard.HOME, PaneWallPage.WIDGETS, HOME_PAGE);
        tapLook(editor, SOLID);
        LookGrid grid = preferences.lookGrid();
        assertTrue(grid.has(LookGrid.Property.OPACITY, null, LookGrid.Kind.HOME));
        assertFalse(grid.has(LookGrid.Property.OPACITY, null, LookGrid.Kind.TERMINAL));
        assertEquals(SOLID, model(editor).value(Row.LOOK));
    }

    @Test
    public void eachScreenCardsLookRowIsItsOwnHalfWhateverTheFrameShows() {
        for (PaneWallPage frame : PaneWallPage.values()) {
            assertEquals(TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(EditorCard.HOME, Row.LOOK));
            assertEquals(TermuxAppSharedPreferences.PAGE_WALL,
                EditorRows.pageFor(EditorCard.WORKSPACE, Row.LOOK));
        }
    }

    // ------------------------------------------------------------------------------- the checks

    private int pageValue(SurfaceSlot slot, SurfaceProperty property, int pageId) {
        // A page's own value is the element's cell on that page.
        String key = preferences.lookGrid().cellKey(property.grid, slot.element, pageId);
        SharedPreferences store = preferences.getSharedPreferences();
        assertTrue(key + " is the page's own", store.contains(key));
        return preferences.getSurfaceValue(slot, property, pageId);
    }

    private SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets(preferences)) {
            if (preset.id.equals(id))
                return preset;
        }
        throw new AssertionError(id);
    }

    /** A tap on a Look tile. */
    private static void tapLook(SurfaceEditorController editor, int stop) {
        model(editor).onValue(Row.LOOK, stop, false);
        model(editor).onReleased(Row.LOOK);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    // ------------------------------------------------------------------------------- the drive

    /** {@code card} up on {@code place}; the wall is a Home page and the Terminal page. */
    private SurfaceEditorController open(EditorCard card, PaneWallPage place, int pageInFront) {
        List<WallPages.Page> pages = Arrays.asList(
            new WallPages.Page(HOME_PAGE, WallPages.PageKind.HOME, "Home 1", null),
            new WallPages.Page(TERMINAL_PAGE, WallPages.PageKind.TERMINAL, "Terminal", null));
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "pageOnScreen": return place;
                case "wallPages": return pages;
                case "pageIdOnScreen": return pageInFront;
                default: return null;
            }
        });
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        com.termux.shared.termux.settings.preferences.LookGrid.install(null, null);
    }
}
