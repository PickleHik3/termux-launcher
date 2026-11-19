package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.ElementGlass;
import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.app.Application;

/**
 * A Look picked on the Look slider shows: what the Home page, the Terminal page and a Display
 * page resolve to for every glass element, as the renderers read it ({@link ElementGlass}), on a
 * store shaped like a real install after the kind seed (509b634db). A Look resets the cells read
 * before its own (developer, 2026-11-03): the Global card's clears the Home and Terminal looks
 * and every element value; a page card's clears the element values reaching its kinds.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PresetApplyTest {

    private static final int HOME_PAGE = 0, TERMINAL_PAGE = 1, DISPLAY_PAGE = 3;
    private static final int[] PAGES = {HOME_PAGE, TERMINAL_PAGE, DISPLAY_PAGE};
    private static final LookGrid.Element[] ELEMENTS = {LookGrid.Element.DOCK,
        LookGrid.Element.STATUS, LookGrid.Element.KEYBOARD};

    private TermuxAppSharedPreferences install() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("preset-apply",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, store, null);
        store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, TERMINAL_PAGE).commit();
        TermuxAppSharedPreferences.setTerminalPageId(TERMINAL_PAGE);
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        return prefs;
    }

    /**
     * The same fresh install again, in the same store and through the same preferences object, as
     * {@link #install} makes it. For a cell that walks on an editor that was closed first.
     */
    private void reinstall(TermuxAppSharedPreferences prefs) {
        SharedPreferences store = RuntimeEnvironment.getApplication().getSharedPreferences(
            "preset-apply", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, TERMINAL_PAGE).commit();
        TermuxAppSharedPreferences.setTerminalPageId(TERMINAL_PAGE);
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
    }

    @After
    public void forget() {
        TermuxAppSharedPreferences.setTerminalPageId(-1);
    }

    // ------------------------------------------------------------------------------ the tests

    @Test
    public void customizationLookShowsOnEveryPage() {
        List<String> wrong = new ArrayList<>();
        // One editor; each cell ends its session, puts the install back to a fresh one and
        // opens the card again, so every cell starts as a fresh install with the card just
        // opened.
        TermuxAppSharedPreferences prefs = install();
        EditorTestHarness harness = null;
        for (int stop : looks()) {
            if (harness == null) {
                harness = harnessFor(prefs, PageKind.HOME, HOME_PAGE);
            } else {
                harness.editor.endSession();
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
                org.junit.Assert.assertFalse("the editor is closed before the next cell",
                    harness.editor.isOpen());
                reinstall(prefs);
            }
            tap(present(harness, EditorCard.APPEARANCE, PageKind.HOME), stop);
            expectEverywhere(prefs, stop, PAGES, "Customization", wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void theSliderAwayAndBackLandsEachTime() {
        TermuxAppSharedPreferences prefs = install();
        List<String> wrong = new ArrayList<>();
        SurfaceEditorController editor = open(prefs, EditorCard.APPEARANCE, PageKind.HOME,
            HOME_PAGE);
        for (int stop : new int[] {looks()[3], looks()[0], looks()[3], looks()[2]}) {
            tap(editor, stop);
            expectEverywhere(prefs, stop, PAGES, "Global to " + stop, wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void aGlobalLookLeavesTheDocksOwnOpacity() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, 23);
        int stop = looks()[3];
        tap(open(prefs, EditorCard.APPEARANCE, PageKind.HOME, HOME_PAGE), stop);
        // Own stays (appearance-one-look spec §1); the rest of the dock wears the Look.
        LookGrid grid = prefs.lookGrid();
        assertEquals(23, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(grid.value(LookGrid.Property.FROST, null),
            grid.value(LookGrid.Property.FROST, LookGrid.Element.DOCK));
    }

    // ------------------------------------------------- a slider on Global or a page card

    /** Own stays (appearance-one-look spec §1): a Global slider leaves a surface's own value. */
    @Test
    public void aGlobalSliderLeavesASurfacesOwnValue() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, 23);
        LookGrid grid = prefs.lookGrid();
        grid.write(LookGrid.Property.FROST, LookGrid.Element.DOCK, 2);

        slide(open(prefs, EditorCard.APPEARANCE, PageKind.HOME, HOME_PAGE), Row.OPACITY,
            61);

        assertEquals(23, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals("the status bar follows Global", 61,
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS));
        assertTrue(grid.has(LookGrid.Property.FROST, LookGrid.Element.DOCK));
    }

    @Test
    public void aSurfaceMovedAfterGlobalDetachesThatSurfaceOnly() {
        TermuxAppSharedPreferences prefs = install();
        SurfaceEditorController first = open(prefs, EditorCard.APPEARANCE, PageKind.HOME,
            HOME_PAGE);
        slide(first, Row.OPACITY, 61);
        first.done();
        LookGrid grid = prefs.lookGrid();
        int status = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS);
        dockOpacity(prefs, 23);
        for (int page : PAGES) {
            assertEquals("dock on page " + page, 23,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
            assertEquals("status on page " + page, status,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS));
        }
    }

    @Test
    public void marginIsOneNumberForEveryPage() {
        TermuxAppSharedPreferences prefs = install();
        slide(open(prefs, EditorCard.APPEARANCE, PageKind.HOME, HOME_PAGE), Row.MARGIN, 10);
        assertMargins(prefs, 10, "Customization on Home");

        // Where standing on a page still writes Global: no page holds a Margin of its own.
        slide(open(prefs, EditorCard.APPEARANCE, PageKind.TERMINAL, TERMINAL_PAGE),
            Row.MARGIN, 20);
        assertMargins(prefs, 20, "Customization on the Terminal");
    }

    @Test
    public void discardPutsTheMarginBack() {
        TermuxAppSharedPreferences prefs = install();
        SurfaceEditorController global = open(prefs, EditorCard.APPEARANCE, PageKind.HOME,
            HOME_PAGE);
        int margin = prefs.getGlobalMarginDp();
        slide(global, Row.MARGIN, 6);
        assertMargins(prefs, 6, "Customization");
        ReflectionHelpers.callInstanceMethod(global, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertMargins(prefs, margin, "after Discard");
    }

    private static void assertMargins(TermuxAppSharedPreferences prefs, int margin, String what) {
        assertEquals(what + ": Home pages", margin, prefs.getLayoutMarginDp(true));
        assertEquals(what + ": Terminal pages", margin, prefs.getLayoutMarginDp(false));
        // What the dress shapes each kind's pages by.
        assertEquals(what + ": Home's shape", margin,
            com.termux.app.place.WallShape.read(prefs, true).marginDp);
        assertEquals(what + ": the Terminal's shape", margin,
            com.termux.app.place.WallShape.read(prefs, false).marginDp);
    }

    /** The row arrow's reset is undone by Discard like any change. */
    @Test
    public void discardPutsAResetOwnValueBack() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, 23);
        LookGrid grid = prefs.lookGrid();
        SurfaceEditorController editor = open(prefs, EditorCard.DOCK, PageKind.HOME,
            HOME_PAGE);
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        model.resetRow(Row.OPACITY);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));

        ReflectionHelpers.callInstanceMethod(editor, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(23, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
    }

    /** A slider moved by a drag of two steps, then let go. */
    private static void slide(SurfaceEditorController editor, Row row, int value) {
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        model.onValue(row, value - 1, true);
        model.onValue(row, value, true);
        model.onReleased(row);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private static void dockOpacity(TermuxAppSharedPreferences prefs, int value) {
        SurfaceEditorController editor = open(prefs, EditorCard.DOCK, PageKind.HOME,
            HOME_PAGE);
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        model.onValue(Row.OPACITY, value, false);
        model.onReleased(Row.OPACITY);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        // The edit is the draft's until Done: the store holds it from here, as a person's Done does.
        editor.done();
    }

    // ---------------------------------------------------------------------------- the checks

    /** Each element on each page wears the Look's Frost, Opacity and Tint. */
    private static void expectEverywhere(TermuxAppSharedPreferences prefs, int stop, int[] pages,
                                         String what, List<String> wrong) {
        int[] look = GlassRows.lookCell(stop);
        LookGrid grid = prefs.lookGrid();
        LookGrid.Property[] properties = {LookGrid.Property.FROST, LookGrid.Property.OPACITY,
            LookGrid.Property.TINT};
        for (LookGrid.Element element : ELEMENTS)
            for (int page : pages)
                for (int i = 0; i < properties.length; i++) {
                    LookGrid.Resolved resolved = grid.resolve(properties[i], element);
                    int want = properties[i].clamp(look[i]);
                    if (resolved.value != want)
                        wrong.add(what + " stop " + stop + ": " + element.key + " on page "
                            + page + " " + properties[i].key + " = " + resolved + ", want "
                            + want);
                }
    }

    // ----------------------------------------------------------------------------- the drive

    private static int[] looks() {
        return new int[] {AppearanceLooks.stopForPresetId("minimal"),
            AppearanceLooks.stopForPresetId("frost"), AppearanceLooks.stopForPresetId("stock"),
            AppearanceLooks.stopForPresetId("solid")};
    }

    private static SurfaceEditorController open(TermuxAppSharedPreferences prefs, EditorCard card,
                                                PageKind place, int pageInFront) {
        return present(harnessFor(prefs, place, pageInFront), card, place);
    }

    /** An editor over a new activity, whose host answers {@code place} and {@code pageInFront}. */
    private static EditorTestHarness harnessFor(TermuxAppSharedPreferences prefs,
                                                PageKind place, int pageInFront) {
        List<WallPages.Page> pages = Arrays.asList(
            new WallPages.Page(HOME_PAGE, PageKind.HOME, "Home 1", null),
            new WallPages.Page(TERMINAL_PAGE, PageKind.TERMINAL, "Terminal", null));
        EditorTestHarness harness = new EditorTestHarness(prefs, (method, args) -> {
            switch (method) {
                case "pageOnScreen": return place;
                case "wallPages": return pages;
                case "pageIdOnScreen": return pageInFront;
                default: return null;
            }
        });
        return harness;
    }

    /** {@code card} opened on {@code harness}'s editor, on {@code place}. */
    private static SurfaceEditorController present(EditorTestHarness harness, EditorCard card,
                                                   PageKind place) {
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    /** A tap on a Look tile. */
    private static void tap(SurfaceEditorController editor, int stop) {
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        model.onValue(Row.LOOK, stop, false);
        model.onReleased(Row.LOOK);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }
}
