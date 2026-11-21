package com.termux.app.sound;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Which command lines are the launcher's own sound processes: the server, the session bus and
 * the media helper. Pure, so the orphan cleanup at a start and the service's reaper at a stop
 * agree on who they are.
 */
final class OwnProcesses {

    private OwnProcesses() {}

    static boolean isLauncherSoundProcess(@NonNull List<String> argv) {
        return StepAside.classify(argv, SoundPaths.STARTUP_FILE) == StepAside.Server.OURS
            || argv.contains(SoundPaths.HELPER)
            || argv.contains("--config-file=" + SoundPaths.BUS_CONFIG);
    }
}
