package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The shape the dock is drawn in (the glossary's <b>Dock design</b>), picked in the Layout editor
 * with the dock selected on any page (project-docs/active/home-dock-styles-2026-10-20.md, "The
 * choice"). One value per layout, both orientations, so it dresses the dock on both halves unless
 * Home holds a Home layout of its own (#93).
 *
 * <p>The stored id is the contract: it is what the preference holds, so a style is renamed in
 * code and strings, never in {@link #storageValue}. Anything the store cannot read, an absent key
 * included, is {@link #GLASS_TRAY}, which is what every existing install migrates to. A new
 * install is the one exception: the layout store writes {@link #NEW_INSTALL_DEFAULT} into every
 * layout's key on its first run ({@code PlaceLayoutStore}), so an install that predates Peek glass
 * and never chose keeps the tray.
 *
 * <p>A style can be chosen before it is drawn: {@link #effective} answers what the dock really
 * draws, so a style that is not in {@link #BUILT} yet renders as the glass tray.
 */
public enum DockStyle {
    /** Today's plank: one glass slab under the icons. The default. */
    GLASS_TRAY("glass_tray"),
    /** No plate: each app on its own round glass lens. */
    LENS_BEADS("lens_beads"),
    /**
     * Once the tray with a tile that opens the drawer among the apps; the tile is the <b>Drawer
     * tile</b> toggle now, beside any design (#97). Kept only so a stored id still parses: the
     * store's migration turns it into {@link #GLASS_TRAY} with the tile on at its place, it is
     * never offered ({@link #CHOICES}) and never drawn ({@link #BUILT}).
     */
    TASKBAR("taskbar"),
    /**
     * Once the apps above the command palette's field; the field is the <b>Search field</b>
     * toggle now, beside any design (#96). Kept only so a stored id still parses: the store's
     * migration turns it into {@link #GLASS_TRAY} with the field on, it is never offered
     * ({@link #CHOICES}) and never drawn ({@link #BUILT}).
     */
    PROMPT_LINE("prompt_line"),
    /** A first row for the Workspace (a clock and the terminals) above the apps. */
    WALL_STRIP("wall_strip"),
    /**
     * A glass card rising from the screen's bottom edge, its top edge through the icons' centres,
     * the icons' reflections fading down it ({@link PeekGlassGeometry}). The default of a new
     * install ({@link #NEW_INSTALL_DEFAULT}).
     */
    PEEK_GLASS("peek_glass");

    /**
     * What a new install's layouts hold before anyone picks: written once, by the layout store's
     * first run on an install that has none of the launcher's earlier state, never read as the
     * absent key's meaning (that stays {@link #GLASS_TRAY} for every existing install).
     */
    @NonNull
    public static final DockStyle NEW_INSTALL_DEFAULT = PEEK_GLASS;

    /**
     * The styles the dock can draw today. A style joins this set in the change that draws it;
     * until then choosing it shows the glass tray.
     */
    @NonNull
    public static final Set<DockStyle> BUILT = Collections.unmodifiableSet(EnumSet.of(GLASS_TRAY, LENS_BEADS,
        WALL_STRIP, PEEK_GLASS));

    /** The designs the Layout editor's picker offers, in the order it shows them. */
    @NonNull
    public static final List<DockStyle> CHOICES = Collections.unmodifiableList(Arrays.asList(
        GLASS_TRAY, LENS_BEADS, WALL_STRIP, PEEK_GLASS));

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
