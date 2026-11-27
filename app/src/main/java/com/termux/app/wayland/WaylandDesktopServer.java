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
 *       ends it too (Xwayland exits when its toplevel is closed), and the same watcher then ends
 *       the desktop, whose own sweep ends everything it left ({@link #startBlock}).</li>
 * </ul>
 *
 * <p>Size and scale: X toolkits scale widgets by whole factors only (GTK's window scale, Qt's
 * device pixel ratio), so a desktop at a Scale stop between two (175%) would draw its panel and
 * icons at the lower one. The desktop is therefore drawn at the whole factor at or above the stop
 * ({@link #drawFactor}: 175% draws at 2) and shown scaled down by the rest:
 * <ul>
 *   <li>The server runs without {@code -hidpi}, so its root is the window's logical size, which
 *       Xwayland follows at each configure (rotation, keyboard).</li>
 *   <li>The launcher configures the desktop's window ({@link #isRootfulTitle}) at
 *       {@link #hostScale}, stop / factor (0.875 at 175%), not at the stop: its logical size, and
 *       so the root, is the view's pixels x factor / stop, and the view shows that buffer scaled
 *       to its own pixels. Panel, icons, decorations and text all come out at the stop together;
 *       a whole stop is drawn 1:1, as before.</li>
 * </ul>
 * The desktop is told the factor the way an X desktop is told on a dense monitor
 * ({@link #scaleEnv}, {@link #startBlock}):
 * <ul>
 *   <li>{@code GDK_SCALE} (the factor) and {@code GDK_DPI_SCALE=1}: GTK 3 and 4 take these over
 *       any XSETTINGS, so MATE's own settings daemon cannot undo them.</li>
 *   <li>{@code Xft.dpi} on the root window (96 x factor), through {@code xrdb} when it is there:
 *       Qt 6 on X11 draws at Xft.dpi / 96 (LXQt), Xft clients size their fonts by it. XFCE's
 *       settings daemon writes its own value there later.</li>
 *   <li>{@code XCURSOR_SIZE}, so the pointer is not a speck.</li>
 * </ul>
 * <ul>
 *   <li>XFCE's own settings: its settings daemon (xfsettingsd) owns XSETTINGS and writes
 *       {@code Xft/DPI} from xfconf ({@code xsettings /Xft/DPI}, 96 unless set) over the root
 *       window's, so the desktop's start sets {@code /Xft/DPI} to 96 and
 *       {@code /Gdk/WindowScalingFactor} to the factor there ({@link #desktopScaleBlock}), once
 *       its session bus is up, with no exports for GTK to take on top. A new Scale stop writes
 *       the same two values into the running desktop ({@link #liveScaleScript}) through the bus
 *       address the start recorded ({@link #busFile}); xfsettingsd then hands them to every
 *       client, while the window's new configure resizes the root. MATE and LXQt are not set
 *       this way: their bus and settings come up inside their own session, after the start block
 *       could write them, so they keep the factor they started with.</li>
 * </ul>
 * The scale is the desktop app's Scale (the rail's stop) or the screen's density; a new stop
 * reaches an XFCE desktop at once, and any other the next time it starts. {@code -geometry}
 * starts the server at the screen's size at {@link #hostScale} so the first configure is a small
 * step.
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
    /** How long the session is given to end after its server went: steps of {@link #READY_STEP_S} (10 s). */
    static final int END_STEPS = 100;
    /** How often the watcher looks whether the desktop is still there, in seconds. */
    static final int WATCH_PERIOD_S = 2;

    /**
     * The {@code WAYLAND_DISPLAY} an app or desktop kept off Wayland is given: set, and empty.
     * libwayland falls back to {@code wayland-0} in {@code XDG_RUNTIME_DIR} only when the variable
     * is unset, and there it found the host's socket (#391); an empty name makes every Wayland
     * connect fail, so clients take the X server, without changing {@code XDG_RUNTIME_DIR}, which
     * stays the shells' own (41fb70a16). Empty rather than a made-up name: toolkits that choose
     * their backend from a non-empty {@code WAYLAND_DISPLAY} (gpui, so Herdr and Zed; winit) take
     * X11 at once, where a name sent gpui to that socket and it stopped with a panic.
     */
    public static final String NO_WAYLAND_DISPLAY = "";

    /** The title Xwayland 24.1 gives its rootful window, followed by the display number. */
    static final String ROOTFUL_TITLE_PREFIX = "Xwayland on :";

    private static final String TMP = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH;
    private static final String LOCK_PREFIX = ".X";
    private static final String LOCK_SUFFIX = "-lock";
    private static final String SOCKET_DIR = ".X11-unix";
    private static final String SOCKET_PREFIX = "X";

    /** The DPI X toolkits take as scale 1, and the cursor size at scale 1 ({@code xscale.rs}). */
    static final int BASE_DPI = 96;
    static final int BASE_CURSOR_SIZE = 24;

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

    /**
     * The size a desktop's server starts at: the screen's size now as the logical size its window
     * is configured at for {@code scale} ({@link #hostScale}).
     */
    @NonNull
    public static int[] startSize(@NonNull Context context, float scale) {
        WaylandAppRunner.HostStart start = WaylandAppRunner.hostStart(context);
        return logicalSize(start.width, start.height, hostScale(scale));
    }

    /** {@code scale} when usable, else 1. */
    private static float sane(float scale) {
        return scale > 0f && !Float.isNaN(scale) && !Float.isInfinite(scale) ? scale : 1f;
    }

    /** The session kinds whose settings daemon is written to ({@link #desktopScaleBlock}). */
    public static final String KIND_XFCE = "xfce";

    /**
     * The shell path, in the desktop's own world, of the file holding its session bus address:
     * {@code $TMPDIR} is the prefix's tmp, or the container's own. Written at start, read by a
     * later Scale stop.
     */
    @NonNull
    static String busFile(int display) {
        return "\"${TMPDIR:-/tmp}/tl-desktop-x" + display + ".bus\"";
    }

    /**
     * The xfconf writes that make XFCE's settings daemon draw at {@code scale}'s factor:
     * {@code /Xft/DPI} 96 (GTK draws text at the window scale already) and
     * {@code /Gdk/WindowScalingFactor} ({@link #drawFactor}), both ints, created when
     * missing ({@code -n}), so running it again changes nothing. Nothing at all without
     * {@code xfconf-query}. Pure.
     */
    @NonNull
    static String xfconfWrites(float scale) {
        String q = "xfconf-query -c xsettings -n -t int";
        return "if command -v xfconf-query >/dev/null 2>&1; then\n"
            + q + " -p /Xft/DPI -s " + BASE_DPI + " 2>/dev/null\n"
            + q + " -p /Gdk/WindowScalingFactor -s " + drawFactor(scale) + " 2>/dev/null\n"
            + "fi\n";
    }

    /**
     * For a desktop of {@code kind} ({@link #KIND_XFCE}), the lines for the session body right
     * after its bus is up and before the desktop starts: record the bus address
     * ({@link #busFile}) and write the scale into the settings daemon's store
     * ({@link #xfconfWrites}), which xfconfd, started on that bus, serves to xfsettingsd when it
     * starts. Empty for any other kind, and when there is no bus. Pure.
     */
    @NonNull
    public static String desktopScaleBlock(@Nullable String kind, float scale, int display) {
        if (!KIND_XFCE.equals(kind)) return "";
        return "if [ -n \"$DBUS_SESSION_BUS_ADDRESS\" ]; then\n"
            + "echo \"$DBUS_SESSION_BUS_ADDRESS\" > " + busFile(display) + "\n"
            + xfconfWrites(scale)
            + "fi\n";
    }

    /**
     * A new Scale for a desktop already running on {@code display}: the same writes, on the bus
     * the start recorded. Exits quietly when the desktop is not one of {@code kind} or recorded
     * none. Run in the desktop's world. Pure.
     */
    @NonNull
    public static String liveScaleScript(@Nullable String kind, float scale, int display) {
        if (!KIND_XFCE.equals(kind)) return "";
        return "tl_bus=$(cat " + busFile(display) + " 2>/dev/null)\n"
            + "[ -n \"$tl_bus\" ] || exit 0\n"
            + "export DBUS_SESSION_BUS_ADDRESS=\"$tl_bus\"\n"
            + xfconfWrites(scale)
            + "exit 0\n";
    }

    /** How far below a whole number a scale may sit and still count as it (float stops). */
    private static final float WHOLE_SLACK = 0.01f;

    /**
     * The whole factor a desktop at {@code scale} draws at ({@code GDK_SCALE}, XFCE's window
     * scale, Qt's pixel ratio): the stop rounded up, at least 1. 175% and 125% draw at 2, 250% at
     * 3; the view scales the result down by {@link #hostScale}. Pure.
     */
    public static int drawFactor(float scale) {
        return Math.max(1, (int) Math.ceil(sane(scale) - WHOLE_SLACK));
    }

    /**
     * The scale the launcher configures a desktop's window at for the Scale {@code scale}: stop /
     * {@link #drawFactor}, in (0.5, 1]. The window's logical size, and so the root of a server
     * without {@code -hidpi}, is then its view's pixels x factor / stop: drawn at the factor, the
     * desktop shows at the stop. Whole stops give 1 (drawn 1:1); every rail stop gives a multiple
     * of 1/120, which the host carries exactly. Pure.
     */
    public static float hostScale(float scale) {
        float s = sane(scale);
        return s / drawFactor(s);
    }

    /** The {@code Xft.dpi} a desktop at {@code scale} is told: 96 x its factor. Pure. */
    static int xftDpi(float scale) {
        return BASE_DPI * drawFactor(scale);
    }

    /**
     * The exports a desktop at {@code scale} gets so its toolkits draw at its factor (see the class
     * comment): {@code GDK_SCALE} ({@link #drawFactor}), {@code GDK_DPI_SCALE=1} (text at the same
     * factor, over any inherited value) and {@code XCURSOR_SIZE}. Pure.
     */
    @NonNull
    public static List<String> scaleEnv(float scale) {
        return scaleEnv(scale, null);
    }

    /**
     * {@link #scaleEnv(float)} for a desktop of {@code kind}. An XFCE desktop gets no
     * {@code GDK_SCALE}/{@code GDK_DPI_SCALE}: its settings daemon serves the scale from xfconf
     * ({@link #xfconfWrites}) and follows a new Scale stop live, which the exports would pin. Pure.
     */
    @NonNull
    public static List<String> scaleEnv(float scale, @Nullable String kind) {
        int factor = drawFactor(scale);
        List<String> env = new ArrayList<>();
        if (!KIND_XFCE.equals(kind)) {
            env.add("GDK_SCALE=" + factor);
            env.add("GDK_DPI_SCALE=1");
        }
        env.add("XCURSOR_SIZE=" + BASE_CURSOR_SIZE * factor);
        return Collections.unmodifiableList(env);
    }

    /** The prefix's Xwayland when it is executable; null otherwise. File work. */
    @Nullable
    public static String xwaylandBinary() {
        return WaylandAppRunner.xwaylandBinary();
    }

    /**
     * The server's arguments after its program: the display, the start size ({@code -geometry},
     * rootful, logical; no {@code -hidpi}, so the root is the window's logical size, see the class
     * comment) and {@code -noreset}, so the server does not reset (and drop its window) whenever
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
     *       script becomes) is gone, and the session once the server is gone: closing the window
     *       ends Xwayland, and the desktop, which never reaps it, would otherwise run on (#391).
     *       A zombie counts as gone. The session is asked to end, then killed after
     *       {@link #END_STEPS} x {@link #READY_STEP_S} seconds; its own sweep then ends the rest
     *       ({@code X11LinuxAppRunner.SESSION_END_BLOCK});</li>
     *   <li>{@code Xft.dpi} for {@code scale} merged into the server's resources, when {@code xrdb}
     *       is installed;</li>
     *   <li>GTK and Qt pinned to X11 and {@code WAYLAND_DISPLAY} set empty
     *       ({@link #NO_WAYLAND_DISPLAY}), so an app opened inside the desktop, and the desktop's
     *       own panel, open on its screen, not as windows of their own.</li>
     * </ul>
     * Pure.
     */
    @NonNull
    public static String startBlock(@NonNull String xwayland, @NonNull String waylandSocket, int display,
                                    int width, int height, float scale) {
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
        b.append("tl_live() { { read -r tl_s < \"/proc/$1/stat\"; } 2>/dev/null || return 1;")
            .append(" case \"$tl_s\" in *') Z '*) return 1;; esac; }\n");
        b.append("(while tl_live $$ && tl_live \"$tl_desktop_x\"; do sleep ").append(WATCH_PERIOD_S)
            .append("; done\n");
        b.append("kill \"$tl_desktop_x\" 2>/dev/null\n");
        b.append("if tl_live $$; then\n");
        b.append("kill $$ 2>/dev/null\n");
        b.append("tl_desktop_n=0\n");
        b.append("while tl_live $$ && [ \"$tl_desktop_n\" -lt ").append(END_STEPS).append(" ]; do\n");
        b.append("sleep ").append(READY_STEP_S).append('\n');
        b.append("tl_desktop_n=$((tl_desktop_n+1))\n");
        b.append("done\n");
        b.append("tl_live $$ && kill -9 $$ 2>/dev/null\n");
        b.append("fi) &\n");
        b.append("if command -v xrdb >/dev/null 2>&1; then\n");
        b.append("echo 'Xft.dpi: ").append(xftDpi(scale)).append("' | DISPLAY=:").append(display)
            .append(" xrdb -nocpp -merge\n");
        b.append("fi\n");
        b.append("export GDK_BACKEND=x11 QT_QPA_PLATFORM=xcb WAYLAND_DISPLAY=")
            .append(NO_WAYLAND_DISPLAY).append('\n');
        b.append("unset tl_desktop_ready tl_desktop_n\n");
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

    /**
     * Whether {@code title} is a rootful Xwayland's window ({@link #ROOTFUL_TITLE_PREFIX} and a
     * digit): a desktop's, which the launcher configures at {@link #hostScale}. Read from the title
     * alone, so a desktop still running after the launcher restarted is known too. Pure.
     */
    public static boolean isRootfulTitle(@Nullable String title) {
        return title != null && title.length() > ROOTFUL_TITLE_PREFIX.length()
            && title.startsWith(ROOTFUL_TITLE_PREFIX)
            && Character.isDigit(title.charAt(ROOTFUL_TITLE_PREFIX.length()));
    }

    /** Whether {@code title} is a running desktop's server window. */
    public static boolean isDesktopWindow(@Nullable String title) {
        String named = tabTitle(title);
        return named != null && !named.equals(title);
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
