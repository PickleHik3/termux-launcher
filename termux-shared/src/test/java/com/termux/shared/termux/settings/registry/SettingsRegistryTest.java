package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Settings registry over an in-memory store: what a door (launcherctl, a row) reads back. */
public class SettingsRegistryTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store);

    @Test
    public void getAnswersTheDefaultUntilSomethingIsStored() {
        assertEquals("100", registry.get("text.size"));
        store.putInt("text_size_percent", 120);
        assertEquals("120", registry.get("text.size"));
    }

    @Test
    public void getNamesAChoiceByItsNameNotWhatItsStoreHolds() {
        assertEquals("system", registry.get("theme.mode"));
        store.putString("night-mode", "true");
        assertEquals("dark", registry.get("theme.mode"));
        store.putString("night-mode", " False");
        assertEquals("light", registry.get("theme.mode"));
        assertEquals("default", registry.get("terminal.contrast"));
    }

    @Test
    public void getClampsWhatAHandEditedStoreHolds() {
        store.putInt("text_size_percent", 400);
        assertEquals("150", registry.get("text.size"));
        store.putString("terminal_contrast_level", "loudest");
        assertEquals("default", registry.get("terminal.contrast"));
    }

    @Test
    public void getOfAnUnknownIdIsNull() {
        assertNull(registry.get("terminal.sparkle"));
        assertNull(registry.get(null));
    }

    @Test
    public void setWritesTheStoresOwnValueAndReturnsTheChange() {
        SettingsRegistry.Change change = registry.set("terminal.contrast", "harder").change();
        assertNotNull(change);
        assertEquals("terminal.contrast", change.id);
        assertEquals("default", change.oldValue);
        assertEquals("harder", change.newValue);
        assertEquals("harder", store.getString("terminal_contrast_level"));

        registry.set("theme.mode", " Dark ");
        assertEquals("true", store.getString("night-mode"));

        SettingsRegistry.Change size = registry.set("text.size", "135").change();
        assertNotNull(size);
        assertEquals("100", size.oldValue);
        assertEquals("135", size.newValue);
        assertEquals(135, store.getInt("text_size_percent", -1));
    }

    @Test
    public void undoIsSetToTheOldValue() {
        store.putString("terminal_contrast_level", "softer");
        SettingsRegistry.Change change = registry.set("terminal.contrast", "harder").change();
        assertNotNull(change);
        SettingsRegistry.Change undo = registry.set(change.id, change.oldValue).change();
        assertNotNull(undo);
        assertEquals("harder", undo.oldValue);
        assertEquals("softer", undo.newValue);
        assertEquals("softer", registry.get("terminal.contrast"));
    }

    @Test
    public void aChangeToTheSameValueSaysNothingChanged() {
        SettingsRegistry.Change same = registry.set("text.size", "100").change();
        assertNotNull(same);
        assertTrue(!same.changed());
        assertTrue(registry.set("text.size", "105").change().changed());
    }

    @Test
    public void setRefusesAnUnknownIdAndWritesNothing() {
        SettingsRegistry.Refusal refusal = registry.set("terminal.sparkle", "on").refusal();
        assertNotNull(refusal);
        assertNull(registry.set("terminal.sparkle", "on").change());
        assertTrue(refusal.reason, refusal.reason.contains("terminal.sparkle"));
    }

    @Test
    public void setRefusesAValueOutsideTheRangeWithTheRange() {
        for (String bad : new String[]{"151", "79", "big", "", "12.5"}) {
            SettingsRegistry.Refusal refusal = registry.set("text.size", bad).refusal();
            assertNotNull(bad, refusal);
            assertEquals(bad, "80–150", refusal.allowed);
            assertEquals("text.size is 80–150", refusal.reason);
        }
        assertEquals(-1, store.getInt("text_size_percent", -1));
    }

    @Test
    public void stepMovesAnIntegerOneStepAndStopsAtEachEnd() {
        SettingsRegistry.Change up = registry.step("text.size", SettingsRegistry.Direction.UP).change();
        assertNotNull(up);
        assertEquals("100", up.oldValue);
        assertEquals("105", up.newValue);

        store.putInt("text_size_percent", 150);
        SettingsRegistry.Refusal top = registry.step("text.size", SettingsRegistry.Direction.UP).refusal();
        assertNotNull(top);
        assertEquals("text.size is already at its highest, 150", top.reason);
        assertEquals("80–150", top.allowed);

        store.putInt("text_size_percent", 82);
        SettingsRegistry.Change down = registry.step("text.size", SettingsRegistry.Direction.DOWN).change();
        assertNotNull(down);
        assertEquals("80", down.newValue);
        SettingsRegistry.Refusal bottom = registry.step("text.size", SettingsRegistry.Direction.DOWN).refusal();
        assertNotNull(bottom);
        assertEquals("text.size is already at its lowest, 80", bottom.reason);
    }

    @Test
    public void previewStepAnswersWhatStepWouldDoAndWritesNothing() {
        SettingsRegistry.Change up = registry.previewStep("text.size", SettingsRegistry.Direction.UP).change();
        assertEquals("100", up.oldValue);
        assertEquals("105", up.newValue);
        assertEquals("100", registry.get("text.size"));
        assertNull(store.getString("text_size_percent"));
        store.putInt("text_size_percent", 150);
        assertNotNull(registry.previewStep("text.size", SettingsRegistry.Direction.UP).refusal());
        assertNotNull(registry.previewStep("theme.mode", SettingsRegistry.Direction.UP).refusal());
    }

    @Test
    public void stepMovesAnOrderedChoiceAlongItsNames() {
        assertEquals("harder",
            registry.step("terminal.contrast", SettingsRegistry.Direction.UP).change().newValue);
        assertNotNull(registry.step("terminal.contrast", SettingsRegistry.Direction.UP).refusal());
        registry.set("terminal.contrast", "softer");
        assertNotNull(registry.step("terminal.contrast", SettingsRegistry.Direction.DOWN).refusal());
        assertEquals("default",
            registry.step("terminal.contrast", SettingsRegistry.Direction.UP).change().newValue);
    }

    @Test
    public void stepRefusesAChoiceWithNoOrderAndAnUnknownId() {
        SettingsRegistry.Refusal refusal =
            registry.step("theme.mode", SettingsRegistry.Direction.UP).refusal();
        assertNotNull(refusal);
        assertEquals("system, light, dark, black", refusal.allowed);
        assertNull(store.getString("night-mode"));
        assertNotNull(registry.step("terminal.sparkle", SettingsRegistry.Direction.UP).refusal());
    }

    @Test
    public void listGivesEveryDeclarationWithItsCurrentValueInOrder() {
        store.putString("terminal_contrast_level", "softer");
        java.util.List<SettingsRegistry.Entry> entries = registry.list();
        assertEquals(SettingDeclarations.ALL.size(), entries.size());
        assertEquals("theme.mode", entries.get(0).declaration.id);
        assertEquals("system", entries.get(0).value);
        assertEquals("terminal.contrast", entries.get(1).declaration.id);
        assertEquals("softer", entries.get(1).value);
        assertEquals("text.size", entries.get(2).declaration.id);
        assertEquals("100", entries.get(2).value);
    }

    @Test
    public void constructingTheRegistryTouchesNoStore() {
        new SettingsRegistry(which -> {
            throw new AssertionError("a store was asked for at construction");
        });
    }

    @Test
    public void setRefusesAnUnknownChoiceWithTheChoices() {
        SettingsRegistry.Refusal refusal = registry.set("terminal.contrast", "loudest").refusal();
        assertNotNull(refusal);
        assertEquals("softer, default, harder", refusal.allowed);
        assertEquals("terminal.contrast is one of softer, default, harder", refusal.reason);
        // The stored value, not the name, is not a name a door may hand in.
        assertNotNull(registry.set("theme.mode", "true").refusal());
        assertNull(store.getString("terminal_contrast_level"));
        assertNull(store.getString("night-mode"));
    }
}
