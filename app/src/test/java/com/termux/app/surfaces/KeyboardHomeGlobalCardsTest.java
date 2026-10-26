package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.launcher.widget.HomeIconSize;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/**
 * The Keyboard, Home and Global cards through the controller (editor-redesign §2, §6): the
 * opacity link moves both opacities and keeps their difference, Home's icon size reads an old
 * percentage as its nearest preset and writes a preset on the same key, Global's glass material
 * stands at any Look, and the sections carry their notes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class KeyboardHomeGlobalCardsTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("keyboard-home-global-cards",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        SurfacePresets.applyLook(preferences, SurfacePresets.presets(preferences).get(1));
    }

    @Test
    public void theOpacityLinkMovesBothOpacitiesAndKeepsTheirDifference() {
        SurfaceEditorController editor = open(EditorCard.KEYBOARD, null);
        EditorCardSheet.Model model = model(editor);
        model.onValue(Row.KEY_OPACITY, 70, false);
        model.onReleased(Row.KEY_OPACITY);
        model.onValue(Row.OPACITY, 30, false);
        model.onReleased(Row.OPACITY);
        assertEquals("unlinked, the keys stay", 70, model.value(Row.KEY_OPACITY));
        assertEquals(0, model.value(Row.OPACITY_LINK));

        model.onValue(Row.OPACITY_LINK, 1, false);
        assertTrue(preferences.isInAppKeyboardOpacityLinked());
        assertEquals(1, model.value(Row.OPACITY_LINK));

        model.onValue(Row.KEY_OPACITY, 75, true);
        model.onValue(Row.KEY_OPACITY, 80, true);
        model.onValue(Row.KEY_OPACITY, 80, false);
        model.onReleased(Row.KEY_OPACITY);
        assertEquals(80, model.value(Row.KEY_OPACITY));
        assertEquals("the background moved as far", 40, model.value(Row.OPACITY));

        model.onValue(Row.OPACITY, 35, false);
        model.onReleased(Row.OPACITY);
        assertEquals("and the keys follow the background", 75, model.value(Row.KEY_OPACITY));
        assertEquals(40, model.value(Row.KEY_OPACITY) - model.value(Row.OPACITY));

        model.onValue(Row.OPACITY_LINK, 0, false);
        assertFalse(preferences.isInAppKeyboardOpacityLinked());
        model.onValue(Row.OPACITY, 20, false);
        model.onReleased(Row.OPACITY);
        assertEquals("unlinked again, the keys stay", 75, model.value(Row.KEY_OPACITY));
    }

    @Test
    public void theKeyboardCardNamesItsBackgroundOpacityAndHasNoSizeLink() {
        SurfaceEditorController editor = open(EditorCard.KEYBOARD, null);
        EditorCardSheet.Model model = model(editor);
        assertEquals(editor.shownCard(), EditorCard.KEYBOARD);
        assertEquals(RuntimeEnvironment.getApplication().getString(
            R.string.editor_row_background_opacity), String.valueOf(looks(editor).name(Row.OPACITY)));
        assertEquals(com.termux.app.surfaces.rows.EditorGlyphs.KB_BACKGROUND_OPACITY,
            looks(editor).glyph(Row.OPACITY));
        assertEquals(3, model.chipIcons(Row.FORM).length);
    }

    @Test
    public void homesIconSizeReadsAnOldPercentageAsItsNearestPresetAndWritesAPreset() {
        Context context = RuntimeEnvironment.getApplication();
        EditorTestHarness harness = new EditorTestHarness(preferences, null);
        AppearanceSettingsStore.putString(harness.themed, HomeSettings.KEY_ICON_SCALE, "110");
        harness.editor.present(EditorCard.HOME, PaneWallPage.WIDGETS, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        EditorCardSheet.Model model = model(harness.editor);
        assertEquals(EditorCard.HOME, harness.editor.shownCard());
        assertEquals(HomeIconSize.DEFAULT.ordinal(), model.value(Row.HOME_ICON_SIZE));

        model.onValue(Row.HOME_ICON_SIZE, HomeIconSize.LARGE.ordinal(), false);
        assertEquals("large", AppearanceSettingsStore.getString(harness.themed,
            HomeSettings.KEY_ICON_SCALE, null));
        assertEquals(HomeIconSize.LARGE.ordinal(), model.value(Row.HOME_ICON_SIZE));
        assertEquals(3, model.choices(Row.HOME_ICON_SIZE).length);
        assertEquals(context.getString(R.string.editor_note_home_only),
            String.valueOf(model.summary(Row.SECTION_LOOK)));
        assertEquals(context.getString(R.string.editor_note_home_glass),
            String.valueOf(model.summary(Row.SECTION_GLASS)));
        assertEquals(context.getString(R.string.editor_chip_edit_pages),
            String.valueOf(looks(harness.editor).name(Row.LINK_PAGES)));
    }

    @Test
    public void globalsGlassMaterialStandsAtAnyLookAndItsTintColourIsThreeSwatches() {
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        EditorCardSheet.Model model = model(editor);
        assertFalse("the Workspace is on a Look, not Custom",
            AppearanceLooks.isCustomStop(model.value(Row.LOOK)));
        assertTrue(model.shown(Row.VIBRANCY));
        assertTrue(model.shown(Row.GLASS_EDGE));
        assertTrue(model.shown(Row.DEPTH));
        assertEquals(3, looks(editor).swatches(Row.TINT_HUE).length);
        assertEquals(3, model.choices(Row.TINT_HUE).length);
        model.onValue(Row.TINT_HUE, 2, false);
        assertEquals("tertiary", preferences.getSurfaceGlassTintHue());
        assertEquals(2, model.value(Row.TINT_HUE));
        assertEquals(RuntimeEnvironment.getApplication().getString(
            R.string.editor_note_both_screens),
            String.valueOf(model.summary(Row.SECTION_GLASS_MATERIAL)));
        assertNull("the palette's sections carry no screen note",
            model.summary(Row.SECTION_COLOUR));
    }

    @Test
    public void showWallpaperIsTheWallpaperRowsSwitch() throws ClassNotFoundException {
        // Settings' style store is a process-wide singleton: one an earlier test built reads the
        // preferences of that test's application, while the wallpaper switch writes through this
        // one's, so the row read back what the earlier test left (full suite only). Build it
        // afresh for this application.
        ReflectionHelpers.setStaticField(Class.forName(
            "com.termux.app.fragments.settings.termux.TermuxStylePreferencesDataStore"),
            "mInstance", null);
        SurfaceEditorController editor = open(EditorCard.APPEARANCE, null);
        EditorCardSheet.Model model = model(editor);
        model.onValue(Row.SHOW_WALLPAPER, 1, false);
        assertEquals(1, model.value(Row.SHOW_WALLPAPER));
        assertEquals("Change wallpaper never reads on", 0, model.value(Row.CHANGE_WALLPAPER));
        model.onValue(Row.SHOW_WALLPAPER, 0, false);
        assertEquals(0, model.value(Row.SHOW_WALLPAPER));
    }

    // ------------------------------------------------------------------------------- the drive

    private SurfaceEditorController open(EditorCard card, PaneWallPage place) {
        EditorTestHarness harness = new EditorTestHarness(preferences, null);
        harness.editor.present(card, place, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return harness.editor;
    }

    private static EditorCardSheet.Model model(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }

    /** What the card says of its rows beyond their values, as its {@link CardBody} reads it. */
    private static CardBody.Looks looks(SurfaceEditorController editor) {
        return ReflectionHelpers.getField(editor, "mModel");
    }
}
