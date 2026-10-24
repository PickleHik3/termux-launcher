package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A theme picked on the Appearance card relaunches the activity into it, and the editor comes
 * back: the same card up, the theme still unsaved (nothing stored until Done), and Undo puts the
 * window back. The relaunch is driven the way a theme change on a phone runs it: the activity
 * that takes the handover is relaunched once more right after its first resume (AppCompat pins
 * the new night mode), before its posted reopen ran.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class ThemeRelaunchTest {

    /** The Theme row's segments: System, Light, Dark. */
    private static final int DARK = 2;

    private TermuxAppSharedPreferences preferences;
    private final List<String> previews = new ArrayList<>();
    private final List<String> commits = new ArrayList<>();

    @Before
    public void setUp() {
        ThemePreview.set(null, null);
        AppearanceRelaunchHandover.resetForTest();
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("theme-relaunch",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @After
    public void tearDown() {
        ThemePreview.set(null, null);
        AppearanceRelaunchHandover.resetForTest();
    }

    @Test
    public void theEditorComesBackOverTheRelaunchWithTheThemeStillUnsaved() {
        Side before = openOnAppearance();
        String stored = storedTheme(before);
        int storedIndex = model(before).value(Row.THEME);

        model(before).onValue(Row.THEME, DARK, false);

        assertEquals("the window is asked to wear it", List.of(dark()), previews);
        assertTrue("nothing stored", commits.isEmpty());
        assertEquals(stored, storedTheme(before));
        assertTrue(isDirty(before));

        Side after = relaunchTwice(before);

        assertTrue(after.surface.isOpen());
        assertEquals("the same card is up", EditorCard.APPEARANCE, after.harness.editor.shownCard());
        assertEquals("the pick still shows", DARK, model(after).value(Row.THEME));
        assertEquals("and is still not stored", stored, storedTheme(after));
        assertTrue(commits.isEmpty());
        assertTrue("still unsaved", isDirty(after));

        after.harness.editor.undo();
        settle();

        assertNull(ThemePreview.pending());
        assertEquals("Undo puts the window back on the stored theme",
            ThemePreview.orSystem(stored), previews.get(previews.size() - 1));
        assertEquals(storedIndex, model(after).value(Row.THEME));
        assertTrue("Undo stores nothing", commits.isEmpty());
        assertFalse(isDirty(after));
    }

    @Test
    public void doneStoresThePreviewedTheme() {
        Side before = openOnAppearance();
        model(before).onValue(Row.THEME, DARK, false);
        Side after = relaunchTwice(before);

        after.harness.editor.done();
        settle();

        assertEquals(List.of(dark()), commits);
        assertNull(ThemePreview.pending());
        assertFalse(after.surface.isOpen());
    }

    @Test
    public void aHandoverTakenButNotYetOpenedIsPassedOnNotDropped() {
        AppearanceSurfaceController.Relaunch carried = new AppearanceSurfaceController.Relaunch(
            new AppearanceReturnState.Builder(AppearanceSurfaceController.PageId.EDITOR)
                .card(EditorCard.APPEARANCE).build(0L), null);
        Bundle first = new Bundle();
        new AppearanceRelaunchHandover().save(carried, true, first);

        AppearanceRelaunchHandover resumed = new AppearanceRelaunchHandover();
        resumed.take(first);
        assertEquals(carried, resumed.pending());
        // Relaunched again with its surface still closed: the reopen it posted never ran.
        Bundle second = new Bundle();
        resumed.save(null, false, second);

        AppearanceRelaunchHandover next = new AppearanceRelaunchHandover();
        next.take(second);
        assertEquals(carried, next.pending());
        assertTrue("opened from Settings, as it was", next.pendingFromSettings());
        assertTrue(next.claim(carried));
        assertFalse("claimed once", next.claim(carried));
        assertNull(next.pending());
    }

    // ------------------------------------------------------------------------------- the drive

    /** One activity's editor and the surface over it. */
    private static final class Side {
        final EditorTestHarness harness;
        final AppearanceSurfaceController surface;

        Side(EditorTestHarness harness, AppearanceSurfaceController surface) {
            this.harness = harness;
            this.surface = surface;
        }
    }

    private Side newSide() {
        EditorTestHarness harness = new EditorTestHarness(preferences, (method, args) -> {
            switch (method) {
                case "previewTheme":
                    previews.add((String) args[0]);
                    return null;
                case "commitTheme":
                    commits.add((String) args[0]);
                    return null;
                default:
                    return null;
            }
        });
        return new Side(harness, new AppearanceSurfaceController(new Host(harness), harness.editor));
    }

    private Side openOnAppearance() {
        Side side = newSide();
        side.surface.open(AppearanceSurfaceController.PageId.EDITOR, EditorCard.APPEARANCE, null);
        settle();
        assertEquals(EditorCard.APPEARANCE, side.harness.editor.shownCard());
        return side;
    }

    /**
     * The theme relaunch as a phone runs it: the old activity saves, the next takes the handover
     * and is relaunched again before its reopen ran, and the third reopens.
     */
    private Side relaunchTwice(Side before) {
        Bundle first = new Bundle();
        new AppearanceRelaunchHandover().save(before.surface.carry(), false, first);

        AppearanceRelaunchHandover second = new AppearanceRelaunchHandover();
        second.take(first);
        Bundle again = new Bundle();
        second.save(null, false, again);

        AppearanceRelaunchHandover third = new AppearanceRelaunchHandover();
        third.take(again);
        AppearanceSurfaceController.Relaunch carried = third.pending();
        assertNotNull("the handover survived the second relaunch", carried);
        assertTrue(third.claim(carried));

        Side after = newSide();
        assertTrue(after.surface.reopen(carried));
        settle();
        return after;
    }

    private static String dark() {
        return com.termux.shared.termux.settings.properties.TermuxPropertyConstants
            .IVALUE_NIGHT_MODE_TRUE;
    }

    @Nullable
    private static String storedTheme(Side side) {
        return EditorSettingsSnapshot.settings(side.harness.themed).getString("theme_mode");
    }

    private static EditorCardSheet.Model model(Side side) {
        return ReflectionHelpers.getField(side.harness.editor, "mModel");
    }

    private static boolean isDirty(Side side) {
        return ReflectionHelpers.callInstanceMethod(side.harness.editor, "isDirty");
    }

    private static void settle() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    /** The surface's host over the harness's activity. */
    private static final class Host implements AppearanceSurfaceController.Host {
        private final EditorTestHarness mHarness;

        Host(EditorTestHarness harness) {
            mHarness = harness;
        }

        @NonNull @Override public Context context() {
            return mHarness.themed;
        }

        @Nullable @Override public ViewGroup content() {
            return mHarness.activity.findViewById(android.R.id.content);
        }

        @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                             @Nullable String centredSlot,
                                             @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
        }

        @Override public void onClosed() {}
    }
}
