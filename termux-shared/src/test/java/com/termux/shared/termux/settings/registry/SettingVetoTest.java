package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/**
 * A value the declaration allows but this build or phone cannot run is refused by the registry's
 * veto, at check and at save, so terl.conf and launcherctl refuse it as they refuse a value
 * outside the range.
 */
public class SettingVetoTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store,
        SettingDeclarations.ALL, Collections.emptyList(), (declaration, value) ->
            declaration.id.equals("terminal.bell") && value.equals("beep")
                ? new SettingsRegistry.Refusal("terminal.bell cannot beep here", "vibrate, ignore")
                : null);

    @Test
    public void checkAndSetRefuseAVetoedValueAndWriteNothing() {
        SettingsRegistry.Refusal checked = registry.check("terminal.bell", "Beep");
        assertNotNull(checked);
        assertEquals("terminal.bell cannot beep here", checked.reason);
        SettingsRegistry.Refusal saved = registry.set("terminal.bell", "beep").refusal();
        assertNotNull(saved);
        assertEquals("vibrate, ignore", saved.allowed);
        assertEquals("vibrate", registry.get("terminal.bell"));
    }

    @Test
    public void otherValuesAndTheDefaultWordStillSave() {
        assertNull(registry.set("terminal.bell", "ignore").refusal());
        assertNull(registry.set("terminal.bell", SettingsRegistry.DEFAULT).refusal());
        assertEquals("vibrate", registry.get("terminal.bell"));
    }

    @Test
    public void aSettingsFileLineWithAVetoedValueIsRefusedAndNothingIsSaved() {
        SettingsFile.Applied applied = SettingsFile.apply("terminal.bell = ignore\nterminal.bell = beep\n",
            registry);
        assertTrue(!applied.ok);
        assertEquals(Collections.singletonList("line 2: terminal.bell cannot beep here"), applied.errors);
        assertEquals("vibrate", registry.get("terminal.bell"));
    }
}
