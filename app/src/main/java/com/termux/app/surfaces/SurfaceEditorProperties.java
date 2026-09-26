package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * What the editor's panel shows, as data: one ordered list per target.
 *
 * <p>There are five targets — the shared layer the editor opens on, and one per surface — and each
 * is a flat list of rows that are all on screen at once. No chips, no second level, no "one open
 * property": a surface's whole table stands on its card.
 *
 * <p>The order is the same list everywhere, and it is the point of {@link #RANK}: opacity (with the
 * keyboard's intensity right after it), blur, grain, corners, margin, then whatever else that
 * surface owns. A row the current state makes inert — a docked surface's margin, the terminal's
 * glass with no frame around it — is dropped rather than drawn dead, and the rows below it close up
 * into its place, so the same property is always found in the same position relative to its
 * neighbours.
 *
 * <p>Two kinds of row live here side by side. Most are cells of the inheritance model and carry
 * their {@link SurfaceEditorRows.Row}, which owns their clamp and their link to Base. The rest —
 * dock size, apps per page, the keyboard's key metrics, the terminal's frame, the wallpaper — are
 * outside the cascade by design and carry their own accessors instead.
 *
 * <p>Pure data, no views and no {@code Context}.
 */
public final class SurfaceEditorProperties {

    private SurfaceEditorProperties() {}

    // Live-preview scopes. A control's ticks fire far faster than a full re-apply fits in a frame,
    // so each control declares only what it actually touches and the editor coalesces the requests
    // to one apply per animation frame. GLASS (the accessory re-render) runs on every apply; BLUR
    // additionally throws away the shared pre-blurred wallpaper bitmap, which is the single most
    // expensive thing a slider can cause, so only blur inputs may ask for it.
    public static final int PREVIEW_GLASS = 1;
    public static final int PREVIEW_BLUR = 1 << 1;
    public static final int PREVIEW_GEOMETRY = 1 << 2;
    public static final int PREVIEW_SURFACES = 1 << 3;
    public static final int PREVIEW_KEYBOARD = 1 << 4;
    /**
     * Lets the geometry pass tell the shell: the terminal resize (a SIGWINCH per reflow) is worth
     * one settle on release, not one per tick of a drag.
     */
    public static final int PREVIEW_GEOMETRY_COMMIT = 1 << 5;
    public static final int PREVIEW_ALL = PREVIEW_GLASS | PREVIEW_BLUR | PREVIEW_GEOMETRY
        | PREVIEW_SURFACES | PREVIEW_KEYBOARD;
    /**
     * Radius and margin move every following surface but never the blur — dropping the BLUR bit is
     * what keeps their ticks from throwing away the pre-blurred wallpaper frames.
     */
    public static final int PREVIEW_ALL_BUT_BLUR = PREVIEW_ALL & ~PREVIEW_BLUR;

    /**
     * What a row is about, and therefore which heading it stands under.
     *
     * <p>Six properties of three kinds rendered as six identical rows is what read as unfinished.
     * The grouping is data rather than a decision the view layer takes, so the order every panel is
     * drawn in is one sort and a test can state it.
     *
     * <p>The names are product copy: one word, naming the thing the user is changing. "Shape", not
     * "Geometry"; "Material", not "Compositing"; "Keys", not "Key metrics".
     */
    public enum Section {
        MATERIAL(R.string.termux_surface_editor_material),
        SHAPE(R.string.termux_surface_editor_shape),
        WALLPAPER(R.string.termux_surface_editor_wallpaper_dim),
        APPS(R.string.termux_dock_tuning_icons),
        KEYS(R.string.termux_surface_editor_section_keys),
        INDICATOR(R.string.termux_surface_editor_section_indicator);

        @StringRes public final int titleRes;

        Section(@StringRes int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** How a row draws itself. */
    public enum Kind {
        /** A number on a track — almost everything. */
        SLIDER,
        /** On or off. */
        SWITCH,
        /** A labelled row that leaves the editor for a screen of its own. */
        ACTION
    }

    /** How a row's number is read out. */
    public enum Unit {
        DP,
        /** Stored in tenths of a dp, so the track is fine enough to find a shape by eye. */
        DP_TENTHS,
        PERCENT,
        /** A bare count, like apps per page. */
        COUNT,
        /** Nothing to print. */
        NONE
    }

    /** One row on the panel. */
    public static final class Control {
        @NonNull public final String id;
        @StringRes public final int labelRes;
        /** The heading this row stands under, which is also what orders the panel. */
        @NonNull public final Section section;
        public final Kind kind;
        public final Unit unit;
        public final int max;
        /** The inheritance cell this edits, or null when the control stands outside Base. */
        @Nullable public final SurfaceEditorRows.Row cell;
        @Nullable private final ToIntFunction<TermuxAppSharedPreferences> read;
        @Nullable private final ObjIntConsumer<TermuxAppSharedPreferences> write;
        public final int previewScopes;

        private Control(@NonNull String id, @StringRes int labelRes, @NonNull Section section,
                        Kind kind, Unit unit, int max,
                        @Nullable SurfaceEditorRows.Row cell,
                        @Nullable ToIntFunction<TermuxAppSharedPreferences> read,
                        @Nullable ObjIntConsumer<TermuxAppSharedPreferences> write,
                        int previewScopes) {
            this.id = id;
            this.labelRes = labelRes;
            this.section = section;
            this.kind = kind;
            this.unit = unit;
            this.max = max;
            this.cell = cell;
            this.read = read;
            this.write = write;
            this.previewScopes = previewScopes;
        }

        public int read(@NonNull TermuxAppSharedPreferences prefs) {
            if (cell != null)
                return cell.read.applyAsInt(prefs);
            return read == null ? 0 : read.applyAsInt(prefs);
        }

        public void write(@NonNull TermuxAppSharedPreferences prefs, int value) {
            if (cell != null) {
                cell.write.accept(prefs, value);
                return;
            }
            if (write != null)
                write.accept(prefs, value);
        }
    }

    // ------------------------------------------------------------------------------------- ids

    /** The five rows every surface shares, named the same everywhere so the order can be shared. */
    public static final String ID_OPACITY = "opacity";
    public static final String ID_BLUR = "blur";
    public static final String ID_GRAIN = "grain";
    public static final String ID_CORNERS = "corners";
    public static final String ID_MARGIN = "margin";

    public static final String ID_APPS = "apps";
    public static final String ID_KEYBOARD_KEY_RADIUS = "keyboard_key_radius";
    public static final String ID_KEYBOARD_KEY_OPACITY = "keyboard_key_opacity";
    public static final String ID_KEYBOARD_SPACING = "keyboard_spacing";
    public static final String ID_KEYBOARD_COLORS = "keyboard_colors";
    /**
     * The keyboard's own colour intensity (tint alpha) — the row that used to be its only glass
     * cell, under {@code ID_OPACITY}. Given its own id once the keyboard gained Blur, Opacity and
     * Grain rows of its own: {@code ID_OPACITY} on this panel now names the whole backdrop's
     * visibility, matching what it means on every other surface's panel.
     */
    public static final String ID_KEYBOARD_INTENSITY = "keyboard_intensity";
    public static final String ID_CHIP_RADIUS = "chip_radius";
    public static final String ID_WALLPAPER = "wallpaper";

    /**
     * The shared layer's rows. Same five names with an {@code all_} prefix, because they are the
     * same five decisions taken once for everything rather than a different set of controls.
     */
    public static final String ID_ALL_OPACITY = "all_opacity";
    public static final String ID_ALL_BLUR = "all_blur";
    public static final String ID_ALL_GRAIN = "all_grain";
    public static final String ID_ALL_CORNERS = "all_corners";
    public static final String ID_ALL_MARGIN = "all_margin";

    /**
     * The one order every panel is drawn in. Anything unranked sorts after the shared five, in the
     * order its surface declares it — the surface's own extras, which no other surface has to line
     * up with.
     */
    private static final List<String> RANK = Collections.unmodifiableList(Arrays.asList(
        ID_OPACITY, ID_ALL_OPACITY, ID_KEYBOARD_INTENSITY,
        ID_BLUR, ID_ALL_BLUR,
        ID_GRAIN, ID_ALL_GRAIN,
        ID_CORNERS, ID_ALL_CORNERS,
        ID_MARGIN, ID_ALL_MARGIN));

    /** Where a row sorts, for the tests that hold every panel to the shared order. */
    public static int rankOf(@NonNull String id) {
        int index = RANK.indexOf(id);
        return index < 0 ? RANK.size() : index;
    }

    // -------------------------------------------------------------------------------- builders

    /** A cell of the inheritance model, taking its clamp and ceiling from the row table. */
    private static Control cell(@NonNull String id, @NonNull SurfaceSlot slot,
                                @NonNull SurfaceProperty property, @StringRes int labelRes,
                                @NonNull Section section, int previewScopes) {
        SurfaceEditorRows.Row row = SurfaceEditorRows.forCell(slot, property);
        if (row == null)
            throw new IllegalArgumentException("no row for " + slot + "/" + property);
        return new Control(id, labelRes, section, Kind.SLIDER,
            row.dp ? Unit.DP : Unit.PERCENT, row.max,
            row, null, null, previewScopes);
    }

    /** A control outside the cascade: its own accessors, and no link to Base. */
    private static Control own(@NonNull String id, @StringRes int labelRes,
                               @NonNull Section section, Kind kind, Unit unit, int max,
                               @Nullable ToIntFunction<TermuxAppSharedPreferences> read,
                               @Nullable ObjIntConsumer<TermuxAppSharedPreferences> write,
                               int previewScopes) {
        return new Control(id, labelRes, section, kind, unit, max, null, read, write,
            previewScopes);
    }

    private static boolean floating(@NonNull TermuxAppSharedPreferences prefs) {
        return TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED.equals(prefs.getAppLauncherDockStyle());
    }

    // ------------------------------------------------------------------------------ the panels

    /** The shared margin's ceiling while Floating, where it is the surfaces' own screen-edge gap. */
    public static final int MAX_ALL_MARGIN_DP = 48;
    /** The terminal's own margin ceiling, which the shared margin never writes past. */
    public static final int MAX_TERMINAL_MARGIN_DP = 24;

    /**
     * The shared layer, which is what the editor opens on.
     *
     * <p>Its three glass rows are Base's own blur, opacity and grain — the same numbers the
     * Solid / Glass / Frost pill writes as a set, so moving one by hand simply leaves the pill with
     * no family to claim. Corners and margin are compound on purpose: the two questions a user
     * actually has here are "how round is everything" and "how much air is there", and answering
     * them one surface at a time is what the shared layer exists to avoid.
     */
    private static final List<Control> GLOBAL = Collections.unmodifiableList(Arrays.asList(
        own(ID_ALL_OPACITY, R.string.termux_dock_tuning_opacity, Section.MATERIAL,
            Kind.SLIDER, Unit.PERCENT, 100,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.OPACITY),
            (prefs, value) -> prefs.setSurfaceBaseValue(SurfaceProperty.OPACITY, value),
            PREVIEW_GLASS | PREVIEW_SURFACES | PREVIEW_KEYBOARD),
        own(ID_ALL_BLUR, R.string.termux_dock_tuning_blur, Section.MATERIAL,
            Kind.SLIDER, Unit.DP, 30,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.BLUR),
            (prefs, value) -> prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, value),
            PREVIEW_BLUR | PREVIEW_SURFACES | PREVIEW_KEYBOARD),
        own(ID_ALL_GRAIN, R.string.termux_dock_tuning_grain, Section.MATERIAL,
            Kind.SLIDER, Unit.PERCENT, 100,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN),
            (prefs, value) -> prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, value),
            PREVIEW_GLASS | PREVIEW_SURFACES | PREVIEW_KEYBOARD),
        // The terminal rounds by its own knob in either style, and every pane in it reads that
        // knob, so the shared radius has to carry it too — otherwise "round everything" leaves one
        // square hole in the middle of the screen.
        own(ID_ALL_CORNERS, R.string.termux_dock_tuning_radius, Section.SHAPE,
            Kind.SLIDER, Unit.DP, 40,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS),
            (prefs, value) -> {
                prefs.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, value);
                prefs.setTerminalCornerRadius(value);
            },
            PREVIEW_ALL_BUT_BLUR),
        // One number for all the air on screen. Docked surfaces are flush with the screen edges by
        // definition, so there it is the terminal's own margin alone; Floating spends it on both.
        own(ID_ALL_MARGIN, R.string.termux_surface_tuning_edges, Section.SHAPE,
            Kind.SLIDER, Unit.DP, MAX_ALL_MARGIN_DP,
            prefs -> floating(prefs)
                ? prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP)
                : prefs.getTerminalPaneGap(),
            (prefs, value) -> {
                if (floating(prefs))
                    prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, value);
                prefs.setTerminalPaneGap(Math.min(MAX_TERMINAL_MARGIN_DP, value));
            },
            PREVIEW_ALL_BUT_BLUR),
        own(ID_WALLPAPER, R.string.termux_surface_editor_wallpaper_dim, Section.WALLPAPER,
            Kind.SLIDER, Unit.PERCENT, 100,
            TermuxAppSharedPreferences::getWallpaperBackdropDim,
            TermuxAppSharedPreferences::setWallpaperBackdropDim,
            PREVIEW_SURFACES)));

    private static final EnumMap<SurfaceSlot, List<Control>> PANELS =
        new EnumMap<>(SurfaceSlot.class);

    static {
        PANELS.put(SurfaceSlot.DOCK, panel(
            cell(ID_OPACITY, SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                R.string.termux_dock_tuning_opacity,
                Section.MATERIAL, PREVIEW_GLASS | PREVIEW_SURFACES),
            cell(ID_BLUR, SurfaceSlot.DOCK, SurfaceProperty.BLUR,
                R.string.termux_dock_tuning_blur,
                Section.MATERIAL, PREVIEW_BLUR | PREVIEW_SURFACES),
            cell(ID_GRAIN, SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
                R.string.termux_dock_tuning_grain,
                Section.MATERIAL, PREVIEW_GLASS | PREVIEW_SURFACES),
            cell(ID_CORNERS, SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
                R.string.termux_dock_tuning_radius,
                Section.SHAPE, PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            cell(ID_MARGIN, SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_tuning_edges,
                Section.SHAPE, PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            own(ID_APPS, R.string.termux_dock_tuning_icons, Section.APPS,
                Kind.SLIDER, Unit.COUNT, 20,
                TermuxAppSharedPreferences::getAppLauncherButtonCount,
                (prefs, value) -> prefs.setAppLauncherButtonCount(Math.max(1, value)),
                PREVIEW_GEOMETRY)));

        // The keyboard used to render only the dock's blur radius and grain, borrowed wholesale,
        // and owned an opacity (really a tint intensity) and a margin of its own. It now owns a
        // Blur, an Opacity and a Grain the same way DOCK/STATUS/CANVAS do — each -1 by default,
        // meaning "follow the dock", so an untouched keyboard still renders exactly as before —
        // plus the pre-existing Intensity row, kept under its own id and pref so old values
        // survive. Its height is the drag handle on its own top edge rather than a row here.
        PANELS.put(SurfaceSlot.KEYBOARD, panel(
            // Same scopes their DOCK/STATUS/CANVAS counterparts use: SCOPE_KEYBOARD_BACKDROP
            // already re-syncs unconditionally on every glass preview tick, so none of these three
            // need PREVIEW_KEYBOARD's full key-layout reload — only Intensity below still does.
            own(ID_BLUR, R.string.termux_dock_tuning_blur, Section.MATERIAL,
                Kind.SLIDER, Unit.DP, TERMUX_APP.MAX_IN_APP_KEYBOARD_BLUR_RADIUS,
                TermuxAppSharedPreferences::getInAppKeyboardBlurRadius,
                TermuxAppSharedPreferences::setInAppKeyboardBlurRadius,
                PREVIEW_BLUR | PREVIEW_SURFACES),
            // The whole backdrop stack's visibility — the blurred wallpaper crop and the tint
            // together — same meaning as every other surface's Opacity row.
            own(ID_OPACITY, R.string.termux_dock_tuning_opacity, Section.MATERIAL,
                Kind.SLIDER, Unit.PERCENT, TERMUX_APP.MAX_IN_APP_KEYBOARD_BACKDROP_OPACITY,
                TermuxAppSharedPreferences::getInAppKeyboardBackdropOpacity,
                TermuxAppSharedPreferences::setInAppKeyboardBackdropOpacity,
                PREVIEW_GLASS | PREVIEW_SURFACES),
            // "Intensity", not "Opacity": this is the slab's own colour strength (tint alpha), not
            // how visible the backdrop is — the row above answers that now. Still the cell it
            // always was, so linking or detaching this cell still means the same thing.
            // Kept on PREVIEW_KEYBOARD as it always was: the colour scheme it can swap in is read
            // at render time and needs the keyboard's full reload, not just its backdrop redrawn.
            cell(ID_KEYBOARD_INTENSITY, SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
                R.string.termux_surface_editor_keyboard_intensity,
                Section.MATERIAL, PREVIEW_SURFACES | PREVIEW_KEYBOARD),
            own(ID_GRAIN, R.string.termux_dock_tuning_grain, Section.MATERIAL,
                Kind.SLIDER, Unit.PERCENT, TERMUX_APP.MAX_IN_APP_KEYBOARD_GRAIN,
                TermuxAppSharedPreferences::getInAppKeyboardGrain,
                TermuxAppSharedPreferences::setInAppKeyboardGrain,
                PREVIEW_GLASS | PREVIEW_SURFACES),
            cell(ID_MARGIN, SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_tuning_edges,
                Section.SHAPE, PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            // A tenth of a dp per step, so the track is fine enough to find a key shape by eye.
            own(ID_KEYBOARD_KEY_RADIUS, R.string.termux_surface_editor_key_radius,
                Section.KEYS, Kind.SLIDER, Unit.DP_TENTHS, 240,
                prefs -> Math.round(prefs.getInAppKeyboardKeyCornerRadiusDp() * 10f),
                (prefs, value) -> prefs.setInAppKeyboardKeyCornerRadiusDp(value / 10f),
                0),
            own(ID_KEYBOARD_KEY_OPACITY, R.string.termux_surface_tuning_keyboard_key_opacity,
                Section.KEYS, Kind.SLIDER, Unit.PERCENT, 100,
                TermuxAppSharedPreferences::getInAppKeyboardKeyOpacity,
                TermuxAppSharedPreferences::setInAppKeyboardKeyOpacity,
                0),
            own(ID_KEYBOARD_SPACING, R.string.termux_surface_tuning_keyboard_spacing,
                Section.KEYS, Kind.SLIDER, Unit.PERCENT, 100,
                prefs -> SurfaceEditorController.keyboardEditorProgress(
                    prefs.getInAppKeyboardKeyMarginScale(),
                    TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE),
                (prefs, value) -> prefs.setInAppKeyboardKeyMarginScale(
                    SurfaceEditorController.keyboardEditorValue(value,
                        TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                        TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE)),
                0),
            own(ID_KEYBOARD_COLORS, R.string.settings_keyboard_colors_title, Section.KEYS,
                Kind.ACTION, Unit.NONE, 0, null, null, 0)));

        PANELS.put(SurfaceSlot.STATUS, panel(
            cell(ID_OPACITY, SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
                R.string.termux_dock_tuning_opacity,
                Section.MATERIAL, PREVIEW_GLASS | PREVIEW_SURFACES),
            cell(ID_BLUR, SurfaceSlot.STATUS, SurfaceProperty.BLUR,
                R.string.termux_dock_tuning_blur,
                Section.MATERIAL, PREVIEW_BLUR | PREVIEW_SURFACES),
            cell(ID_GRAIN, SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
                R.string.termux_dock_tuning_grain,
                Section.MATERIAL, PREVIEW_GLASS | PREVIEW_SURFACES),
            cell(ID_CORNERS, SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
                R.string.termux_dock_tuning_radius,
                Section.SHAPE, PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            cell(ID_MARGIN, SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_tuning_edges,
                Section.SHAPE, PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            own(ID_CHIP_RADIUS, R.string.termux_surface_tuning_indicator_radius,
                Section.INDICATOR, Kind.SLIDER, Unit.DP,
                TERMUX_APP.MAX_STATUS_INDICATOR_CORNER_RADIUS,
                TermuxAppSharedPreferences::getStatusIndicatorCornerRadius,
                TermuxAppSharedPreferences::setStatusIndicatorCornerRadius,
                0)));

        PANELS.put(SurfaceSlot.CANVAS, panel(
            cell(ID_OPACITY, SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
                R.string.termux_dock_tuning_opacity,
                Section.MATERIAL, PREVIEW_SURFACES),
            cell(ID_BLUR, SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
                R.string.termux_dock_tuning_blur,
                Section.MATERIAL, PREVIEW_BLUR | PREVIEW_SURFACES),
            cell(ID_GRAIN, SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
                R.string.termux_dock_tuning_grain,
                Section.MATERIAL, PREVIEW_GLASS | PREVIEW_SURFACES),
            own(ID_CORNERS, R.string.termux_dock_tuning_radius, Section.SHAPE,
                Kind.SLIDER, Unit.DP, 40,
                TermuxAppSharedPreferences::getTerminalCornerRadius,
                TermuxAppSharedPreferences::setTerminalCornerRadius,
                PREVIEW_SURFACES),
            own(ID_MARGIN, R.string.termux_surface_tuning_edges, Section.SHAPE,
                Kind.SLIDER, Unit.DP, MAX_TERMINAL_MARGIN_DP,
                TermuxAppSharedPreferences::getTerminalPaneGap,
                TermuxAppSharedPreferences::setTerminalPaneGap,
                PREVIEW_SURFACES)));
    }

    /**
     * Sorts one surface's declared rows into the shared order and freezes them.
     *
     * <p>Section-major, so {@link #rowsFor} hands the editor a list it can walk straight down,
     * adding a heading each time the section changes. Inside a section the shared {@link #RANK}
     * still decides, so the same property is always found in the same position relative to its
     * neighbours.
     */
    private static List<Control> panel(Control... controls) {
        List<Control> ordered = new ArrayList<>(Arrays.asList(controls));
        // A stable sort, so two unranked extras keep the order the surface declared them in.
        Collections.sort(ordered, (left, right) -> {
            int bySection = Integer.compare(left.section.ordinal(), right.section.ordinal());
            return bySection != 0 ? bySection
                : Integer.compare(rankOf(left.id), rankOf(right.id));
        });
        return Collections.unmodifiableList(ordered);
    }

    /** The shared layer's rows, which is what the editor opens on. */
    @NonNull
    public static List<Control> global() {
        return GLOBAL;
    }

    /** One surface's rows, in the shared order. */
    @NonNull
    public static List<Control> panel(@NonNull SurfaceSlot slot) {
        return PANELS.get(slot);
    }

    /** The rows of one target: a surface's, or the shared layer's for a null slot. */
    @NonNull
    public static List<Control> rowsFor(@Nullable SurfaceSlot slot) {
        return slot == null ? global() : panel(slot);
    }

    /** The row with this id on that target, or null. */
    @Nullable
    public static Control find(@Nullable SurfaceSlot slot, @Nullable String id) {
        if (id == null)
            return null;
        for (Control control : rowsFor(slot)) {
            if (control.id.equals(id))
                return control;
        }
        return null;
    }
}
