package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.chrome.ElementGlass;
import com.termux.app.chrome.FancierGlassPolicy;
import com.termux.app.page.PageKind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.LookGrid;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import android.app.Application;

/**
 * Every glass card's Opacity, Blur, Grain and Depth, moved through the editor's model, reach
 * exactly the surfaces the card names, on every page alike (ADR 0031), as the renderers read
 * them ({@link ElementGlass}), on a fresh install. Before the fix the Terminal card's Opacity
 * moved every surface.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceControlsReachTest {

    private static final int HOME_PAGE = 0, TERMINAL_PAGE = 1, DISPLAY_PAGE = 2;
    private static final int[] PAGES = {HOME_PAGE, TERMINAL_PAGE, DISPLAY_PAGE};
    private static final Row[] ROWS = {Row.OPACITY, Row.FROST, Row.GRAIN, Row.DEPTH};

    private SharedPreferences mStore;

    private TermuxAppSharedPreferences fresh() {
        Context context = RuntimeEnvironment.getApplication();
        mStore = context.getSharedPreferences("appearance-controls-reach", Context.MODE_PRIVATE);
        mStore.edit().clear().commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, mStore, null);
        // A fresh install's start-up: a shipped Look.
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        return prefs;
    }

    /**
     * The same fresh install again, in the same store and through the same preferences object:
     * every key cleared, then the start-up's shipped Look. For a cell that walks
     * on an editor that was closed first, so the cell starts on what {@link #fresh} gives.
     */
    private void backToFresh(TermuxAppSharedPreferences prefs) {
        mStore.edit().clear().commit();
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
    }


    // ---------------------------------------------------------------------------- the matrix

    @Test
    public void everyGlassRowReachesExactlyItsCardsSurfaces() {
        List<String> wrong = new ArrayList<>();
        EditorRows.Offers offers = new EditorRows.Offers(false, true, true, false, true);
        for (EditorCard card : EditorCard.values()) {
            // One editor per card. Each cell ends its session, puts the install back to a fresh
            // one and opens the card again, so every cell starts as a fresh install with the
            // card just opened, as it did on an editor of its own.
            TermuxAppSharedPreferences prefs = fresh();
            EditorTestHarness harness = null;
            for (Row row : ROWS) {
                    if (!has(EditorRows.rows(card, offers), row)) continue;
                    if (harness == null) {
                        harness = new EditorTestHarness(prefs, null);
                    } else {
                        harness.editor.endSession();
                        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
                        assertFalse("the editor is closed before the next cell",
                            harness.editor.isOpen());
                        backToFresh(prefs);
                    }
                    Set<String> moved = move(harness, prefs, card, row);
                    Set<String> expected = expected(card);
                    if (!moved.equals(expected))
                        wrong.add(card + " " + row + ": moved " + moved
                            + ", expected " + expected);
                }
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void customizationVibrancyIsSetForAllGlass() {
        TermuxAppSharedPreferences prefs = fresh();
        EditorCardSheet.Model model = open(prefs, EditorCard.APPEARANCE);
        int to = prefs.getSurfaceGlassVibrancy() == 120 ? 90 : 120;
        model.onValue(Row.VIBRANCY, to, false);
        model.onReleased(Row.VIBRANCY);
        assertEquals(to, prefs.getSurfaceGlassVibrancy());
    }

    // ------------------------------------------------------------------- what stays the user's

    // ------------------------------------------------------------- Depth and Edge, when out

    @Test
    public void depthAndEdgeSayWhyTheyAreOut() {
        assertEquals(FancierGlassPolicy.Off.PHONE, FancierGlassPolicy.off(29, true, true, false));
        assertEquals(FancierGlassPolicy.Off.LAZY, FancierGlassPolicy.off(33, true, true, true));
        assertEquals(FancierGlassPolicy.Off.SWITCH, FancierGlassPolicy.off(33, false, true, false));
        assertEquals(FancierGlassPolicy.Off.WALLPAPER,
            FancierGlassPolicy.off(33, true, false, false));
        assertEquals(FancierGlassPolicy.Off.NONE, FancierGlassPolicy.off(33, true, true, false));

        assertEquals(R.string.editor_row_not_on_this_phone,
            SurfaceEditorController.refractionOffText(FancierGlassPolicy.Off.PHONE));
        assertEquals(R.string.editor_row_refraction_off_switch,
            SurfaceEditorController.refractionOffText(FancierGlassPolicy.Off.SWITCH));
        assertEquals(R.string.editor_row_refraction_off_lazy,
            SurfaceEditorController.refractionOffText(FancierGlassPolicy.Off.LAZY));
        assertEquals(R.string.editor_row_refraction_off_wallpaper,
            SurfaceEditorController.refractionOffText(FancierGlassPolicy.Off.WALLPAPER));
    }

    @Test
    public void everyGlassCardLinksToTheSettingOnlyWhileOneIsTheReason() {
        EditorRows.Offers fixable = new EditorRows.Offers(false, true, true, false, true);
        EditorRows.Offers phone = new EditorRows.Offers(false, true, true, false, false);
        for (EditorCard card : EditorCard.values()) {
            boolean hasDepth = has(EditorRows.rows(card, phone), Row.DEPTH);
            assertFalse(card + " never links for the phone",
                has(EditorRows.rows(card, phone), Row.LINK_GLASS_REFRACTION));
            assertEquals(card + " links under Depth", hasDepth,
                has(EditorRows.rows(card, fixable), Row.LINK_GLASS_REFRACTION));
        }
    }

    // -------------------------------------------------------------------------------- helpers

    private static boolean has(List<EditorRows.Spec> rows, Row row) {
        for (EditorRows.Spec spec : rows) if (spec.row == row) return true;
        return false;
    }

    private EditorCardSheet.Model open(TermuxAppSharedPreferences prefs, EditorCard card) {
        return open(new EditorTestHarness(prefs, null), card);
    }

    /** The card opened on {@code harness}'s editor; its model. */
    private EditorCardSheet.Model open(EditorTestHarness harness, EditorCard card) {
        PageKind place = card == EditorCard.HOME ? PageKind.HOME : null;
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return ReflectionHelpers.getField(harness.editor, "mModel");
    }

    /** Moves {@code row} on the card and answers every element@page whose renderer read moved. */
    private Set<String> move(EditorTestHarness harness, TermuxAppSharedPreferences prefs,
                             EditorCard card, Row row) {
        EditorCardSheet.Model model = open(harness, card);
        LookGrid.Element[] elements = LookGrid.Element.values();
        int[][] before = new int[elements.length][PAGES.length];
        for (int i = 0; i < elements.length; i++)
            for (int j = 0; j < PAGES.length; j++)
                before[i][j] = read(prefs, row, elements[i], PAGES[j]);
        model.onValue(row, target(row, model.value(row)), false);
        model.onReleased(row);
        Set<String> moved = new TreeSet<>();
        for (int i = 0; i < elements.length; i++)
            for (int j = 0; j < PAGES.length; j++)
                if (read(prefs, row, elements[i], PAGES[j]) != before[i][j])
                    moved.add(name(elements[i], PAGES[j]));
        return moved;
    }

    /** Every element@page the card speaks for: its element, or every one, on every page. */
    private static Set<String> expected(EditorCard card) {
        Set<String> out = new TreeSet<>();
        EditorChips chips = EditorChips.openFor(card);
        for (LookGrid.Element element : LookGrid.Element.values()) {
            if (chips.element != null && chips.element != element) continue;
            for (int page : PAGES) out.add(name(element, page));
        }
        return out;
    }

    private static LookGrid.Kind kindOf(int page) {
        return page == HOME_PAGE ? LookGrid.Kind.HOME
            : page == TERMINAL_PAGE ? LookGrid.Kind.TERMINAL : LookGrid.Kind.DISPLAY;
    }

    private static String name(LookGrid.Element element, int page) {
        return element.key + "@" + kindOf(page).key;
    }

    /** What a renderer reads for {@code row} on {@code element} at {@code page}. */
    private static int read(TermuxAppSharedPreferences prefs, Row row, LookGrid.Element element,
                            int page) {
        LookGrid grid = prefs.lookGrid();
        ElementGlass glass = ElementGlass.resolve(grid, element, page);
        switch (row) {
            case OPACITY: return glass.opacityPercent;
            case FROST: return glass.blurDp;
            case GRAIN: return glass.grainPercent;
            case DEPTH: return grid.value(LookGrid.Property.DEPTH, element);
            default: throw new IllegalArgumentException(row.name());
        }
    }

    private static int target(Row row, int was) {
        switch (row) {
            case OPACITY: return was == 37 ? 63 : 37;
            case FROST: return was == 2 ? 5 : 2;
            case GRAIN: return was == 17 ? 43 : 17;
            case DEPTH: return was == 30 ? 80 : 30;
            default: return was;
        }
    }
}
