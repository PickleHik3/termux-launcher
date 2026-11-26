package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What one app's own settings add to its launch: scale variables and which GPU profile it draws
 * with. A whole desktop gets no scale; its graphics are its own pick or the display's. Pure; the runner exports the result.
 */
public final class X11AppLaunchEnv {

    /** The variables the scale puts on an app, in the order {@link #scaleLines} writes them. */
    public static final List<String> SCALE_VARIABLES = Collections.unmodifiableList(Arrays.asList(
        "GDK_SCALE", "GDK_DPI_SCALE", "QT_SCALE_FACTOR", "QT_AUTO_SCREEN_SCALE_FACTOR",
        "QT_ENABLE_HIGHDPI_SCALING"));

    /**
     * What an app or desktop sent to X11 Display is launched with, so it draws there: the shells'
     * runtime folder holds the Wayland host's {@code wayland-0} in a Wayland build, and libwayland
     * connects to it whenever {@code WAYLAND_DISPLAY} is unset, so the variable names a socket
     * that never exists ({@link com.termux.app.wayland.WaylandDesktopServer#NO_WAYLAND_DISPLAY});
     * GTK, Qt, SDL and Firefox are each pinned to X11 as well. One token per value. Carried into a
     * container by its X11 Display login ({@link ProotDistro#FORWARDED_ENV}).
     */
    public static final List<String> X11_DISPLAY_ONLY = Collections.unmodifiableList(Arrays.asList(
        "WAYLAND_DISPLAY=" + com.termux.app.wayland.WaylandDesktopServer.NO_WAYLAND_DISPLAY,
        "GDK_BACKEND=x11", "QT_QPA_PLATFORM=xcb", "SDL_VIDEODRIVER=x11", "MOZ_ENABLE_WAYLAND=0"));

    private X11AppLaunchEnv() {}

    /**
     * {@code env} for a launch that {@code backend} answers: under X11 Display led by
     * {@link #X11_DISPLAY_ONLY} (so a GPU profile still has the last word), under Wayland Display
     * as it is.
     */
    @NonNull
    public static List<String> forDisplay(@NonNull com.termux.app.wayland.LinuxDisplayBackend backend,
                                          @NonNull List<String> env) {
        if (backend != com.termux.app.wayland.LinuxDisplayBackend.X11) return env;
        List<String> lines = new ArrayList<>(X11_DISPLAY_ONLY);
        lines.addAll(env);
        return lines;
    }

    /**
     * {@code KEY=${KEY:-value}} lines for the app's scale, empty for a desktop or Follow. The shell
     * default means a value the user or the app's environment already set is left alone.
     */
    @NonNull
    public static List<String> scaleLines(boolean desktop, @Nullable String scale) {
        List<String> lines = new ArrayList<>();
        if (desktop) return lines;
        for (Map.Entry<String, String> e : X11AppScaleEnv.of(scale).env.entrySet()) {
            lines.add(e.getKey() + "=${" + e.getKey() + ":-" + e.getValue() + "}");
        }
        return lines;
    }

    /**
     * The profile pick a launch uses: the app's or desktop's own, else the display's. A desktop
     * inside a container has no Graphics row, so it always takes the display's.
     */
    @NonNull
    public static String gpuPick(boolean desktop, boolean inContainer, @NonNull String displayPick,
                                 @Nullable String ownProfile) {
        return !(desktop && inContainer) && X11AppSettings.isGpuProfile(ownProfile) ? ownProfile : displayPick;
    }

    /** Whether the launch draws in software whatever the pick; a container's desktop never does. */
    public static boolean drawsInSoftware(boolean desktop, boolean inContainer, boolean ownSoftware) {
        return !(desktop && inContainer) && ownSoftware;
    }
}
