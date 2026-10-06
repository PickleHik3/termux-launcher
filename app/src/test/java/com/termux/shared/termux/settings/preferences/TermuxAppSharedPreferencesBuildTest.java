package com.termux.shared.termux.settings.preferences;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * {@link TermuxAppSharedPreferences#build} reuses the package context it made for a caller, per
 * caller, and every handle still reads the one preferences store.
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxAppSharedPreferencesBuildTest {

    @Test
    public void aCallerGetsTheSamePackageContextEveryTime() {
        Context app = ApplicationProvider.getApplicationContext();
        TermuxAppSharedPreferences first = TermuxAppSharedPreferences.build(app);
        TermuxAppSharedPreferences second = TermuxAppSharedPreferences.build(app);
        TermuxAppSharedPreferences exiting = TermuxAppSharedPreferences.build(app, false);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(exiting);
        assertNotSame(first, second);
        assertSame(first.getContext(), second.getContext());
        assertSame(first.getContext(), exiting.getContext());
    }

    @Test
    public void anotherCallerOfTheSameApplicationSharesThePackageContext() {
        Context app = ApplicationProvider.getApplicationContext();
        Context other = new ContextWrapper(app);
        TermuxAppSharedPreferences mine = TermuxAppSharedPreferences.build(app);
        TermuxAppSharedPreferences theirs = TermuxAppSharedPreferences.build(other);
        assertNotNull(mine);
        assertNotNull(theirs);
        assertSame(mine.getContext(), theirs.getContext());
        assertSame(mine.getSharedPreferences(), theirs.getSharedPreferences());
    }

    @Test
    public void aWriteThroughOneHandleIsReadByTheNext() {
        Context app = ApplicationProvider.getApplicationContext();
        TermuxAppSharedPreferences writer = TermuxAppSharedPreferences.build(app);
        assertNotNull(writer);
        writer.getSharedPreferences().edit().putString("build_cache_probe", "seen").commit();
        TermuxAppSharedPreferences reader = TermuxAppSharedPreferences.build(app);
        assertNotNull(reader);
        assertEquals("seen", reader.getSharedPreferences().getString("build_cache_probe", null));
    }
}
