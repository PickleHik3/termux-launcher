package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The shape Home's dock is drawn in, picked in the Layout editor while it edits Home's layout
 * (project-docs/active/home-dock-styles-2026-10-20.md, "The choice"). One value for Home, both
 * orientations; the Workspace's dock has no style and stays as it is.
 *
 * <p>The stored id is the contract: it is what the preference holds, so a style is renamed in
 * code and strings, never in {@link #storageValue}. Anything the store cannot read, an absent key
 * included, is {@link #GLASS_TRAY}, which is what every existing install migrates to.
 *
 * <p>A style can be chosen before it is drawn: {@link #effective} answers what the dock really
 * draws, so a style that is not in {@link #BUILT} yet renders as the glass tray.
 */
public enum DockStyle {
    /** Today's plank: one glass slab under the icons. The default. */
    GLASS_TRAY("glass_tray"),
    /** No plate: each app on its own round glass lens. */
    LENS_BEADS("lens_beads"),
    /** A Home tile beside the apps, and a strip of running windows above. */
    TASKBAR("taskbar"),
    /** The apps above a permanent command palette field, styled as a shell prompt. */
    PROMPT_LINE("prompt_line"),
    /** A first row for the Workspace (a clock and the terminals) above the apps. */
    WALL_STRIP("wall_strip");

    /**
     * The styles the dock can draw today. A style joins this set in the change that draws it;
     * until then choosing it shows the glass tray.
     */
    @NonNull
    public static final Set<DockStyle> BUILT = Collections.unmodifiableSet(EnumSet.of(GLASS_TRAY));

    @NonNull private final String mId;

    DockStyle(@NonNull String id) {
        mId = id;
    }

    /** The id the preference holds. Stable: never renamed. */
    @NonNull
    public String storageValue() {
        return mId;
    }

    /** The style a stored id names, or {@link #GLASS_TRAY} for an absent or unknown one. */
    @NonNull
    public static DockStyle parse(@Nullable String value) {
        if (value != null) {
            for (DockStyle style : values()) {
                if (style.mId.equals(value)) return style;
            }
        }
        return GLASS_TRAY;
    }

    /** What the dock draws for a stored choice: the choice where it is built, the glass tray otherwise. */
    @NonNull
    public static DockStyle effectiveStyle(@Nullable DockStyle stored, @NonNull Set<DockStyle> built) {
        return stored != null && built.contains(stored) ? stored : GLASS_TRAY;
    }

    /** {@link #effectiveStyle(DockStyle, Set)} against what is built today. */
    @NonNull
    public static DockStyle effectiveStyle(@Nullable DockStyle stored) {
        return effectiveStyle(stored, BUILT);
    }
}
