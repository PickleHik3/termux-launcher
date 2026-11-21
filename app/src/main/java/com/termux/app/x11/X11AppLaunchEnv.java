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
 * with. A whole desktop gets neither, it follows the display. Pure; the runner exports the result.
 */
public final class X11AppLaunchEnv {

    /** The variables the scale puts on an app, in the order {@link #scaleLines} writes them. */
    public static final List<String> SCALE_VARIABLES = Collections.unmodifiableList(Arrays.asList(
        "GDK_SCALE", "GDK_DPI_SCALE", "QT_SCALE_FACTOR", "QT_AUTO_SCREEN_SCALE_FACTOR",
        "QT_ENABLE_HIGHDPI_SCALING"));

    private X11AppLaunchEnv() {}

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

    /** The profile pick the app launches with: its own, else the display's; a desktop always the display's. */
    @NonNull
    public static String gpuPick(boolean desktop, @NonNull String displayPick, @Nullable String appProfile) {
        return !desktop && X11AppSettings.isGpuProfile(appProfile) ? appProfile : displayPick;
    }

    /** Whether the app draws in software whatever the pick; never for a desktop. */
    public static boolean drawsInSoftware(boolean desktop, boolean appSoftware) {
        return !desktop && appSoftware;
    }
}
