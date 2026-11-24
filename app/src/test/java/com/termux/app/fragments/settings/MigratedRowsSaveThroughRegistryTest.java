package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.preference.PreferenceDataStore;

import com.termux.app.settings.LowMemoryPresetAdapter;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The rows of the migrated screens (Battery &amp; performance, Notifications) save through the
 * registry: each row's value lands in the preferences under the key it always used, as the type it
 * always had, and the refresh its declaration names runs.
 */
public class MigratedRowsSaveThroughRegistryTest {

    private final InMemorySettingStore preferences = new InMemorySettingStore();
    private final List<SettingRefresh> refreshed = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private boolean presetOn;
    private int presetSwitches;

    private final PreferenceDataStore untouched = new PreferenceDataStore() { };

    private RegistryPreferenceDataStore store() {
        LowMemoryPresetAdapter preset = new LowMemoryPresetAdapter(
            new LowMemoryPresetAdapter.Preset() {
                @Override public boolean isOn() { return presetOn; }
                @Override public void turnOn() { presetOn = true; presetSwitches++; }
                @Override public void turnOff() { presetOn = false; presetSwitches++; }
            });
        SettingsRegistry registry = new SettingsRegistry(which -> preferences,
            SettingDeclarations.ALL, Collections.singletonList(preset));
        SettingsApplier applier = new SettingsApplier(new SettingsApplier.Host() {
            @Override public boolean isLauncherResumed() { return true; }
            @Override public void run(SettingRefresh refresh) { refreshed.add(refresh); }
        });
        return new RegistryPreferenceDataStore(untouched, registry, applier,
            RegistryPreferenceDataStore.ROWS, messages::add);
    }

    @Test
    public void everyRowSavesUnderItsKeyAsItsTypeAndRunsItsDeclaredRefresh() {
        for (Map.Entry<String, String> row : RegistryPreferenceDataStore.ROWS.entrySet()) {
            String key = row.getKey();
            SettingDeclaration declaration = SettingsRegistryScreensTest.declarationOf(row.getValue());
            if (key.equals("low_memory_preset")) continue;
            refreshed.clear();
            RegistryPreferenceDataStore store = store();
            if (declaration.kind == SettingDeclaration.Kind.BOOLEAN) {
                boolean other = !Boolean.parseBoolean(declaration.defaultValue());
                store.putBoolean(key, other);
                assertEquals(key, other, preferences.getBoolean(key, !other));
                assertEquals(key, other, store.getBoolean(key, !other));
            } else {
                String other = declaration.choices.get(declaration.choices.size() - 1);
                if (other.equals(declaration.defaultValue())) other = declaration.choices.get(0);
                store.putString(key, other);
                assertEquals(key, other, preferences.getString(key));
                assertEquals(key, other, store.getString(key, "x"));
            }
            List<SettingRefresh> expected = declaration.refresh == SettingRefresh.LISTENER
                ? Collections.<SettingRefresh>emptyList()
                : Collections.singletonList(declaration.refresh);
            assertEquals(key, expected, refreshed);
            assertTrue(key, messages.isEmpty());
        }
    }

    @Test
    public void aRowReadsItsDeclaredDefaultUntilSomethingIsSaved() {
        RegistryPreferenceDataStore store = store();
        assertTrue(store.getBoolean("fancier_glass", false));
        assertFalse(store.getBoolean("live_blur", true));
        assertEquals("none", store.getString("terminal_retro_effect", "x"));
        assertEquals("rare", store.getString("idle_tear_rate", "x"));
        assertEquals("dot", store.getString("app_launcher_notification_badge_style", "x"));
    }

    @Test
    public void aRefusedChoiceWritesNothingAndSaysWhatIsAllowed() {
        RegistryPreferenceDataStore store = store();
        store.putString("idle_tear_rate", "constantly");
        assertEquals("rare", store.getString("idle_tear_rate", "x"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains("rare, sometimes, often"));
        assertTrue(refreshed.isEmpty());
        assertFalse(store.accepts("terminal_retro_effect", "plasma"));
        assertTrue(store.accepts("terminal_retro_effect", "crt"));
    }

    @Test
    public void theLowMemoryPresetSwitchRunsThePresetNotJustTheFlag() {
        RegistryPreferenceDataStore store = store();
        store.putBoolean("low_memory_preset", true);
        assertTrue(presetOn);
        assertEquals(1, presetSwitches);
        assertTrue(store.getBoolean("low_memory_preset", false));
        assertEquals(Collections.singletonList(SettingRefresh.RESTYLE), refreshed);
        store.putBoolean("low_memory_preset", true);
        assertEquals(1, presetSwitches);
        store.putBoolean("low_memory_preset", false);
        assertFalse(presetOn);
        assertEquals(2, presetSwitches);
    }

    @Test
    public void theBatteryAndRiskyOnesAreNotOfferedToTheAssistant() {
        assertFalse(SettingDeclarations.LIVE_BLUR.mayOffer);
        assertFalse(SettingDeclarations.IDLE_ANIMATIONS.mayOffer);
        assertFalse(SettingDeclarations.TERMINAL_EFFECT_COMPOSITE.mayOffer);
        assertTrue(SettingDeclarations.ANIMATION_SPEED.mayOffer);
    }

    @Test
    public void theLowMemoryPresetRewritesTheLookSoItIsNotOffered() {
        assertFalse(new LowMemoryPresetAdapter(null).declaration(LowMemoryPresetAdapter.ID).mayOffer);
    }
}
