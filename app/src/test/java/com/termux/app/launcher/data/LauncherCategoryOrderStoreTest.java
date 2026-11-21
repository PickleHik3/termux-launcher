package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherCategoryOrderStoreTest {

    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void putRaw(String raw) {
        context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit()
            .putString(LauncherCategoryOrderStore.PREFS_KEY_CATEGORY_ORDER_V1, raw).commit();
    }

    @Test public void nothingStoredReadsEmpty() {
        assertTrue(new LauncherCategoryOrderStore(context).read().isEmpty());
    }

    @Test public void writeThenReadRoundTrips() {
        new LauncherCategoryOrderStore(context).write(Arrays.asList("games", "social"));
        assertEquals(Arrays.asList("games", "social"),
            new LauncherCategoryOrderStore(context).read());
    }

    @Test public void clearReturnsToTheDefault() {
        LauncherCategoryOrderStore store = new LauncherCategoryOrderStore(context);
        store.write(Arrays.asList("games"));
        store.clear();
        assertTrue(store.read().isEmpty());
    }

    @Test public void writingNullOrEmptyClears() {
        LauncherCategoryOrderStore store = new LauncherCategoryOrderStore(context);
        store.write(Arrays.asList("games"));
        store.write(null);
        assertTrue(store.read().isEmpty());
    }

    @Test public void malformedValuesReadEmpty() {
        for (String raw : new String[] {"not json", "{\"a\":1}", "[\"games\"", "   "}) {
            putRaw(raw);
            assertTrue(raw, new LauncherCategoryOrderStore(context).read().isEmpty());
        }
    }

    @Test public void nonStringItemsAreSkipped() {
        putRaw("[\"games\", 3, null, \"\", \"social\"]");
        assertEquals(Arrays.asList("games", "social"),
            new LauncherCategoryOrderStore(context).read());
    }

    @Test public void aWrongTypedValueReadsEmpty() {
        context.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit()
            .putInt(LauncherCategoryOrderStore.PREFS_KEY_CATEGORY_ORDER_V1, 4).commit();
        assertTrue(new LauncherCategoryOrderStore(context).read().isEmpty());
    }
}
