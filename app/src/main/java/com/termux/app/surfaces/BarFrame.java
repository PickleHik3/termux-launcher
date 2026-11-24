package com.termux.app.surfaces;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;

/** What the bar controllers read of the frame: its overlay, scale, rects and the editor's colours. */
interface BarFrame {
    @NonNull Context context();

    /** The tap overlay the chips and lines join, or null while it is not there. */
    @Nullable ViewGroup overlay();

    /** The frame's scale, so what is drawn keeps its on-screen size. */
    float scale();

    boolean open();

    /** Where {@code bar} stands in the overlay (l, t, r, b); false when it is not on screen. */
    boolean rectOf(@NonNull Bar bar, @NonNull float[] out);

    /** The Hidden column's rect in the overlay (#339); false until it exists. */
    default boolean hiddenRect(@NonNull float[] out) {
        return false;
    }

    /** The pane's rect in the overlay (#342); false until the status bar may stand there. */
    default boolean paneRect(@NonNull float[] out) {
        return false;
    }

    int primaryColor();

    int surfaceColor();

    int onSurfaceColor();
}
