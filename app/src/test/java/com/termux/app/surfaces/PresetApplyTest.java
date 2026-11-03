package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
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
import org.junit.Ignore;
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

/**
 * A Look picked on the Look slider shows: what the Home page, the Workspace page and a Display
 * page resolve to for every glass element, as the renderers read it ({@link ElementGlass}), on a
 * store shaped like a real install after the kind seed (509b634db). The ignored cases show a
 * Look hidden by a cell the precedence reads first (element@kind, element, all-glass@kind, then
 * Base): they wait on whether a Look picked there should clear those cells.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
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

    @After
    public void forget() {
        LookGrid.install(null, null);
        TermuxAppSharedPreferences.setTerminalPageId(-1);
    }

    // ------------------------------------------------------------------------------ the tests

    @Test
    public void customizationLookShowsOnEveryPage() {
        List<String> wrong = new ArrayList<>();
        for (int stop : looks()) {
            TermuxAppSharedPreferences prefs = install();
            tap(open(prefs, EditorCard.APPEARANCE, PaneWallPage.WIDGETS, HOME_PAGE), stop);
            expectEverywhere(prefs, stop, PAGES, "Customization", wrong);
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Ignore("Intent owed (preset-apply 11-03): the 11-02 precedence lets a Home or Workspace card's cell win over the Look")
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
        for (int stop : looks()) {
            TermuxAppSharedPreferences prefs = install();
            tap(open(prefs, EditorCard.WORKSPACE, PaneWallPage.TERMINAL, TERMINAL_PAGE), stop);
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

    @Ignore("Intent owed (preset-apply 11-03): the 11-02 precedence lets a element card's cell win over the Look")
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

    @Ignore("Intent owed (preset-apply 11-03): the 11-02 precedence lets a element card's cell win over the Look")
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
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
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
