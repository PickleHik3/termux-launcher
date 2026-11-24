package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.preference.PreferenceDataStore;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A migrated row saves through the registry; every other key falls through to the old store. */
public class RegistryPreferenceDataStoreTest {

    private final InMemorySettingStore preferences = new InMemorySettingStore();
    private final InMemorySettingStore properties = new InMemorySettingStore();
    private final List<SettingRefresh> refreshed = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final Map<String, String> fallbackWrites = new HashMap<>();

    private final PreferenceDataStore fallback = new PreferenceDataStore() {
        @Override public void putString(String key, String value) { fallbackWrites.put(key, value); }
        @Override public String getString(String key, String defValue) {
            return fallbackWrites.containsKey(key) ? fallbackWrites.get(key) : defValue;
        }
        @Override public void putInt(String key, int value) { fallbackWrites.put(key, "int " + value); }
        @Override public int getInt(String key, int defValue) { return 77; }
    };

    private RegistryPreferenceDataStore store(boolean launcherResumed) {
        SettingStore.Stores stores = which ->
            which == SettingDeclaration.Store.PROPERTIES ? properties : preferences;
        SettingsRegistry registry = new SettingsRegistry(stores);
        SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
            @Override public boolean isLauncherResumed() { return launcherResumed; }
            @Override public void run(SettingRefresh refresh) { refreshed.add(refresh); }
        });
        Map<String, String> rows = new HashMap<>();
        rows.put("terminal_contrast_level", "terminal.contrast");
        rows.put("text_size_percent", "text.size");
        return new RegistryPreferenceDataStore(fallback, registry, applier, rows, messages::add);
    }

    @Test
    public void aMigratedChoiceSavesThroughTheRegistryAndRunsItsRefresh() {
        RegistryPreferenceDataStore store = store(true);
        store.putString("terminal_contrast_level", "harder");
        assertEquals("harder", store.getString("terminal_contrast_level", "x"));
        assertEquals(Collections.singletonList(SettingRefresh.RESTYLE), refreshed);
        assertTrue(fallbackWrites.isEmpty());
    }

    @Test
    public void aMigratedNumberSavesThroughTheRegistry() {
        RegistryPreferenceDataStore store = store(true);
        store.putInt("text_size_percent", 120);
        assertEquals(120, store.getInt("text_size_percent", 0));
        assertEquals(Collections.singletonList(SettingRefresh.TEXT_SIZE), refreshed);
    }

    @Test
    public void aSaveWhileTheLauncherIsAwayWaitsForItsNextResume() {
        RegistryPreferenceDataStore store = store(false);
        store.putString("terminal_contrast_level", "harder");
        assertTrue(refreshed.isEmpty());
    }

    @Test
    public void anotherKeyFallsThroughToTheOldStore() {
        RegistryPreferenceDataStore store = store(true);
        store.putString("app_launcher_input_char", ">");
        assertEquals(">", fallbackWrites.get("app_launcher_input_char"));
        assertEquals(">", store.getString("app_launcher_input_char", "x"));
        assertEquals(77, store.getInt("something_else", 0));
        assertTrue(refreshed.isEmpty());
    }

    @Test
    public void aRefusedSaveWritesNothingAndSaysTheRange() {
        RegistryPreferenceDataStore store = store(true);
        store.putInt("text_size_percent", 999);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains(
            SettingDeclarations.TEXT_SIZE.allowed()));
        assertEquals(SettingDeclarations.TEXT_SIZE.defaultInt, store.getInt("text_size_percent", 0));
        assertTrue(refreshed.isEmpty());
    }

    @Test
    public void theChangeListenerVetoesARefusedValueAndLetsAGoodOneThrough() {
        RegistryPreferenceDataStore store = store(true);
        assertFalse(store.accepts("terminal_contrast_level", "loudest"));
        assertEquals(1, messages.size());
        assertTrue(store.accepts("terminal_contrast_level", "softer"));
        assertTrue(store.accepts("app_launcher_input_char", "anything"));
        assertEquals(1, messages.size());
    }
}
