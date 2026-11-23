package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
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
import android.app.Application;

/**
 * A Look by card (appearance-cards-overhaul §1): Customization's reaches every page; Home's lands
 * in Home pages' all-glass cells alone, the Terminal's in Terminal and Display pages' together,
 * and their Reset returns those pages to Global.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
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

    /** The user's own look: Opacity 12, Blur 20%, Grain 3, Tint 0, Edge 40, Depth, Rim Bright. */
    private void setUpCustom() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, 12);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 3);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, 3);
        preferences.setSurfaceGlobalValue(SurfaceProperty.TINT, 0);
        preferences.setGlassShine(70);
        preferences.setFancierGlassBendDp(30);
    }

    private void assertCustomValues() {
        assertEquals(12, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        assertEquals(3, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN));
        assertEquals(1, preferences.lookGrid().value(LookGrid.Property.FROST, null));
        assertEquals(0, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        assertEquals(70, preferences.getGlassShine());
        assertEquals(30, preferences.getFancierGlassBendDp());
    }

    @Test
    public void customComesBackAfterALookInTheSameVisit() {
        setUpCustom();
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);
        assertEquals(92, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        tapLook(editor, AppearanceLooks.CUSTOM_STOP);

        assertCustomValues();
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
    }

    @Test
    public void customComesBackAfterALookWasKeptWithDone() {
        setUpCustom();
        SurfaceEditorController first = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        tapLook(first, SOLID);
        first.done();

        // The next visit opens on Solid; its Custom chip is the user's own look again.
        SurfaceEditorController second = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        assertEquals(SOLID, model(second).value(Row.LOOK));
        tapLook(second, AppearanceLooks.CUSTOM_STOP);
        assertCustomValues();
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(second).value(Row.LOOK));
    }

    @Test
    public void aSliderMovedOffALookIsCustomAndSavedWhenItLeaves() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        assertEquals(FROST, model(editor).value(Row.LOOK));
        model(editor).onValue(Row.OPACITY, 33, false);
        model(editor).onReleased(Row.OPACITY);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));

        tapLook(editor, SOLID);
        tapLook(editor, AppearanceLooks.CUSTOM_STOP);
        assertEquals(33, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        assertNotNull(SurfacePresets.custom(preferences));
    }

    @Test
    public void aCustomLookAtOpenIsSavedAndUndoPutsTheSlotBack() {
        setUpCustom();
        assertNull(SurfacePresets.custom(preferences));
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        assertNotNull("saved at open", SurfacePresets.custom(preferences));

        tapLook(editor, SOLID);
        editor.undo();
        assertNull("Cancel leaves the slot as it was at open", SurfacePresets.custom(preferences));
        assertCustomValues();
    }

    @Test
    public void aLookOnCustomizationReachesEveryPage() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        assertEquals(FROST, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);

        assertTrue("the wall wears Solid", SurfacePresets.wornOn(preferences, preset("solid"),
            PageKind.HOME));
        assertEquals(SOLID, model(editor).value(Row.LOOK));
    }

    @Test
    public void aLookTileStaysSelectedWhileSurfacesKeepTheirOwnGlass() {
        // The status bar, the dock and the keyboard own their Opacity and Blur; a Global Look
        // leaves them, so the tile that was tapped is the one shown.
        preferences.setSurfaceValueExact(SurfaceSlot.STATUS, SurfaceProperty.OPACITY, 20);
        preferences.setSurfaceValueExact(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 3);
        preferences.setSurfaceValueExact(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, 70);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        for (String id : new String[] {"solid", "frost", "minimal", "solid"}) {
            int stop = AppearanceLooks.stopForPresetId(id);
            tapLook(editor, stop);
            assertEquals(id + " stays selected", stop, model(editor).value(Row.LOOK));
        }
        assertFalse("the own value stays",
            preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.OPACITY));
    }

    @Test
    public void aGlobalValueEditedAfterALookIsCustomEvenWithOwnSurfaceGlass() {
        preferences.setSurfaceValueExact(SurfaceSlot.STATUS, SurfaceProperty.OPACITY, 20);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, PageKind.TERMINAL,
            TERMINAL_PAGE);
        tapLook(editor, SOLID);
        assertEquals(SOLID, model(editor).value(Row.LOOK));
        model(editor).onValue(Row.OPACITY, 33, false);
        model(editor).onReleased(Row.OPACITY);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
    }

    @Test
    public void homesLookRowIsItsOwnHalfWhateverTheFrameShows() {
        for (PageKind frame : PageKind.values())
            assertEquals(TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(EditorCard.HOME, Row.LOOK));
    }

    // ------------------------------------------------------------------------------- the checks

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
    private SurfaceEditorController open(EditorCard card, PageKind place, int pageInFront) {
        List<WallPages.Page> pages = Arrays.asList(
            new WallPages.Page(HOME_PAGE, PageKind.HOME, "Home 1", null),
            new WallPages.Page(TERMINAL_PAGE, PageKind.TERMINAL, "Terminal", null));
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

}
