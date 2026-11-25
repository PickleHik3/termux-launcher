package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.shared.settings.preferences.PreferenceDraft;
import com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsApplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The Appearance editor saves through the Settings registry over its draft: the declaration
 * clamps, nothing is stored until Done, Done refreshes only what the draft changed, and Discard
 * writes nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorRegistryTest {

    private static final float DENSITY = 2f;
    private static final String FONT_KEY = "fontsize";

    private SharedPreferences mStore;
    private PreferenceDraft mDraft;
    private EditorRegistry mRegistry;
    private final List<SettingRefresh> mRefreshed = new ArrayList<>();
    private SettingsApplier mApplier;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mStore = context.getSharedPreferences("editor-registry-test", Context.MODE_PRIVATE);
        mStore.edit().clear().commit();
        mDraft = PreferenceDraft.begin(mStore);
        mRegistry = new EditorRegistry(mDraft, mStore, DENSITY, "");
        mApplier = new SettingsApplier(new SettingsApplier.Host() {
            @Override public boolean isLauncherResumed() {
                return true;
            }

            @Override public void run(@NonNull SettingRefresh refresh) {
                mRefreshed.add(refresh);
            }
        });
    }

    @After
    public void tearDown() {
        PreferenceDraft.discardCurrent();
    }

    private SettingDeclaration fontDeclaration() {
        SettingDeclaration declaration = mRegistry.declaration(FontSizeSettingsAdapter.FONT_SIZE);
        assertNotNull(declaration);
        return declaration;
    }

    @Test
    public void theFontSizeRangeIsTheDeclarations() {
        FontSizeSettingsAdapter.Range range = FontSizeSettingsAdapter.range(DENSITY);
        assertEquals(range.min, fontDeclaration().min);
        assertEquals(range.max, fontDeclaration().max);
        assertEquals(range.defaultSize, mRegistry.getInt(FontSizeSettingsAdapter.FONT_SIZE, -1));
    }

    @Test
    public void aValueOutsideTheRangeIsClampedIntoTheDraftAndNotStored() {
        SettingDeclaration declaration = fontDeclaration();
        assertTrue(mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, 100000));
        assertEquals(declaration.max, mRegistry.getInt(FontSizeSettingsAdapter.FONT_SIZE, -1));
        assertTrue(mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, 1));
        assertEquals(declaration.min, mRegistry.getInt(FontSizeSettingsAdapter.FONT_SIZE, -1));
        assertFalse(mStore.contains(FONT_KEY));
        assertTrue(mDraft.isDirty());
    }

    @Test
    public void settingTheValueAlreadyHeldMovesNothing() {
        int size = fontDeclaration().min + 4;
        assertTrue(mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, size));
        assertFalse(mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, size));
    }

    @Test
    public void doneRefreshesOnlyTheSettingsTheDraftChanged() {
        mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, fontDeclaration().min + 6);
        // A declared setting a listener refreshes, an undeclared key, and a declared key whose
        // held value equals the stored one: none of them asks the applier for anything.
        mDraft.edit().putBoolean("terminal_screen_events", false)
            .putString("some_undeclared_key", "x")
            .putBoolean("status_widget_media", true).apply();
        mStore.edit().putBoolean("status_widget_media", true).commit();
        mDraft.edit().putString("terminal_retro_effect", "crt").apply();

        mRegistry.commit(mDraft, mApplier);

        assertEquals(2, mRefreshed.size());
        assertTrue(mRefreshed.contains(SettingRefresh.FONT_SIZE));
        assertTrue(mRefreshed.contains(SettingRefresh.RESTYLE));
        assertEquals("crt", mStore.getString("terminal_retro_effect", null));
        assertEquals("x", mStore.getString("some_undeclared_key", null));
        assertTrue(mStore.contains(FONT_KEY));
    }

    @Test
    public void doneWithNothingChangedRefreshesNothing() {
        mRegistry.commit(mDraft, mApplier);
        assertTrue(mRefreshed.isEmpty());
    }

    @Test
    public void discardWritesNothingAndRefreshesNothing() {
        mRegistry.setClamped(FontSizeSettingsAdapter.FONT_SIZE, fontDeclaration().min + 6);
        mDraft.edit().putString("terminal_retro_effect", "crt").apply();

        mDraft.discard();

        assertTrue(mStore.getAll().isEmpty());
        assertTrue(mRefreshed.isEmpty());
        assertNull(mStore.getString("terminal_retro_effect", null));
    }
}
