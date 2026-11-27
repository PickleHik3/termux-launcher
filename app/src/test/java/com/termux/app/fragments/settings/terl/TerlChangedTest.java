package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.settings.UseAsAdapter;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Changed filter lists exactly the settings off their default; Reset and Copy as config. */
public class TerlChangedTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = TerlSettingsTreeTest.registry(store);

    private List<String> changedIds() {
        List<String> ids = new ArrayList<>();
        for (TerlChanged.Setting setting : TerlChanged.changed(registry, id -> true)) {
            ids.add(setting.declaration.id);
        }
        return ids;
    }

    @Test
    public void aFreshInstallHasNothingChanged() {
        assertEquals(new ArrayList<>(), changedIds());
    }

    @Test
    public void listsExactlyTheSettingsOffTheirDefaultInTreeOrder() {
        registry.set(SettingDeclarations.TERMINAL_BELL.id, "beep");
        registry.set(UseAsAdapter.ID, "terminal");
        registry.set(SettingDeclarations.KEYBOARD_HAPTICS.id, "false");
        // Set to what it already is: not a change.
        registry.set(SettingDeclarations.TERMINAL_SCROLLBACK.id, "2000");
        // Off its default, but not a row of Terl's Settings (it retires with the status bar).
        registry.set(SettingDeclarations.STATUS_BATTERY.id, "true");
        assertEquals(Arrays.asList(UseAsAdapter.ID, SettingDeclarations.TERMINAL_BELL.id,
            SettingDeclarations.KEYBOARD_HAPTICS.id), changedIds());
        TerlChanged.Setting bell = TerlChanged.changed(registry, id -> true).get(1);
        assertEquals("beep", bell.value);
    }

    @Test
    public void aRowThisPhoneDoesNotShowIsLeftOut() {
        registry.set(SettingDeclarations.TERMINAL_CORE.id, "libghostty");
        assertEquals(Arrays.asList(SettingDeclarations.TERMINAL_CORE.id), changedIds());
        assertTrue(TerlChanged.changed(registry,
            id -> !id.equals(SettingDeclarations.TERMINAL_CORE.id)).isEmpty());
    }

    @Test
    public void resetPutsTheDefaultBackAndDropsItFromTheList() {
        registry.set(SettingDeclarations.TERMINAL_SCROLLBACK.id, "9000");
        registry.set(UseAsAdapter.ID, "terminal");
        assertTrue(TerlChanged.isChanged(registry, SettingDeclarations.TERMINAL_SCROLLBACK.id));
        SettingsRegistry.Change change = TerlChanged.reset(registry,
            SettingDeclarations.TERMINAL_SCROLLBACK.id).change();
        assertEquals("9000", change.oldValue);
        assertEquals("2000", change.newValue);
        assertNull(TerlChanged.reset(registry, UseAsAdapter.ID).refusal());
        assertEquals("home", registry.get(UseAsAdapter.ID));
        assertFalse(TerlChanged.isChanged(registry, SettingDeclarations.TERMINAL_SCROLLBACK.id));
        assertEquals(new ArrayList<>(), changedIds());
    }

    @Test
    public void copyAsConfigIsTheRowsIdEqualsValueLine() {
        registry.set(SettingDeclarations.TERMINAL_BELL.id, "ignore");
        assertEquals("terminal.bell = ignore", SettingsFile.line(registry, SettingDeclarations.TERMINAL_BELL.id));
        assertEquals("cutout.inset = 4", SettingsFile.line(registry, SettingDeclarations.CUTOUT_INSET.id));
        assertEquals("home.use_as = home", SettingsFile.line(registry, UseAsAdapter.ID));
        // Every row's line is one terl.conf applies again.
        for (String id : TerlSettingsTree.settingIds()) {
            String line = SettingsFile.line(registry, id);
            if (line == null) continue;
            assertTrue(line, line.startsWith(id + " = "));
            assertTrue(line + ": " + SettingsFile.check(line, registry).errors,
                SettingsFile.check(line, registry).errors.isEmpty());
        }
    }
}
