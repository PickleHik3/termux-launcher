package com.termux.app.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OwnProcessesTest {

    private static final int SERVER = 10, BUS = 11, HELPER = 12, MONITOR = 13, PACTL = 14,
        SHELL = 20, SHELL_CHILD = 21, STOCK = 30;

    private final Map<Integer, java.util.List<String>> argv = new HashMap<>();
    private final Map<Integer, Integer> parents = new HashMap<>();

    private void process(int pid, int parent, String... args) {
        argv.put(pid, Arrays.asList(args));
        parents.put(pid, parent);
    }

    @Test
    public void sparesTheSoundProcessesAndTheHelpersChildrenOnly() {
        process(SERVER, 1, SoundPaths.PULSEAUDIO, "-n", "--file=" + SoundPaths.STARTUP_FILE,
            SoundPaths.STARTUP_FILE);
        process(BUS, 1, SoundPaths.DBUS_DAEMON, "--config-file=" + SoundPaths.BUS_CONFIG);
        process(HELPER, 1, SoundPaths.BASH, SoundPaths.HELPER);
        process(MONITOR, HELPER, SoundPaths.DBUS_MONITOR, "--session");
        process(PACTL, HELPER, SoundPaths.PACTL, "subscribe");
        process(SHELL, 1, SoundPaths.BASH, "-l");
        process(SHELL_CHILD, SHELL, "sleep", "100");
        process(STOCK, 1, "pulseaudio", "--start");

        Set<Integer> spared = LauncherSound.spares(argv, parents);

        assertEquals(new java.util.HashSet<>(Arrays.asList(SERVER, BUS, HELPER, MONITOR, PACTL)),
            spared);
        assertFalse(spared.contains(SHELL));
        assertFalse(spared.contains(SHELL_CHILD));
        assertFalse(spared.contains(STOCK));
    }

    @Test
    public void recognisesEachKindByItsCommandLine() {
        assertTrue(OwnProcesses.isLauncherSoundProcess(
            Arrays.asList("pulseaudio", SoundPaths.STARTUP_FILE)));
        assertFalse(OwnProcesses.isLauncherSoundProcess(Arrays.asList("pulseaudio", "--start")));
        assertFalse(OwnProcesses.isLauncherSoundProcess(Arrays.asList("sleep", "1")));
    }
}
