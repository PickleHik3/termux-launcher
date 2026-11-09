package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
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
 * The page label in the app's Dark theme when the editor could read no picture of the wallpaper
 * (a fresh install has no storage permission, so the system's wallpaper cannot be read): the
 * system's description of it decides the ink, so a light wallpaper gets dark writing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, qualifiers = "night")
public class EditorLabelHintTest {
    private static final int DARK = 0xFF1B1B1F;
    private static final int PALE = 0xFFE3E2E6;

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("editor-label-hint",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void aLightWallpaperDescribedGetsDarkWritingInDarkTheme() {
        EditorTestHarness harness = open(0xFFF3EFE6);
        assertEquals(DARK, label(harness).getCurrentTextColor());
    }

    @Test
    public void aDarkWallpaperDescribedGetsPaleWriting() {
        EditorTestHarness harness = open(0xFF202020);
        assertEquals(PALE, label(harness).getCurrentTextColor());
    }

    @Test
    public void nothingDescribedKeepsTheThemesInk() {
        EditorTestHarness harness = open(0);
        int theme = MaterialColors.getColor(harness.themed,
            com.google.android.material.R.attr.colorOnSurface, 0);
        assertTrue("the Dark theme's ink is pale, the case the device showed",
            androidx.core.graphics.ColorUtils.calculateLuminance(theme) > 0.5d);
        assertEquals(theme, label(harness).getCurrentTextColor());
    }

    @Test
    public void reopeningKeepsTheDarkWriting() {
        EditorTestHarness harness = open(0xFFF3EFE6);
        harness.editor.done();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        harness.editor.present(null, PaneWallPage.WIDGETS, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(DARK, label(harness).getCurrentTextColor());
    }

    private EditorTestHarness open(int hint) {
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "editorWallpaperHintColor": return hint;
                case "pageOnScreen": return PaneWallPage.WIDGETS;
                default: return null;
            }
        });
        harness.editor.present(null, PaneWallPage.WIDGETS, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        return harness;
    }

    private static TextView label(EditorTestHarness harness) {
        EditorBar bar = ReflectionHelpers.getField(harness.editor, "mBar");
        assertNotNull("the editor is up", bar);
        return ReflectionHelpers.getField(bar, "mHalfName");
    }
}
