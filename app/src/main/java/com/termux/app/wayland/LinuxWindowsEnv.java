package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.X11GpuProbe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The environment a Linux app gets so it finds the Wayland host: one list of {@code NAME=value}
 * lines, added after the GPU profile's own lines ({@code X11GpuProbe.Recommendation.env}), which
 * the caller takes unchanged. {@link #gpuEnv} adds the lines the Wayland compositor wants on top
 * of a profile's. {@code X11LinuxAppRunner} builds an app's environment through this class.
 *
 * Rules:
 * - {@code WAYLAND_DISPLAY} is the socket's absolute path (libwayland takes one), so nothing
 *   depends on {@code XDG_RUNTIME_DIR}, which is never set: changing it broke another app's own
 *   socket lookup on the phone.
 * - {@code DISPLAY=:n} only while Xwayland runs; an app that speaks only X11 goes through it.
 * - Toolkit hints only where the toolkit falls back by itself: {@code GDK_BACKEND=wayland,x11}
 *   (GTK 3 and 4 try each in turn) and {@code QT_QPA_PLATFORM=wayland;xcb} (Qt tries each).
 *   {@code SDL_VIDEODRIVER} is not set: SDL before 2.0.22 reads it as one driver name, so a list
 *   fails outright, and a single "wayland" would leave no X fallback. {@code MOZ_ENABLE_WAYLAND}
 *   is not set: Firefox 121 and later pick Wayland from {@code WAYLAND_DISPLAY} themselves, and
 *   forcing it removes the X fallback of older ones.
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
     * @param socketPath the Wayland socket, absolute ({@code <prefix>/tmp/tl-wayland/wayland-0}).
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
