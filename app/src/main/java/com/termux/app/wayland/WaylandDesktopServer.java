package com.termux.app.wayland;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.ProotDistro;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The X server of a whole desktop under Wayland Display: a rootful Xwayland, a client of the
 * Linux windows host, whose one window holds the desktop. {@code X11LinuxAppRunner} runs the
 * desktop exactly as on X11 Display (its session environment, bus, pid file, log and the
 * one-desktop question) and puts {@link #startBlock} in its session script, before the desktop.
 *
 * <p>How the server is held:
 * <ul>
 *   <li>Its display number is chosen here ({@link #pickDisplay}): never {@code :0} (X11 Display's),
 *       never the host's own rootless Xwayland, never one with a lock or socket in the prefix's
 *       tmp, where Termux's X servers keep both.</li>
 *   <li>The script waits for it with {@code -displayfd}, which the server writes once its screen
 *       is up, for at most {@link #READY_STEPS} x {@link #READY_STEP_S} seconds. A server that does
 *       not come up ends the script with {@link #EXIT_NO_SCREEN}, which the runner turns into a
 *       notice; nothing waits on it beyond that.</li>
 *   <li>It dies with the host: it is the host's Wayland client, and Xwayland exits when its
 *       compositor connection goes. It dies with the desktop: a watcher started beside it ends it
 *       within {@link #WATCH_PERIOD_S} seconds of the desktop's process going. The window's close
 *       ends it too (Xwayland exits when its toplevel is closed), and the desktop then loses its
 *       display and ends.</li>
 * </ul>
 *
 * <p>Size and scale: the server is not given {@code -hidpi}. Its root is the window's logical
 * size (the host configures an xdg window in logical units), so a desktop draws at the size it
 * would on an ordinary 96 dpi monitor and the window's view scales it up to the screen's density:
 * the right size on every desktop and every X toolkit inside it, a little soft. Xwayland 24.1
 * resizes its root to each configure, so the desktop follows the window (rotation, keyboard).
 * {@code -geometry} starts it at the screen's logical size so the first configure is a small step.
 */
public final class WaylandDesktopServer {

    /** X11 Display's display number, which a desktop's server never takes. */
    public static final int X11_DISPLAY = 0;
    /** The display numbers a desktop's server may take, first to last. */
    static final int FIRST_DISPLAY = X11_DISPLAY + 1;
    static final int LAST_DISPLAY = 32;
    /** No display number is free. */
    public static final int NO_DISPLAY = -1;

    /** What the session script exits with when the desktop's server did not come up. */
    public static final int EXIT_NO_SCREEN = 91;
    /** The readiness wait: this many steps of {@link #READY_STEP_S} seconds (10 s). */
    static final int READY_STEPS = 100;
    static final String READY_STEP_S = "0.1";
    /** How often the watcher looks whether the desktop is still there, in seconds. */
    static final int WATCH_PERIOD_S = 2;

    /** The title Xwayland 24.1 gives its rootful window, followed by the display number. */
    static final String ROOTFUL_TITLE_PREFIX = "Xwayland on :";

    private static final String TMP = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
    private static final String LOCK_PREFIX = ".X";
    private static final String LOCK_SUFFIX = "-lock";
    private static final String SOCKET_DIR = ".X11-unix";
    private static final String SOCKET_PREFIX = "X";

    /** Each running desktop's name by its display number, for the window's tab. */
    private static final Map<Integer, String> NAMES = new ConcurrentHashMap<>();

    private WaylandDesktopServer() {}

    /**
     * The lowest display number from {@link #FIRST_DISPLAY} to {@link #LAST_DISPLAY} that is not
     * in {@code taken}, not X11 Display's and not {@code rootless} (the host's Xwayland, or a
     * negative number for none); {@link #NO_DISPLAY} when every one is taken. Pure.
     */
    public static int pickDisplay(@NonNull Set<Integer> taken, int rootless) {
        for (int n = FIRST_DISPLAY; n <= LAST_DISPLAY; n++) {
            if (n == rootless || taken.contains(n)) continue;
            return n;
        }
        return NO_DISPLAY;
    }

    /** The display number a lock file name ({@code .X3-lock}) names, else -1. Pure. */
    static int lockDisplay(@NonNull String name) {
        if (!name.startsWith(LOCK_PREFIX) || !name.endsWith(LOCK_SUFFIX)) return -1;
        return number(name.substring(LOCK_PREFIX.length(), name.length() - LOCK_SUFFIX.length()));
    }

    /** The display number a socket name in {@code .X11-unix} ({@code X3}) names, else -1. Pure. */
    static int socketDisplay(@NonNull String name) {
        if (!name.startsWith(SOCKET_PREFIX)) return -1;
        return number(name.substring(SOCKET_PREFIX.length()));
    }

    private static int number(@NonNull String digits) {
        if (digits.isEmpty() || digits.length() > 4) return -1;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) return -1;
        }
        return Integer.parseInt(digits);
    }

    /** The display numbers with a lock or a socket in {@code tmp} (the prefix's tmp). File work. */
    @NonNull
    public static Set<Integer> takenDisplays(@NonNull File tmp) {
        Set<Integer> taken = new HashSet<>();
        addAll(taken, tmp.list(), true);
        addAll(taken, new File(tmp, SOCKET_DIR).list(), false);
        return taken;
    }

    /** {@link #takenDisplays(File)} in the prefix's tmp. */
    @NonNull
    public static Set<Integer> takenDisplays() {
        return takenDisplays(new File(TMP));
    }

    private static void addAll(@NonNull Set<Integer> into, @Nullable String[] names, boolean locks) {
        if (names == null) return;
        for (String name : names) {
            int n = locks ? lockDisplay(name) : socketDisplay(name);
            if (n >= 0) into.add(n);
        }
    }

    /**
     * {@code width} x {@code height} pixels at {@code scale}, as the logical size the host asks of
     * a window (each side rounded, at least 1). Pure.
     */
    @NonNull
    public static int[] logicalSize(int width, int height, float scale) {
        float s = scale > 0f && !Float.isNaN(scale) ? scale : 1f;
        return new int[] {Math.max(1, Math.round(width / s)), Math.max(1, Math.round(height / s))};
    }

    /** The size a desktop's server starts at: the screen's logical size now. */
    @NonNull
    public static int[] startSize(@NonNull Context context) {
        WaylandAppRunner.HostStart start = WaylandAppRunner.hostStart(context);
        return logicalSize(start.width, start.height, start.scale);
    }

    /** The prefix's Xwayland when it is executable; null otherwise. File work. */
    @Nullable
    public static String xwaylandBinary() {
        return WaylandAppRunner.xwaylandBinary();
    }

    /**
     * The server's arguments after its program: the display, the start size ({@code -geometry},
     * rootful), and {@code -noreset}, so the server does not reset (and drop its window) whenever
     * its last client leaves, as it would between a desktop's start-up helpers. Size below 1 is
     * left out. Pure.
     */
    @NonNull
    public static List<String> serverArgs(int display, int width, int height) {
        List<String> args = new ArrayList<>();
        args.add(":" + display);
        if (width > 0 && height > 0) {
            args.add("-geometry");
            args.add(width + "x" + height);
        }
        args.add("-noreset");
        return Collections.unmodifiableList(args);
    }

    /** The file the server writes its display number into once it is up. */
    @NonNull
    static String readyPath(int display) {
        return TMP + "/tl-desktop-x" + display + ".ready";
    }

    /**
     * The shell lines that start the desktop's server and wait for it, for the session script
     * after its output goes to the session log and its pid is written down, before the desktop:
     * <ul>
     *   <li>the server, with {@code WAYLAND_DISPLAY} for itself only, writing its display to a
     *       file through {@code -displayfd 3} once its screen is up;</li>
     *   <li>the bounded wait for that file, ending at once if the server exits; on no answer, the
     *       server is ended and the script exits with {@link #EXIT_NO_SCREEN};</li>
     *   <li>the watcher, which ends the server once the session's process ({@code $$}, which the
     *       script becomes) is gone;</li>
     *   <li>{@code WAYLAND_DISPLAY} and the toolkit hints taken out of the desktop's environment,
     *       so an app opened inside the desktop opens on its screen, not as a window of its own.</li>
     * </ul>
     * Pure.
     */
    @NonNull
    public static String startBlock(@NonNull String xwayland, @NonNull String waylandSocket, int display,
                                    int width, int height) {
        String ready = ProotDistro.singleQuote(readyPath(display));
        StringBuilder b = new StringBuilder();
        b.append("tl_desktop_ready=").append(ready).append('\n');
        b.append("rm -f \"$tl_desktop_ready\"\n");
        b.append("WAYLAND_DISPLAY=").append(ProotDistro.singleQuote(waylandSocket)).append(' ')
            .append(ProotDistro.singleQuote(xwayland));
        for (String arg : serverArgs(display, width, height)) b.append(' ').append(arg);
        b.append(" -displayfd 3 3>\"$tl_desktop_ready\" &\n");
        b.append("tl_desktop_x=$!\n");
        b.append("tl_desktop_n=0\n");
        b.append("while [ ! -s \"$tl_desktop_ready\" ] && kill -0 \"$tl_desktop_x\" 2>/dev/null")
            .append(" && [ \"$tl_desktop_n\" -lt ").append(READY_STEPS).append(" ]; do\n");
        b.append("sleep ").append(READY_STEP_S).append('\n');
        b.append("tl_desktop_n=$((tl_desktop_n+1))\n");
        b.append("done\n");
        b.append("if [ ! -s \"$tl_desktop_ready\" ]; then\n");
        b.append("echo \"The desktop's X server did not start on :").append(display).append("\"\n");
        b.append("kill \"$tl_desktop_x\" 2>/dev/null\n");
        b.append("rm -f \"$tl_desktop_ready\"\n");
        b.append("exit ").append(EXIT_NO_SCREEN).append('\n');
        b.append("fi\n");
        b.append("rm -f \"$tl_desktop_ready\"\n");
        b.append("(while kill -0 $$ 2>/dev/null && kill -0 \"$tl_desktop_x\" 2>/dev/null; do sleep ")
            .append(WATCH_PERIOD_S).append("; done; kill \"$tl_desktop_x\" 2>/dev/null) &\n");
        b.append("unset WAYLAND_DISPLAY GDK_BACKEND QT_QPA_PLATFORM tl_desktop_ready tl_desktop_n\n");
        return b.toString();
    }

    /** The desktop {@code name} runs on {@code display}: its window's tab shows that name. */
    public static void named(int display, @NonNull String name) {
        NAMES.put(display, name);
    }

    /** The desktop on {@code display} ended. */
    public static void forget(int display) {
        NAMES.remove(display);
    }

    /** {@code title}, or the running desktop's name when it is that desktop's server window. */
    @Nullable
    public static String tabTitle(@Nullable String title) {
        return tabTitle(title, NAMES);
    }

    /**
     * {@code title}, or {@code names}' entry for its display when it is a rootful Xwayland's title
     * ({@link #ROOTFUL_TITLE_PREFIX} and the number, anything after the number allowed). Pure.
     */
    @Nullable
    static String tabTitle(@Nullable String title, @NonNull Map<Integer, String> names) {
        if (title == null || !title.startsWith(ROOTFUL_TITLE_PREFIX)) return title;
        int start = ROOTFUL_TITLE_PREFIX.length();
        int end = start;
        while (end < title.length() && Character.isDigit(title.charAt(end))) end++;
        int display = number(title.substring(start, end));
        if (display < 0) return title;
        String name = names.get(display);
        return name == null ? title : name;
    }
}
