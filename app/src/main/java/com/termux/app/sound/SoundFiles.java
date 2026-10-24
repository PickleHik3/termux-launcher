package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The text of every file the launcher writes for sound, the server's command line and
 * environment, and the rule for when a file on disk is the launcher's to rewrite. Pure, so each
 * piece is read back by a test.
 */
final class SoundFiles {

    private SoundFiles() {}

    /** Where the server sends its sound (decision 2). */
    enum Sink {
        /** Android's current audio path. Tried first, every start. */
        AAUDIO("module-aaudio-sink"),
        /** OpenSL ES: deprecated since Android 11, kept for a phone whose AAudio will not open. */
        SLES("module-sles-sink");

        @NonNull final String module;

        Sink(@NonNull String module) {
            this.module = module;
        }
    }

    /**
     * The modules Termux's {@code default.pa} loads around its sink, minus what a phone has no
     * use for (hardware detection, X11, Bluetooth, esound). Each may be missing from a build, so
     * none of them can stop the server.
     */
    private static final List<String> BEFORE_SINK = Collections.unmodifiableList(Arrays.asList(
        "module-device-restore", "module-stream-restore", "module-card-restore"));
    private static final List<String> AFTER_SINK = Collections.unmodifiableList(Arrays.asList(
        "module-default-device-restore", "module-always-sink", "module-intended-roles",
        "module-suspend-on-idle", "module-position-event-sounds", "module-role-cork",
        "module-filter-heuristics", "module-filter-apply"));

    /**
     * The server's startup script ({@code pulseaudio -n -F}), in place of Termux's
     * {@code default.pa}, which stays untouched.
     *
     * <p>The sink and the prefix's socket are loaded in {@code .fail} mode: a sink that does not
     * load ends the start, and the launcher reads which module failed and starts again with
     * {@link Sink#SLES} (decision 2). The loopback listener is {@code .nofail}, so a port someone
     * else holds costs the distros their sound and never the server. It takes the cookie and no
     * {@code auth-ip-acl}: an ACL would let every app on the phone in, since they all share
     * loopback (decision 5). The prefix's socket authenticates by the peer's uid as well.
     */
    @NonNull
    static String startupFile(@NonNull Sink sink, @NonNull String socket, @NonNull String cookie,
                              int tcpPort) {
        StringBuilder out = new StringBuilder();
        out.append(SoundPaths.MARKER).append(" for its sound server; it rewrites this file\n");
        out.append(".nofail\n");
        for (String module : BEFORE_SINK) out.append("load-module ").append(module).append('\n');
        out.append(".fail\n");
        out.append("load-module ").append(sink.module).append('\n');
        out.append("load-module module-native-protocol-unix socket=").append(socket)
            .append(" auth-cookie=").append(cookie).append('\n');
        out.append(".nofail\n");
        out.append("load-module module-native-protocol-tcp listen=127.0.0.1 port=").append(tcpPort)
            .append(" auth-cookie=").append(cookie).append('\n');
        for (String module : AFTER_SINK) out.append("load-module ").append(module).append('\n');
        return out.toString();
    }

    /**
     * Whether a line the server printed says {@code sink} would not load. PulseAudio says
     * {@code Failed to load module "…"} when the module's own start fails and
     * {@code Failed to open module "…"} when the build has no such module.
     */
    static boolean reportsSinkFailure(@Nullable String line, @NonNull Sink sink) {
        return line != null && line.contains("Failed to") && line.contains("\"" + sink.module + "\"");
    }

    /**
     * The server's command line. Not daemonized, so the launcher's thread can wait on the process
     * itself; never idle-exiting (decision 1); its own startup file and nothing of
     * {@code default.pa} ({@code -n}).
     */
    @NonNull
    static List<String> serverArgv(@NonNull String pulseaudio, @NonNull String startupFile) {
        return Arrays.asList(pulseaudio, "-n", "-F", startupFile, "--daemonize=no",
            "--exit-idle-time=-1", "--use-pid-file=yes", "--system=no", "--log-target=stderr");
    }

    /**
     * The environment the server, the bus and the helper run with: the Termux environment a
     * session gets, plus termux-exec's preload as Termux's {@code login} sets it, when it is
     * installed. The server additionally gets its own runtime and state directories and no
     * client configuration: a {@code default-server} in one makes PulseAudio take itself for a
     * client of somebody else's server.
     *
     * @param preload the first of {@link SoundPaths#EXEC_PRELOADS} that exists, or null
     */
    @NonNull
    static Map<String, String> processEnvironment(@NonNull Map<String, String> termux,
                                                  @Nullable String preload, boolean forServer) {
        Map<String, String> env = new HashMap<>(termux);
        if (preload != null) env.put("LD_PRELOAD", preload);
        env.remove("PULSE_SERVER");
        if (forServer) {
            env.remove("DBUS_SESSION_BUS_ADDRESS");
            env.put("PULSE_RUNTIME_PATH", SoundPaths.RUNTIME_DIR);
            env.put("PULSE_STATE_PATH", SoundPaths.VOLUMES_DIR);
            env.put("PULSE_CLIENTCONFIG", "/dev/null");
        } else {
            env.put("PULSE_SERVER", SoundPaths.PULSE_SERVER);
            env.put("DBUS_SESSION_BUS_ADDRESS", SoundPaths.BUS_ADDRESS);
        }
        return env;
    }

