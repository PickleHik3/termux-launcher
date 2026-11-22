package com.termux.privileged;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RootSystemCommandsTest {

    @Test
    public void lock_triesSleepKeyThenPowerKey() {
        assertEquals("input keyevent 223 || input keyevent 26", RootSystemCommands.lock());
    }

    @Test
    public void reboot_targets() {
        assertEquals("svc power reboot || reboot", RootSystemCommands.reboot());
        assertEquals("svc power reboot recovery || reboot recovery",
            RootSystemCommands.rebootRecovery());
        assertEquals("svc power reboot bootloader || reboot bootloader",
            RootSystemCommands.rebootBootloader());
    }

    @Test
    public void wifiAndData() {
        assertEquals("svc wifi enable", RootSystemCommands.wifi(true));
        assertEquals("svc wifi disable", RootSystemCommands.wifi(false));
        assertEquals("svc data enable", RootSystemCommands.data(true));
        assertEquals("svc data disable", RootSystemCommands.data(false));
    }

    @Test
    public void airplane_fallsBackToSettingsAndBroadcast() {
        assertEquals("cmd connectivity airplane-mode enable 2>/dev/null || "
            + "{ settings put global airplane_mode_on 1 && am broadcast -a "
            + "android.intent.action.AIRPLANE_MODE --ez state true; }", RootSystemCommands.airplane(true));
        assertEquals("cmd connectivity airplane-mode disable 2>/dev/null || "
            + "{ settings put global airplane_mode_on 0 && am broadcast -a "
            + "android.intent.action.AIRPLANE_MODE --ez state false; }", RootSystemCommands.airplane(false));
    }

    @Test
    public void dnd() {
        assertEquals("cmd notification set_dnd priority", RootSystemCommands.dnd(true));
        assertEquals("cmd notification set_dnd off", RootSystemCommands.dnd(false));
    }
}
