package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Collections;

/**
 * The display server and the terminal core refuse what this build or phone cannot run: Wayland
 * without the Wayland host, libghostty where it does not load. terl.conf and launcherctl see the
 * same refusal as Settings.
 */
public class RunnableChoicesTest {

    private static SettingsRegistry registry(boolean waylandBuilt, boolean ghosttyLoads) {
        InMemorySettingStore store = new InMemorySettingStore();
        return new SettingsRegistry(which -> store, SettingDeclarations.ALL,
            Collections.emptyList(), new RunnableChoices(waylandBuilt, ghosttyLoads));
    }

    @Test
    public void waylandIsRefusedInABuildWithoutIt() {
        SettingsRegistry registry = registry(false, true);
        SettingsRegistry.Refusal refusal = registry.check(SettingDeclarations.LINUX_DISPLAY_SERVER.id, "wayland");
        assertNotNull(refusal);
        assertEquals("linux.display_server can only be x11: this version has no Wayland display",
            refusal.reason);
        assertEquals("x11", refusal.allowed);
        assertNotNull(registry.set(SettingDeclarations.LINUX_DISPLAY_SERVER.id, "wayland").refusal());
        assertEquals("x11", registry.get(SettingDeclarations.LINUX_DISPLAY_SERVER.id));
        assertNull(registry.check(SettingDeclarations.LINUX_DISPLAY_SERVER.id, "x11"));
        assertEquals("[line 1: linux.display_server can only be x11: this version has no Wayland display]",
            SettingsFile.check("linux.display_server = wayland", registry).errors.toString());
    }

    @Test
    public void waylandSavesInABuildWithIt() {
        SettingsRegistry registry = registry(true, false);
        assertNull(registry.set(SettingDeclarations.LINUX_DISPLAY_SERVER.id, "wayland").refusal());
        assertEquals("wayland", registry.get(SettingDeclarations.LINUX_DISPLAY_SERVER.id));
    }

    @Test
    public void libghosttyIsRefusedWhereItDoesNotLoad() {
        SettingsRegistry registry = registry(true, false);
        SettingsRegistry.Refusal refusal = registry.check(SettingDeclarations.TERMINAL_CORE.id, "libghostty");
        assertNotNull(refusal);
        assertEquals("terminal.core can only be termux: libghostty does not load on this phone",
            refusal.reason);
        assertEquals("termux", refusal.allowed);
        assertNotNull(registry.set(SettingDeclarations.TERMINAL_CORE.id, "libghostty").refusal());
        assertEquals("termux", registry.get(SettingDeclarations.TERMINAL_CORE.id));
        assertNull(registry.check(SettingDeclarations.TERMINAL_CORE.id, SettingsRegistry.DEFAULT));
    }

    @Test
    public void libghosttySavesWhereItLoads() {
        SettingsRegistry registry = registry(false, true);
        assertNull(registry.set(SettingDeclarations.TERMINAL_CORE.id, "libghostty").refusal());
        assertEquals("libghostty", registry.get(SettingDeclarations.TERMINAL_CORE.id));
    }

    @Test
    public void libghosttyIsLoadedOnlyToJudgeLibghostty() {
        InMemorySettingStore store = new InMemorySettingStore();
        SettingsRegistry registry = new SettingsRegistry(which -> store,
            SettingDeclarations.ALL, Collections.emptyList(), new RunnableChoices(true, () -> {
                throw new AssertionError("libghostty was loaded");
            }));
        assertNull(registry.set(SettingDeclarations.TERMINAL_CORE.id, "termux").refusal());
        assertNull(registry.set(SettingDeclarations.TERMINAL_BELL.id, "beep").refusal());
        assertNull(registry.check(SettingDeclarations.LINUX_DISPLAY_SERVER.id, "wayland"));
    }
}
