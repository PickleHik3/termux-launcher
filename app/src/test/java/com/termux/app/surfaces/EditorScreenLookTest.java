package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * Each screen keeps its own Look (editor-redesign §1 Model, §2 Home/Workspace cards): a Look
 * picked on the Workspace card is the Workspace half's alone, it survives the card following the
 * page swipe to Home and back, and Home's Look and glass stay where they were.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class EditorScreenLookTest {

    private static final int SOLID = AppearanceLooks.stopForPresetId("solid");
    private static final int FROST = AppearanceLooks.stopForPresetId("frost");

    private TermuxAppSharedPreferences preferences;
    private PlaceLayoutStore places;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("editor-screen-look",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
        places = new PlaceLayoutStore(preferences);
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        SurfacePresets.applyLook(preferences, preset("frost"));
    }

    @Test
    public void theWorkspacesLookOutlivesASwipeToHomeAndBack() {
        assertTrue(SurfacePresets.wornOn(preferences, preset("frost"), WallHalf.HOME));
        Map<SurfaceProperty, Integer> home = homeBase();

        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        assertEquals(FROST, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);
        assertWorkspaceSolid(editor);
        assertHomeUnchanged(home);

        swipe(editor, WallHalf.HOME);
        assertEquals("the Home card followed the swipe", EditorCard.HOME, editor.shownCard());
        assertEquals("Home's Look reads Home's own", FROST, model(editor).value(Row.LOOK));
        assertHomeUnchanged(home);
        assertTrue(SurfacePresets.wornOn(preferences, preset("solid"), WallHalf.WORKSPACE));

        swipe(editor, WallHalf.WORKSPACE);
        assertEquals(EditorCard.WORKSPACE, editor.shownCard());
        assertWorkspaceSolid(editor);
        assertHomeUnchanged(home);
    }

    @Test
    public void aLookOnHomeIsHomesAlone() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        tapLook(editor, SOLID);
        swipe(editor, WallHalf.HOME);
        tapLook(editor, AppearanceLooks.stopForPresetId("stock"));
        assertTrue(SurfacePresets.wornOn(preferences, preset("stock"), WallHalf.HOME));

        swipe(editor, WallHalf.WORKSPACE);
        assertWorkspaceSolid(editor);
        swipe(editor, WallHalf.HOME);
        assertEquals(AppearanceLooks.stopForPresetId("stock"), model(editor).value(Row.LOOK));
    }

    /**
     * The emulator's case (2026-10-26): both screens on Clear, Solid on the Workspace flips the
     * glass edge the wall shares to 0. Home still reads Clear, and Match Home styling puts the
     * Workspace back on Clear, which it keeps over a swipe round trip.
     */
    @Test
    public void fromClearSolidOnTheWorkspaceLeavesHomeOnClearAndMatchBringsClearBack() {
        int clear = AppearanceLooks.stopForPresetId("minimal");
        SurfacePresets.applyLook(preferences, preset("minimal"));
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        assertEquals(clear, model(editor).value(Row.LOOK));
        tapLook(editor, SOLID);
        assertWorkspaceSolid(editor);

        swipe(editor, WallHalf.HOME);
        assertEquals(EditorCard.HOME, editor.shownCard());
        assertEquals("Home reads its own Clear, not Custom", clear, model(editor).value(Row.LOOK));

        swipe(editor, WallHalf.WORKSPACE);
        assertEquals(EditorCard.WORKSPACE, editor.shownCard());
        assertWorkspaceSolid(editor);
        ReflectionHelpers.callInstanceMethod(editor, "toggleMatch",
            ClassParameter.from(WallHalf.class, WallHalf.WORKSPACE));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("Match Home styling copies Home's Clear", clear,
            model(editor).value(Row.LOOK));

        swipe(editor, WallHalf.HOME);
        assertEquals(clear, model(editor).value(Row.LOOK));
        swipe(editor, WallHalf.WORKSPACE);
        assertEquals("the Workspace keeps Clear over the round trip", clear,
            model(editor).value(Row.LOOK));
        assertTrue(SurfacePresets.wornOn(preferences, preset("minimal"), WallHalf.WORKSPACE));
    }

    @Test
    public void eachScreenCardsLookRowIsItsOwnHalfWhateverTheFrameShows() {
        for (WallHalf frame : WallHalf.values()) {
            assertEquals(WallHalf.HOME, EditorRows.halfFor(EditorCard.HOME, Row.LOOK, frame));
            assertEquals(WallHalf.WORKSPACE,
                EditorRows.halfFor(EditorCard.WORKSPACE, Row.LOOK, frame));
        }
    }

    @Test
    public void matchWorkspaceStylingCopiesTheWorkspacesLookOntoHome() {
        SurfaceEditorController editor = open(EditorCard.WORKSPACE, PaneWallPage.TERMINAL);
        tapLook(editor, SOLID);
        swipe(editor, WallHalf.HOME);
        assertEquals(FROST, model(editor).value(Row.LOOK));

        ReflectionHelpers.callInstanceMethod(editor, "toggleMatch",
            ClassParameter.from(WallHalf.class, WallHalf.HOME));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("Home took the Workspace's Look", SOLID, model(editor).value(Row.LOOK));
        assertTrue(SurfacePresets.wornOn(preferences, preset("solid"), WallHalf.HOME));
        assertWorkspaceSolid(editor);
    }

    // ------------------------------------------------------------------------------- the checks

    private void assertWorkspaceSolid(SurfaceEditorController editor) {
        assertEquals("the Workspace card's Look reads Solid", SOLID, model(editor).value(Row.LOOK));
        assertTrue("the Workspace half wears Solid",
            SurfacePresets.wornOn(preferences, preset("solid"), WallHalf.WORKSPACE));
        assertEquals(TERMUX_APP.SURFACE_MATERIAL_SOLID,
            preferences.getSurfaceMaterial(WallHalf.WORKSPACE));
        assertEquals(92,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
        assertEquals(0,
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
        assertEquals(0,
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE));
    }

    private Map<SurfaceProperty, Integer> homeBase() {
        Map<SurfaceProperty, Integer> out = new EnumMap<>(SurfaceProperty.class);
        for (SurfaceProperty property : new SurfaceProperty[] {SurfaceProperty.OPACITY,
                SurfaceProperty.BLUR, SurfaceProperty.GRAIN, SurfaceProperty.TINT})
            out.put(property, preferences.getSurfaceBaseValue(property, WallHalf.HOME));
        return out;
    }

    private void assertHomeUnchanged(Map<SurfaceProperty, Integer> home) {
        assertEquals("Home's glass is untouched", home, homeBase());
        assertTrue("Home still wears Frost",
            SurfacePresets.wornOn(preferences, preset("frost"), WallHalf.HOME));
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

    private SurfaceEditorController open(EditorCard card, PaneWallPage place) {
        LayoutEditorController layout = layoutEditor();
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "layoutEditor": return layout;
                case "pageOnScreen": return place;
                default: return null;
            }
        });
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(card, harness.editor.shownCard());
        return harness.editor;
    }

    /** A strip dragged to {@code value} and let go, as a finger does it. */
    private static void moveStrip(SurfaceEditorController editor, Row row, int value) {
        model(editor).onValue(row, value / 2, true);
        model(editor).onValue(row, value, true);
        model(editor).onReleased(row);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** The preview swiped to {@code half}: the page swipe's own path. */
    private static void swipe(SurfaceEditorController editor, WallHalf half) {
        ReflectionHelpers.callInstanceMethod(editor, "switchHalf",
            ClassParameter.from(WallHalf.class, half),
            ClassParameter.from(boolean.class, true),
            ClassParameter.from(boolean.class, true));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** A live layout session, so a half's slide runs the place's own restatement too. */
    private LayoutEditorController layoutEditor() {
        Context context = RuntimeEnvironment.getApplication();
        LayoutEditorController.Host host = (LayoutEditorController.Host) Proxy.newProxyInstance(
            LayoutEditorController.Host.class.getClassLoader(),
            new Class<?>[] {LayoutEditorController.Host.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "context": return context;
                    case "places": return places;
                    case "placeOnScreen": return PaneWallPage.TERMINAL;
                    case "placeOrientation": return PlaceOrientation.PORTRAIT;
                    case "extraKeySlots": return new ArrayList<>();
                    case "extraKeyCount":
                    case "measuredDockHeightDp":
                    case "measuredKeyboardHeightDp":
                        return -1;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: break;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == float.class) return 0f;
                return null;
            });
        return new LayoutEditorController(host);
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
