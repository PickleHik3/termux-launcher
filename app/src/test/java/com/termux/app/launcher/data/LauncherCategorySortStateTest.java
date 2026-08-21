package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherCategorySortStateTest {
    private static final String KEY = "app_launcher_category_sort_state_v1";
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
        preferences.edit().remove(KEY).commit();
    }

    @Test public void freshStateHasNotRun() {
        LauncherCategorySortState state = new LauncherCategorySortState(context);
        assertFalse(state.hasRun());
        assertEquals(0L, state.getLastRunEpochMs());
        assertEquals(0, state.getAppCount());
        assertNull(state.getSource());
        assertNull(state.getModelId());
    }

    @Test public void recordRunRoundTripsThroughPreferences() {
        new LauncherCategorySortState(context).recordRun(1755273720000L, 113,
            LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL, "gemma-3n-e2b");

        LauncherCategorySortState reloaded = new LauncherCategorySortState(context);
        assertTrue(reloaded.hasRun());
        assertEquals(1755273720000L, reloaded.getLastRunEpochMs());
        assertEquals(113, reloaded.getAppCount());
        assertEquals(LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL, reloaded.getSource());
        assertEquals("gemma-3n-e2b", reloaded.getModelId());
    }

    @Test public void recordRunKeepsNullModelId() {
        new LauncherCategorySortState(context).recordRun(42L, 7,
            LauncherCategorySortState.SOURCE_PASTED, null);

        LauncherCategorySortState reloaded = new LauncherCategorySortState(context);
        assertTrue(reloaded.hasRun());
        assertEquals(42L, reloaded.getLastRunEpochMs());
        assertEquals(7, reloaded.getAppCount());
        assertEquals(LauncherCategorySortState.SOURCE_PASTED, reloaded.getSource());
        assertNull(reloaded.getModelId());
    }

    @Test public void secondRecordRunOverwritesTheFirst() {
        LauncherCategorySortState state = new LauncherCategorySortState(context);
        state.recordRun(1000L, 50, LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL, "old-model");
        state.recordRun(2000L, 51, LauncherCategorySortState.SOURCE_MANUAL, null);

        assertEquals(2000L, state.getLastRunEpochMs());
        assertEquals(51, state.getAppCount());
        assertEquals(LauncherCategorySortState.SOURCE_MANUAL, state.getSource());
        assertNull(state.getModelId());

        LauncherCategorySortState reloaded = new LauncherCategorySortState(context);
        assertEquals(2000L, reloaded.getLastRunEpochMs());
        assertEquals(51, reloaded.getAppCount());
        assertEquals(LauncherCategorySortState.SOURCE_MANUAL, reloaded.getSource());
        assertNull(reloaded.getModelId());
    }

    @Test public void clearResetsEveryField() {
        LauncherCategorySortState state = new LauncherCategorySortState(context);
        state.recordRun(1755273720000L, 113,
            LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL, "gemma-3n-e2b");
        state.clear();

        assertFalse(state.hasRun());
        assertEquals(0L, state.getLastRunEpochMs());
        assertEquals(0, state.getAppCount());
        assertNull(state.getSource());
        assertNull(state.getModelId());

        LauncherCategorySortState reloaded = new LauncherCategorySortState(context);
        assertFalse(reloaded.hasRun());
        assertNull(reloaded.getSource());
    }

    @Test public void corruptBlobIsTreatedAsAbsent() {
        preferences.edit().putString(KEY, "not-json-at-all}{").commit();

        LauncherCategorySortState state = new LauncherCategorySortState(context);
        assertFalse(state.hasRun());
        assertEquals(0L, state.getLastRunEpochMs());
        assertEquals(0, state.getAppCount());
        assertNull(state.getSource());
        assertNull(state.getModelId());
    }
}
