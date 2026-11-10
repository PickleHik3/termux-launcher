package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.ElementGlass;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGridKindSeed;
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
 * A Look picked on the Look slider shows: what the Home page, the Workspace page and a Display
 * page resolve to for every glass element, as the renderers read it ({@link ElementGlass}), on a
 * store shaped like a real install after the kind seed (509b634db). A Look resets the cells read
 * before its own (developer, 2026-11-03): the Global card's clears the Home and Workspace looks
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
        LookGrid.install(page -> page == HOME_PAGE ? LookGrid.Kind.HOME
                : page == TERMINAL_PAGE ? LookGrid.Kind.TERMINAL
                : page == DISPLAY_PAGE ? LookGrid.Kind.DISPLAY : null,
            SurfacePresets.SHIPPED_BY_KIND);
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, store, null);
        store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, TERMINAL_PAGE).commit();
        TermuxAppSharedPreferences.setTerminalPageId(TERMINAL_PAGE);
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        LookGridKindSeed.run(new LookGrid.PrefsStore(store), SurfacePresets.SHIPPED_BY_KIND);
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
        LookGrid.install(page -> page == HOME_PAGE ? LookGrid.Kind.HOME
                : page == TERMINAL_PAGE ? LookGrid.Kind.TERMINAL
                : page == DISPLAY_PAGE ? LookGrid.Kind.DISPLAY : null,
            SurfacePresets.SHIPPED_BY_KIND);
        store.edit().putInt(PageLookKeys.KEY_TERMINAL_PAGE_ID, TERMINAL_PAGE).commit();
        TermuxAppSharedPreferences.setTerminalPageId(TERMINAL_PAGE);
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        LookGridKindSeed.run(new LookGrid.PrefsStore(store), SurfacePresets.SHIPPED_BY_KIND);
    }

    @After
    public void forget() {
        LookGrid.install(null, null);
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
                harness = harnessFor(prefs, PaneWallPage.WIDGETS, HOME_PAGE);
            } else {
                harness.editor.endSession();
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
                org.junit.Assert.assertFalse("the editor is closed before the next cell",
                    harness.editor.isOpen());
                reinstall(prefs);
            }
            tap(present(harness, EditorCard.APPEARANCE, PaneWallPage.WIDGETS), stop);
            expectEverywhere(prefs, stop, PAGES, "Customization", wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void customizationLookShowsAfterTheWorkspaceAndHomeHeldTheirOwn() {
        List<String> wrong = new ArrayList<>();
        for (int stop : looks()) {
            TermuxAppSharedPreferences prefs = install();
            int other = stop == looks()[0] ? looks()[1] : looks()[0];
            tap(open(prefs, EditorCard.WORKSPACE, PaneWallPage.TERMINAL, TERMINAL_PAGE), other);
            tap(open(prefs, EditorCard.HOME, PaneWallPage.WIDGETS, HOME_PAGE), other);
            tap(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), stop);
            expectEverywhere(prefs, stop, PAGES, "Customization after screens", wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void workspaceLookShowsOnTheWorkspacePages() {
        List<String> wrong = new ArrayList<>();
        // One editor; each cell ends its session, puts the install back to a fresh one and
        // opens the card again, so every cell starts as a fresh install with the card just
        // opened.
        TermuxAppSharedPreferences prefs = install();
        EditorTestHarness harness = null;
        for (int stop : looks()) {
            if (harness == null) {
                harness = harnessFor(prefs, PaneWallPage.TERMINAL, TERMINAL_PAGE);
            } else {
                harness.editor.endSession();
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
                org.junit.Assert.assertFalse("the editor is closed before the next cell",
                    harness.editor.isOpen());
                reinstall(prefs);
            }
            tap(present(harness, EditorCard.WORKSPACE, PaneWallPage.TERMINAL), stop);
            expectEverywhere(prefs, stop, new int[] {TERMINAL_PAGE, DISPLAY_PAGE}, "Workspace",
                wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void workspaceLookShowsAfterACustomizationLook() {
        List<String> wrong = new ArrayList<>();
        for (int stop : looks()) {
            TermuxAppSharedPreferences prefs = install();
            int other = stop == looks()[0] ? looks()[1] : looks()[0];
            tap(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), other);
            tap(open(prefs, EditorCard.WORKSPACE, PaneWallPage.TERMINAL, TERMINAL_PAGE), stop);
            expectEverywhere(prefs, stop, new int[] {TERMINAL_PAGE, DISPLAY_PAGE},
                "Workspace after Customization", wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void theSliderAwayAndBackLandsEachTime() {
        TermuxAppSharedPreferences prefs = install();
        List<String> wrong = new ArrayList<>();
        for (EditorCard card : new EditorCard[] {EditorCard.APPEARANCE, EditorCard.WORKSPACE}) {
            boolean workspace = card == EditorCard.WORKSPACE;
            SurfaceEditorController editor = open(prefs, card,
                workspace ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS,
                workspace ? TERMINAL_PAGE : HOME_PAGE);
            int[] pages = workspace ? new int[] {TERMINAL_PAGE, DISPLAY_PAGE} : PAGES;
            for (int stop : new int[] {looks()[3], looks()[0], looks()[3], looks()[2]}) {
                tap(editor, stop);
                expectEverywhere(prefs, stop, pages, card + " to " + stop, wrong);
            }
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void customizationLookShowsAfterAnElementCardHeldItsOwn() {
        List<String> wrong = new ArrayList<>();
        for (EditorChips.AppliesTo where : EditorChips.appliesToOptions(EditorCard.DOCK)) {
            TermuxAppSharedPreferences prefs = install();
            dockOpacity(prefs, where, 23);
            int stop = looks()[3];
            tap(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), stop);
            expectEverywhere(prefs, stop, PAGES, "Customization after Dock " + where, wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void workspaceLookShowsAfterAnElementCardHeldItsOwn() {
        List<String> wrong = new ArrayList<>();
        for (EditorChips.AppliesTo where : EditorChips.appliesToOptions(EditorCard.DOCK)) {
            TermuxAppSharedPreferences prefs = install();
            dockOpacity(prefs, where, 23);
            int stop = looks()[3];
            tap(open(prefs, EditorCard.WORKSPACE, PaneWallPage.TERMINAL, TERMINAL_PAGE), stop);
            expectEverywhere(prefs, stop, new int[] {TERMINAL_PAGE, DISPLAY_PAGE},
                "Workspace after Dock " + where, wrong);
            if (where == EditorChips.AppliesTo.GLOBAL || where == EditorChips.AppliesTo.HOME) {
                int home = prefs.lookGrid().value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK,
                    HOME_PAGE);
                if (home != 23)
                    wrong.add("Dock " + where + " left Home pages: dock opacity " + home);
            }
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    // ------------------------------------------------- a slider on Global or a page card

    @Test
    public void aGlobalSliderResetsTheDetachedValuesOfItsPropertyOnly() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, EditorChips.AppliesTo.GLOBAL, 23);
        dockOpacity(prefs, EditorChips.AppliesTo.WORKSPACE, 29);
        LookGrid grid = prefs.lookGrid();
        grid.write(LookGrid.Property.FROST, LookGrid.Element.DOCK, (LookGrid.Kind) null, 2);

        slide(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), Row.OPACITY,
            61);

        List<String> wrong = new ArrayList<>();
        for (int page : PAGES) {
            int dock = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, page);
            int status = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, page);
            if (dock != status)
                wrong.add("dock opacity on page " + page + " = " + dock + ", status " + status);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
        assertTrue("Frost was not moved: the dock keeps its own",
            grid.has(LookGrid.Property.FROST, LookGrid.Element.DOCK, (LookGrid.Kind) null));
    }

    @Test
    public void aGlobalCornersMoveResetsADetachedCornerRadius() {
        TermuxAppSharedPreferences prefs = install();
        LookGrid grid = prefs.lookGrid();
        grid.write(LookGrid.Property.CORNERS, LookGrid.Element.DOCK, (LookGrid.Kind) null, 30);
        slide(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), Row.CORNERS,
            12);
        for (int page : PAGES)
            assertEquals("dock corners on page " + page, 12,
                grid.value(LookGrid.Property.CORNERS, LookGrid.Element.DOCK, page));
    }

    @Test
    public void aSurfaceMovedAfterGlobalDetachesThatSurfaceOnly() {
        TermuxAppSharedPreferences prefs = install();
        slide(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), Row.OPACITY,
            61);
        LookGrid grid = prefs.lookGrid();
        int status = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, HOME_PAGE);
        dockOpacity(prefs, EditorChips.AppliesTo.GLOBAL, 23);
        for (int page : PAGES) {
            assertEquals("dock on page " + page, 23,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, page));
            assertEquals("status on page " + page, status,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, page));
        }
    }

    @Test
    public void aWorkspaceSliderLeavesTheHomePagesAlone() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, EditorChips.AppliesTo.GLOBAL, 23);
        LookGrid grid = prefs.lookGrid();
        int homeStatus = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, HOME_PAGE);

        slide(open(prefs, EditorCard.WORKSPACE, PaneWallPage.TERMINAL, TERMINAL_PAGE),
            Row.OPACITY, 61);

        assertEquals(23, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, HOME_PAGE));
        assertEquals(homeStatus,
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, HOME_PAGE));
        for (int page : new int[] {TERMINAL_PAGE, DISPLAY_PAGE})
            assertEquals("the dock follows the Workspace on page " + page,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, page),
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, page));
    }

    @Test
    public void aHomeSliderResetsHomeAndLeavesTheWorkspacePagesAlone() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, EditorChips.AppliesTo.GLOBAL, 23);
        LookGrid grid = prefs.lookGrid();
        int terminalStatus = grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS,
            TERMINAL_PAGE);

        slide(open(prefs, EditorCard.HOME, PaneWallPage.WIDGETS, HOME_PAGE), Row.OPACITY, 61);

        for (int page : new int[] {TERMINAL_PAGE, DISPLAY_PAGE})
            assertEquals("dock on page " + page, 23,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, page));
        assertEquals(terminalStatus,
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, TERMINAL_PAGE));
        assertEquals("the dock follows Home on Home pages",
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS, HOME_PAGE),
            grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, HOME_PAGE));
    }

    @Test
    public void marginIsOneNumberForEveryPage() {
        TermuxAppSharedPreferences prefs = install();
        slide(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), Row.MARGIN, 10);
        assertMargins(prefs, 10, "Customization on Home");

        // Where standing on a page still writes Base: no page holds a Margin of its own.
        slide(open(prefs, EditorCard.APPEARANCE, PaneWallPage.TERMINAL, TERMINAL_PAGE),
            Row.MARGIN, 20);
        assertMargins(prefs, 20, "Customization on the Workspace");
        LookGrid grid = prefs.lookGrid();
        for (LookGrid.Kind kind : LookGrid.Kind.values())
            org.junit.Assert.assertFalse(kind + " holds no Margin",
                grid.has(LookGrid.Property.MARGIN, null, kind));
    }

    @Test
    public void discardPutsTheMarginBack() {
        TermuxAppSharedPreferences prefs = install();
        SurfaceEditorController global = open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS,
            HOME_PAGE);
        int base = prefs.getBaseMarginDp();
        slide(global, Row.MARGIN, 6);
        assertMargins(prefs, 6, "Customization");
        ReflectionHelpers.callInstanceMethod(global, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertMargins(prefs, base, "after Discard");
    }

    @Test
    public void discardKeepsASeededCellTheSeeds() {
        TermuxAppSharedPreferences prefs = install();
        LookGrid grid = prefs.lookGrid();
        String seeded = null;
        for (String key : grid.gridKeys())
            if (grid.isSeeded(key)) { seeded = key; break; }
        org.junit.Assume.assumeNotNull(seeded);
        SurfaceEditorController global = open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS,
            HOME_PAGE);
        LookGrid.Key cell = LookGrid.parse(seeded);
        Row row = cell.property == LookGrid.Property.FROST ? Row.FROST
            : cell.property == LookGrid.Property.TINT ? Row.TINT : Row.OPACITY;
        slide(global, row, 40);
        assertFalse("the reset took the seeded cell", grid.isSeeded(seeded));
        ReflectionHelpers.callInstanceMethod(global, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertTrue("the seeded cell is back", grid.has(cell));
        assertTrue("and it is the seed's again", grid.isSeeded(seeded));
    }

    private static void assertMargins(TermuxAppSharedPreferences prefs, int margin, String what) {
        assertEquals(what + ": Home pages", margin, prefs.getLayoutMarginDp(true));
        assertEquals(what + ": Workspace pages", margin, prefs.getLayoutMarginDp(false));
        // What the dress shapes each kind's pages by.
        assertEquals(what + ": Home's shape", margin,
            com.termux.app.place.WallShape.read(prefs, true).marginDp);
        assertEquals(what + ": the Workspace's shape", margin,
            com.termux.app.place.WallShape.read(prefs, false).marginDp);
    }

    @Test
    public void discardPutsTheResetValuesBack() {
        TermuxAppSharedPreferences prefs = install();
        dockOpacity(prefs, EditorChips.AppliesTo.GLOBAL, 23);
        LookGrid grid = prefs.lookGrid();
        SurfaceEditorController editor = open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS,
            HOME_PAGE);
        slide(editor, Row.OPACITY, 61);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK,
            (LookGrid.Kind) null));

        ReflectionHelpers.callInstanceMethod(editor, "revertToEntry");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        for (int page : PAGES)
            assertEquals("dock on page " + page, 23,
                grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, page));
    }

    /** A slider moved by a drag of two steps, then let go. */
    private static void slide(SurfaceEditorController editor, Row row, int value) {
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        model.onValue(row, value - 1, true);
        model.onValue(row, value, true);
        model.onReleased(row);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private static void dockOpacity(TermuxAppSharedPreferences prefs, EditorChips.AppliesTo where,
                                    int value) {
        SurfaceEditorController editor = open(prefs, EditorCard.DOCK, PaneWallPage.WIDGETS,
            HOME_PAGE);
        EditorCardSheet.Model model = ReflectionHelpers.getField(editor, "mModel");
        ReflectionHelpers.callInstanceMethod(model, "pickAppliesTo",
            ReflectionHelpers.ClassParameter.from(int.class,
                EditorChips.appliesToOptions(EditorCard.DOCK).indexOf(where)));
        model.onValue(Row.OPACITY, value, false);
        model.onReleased(Row.OPACITY);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
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
                    LookGrid.Resolved resolved = grid.resolve(properties[i], element, page);
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
                                                PaneWallPage place, int pageInFront) {
        return present(harnessFor(prefs, place, pageInFront), card, place);
    }

    /** An editor over a new activity, whose host answers {@code place} and {@code pageInFront}. */
    private static EditorTestHarness harnessFor(TermuxAppSharedPreferences prefs,
                                                PaneWallPage place, int pageInFront) {
        List<WallPages.Page> pages = Arrays.asList(
            new WallPages.Page(HOME_PAGE, WallPages.PageKind.HOME, "Home 1", null),
            new WallPages.Page(TERMINAL_PAGE, WallPages.PageKind.TERMINAL, "Terminal", null));
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
                                                   PaneWallPage place) {
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