    /**
     * The prefix's client drop-in: every client in the prefix that is not handed
     * {@code PULSE_SERVER} — ssh, Termux:Widget, run-command, services — finds the server
     * through it (decision 4). No autospawn: a client never starts a second server.
     */
    @NonNull
    static String termuxDropIn(@NonNull String server) {
        return SoundPaths.MARKER + "; it rewrites this file and removes it when Sound is off\n"
            + "default-server = " + server + "\n"
            + "autospawn = no\n";
    }

    /** A distro's client drop-in: the loopback listener and the cookie's copy (decisions 4, 5). */
    @NonNull
    static String distroDropIn(@NonNull String server, @NonNull String cookieFile) {
        return SoundPaths.MARKER + "; it rewrites this file and removes it when Sound is off\n"
            + "default-server = " + server + "\n"
            + "cookie-file = " + cookieFile + "\n"
            + "autospawn = no\n";
    }

    /**
     * The session bus's configuration (decision 9). Anonymous connections are allowed because a
     * distro's apps cannot prove who they are: proot reports a uid the bus does not know. The
     * socket sits in the launcher's private directory, which no other app can reach, so that
     * lets nothing in from outside.
     */
    @NonNull
    static String busConfig(@NonNull String socket) {
        return "<!DOCTYPE busconfig PUBLIC \"-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN\"\n"
            + " \"http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd\">\n"
            + "<!-- " + SoundPaths.MARKER.substring(2) + " for its session bus; it rewrites this file -->\n"
            + "<busconfig>\n"
            + "  <type>session</type>\n"
            + "  <listen>unix:path=" + socket + "</listen>\n"
            + "  <allow_anonymous/>\n"
            + "  <standard_session_servicedirs/>\n"
            + "  <policy context=\"default\">\n"
            + "    <allow send_destination=\"*\" eavesdrop=\"true\"/>\n"
            + "    <allow eavesdrop=\"true\"/>\n"
            + "    <allow own=\"*\"/>\n"
            + "  </policy>\n"
            + "</busconfig>\n";
    }

    /** The bus's command line: in the foreground, so the launcher can wait on it. */
    @NonNull
    static List<String> busArgv(@NonNull String daemon, @NonNull String config) {
        return Arrays.asList(daemon, "--config-file=" + config, "--nofork", "--nopidfile");
    }

    /** Whether {@code content} is a file the launcher wrote. */
    static boolean isOurs(@Nullable String content) {
        return content != null && content.contains(SoundPaths.MARKER.substring(2));
    }

    /** What to do with a file the launcher wants at a path. */
    enum Write {
        /** Absent, or the launcher's and stale: write it. */
        WRITE,
        /** The launcher's and already what it wants. */
        KEEP,
        /** Somebody else's: never touched. */
        FOREIGN
    }

    /**
     * {@code exists} and {@code content} describe what is at the path now ({@code content} null
     * when it cannot be read); {@code wanted} is what the launcher would write.
     */
    @NonNull
    static Write decide(boolean exists, @Nullable String content, @NonNull String wanted) {
        if (!exists) return Write.WRITE;
        if (!isOurs(content)) return Write.FOREIGN;
        return wanted.equals(content) ? Write.KEEP : Write.WRITE;
    }

    /** The environment handed to every new shell and Linux app while the server is the launcher's. */
    @NonNull
    static Map<String, String> shellEnvironment(boolean bus) {
        Map<String, String> env = new HashMap<>();
        env.put("PULSE_SERVER", SoundPaths.PULSE_SERVER);
        if (bus) {
            env.put("DBUS_SESSION_BUS_ADDRESS", SoundPaths.BUS_ADDRESS);
            // Read by ProotDistro.loginCommand, which hands the bus to a distro only when set.
            env.put(LauncherSound.DISTRO_BUS_VARIABLE, SoundPaths.DISTRO_BUS_ADDRESS);
        }
        return env;
    }

    /** {@code argv} as one list, for building commands from fixed parts and a tail. */
    @NonNull
    static List<String> argv(@NonNull String... parts) {
        return new ArrayList<>(Arrays.asList(parts));
    }

    /**
     * The first volume {@code pactl get-sink-volume} printed, in percent
     * ({@code Volume: front-left: 65536 / 100% / 0.00 dB, …}); -1 when there is none.
     */
    static int volumePercent(@Nullable String pactlOutput) {
        if (pactlOutput == null) return -1;
        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile("(\\d+)%").matcher(pactlOutput);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether {@code pactl get-sink-mute} said {@code Mute: yes}. */
    static boolean isMuted(@Nullable String pactlOutput) {
        return pactlOutput != null && pactlOutput.trim().toLowerCase(java.util.Locale.ROOT)
            .startsWith("mute: yes");
    }
}
