package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Use Terl as through the registry: every door writes it the way Settings' own row does. */
public class UseAsAdapterTest {

    private final List<String> writes = new ArrayList<>();
    private String stored = null;
    private final UseAsAdapter adapter = new UseAsAdapter(new UseAsAdapter.Store() {
        @Override public String read() { return stored; }
        @Override public void write(String value) {
            writes.add(value);
            stored = value;
        }
    });
    private final SettingsRegistry registry = new SettingsRegistry(
        which -> new InMemorySettingStore(), SettingDeclarations.ALL,
        Collections.singletonList(adapter));

    @Test
    public void readsHomeUntilSomethingIsStored() {
        assertEquals("home", registry.get(UseAsAdapter.ID));
        assertEquals("home", registry.declaration(UseAsAdapter.ID).defaultValue());
    }

    @Test
    public void savesThroughItsStoreAndAnswersTheChange() {
        SettingsRegistry.Change change = registry.set(UseAsAdapter.ID, "Terminal").change();
        assertNotNull(change);
        assertEquals("home", change.oldValue);
        assertEquals("terminal", change.newValue);
        assertEquals(Collections.singletonList("terminal"), writes);
        assertEquals("terminal", registry.get(UseAsAdapter.ID));
    }

    @Test
    public void defaultPutsHomeBack() {
        stored = "terminal";
        assertNull(registry.set(UseAsAdapter.ID, SettingsRegistry.DEFAULT).refusal());
        assertEquals("home", registry.get(UseAsAdapter.ID));
    }

    @Test
    public void refusesAnythingElseAndWritesNothing() {
        SettingsRegistry.Refusal refusal = registry.set(UseAsAdapter.ID, "desktop").refusal();
        assertNotNull(refusal);
        assertEquals("home.use_as is one of home, terminal", refusal.reason);
        assertEquals(Collections.emptyList(), writes);
    }

    @Test
    public void savingTheValueItHoldsWritesNothing() {
        stored = "terminal";
        registry.set(UseAsAdapter.ID, "terminal");
        assertEquals(Collections.emptyList(), writes);
    }
}
