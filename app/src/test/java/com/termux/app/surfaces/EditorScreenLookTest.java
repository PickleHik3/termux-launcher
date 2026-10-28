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
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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
 * A Look and the scope chip (ADR 0015 §6, #175 B2): the editor writes Global (Base) by default, so
 * a Look reaches every page; on the page side of the chip a Look lands as that page's own
 * {@code @id} values alone, the slider reads the stop the page wears from its overrides (so a page
 * detached before this visit shows its stop), Custom brings the saved Custom to that page alone,
 * and Reset returns the page to Base. The halves and their Match rows are gone.
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
    public void aLookUnderGlobalReachesEveryPage() {
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, TERMINAL_PAGE);
        assertFalse("the editor opens on Global", model(editor).scopeOnPage());
        assertEquals(FROST, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);

        assertTrue("the wall wears Solid", SurfacePresets.wornOn(preferences, preset("solid"),
            WallHalf.HOME));
        assertFalse(SurfacePresets.holdsPageLook(preferences, TERMINAL_PAGE));
        assertFalse(SurfacePresets.holdsPageLook(preferences, HOME_PAGE));
        assertEquals(SOLID, model(editor).value(Row.LOOK));
    }

    @Test
    public void aLookOnThePageSideIsThatPagesAlone() {
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, TERMINAL_PAGE);
        model(editor).setScopeOnPage(true);
        assertTrue(model(editor).scopeOnPage());
        assertEquals("a page that holds nothing wears the wall's stop", FROST,
            model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);

        assertEquals(SOLID, model(editor).value(Row.LOOK));
        assertTrue(SurfacePresets.wornAtPage(preferences, preset("solid"), TERMINAL_PAGE));
        assertEquals(92, pageValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, TERMINAL_PAGE));
        assertFalse("the other page holds nothing",
            SurfacePresets.holdsPageLook(preferences, HOME_PAGE));
        assertTrue("the wall still wears Frost",
            SurfacePresets.wornOn(preferences, preset("frost"), WallHalf.HOME));

        model(editor).setScopeOnPage(false);
        assertEquals("Global reads the wall's stop", FROST, model(editor).value(Row.LOOK));
    }

    @Test
    public void aPageDetachedBeforeThisVisitShowsItsStop() {
        SurfacePresets.applyLookAtPage(preferences, preset("solid"), TERMINAL_PAGE);
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, TERMINAL_PAGE);
        assertEquals(FROST, model(editor).value(Row.LOOK));
        model(editor).setScopeOnPage(true);
        assertEquals("the stop is read from the page's overrides", SOLID,
            model(editor).value(Row.LOOK));
    }

    @Test
    public void customOnThePageSideBringsTheSavedCustomToThatPageAlone() {
        SurfacePresets.applyLook(preferences, preset("solid"));
        SurfacePresets.saveCustom(preferences);
        SurfacePresets.applyLook(preferences, preset("frost"));
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, TERMINAL_PAGE);
        model(editor).setScopeOnPage(true);
        tapLook(editor, AppearanceLooks.CUSTOM_STOP);

        assertEquals(AppearanceLooks.CUSTOM_STOP, model(editor).value(Row.LOOK));
        assertEquals("the saved Custom's numbers land on the page", 92,
            pageValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, TERMINAL_PAGE));
        assertTrue("the wall is untouched",
            SurfacePresets.wornOn(preferences, preset("frost"), WallHalf.HOME));
        assertFalse(SurfacePresets.holdsPageLook(preferences, HOME_PAGE));
    }

    @Test
    public void resetOnThePageSideReturnsThePageToBase() {
        SurfaceEditorController editor = open(PaneWallPage.TERMINAL, TERMINAL_PAGE);
        model(editor).setScopeOnPage(true);
        tapLook(editor, SOLID);
        assertTrue(SurfacePresets.holdsPageLook(preferences, TERMINAL_PAGE));

        ReflectionHelpers.callInstanceMethod(editor, "resetCard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertFalse(SurfacePresets.holdsPageLook(preferences, TERMINAL_PAGE));
        assertEquals("the page follows Base again", FROST, model(editor).value(Row.LOOK));
        assertTrue(SurfacePresets.wornOn(preferences, preset("frost"), WallHalf.HOME));
    }

    @Test
    public void eachScreenCardsLookRowIsItsOwnHalfWhateverTheFrameShows() {
        for (WallHalf frame : WallHalf.values()) {
            assertEquals(WallHalf.HOME, EditorRows.halfFor(EditorCard.HOME, Row.LOOK, frame));
            assertEquals(WallHalf.WORKSPACE,
                EditorRows.halfFor(EditorCard.WORKSPACE, Row.LOOK, frame));
        }
    }

    // ------------------------------------------------------------------------------- the checks

    private int pageValue(SurfaceSlot slot, SurfaceProperty property, int pageId) {
        String key = PageLookKeys.pageKey(
            TermuxAppSharedPreferences.surfaceOverrideKey(slot, property), pageId);
        SharedPreferences store = preferences.getSharedPreferences();
        assertTrue(key + " is the page's own", store.contains(key));
        return store.getInt(key, -1);
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

    /** The Appearance card up on {@code place}; the wall is a Home page and the Terminal page. */
    private SurfaceEditorController open(PaneWallPage place, int pageInFront) {
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
        harness.editor.present(EditorCard.APPEARANCE, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(EditorCard.APPEARANCE, harness.editor.shownCard());
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
