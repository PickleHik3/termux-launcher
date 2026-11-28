package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Collections;

/** The keyboard Terl types with, through the registry: both of v1's switches move together. */
public class InputMethodAdapterTest {

    private boolean builtIn = true;
    private boolean android = true;
    private final SettingsRegistry registry = new SettingsRegistry(
        which -> new InMemorySettingStore(), SettingDeclarations.ALL,
        Collections.singletonList(new InputMethodAdapter(new InputMethodAdapter.Store() {
            @Override public boolean builtIn() { return builtIn; }
            @Override public boolean android() { return android; }
            @Override public void write(boolean b, boolean a) { builtIn = b; android = a; }
        })));

    @Test
    public void readsAsV1sPageReadsTheTwoSwitches() {
        assertEquals("built_in", registry.get(InputMethodAdapter.ID));
        builtIn = false;
        assertEquals("android", registry.get(InputMethodAdapter.ID));
        android = false;
        assertEquals("off", registry.get(InputMethodAdapter.ID));
    }

    @Test
    public void thePhonesKeyboardTurnsTheLaunchersOff() {
        SettingsRegistry.Change change = registry.set(InputMethodAdapter.ID, "android").change();
        assertNotNull(change);
        assertEquals("built_in", change.oldValue);
        assertFalse(builtIn);
        assertTrue(android);
    }

    @Test
    public void offAndV1sNoneTurnBothOff() {
        registry.set(InputMethodAdapter.ID, "none");
        assertFalse(builtIn);
        assertFalse(android);
        registry.set(InputMethodAdapter.ID, "built_in");
        assertTrue(builtIn);
        assertFalse(android);
    }

    @Test
    public void refusesAWordItDoesNotKnow() {
        assertNull(registry.set(InputMethodAdapter.ID, "swype").change());
        assertTrue(builtIn);
    }
}
