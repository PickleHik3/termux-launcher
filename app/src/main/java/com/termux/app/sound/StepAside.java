package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decisions 7 and 8: whether the user has a sound setup of their own, which the launcher then
 * leaves alone entirely, or only a server some client autospawned, which the launcher replaces.
 * Pure: the caller lists the directory and reads the process table, this decides.
 */
final class StepAside {

    private StepAside() {}

    /**
     * What PulseAudio itself writes into {@code ~/.config/pulse} whenever it runs — its cookie,
     * the link to its runtime directory, its volume databases and the remembered default devices.
     * A user who never configured anything has these as soon as any client autospawned a server,
     * so they are not a setup of the user's. Everything else there is.
     */
    static boolean isPulseState(@NonNull String name) {
        return name.equals("cookie") || name.equals(".pulse-cookie")
            || name.endsWith("-runtime") || name.endsWith(".tdb")
            || name.endsWith("-default-sink") || name.endsWith("-default-source");
    }

    /** The entries of {@code ~/.config/pulse} that are the user's own (decision 7). */
    @NonNull
    static List<String> userFiles(@Nullable String[] entries) {
        if (entries == null) return Collections.emptyList();
        List<String> user = new ArrayList<>();
        for (String name : entries) {
            if (name != null && !isPulseState(name)) user.add(name);
        }
        Collections.sort(user);
        return user;
    }

    /** Whose a running PulseAudio server is. */
    enum Server {
        /** Started by this launcher: its own startup file is on the command line. */
        OURS,
        /**
         * Started the stock way and nothing more: a client's autospawn
         * ({@code pulseaudio --start --log-target=syslog}) or a bare {@code pulseaudio --start}.
         */
        STOCK,
        /** Started with options of somebody's choosing: the user's own setup. */
        USER
    }

    /** What an autospawn or a bare {@code --start} puts on the command line, and nothing else. */
    private static final Set<String> STOCK_ARGUMENTS = new HashSet<>(Arrays.asList(
        "--start", "-D", "--daemonize", "--daemonize=yes", "--daemonize=true", "--daemonize=1",
        "--log-target=syslog"));

    /**
     * Whose the server with {@code argv} is, or null when {@code argv} is not a PulseAudio
     * server at all.
     */
    @Nullable
    static Server classify(@NonNull List<String> argv, @NonNull String ourStartupFile) {
        if (argv.isEmpty()) return null;
        String program = argv.get(0);
        int slash = program.lastIndexOf('/');
        if (!"pulseaudio".equals(slash < 0 ? program : program.substring(slash + 1))) return null;
        if (argv.contains(ourStartupFile)) return Server.OURS;
        for (String argument : argv.subList(1, argv.size())) {
            if (!STOCK_ARGUMENTS.contains(argument)) return Server.USER;
        }
        return Server.STOCK;
    }

    /** A process's command line from {@code /proc/PID/cmdline}: NUL-separated, NUL-ended. */
    @NonNull
    static List<String> parseCmdline(@Nullable byte[] raw, int length) {
        List<String> argv = new ArrayList<>();
        if (raw == null) return argv;
        int start = 0;
        int end = Math.min(length, raw.length);
        for (int i = 0; i < end; i++) {
            if (raw[i] != 0) continue;
            argv.add(new String(raw, start, i - start, java.nio.charset.StandardCharsets.UTF_8));
            start = i + 1;
        }
        if (start < end) {
            argv.add(new String(raw, start, end - start, java.nio.charset.StandardCharsets.UTF_8));
        }
        return argv;
    }
}
