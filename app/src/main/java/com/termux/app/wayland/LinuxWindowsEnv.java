package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.X11GpuProbe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The environment a Linux app gets so it finds the Wayland host: one list of {@code NAME=value}
 * lines, added after the GPU profile's own lines ({@code X11GpuProbe.Recommendation.env}), which
 * the caller takes unchanged. {@link #gpuEnv} adds the lines the Wayland compositor wants on top
 * of a profile's. {@code X11LinuxAppRunner} builds an app's environment through this class, and
 * {@link #forSession} says what a new terminal session gets.
 *
 * Where the display is, the same on every side:
 * - The socket is {@code wayland-0} in the prefix's tmp ({@code $TMPDIR}), beside the X sockets in
 *   {@code .X11-unix/}: {@code <prefix>/tmp/wayland-0}. Termux guides already use that directory
 *   as the runtime directory ({@code XDG_RUNTIME_DIR=$TMPDIR}), so with it {@code wayland-0} is
 *   found the usual way, {@code $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY}.
 * - {@code WAYLAND_DISPLAY} is {@code wayland-0} when the client's {@code XDG_RUNTIME_DIR} is that
 *   directory, else the socket's absolute path, which libwayland takes as it is
 *   ({@link #waylandDisplay}).
 * - {@code XDG_RUNTIME_DIR} is not set here. Every shell gets one default, the prefix's tmp,
 *   from the profile files {@link com.termux.app.x11.RuntimeFolderProfile} writes (ADR 0036), so
 *   ssh, Termux:Boot and the launcher's own shells agree: a tool that keeps a socket there (Herdr)
 *   finds one core, not one per value (41fb70a16).
 * - A proot-distro container sees the same absolute path: a default (not isolated) login binds
 *   the whole prefix at its own path ({@link LinuxWindowsProot}).
 * - {@code DISPLAY=:n} only while Xwayland runs; an app that speaks only X11 goes through it.
 *   Xwayland takes the lowest free number, so {@code :0} when X11 Display is not running.
 * - Toolkit hints only where the toolkit falls back by itself: {@code GDK_BACKEND=wayland,x11}
 *   (GTK 3 and 4 try each in turn) and {@code QT_QPA_PLATFORM=wayland;xcb} (Qt tries each).
 *   {@code SDL_VIDEODRIVER} is not set: SDL before 2.0.22 reads it as one driver name, so a list
 *   fails outright, and a single "wayland" would leave no X fallback. {@code MOZ_ENABLE_WAYLAND}
 *   is not set: Firefox 121 and later pick Wayland from {@code WAYLAND_DISPLAY} themselves, and
 *   forcing it removes the X fallback of older ones. A terminal session gets no hints, only the
 *   two display variables a Wayland session has.
 * - The GPU profile does not change these lines yet: every profile's clients present through
 *   wl_shm or linear dmabufs, both of which the host copies. The id is taken so a profile that
 *   needs a Wayland-specific line later gets it here; an unknown id is treated like any other.
 */
public final class LinuxWindowsEnv {

    /** Xwayland's display number when none runs. */
    public static final int NO_X_DISPLAY = -1;

    private LinuxWindowsEnv() {}

    /**
     * The GPU lines an app gets under Wayland Display: {@code profileEnv} (what the GPU profile
     * already yields, unchanged), and for {@code turnip-zink} also {@code LIBGL_KOPPER_DRI2=1} and
     * {@code VK_DRIVER_FILES} naming the freedreno ICD. Measured on a phone: with these two and a
     * Mesa that carries the Wayland kopper patch, GL apps render on Turnip under this compositor;
     * on a stock Mesa they change nothing on Wayland. Other profiles pass through unchanged.
     */
    @NonNull
    public static List<String> gpuEnv(@NonNull List<String> profileEnv, @Nullable String gpuProfile) {
        List<String> env = new ArrayList<>(profileEnv);
        if (X11GpuProbe.Profile.of(gpuProfile) == X11GpuProbe.Profile.TURNIP_ZINK) {
            env.add("LIBGL_KOPPER_DRI2=1");
            env.add("VK_DRIVER_FILES=" + X11GpuProbe.FREEDRENO_ICD);
        }
        return env;
    }

    /**
     * {@code env} with the patched Mesa overlay's library directory first in the client's
     * {@code LD_LIBRARY_PATH} when {@code answer} says USE: prepended to an existing line's value,
     * else added as a line that keeps whatever the shell already has. Any other answer returns
     * {@code env} as it is (the app renders in software).
     */
    @NonNull
    public static List<String> withMesaOverlay(@NonNull List<String> env, @NonNull MesaWaylandOverlay.Answer answer) {
        if (answer.state != MesaWaylandOverlay.State.USE || answer.libDir == null) return env;
        List<String> out = new ArrayList<>(env.size() + 1);
        boolean merged = false;
        for (String line : env) {
            if (!merged && line.startsWith("LD_LIBRARY_PATH=")) {
                String old = line.substring("LD_LIBRARY_PATH=".length());
                out.add("LD_LIBRARY_PATH=" + answer.libDir + (old.isEmpty() ? "" : ":" + old));
                merged = true;
            } else {
                out.add(line);
            }
        }
        if (!merged) out.add("LD_LIBRARY_PATH=" + answer.libDir + "${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}");
        return out;
    }

    /** The number in a display such as {@code ":1"}, or {@link #NO_X_DISPLAY} for null, empty or anything else. */
    public static int displayNumber(@Nullable String display) {
        if (display == null || display.length() < 2 || display.charAt(0) != ':') return NO_X_DISPLAY;
        try {
            int n = Integer.parseInt(display.substring(1));
            return n < 0 ? NO_X_DISPLAY : n;
        } catch (NumberFormatException e) {
            return NO_X_DISPLAY;
        }
    }

    /**
     * {@code WAYLAND_DISPLAY} for the socket {@code socketName} in {@code socketDir} (absolute), for
     * a client whose {@code XDG_RUNTIME_DIR} is {@code runtimeDir} (null when it has none): the bare
     * name when that directory is the socket's, so {@code $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY} is the
     * socket; otherwise the absolute path, which libwayland uses without a runtime directory.
     */
    @NonNull
    public static String waylandDisplay(@NonNull String socketDir, @NonNull String socketName,
                                        @Nullable String runtimeDir) {
        String dir = trimSlashes(socketDir);
        if (!dir.startsWith("/")) throw new IllegalArgumentException("socket dir must be absolute: " + socketDir);
        if (runtimeDir != null && dir.equals(trimSlashes(runtimeDir.trim()))) return socketName;
        return dir + "/" + socketName;
    }

    @NonNull
    private static String trimSlashes(@NonNull String path) {
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') end--;
        return path.substring(0, end);
    }

    /**
     * What a new terminal session gets, as {@code NAME -> value}: {@code DISPLAY} and
     * {@code WAYLAND_DISPLAY}, each only when there is one; nothing when {@code wanted} (the
     * setting that gives new sessions the display) is off. While the Wayland host runs
     * ({@code waylandDisplay} not null): {@code WAYLAND_DISPLAY}, and its Xwayland as
     * {@code DISPLAY} ({@code x11Display} until Xwayland has said its number). Otherwise X11
     * Display's server {@code x11Display} (null when it is not running), as before.
     */
    @NonNull
    public static Map<String, String> forSession(boolean wanted, @Nullable String x11Display,
                                                 @Nullable String waylandDisplay,
                                                 @Nullable String xwaylandDisplay) {
        Map<String, String> env = new LinkedHashMap<>();
        if (!wanted) return env;
        String display = x11Display;
        if (!isBlank(waylandDisplay)) {
            env.put("WAYLAND_DISPLAY", waylandDisplay.trim());
            if (!isBlank(xwaylandDisplay)) display = xwaylandDisplay;
        }
        if (!isBlank(display)) env.put("DISPLAY", display.trim());
        return env;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * @param socketPath the Wayland socket, absolute ({@code <prefix>/tmp/wayland-0}). An app the
     *                   launcher starts has no {@code XDG_RUNTIME_DIR}, so this is its
     *                   {@code WAYLAND_DISPLAY}.
     * @param xDisplay   Xwayland's display number, or {@link #NO_X_DISPLAY}.
     * @param gpuProfile an {@link X11GpuProbe.Profile} id, or null.
     */
    @NonNull
    public static List<String> forClient(@NonNull String socketPath, int xDisplay, @Nullable String gpuProfile) {
        if (!socketPath.startsWith("/")) throw new IllegalArgumentException("socket path must be absolute: " + socketPath);
        // Reserved for profile-specific lines; see the class comment.
        X11GpuProbe.Profile.of(gpuProfile);
        List<String> env = new ArrayList<>();
        env.add("WAYLAND_DISPLAY=" + socketPath);
        if (xDisplay >= 0) env.add("DISPLAY=:" + xDisplay);
        env.add("GDK_BACKEND=wayland,x11");
        env.add("QT_QPA_PLATFORM=wayland;xcb");
        return Collections.unmodifiableList(env);
    }
}
