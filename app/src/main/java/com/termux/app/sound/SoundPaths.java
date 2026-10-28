package com.termux.app.sound;

import com.termux.shared.termux.TermuxConstants;

/**
 * Where the launcher's sound server, its session bus and what they leave on disk live. Every path
 * comes from {@link TermuxConstants}, so each edition gets its own prefix.
 */
final class SoundPaths {

    private SoundPaths() {}

    static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    static final String BIN = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    static final String TMP = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
    static final String HOME = TermuxConstants.TERMUX_HOME_DIR_PATH;

    static final String PULSEAUDIO = BIN + "/pulseaudio";
    static final String PACTL = BIN + "/pactl";
    static final String DBUS_DAEMON = BIN + "/dbus-daemon";
    static final String DBUS_SEND = BIN + "/dbus-send";
    static final String DBUS_MONITOR = BIN + "/dbus-monitor";
    static final String BASH = BIN + "/bash";
    static final String LAUNCHERCTL = BIN + "/launcherctl";

    /**
     * The server's runtime directory: its pid file and its socket. Its own, so a server some
     * client autospawned in the stock place ({@code ~/.config/pulse/*-runtime}) never holds the
     * pid file or the socket ours needs. Not in the prefix's tmp: the service empties that on every
     * stop (Exit, the last shell), which would leave a live server with no socket.
     */
    static final String RUNTIME_DIR = PREFIX + "/var/run/termux-launcher/sound";
    /** The server's socket for everything in the prefix. */
    static final String SOCKET = RUNTIME_DIR + "/native";
    /** The running server's pid, written by the server itself ({@code --use-pid-file=yes}). */
    static final String PID_FILE = RUNTIME_DIR + "/pid";
    /** What every prefix client is handed as {@code PULSE_SERVER}. */
    static final String PULSE_SERVER = "unix:" + SOCKET;
    /** PulseAudio's own port, on loopback, for the proot distros (decision 5). */
    static final int TCP_PORT = 4713;
    /** What a distro's drop-in names as its {@code default-server}. */
    static final String PULSE_SERVER_TCP = "tcp:127.0.0.1:" + TCP_PORT;

    /** What the launcher keeps between boots: the cookie, the startup file, the volumes. */
    static final String STATE_DIR = PREFIX + "/var/lib/termux-launcher/sound";
    /** The server's cookie; a distro client proves it may connect over TCP with a copy of it. */
    static final String COOKIE = STATE_DIR + "/cookie";
    /** The server's startup script, in place of Termux's {@code default.pa} (decision 2). */
    static final String STARTUP_FILE = STATE_DIR + "/startup.pa";
    /** Where the server keeps its stream and device volumes. */
    static final String VOLUMES_DIR = STATE_DIR + "/state";
    /** The session bus's configuration. */
    static final String BUS_CONFIG = STATE_DIR + "/bus.conf";

    /** The MPRIS and stream watcher; see {@code assets/sound/launcher-media.sh}. */
    static final String HELPER = PREFIX + "/libexec/termux-launcher/sound/termux-launcher-media";
    static final String HELPER_ASSET = "sound/launcher-media.sh";

    /**
     * The X socket directory: the one directory of the prefix's tmp a distro login shares, with
     * {@code --shared-x11} and with {@code --shared-tmp} alike, at {@code /tmp/.X11-unix}. The bus
     * socket sits here so a distro's apps can reach it.
     */
    static final String X11_SOCKETS = TMP + "/.X11-unix";
    /**
     * The bus socket's name. Never starting with {@code X}: the display code reads every
     * {@code X*} entry of the directory as a display.
     */
    static final String BUS_NAME = "termux-launcher-bus";
    static final String BUS_SOCKET = X11_SOCKETS + "/" + BUS_NAME;
    /** The bus for the prefix's shells and apps. */
    static final String BUS_ADDRESS = "unix:path=" + BUS_SOCKET;
    /** The same bus as a distro sees it. */
    static final String DISTRO_BUS_ADDRESS = "unix:path=/tmp/.X11-unix/" + BUS_NAME;

    /** The prefix's client drop-in, which libpulse reads after {@code $PREFIX/etc/pulse/client.conf}. */
    static final String TERMUX_DROP_IN = PREFIX + "/etc/pulse/client.conf.d/termux-launcher.conf";
    /** A distro's client drop-in, relative to its rootfs. */
    static final String DISTRO_DROP_IN = "etc/pulse/client.conf.d/termux-launcher.conf";
    /** The cookie's copy inside a distro, absolute there. */
    static final String DISTRO_COOKIE = "/etc/pulse/termux-launcher.cookie";

    /** The user's own PulseAudio configuration (decision 7). */
    static final String USER_CONFIG_DIR = HOME + "/.config/pulse";

    /** Every file the launcher writes for sound carries this line, so "is it ours?" is a read. */
    static final String MARKER = "# written by termux-launcher";

    /** The termux-exec preload libraries, newest first, as Termux's {@code login} picks them. */
    static final String[] EXEC_PRELOADS = {
        TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH + "/libtermux-exec-ld-preload.so",
        TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH + "/libtermux-exec.so"};
}
