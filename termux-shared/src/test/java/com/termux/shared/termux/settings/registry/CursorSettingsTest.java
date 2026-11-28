package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;

/**
 * The terminal cursor's shape and blink as settings: termux.properties' own keys, so launcherctl,
 * terl.conf and the Windows card's Text tab write what the terminal reads.
 */
public class CursorSettingsTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store,
        SettingDeclarations.ALL, Collections.emptyList());

    @Test
    public void theShapeIsTermuxPropertiesCursorStyle() {
        assertEquals(SettingDeclaration.Store.PROPERTIES, SettingDeclarations.TERMINAL_CURSOR_STYLE.store);
        assertEquals("block", registry.get("terminal.cursor_style"));
        assertNull(registry.set("terminal.cursor_style", "Bar").refusal());
        assertEquals("bar", store.getString("terminal-cursor-style"));
        assertNotNull(registry.set("terminal.cursor_style", "beam").refusal());
    }

    @Test
    public void blinkOnWritesTheUsualRateAndAnyRateReadsOn() {
        assertEquals("off", registry.get("terminal.cursor_blink"));
        assertNull(registry.set("terminal.cursor_blink", "on").refusal());
        assertEquals("600", store.getString("terminal-cursor-blink-rate"));
        store.putString("terminal-cursor-blink-rate", "450");
        assertEquals("a rate set by hand reads on", "on", registry.get("terminal.cursor_blink"));
        store.putString("terminal-cursor-blink-rate", "0");
        assertEquals("off", registry.get("terminal.cursor_blink"));
        assertNull(registry.set("terminal.cursor_blink", "off").refusal());
        assertEquals("0", store.getString("terminal-cursor-blink-rate"));
    }
}
