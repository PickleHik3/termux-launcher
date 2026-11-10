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
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGridKindSeed;
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
 * exactly the surfaces the card and its Applies to name, as the renderers read them
 * ({@link ElementGlass}), on a fresh install after the start-up kind seed. Before the fix the
 * Terminal card's Opacity moved every surface, and the keyboard's seeded Home and Display looks
 * hid Customization, Home, Workspace and Keyboard › Global.
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
        LookGrid.install(page -> page == HOME_PAGE ? LookGrid.Kind.HOME
                : page == TERMINAL_PAGE ? LookGrid.Kind.TERMINAL
                : page == DISPLAY_PAGE ? LookGrid.Kind.DISPLAY : null,
            SurfacePresets.SHIPPED_BY_KIND);
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, mStore, null);
        // A fresh install's start-up: a shipped Look, then the kind seed.
        SurfacePresets.applyLook(prefs, SurfacePresets.presets(prefs).get(1));
        LookGridKindSeed.run(new LookGrid.PrefsStore(mStore), SurfacePresets.SHIPPED_BY_KIND);
        return prefs;
    }

    @After public void resetLookGridPageKinds() { LookGrid.install(null, null); }

    // ---------------------------------------------------------------------------- the matrix

    @Test
    public void everyGlassRowReachesExactlyItsCardsSurfaces() {
        List<String> wrong = new ArrayList<>();
        for (EditorCard card : EditorCard.values()) {
            List<EditorChips.AppliesTo> scopes = new ArrayList<>(EditorChips.appliesToOptions(card));
            if (scopes.isEmpty()) scopes.add(null);
            for (EditorChips.AppliesTo scope : scopes)
                for (Row row : ROWS) {
                    TermuxAppSharedPreferences prefs = fresh();
                    Set<String> moved = move(prefs, card, scope, row);
                    Set<String> expected = expected(card, scope);
                    if (!moved.equals(expected))
                        wrong.add(card + " " + scope + " " + row + ": moved " + moved
                            + ", expected " + expected);
                }
        }
        assertTrue(String.join("\n", wrong), wrong.isEmpty());
    }

    @Test
    public void customizationVibrancyIsSetForAllGlass() {
        TermuxAppSharedPreferences prefs = fresh();
        EditorCardSheet.Model model = open(prefs, EditorCard.APPEARANCE, null);
        int to = prefs.getSurfaceGlassVibrancy() == 120 ? 90 : 120;
        model.onValue(Row.VIBRANCY, to, false);
        model.onReleased(Row.VIBRANCY);
        assertEquals(to, prefs.getSurfaceGlassVibrancy());
    }

    // ------------------------------------------------------------------- what stays the user's

    @Test
    public void customizationResetsAKeyboardHomeValueTheUserSet() {
        TermuxAppSharedPreferences prefs = fresh();
        EditorCardSheet.Model keyboard = open(prefs, EditorCard.KEYBOARD, EditorChips.AppliesTo.HOME);
        keyboard.onValue(Row.OPACITY, 23, false);
        keyboard.onReleased(Row.OPACITY);
        int home = read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, HOME_PAGE);

        EditorCardSheet.Model customization = open(prefs, EditorCard.APPEARANCE, null);
        customization.onValue(Row.OPACITY, 71, false);
        customization.onReleased(Row.OPACITY);

        // Last change wins (developer, 2026-11-04): the Global move resets the keyboard's own.
        assertEquals("the user's Home value gave way to Global",
            read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, TERMINAL_PAGE),
            read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, HOME_PAGE));
        org.junit.Assert.assertNotEquals("it was the user's own before", home,
            read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, HOME_PAGE));
        assertEquals("the seeded Display value gave way",
            read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, TERMINAL_PAGE),
            read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, DISPLAY_PAGE));
    }

    @Test
    public void keyboardGlobalReplacesTheKeyboardsOwnPageValues() {
        TermuxAppSharedPreferences prefs = fresh();
        EditorCardSheet.Model keyboard = open(prefs, EditorCard.KEYBOARD, EditorChips.AppliesTo.HOME);
        keyboard.onValue(Row.OPACITY, 23, false);
        keyboard.onReleased(Row.OPACITY);

        EditorCardSheet.Model global = open(prefs, EditorCard.KEYBOARD, EditorChips.AppliesTo.GLOBAL);
        global.onValue(Row.OPACITY, 66, false);
        global.onReleased(Row.OPACITY);

        int expected = read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, TERMINAL_PAGE);
        for (int page : PAGES)
            assertEquals("Global is everywhere, page " + page, expected,
                read(prefs, Row.OPACITY, LookGrid.Element.KEYBOARD, page));
    }

    @Test
    public void anInstallSeededEarlierListsItsUntouchedSeededCells() {
        fresh();
        LookGrid.PrefsStore store = new LookGrid.PrefsStore(mStore);
        LookGrid grid = new LookGrid(store, null, null);
        String home = LookGrid.key(LookGrid.Property.OPACITY, LookGrid.Element.KEYBOARD,
            LookGrid.Kind.HOME);
        String display = LookGrid.key(LookGrid.Property.OPACITY, LookGrid.Element.KEYBOARD,
            LookGrid.Kind.DISPLAY);
        assertTrue("a fresh seed lists its cells", grid.isSeeded(home));

        // Before the list: the seed ran, no list, and the user moved the Home cell since.
        mStore.edit().remove(LookGrid.KEY_SEEDED_CELLS).remove(LookGridKindSeed.KEY_MARKED)
            .putInt(home, mStore.getInt(home, 0) == 11 ? 12 : 11).commit();
        LookGridKindSeed.run(store, SurfacePresets.SHIPPED_BY_KIND);

        assertFalse("the moved cell is the user's", grid.isSeeded(home));
        assertTrue("the untouched cell is listed", grid.isSeeded(display));
    }

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

    private EditorCardSheet.Model open(TermuxAppSharedPreferences prefs, EditorCard card,
                                       EditorChips.AppliesTo scope) {
        EditorTestHarness harness = new EditorTestHarness(prefs, null);
        PaneWallPage place = card == EditorCard.WIDGETS || card == EditorCard.HOME
            ? PaneWallPage.WIDGETS : null;
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        EditorCardSheet.Model model = ReflectionHelpers.getField(harness.editor, "mModel");
        if (scope != null) {
            ReflectionHelpers.callInstanceMethod(model, "pickAppliesTo",
                ReflectionHelpers.ClassParameter.from(int.class,
                    EditorChips.appliesToOptions(card).indexOf(scope)));
        }
        return model;
    }

    /** Moves {@code row} on the card and answers every element@kind whose renderer read moved. */
    private Set<String> move(TermuxAppSharedPreferences prefs, EditorCard card,
                             EditorChips.AppliesTo scope, Row row) {
        EditorCardSheet.Model model = open(prefs, card, scope);
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

    /** Every element@kind the card at {@code scope} speaks for. */
    private static Set<String> expected(EditorCard card, EditorChips.AppliesTo scope) {
        Set<String> out = new TreeSet<>();
        EditorChips chips = EditorChips.openFor(card, scope);
        List<LookGrid.Kind> kinds = chips.appliesTo.kinds.isEmpty()
            ? java.util.Arrays.asList(LookGrid.Kind.values()) : chips.appliesTo.kinds;
        for (LookGrid.Element element : LookGrid.Element.values()) {
            if (chips.element != null && chips.element != element) continue;
            for (int page : PAGES)
                if (kinds.contains(kindOf(page))) out.add(name(element, page));
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
            case DEPTH: return grid.value(LookGrid.Property.DEPTH, element, page);
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
