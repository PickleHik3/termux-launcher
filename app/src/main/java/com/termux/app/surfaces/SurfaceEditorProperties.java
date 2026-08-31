package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
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
 * What each surface offers the editor's pill, as data.
 *
 * <p>The pill shows one property at a time: a chip row names the few a surface is usually tuned by,
 * and a trailing ⋯ chip opens the rest in a sheet. This table is both lists, per surface, plus
 * everything a single control needs to render and write itself — so a property moves between the
 * chip row and the sheet by moving one entry, and a new one is an entry rather than a layout.
 *
 * <p>Two kinds of control live here side by side. Most are cells of the inheritance model and carry
 * their {@link SurfaceEditorRows.Row}, which owns their clamp and their link to Base. The rest —
 * dock size, apps per page, the keyboard's own key metrics, the terminal's frame — are outside the
 * cascade by design and carry their own accessors instead; they have no link and no footnote.
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

    /** How a control draws itself in the pill's one open row. */
    public enum Kind {
        /** The material macro: a Solid / Glass / Frost family, one intensity, and Fine underneath. */
        LOOK,
        /** A number on a track. */
        SLIDER,
        /** On or off. */
        SWITCH,
        /** A row that opens something which picks for itself. */
        PICKER
    }

    /** How a control's number is read out. */
    public enum Unit {
        DP,
        /** Stored in tenths of a dp, so the track is fine enough to find a shape by eye. */
        DP_TENTHS,
        PERCENT,
        /** The dock's four height presets, printed by name. */
        DOCK_SIZE,
        /** A bare count, like apps per page. */
        COUNT,
        /** Nothing to print. */
        NONE
    }

    /** One thing the pill can have open. */
    public static final class Control {
        /** Stable across a session; the chip the user left a surface on is remembered by this. */
        @NonNull public final String id;
        @StringRes public final int labelRes;
        /**
         * The name the chip row uses. A chip is read at a glance next to three others, so it wants
         * the short noun — "Corners", not "Corner radius" — while a sheet row, with a whole line to
         * itself, wants the full one. Where the two agree this is just {@link #labelRes}.
         */
        @StringRes public final int chipLabelRes;
        public final Kind kind;
        public final Unit unit;
        public final int max;
        /** The inheritance cell this edits, or null when the control stands outside Base. */
        @Nullable public final SurfaceEditorRows.Row cell;
        @Nullable private final ToIntFunction<TermuxAppSharedPreferences> read;
        @Nullable private final ObjIntConsumer<TermuxAppSharedPreferences> write;
        public final int previewScopes;

        private Control(@NonNull String id, @StringRes int labelRes, @StringRes int chipLabelRes,
                        Kind kind, Unit unit, int max,
                        @Nullable SurfaceEditorRows.Row cell,
                        @Nullable ToIntFunction<TermuxAppSharedPreferences> read,
                        @Nullable ObjIntConsumer<TermuxAppSharedPreferences> write,
                        int previewScopes) {
            this.id = id;
            this.labelRes = labelRes;
            this.chipLabelRes = chipLabelRes == 0 ? labelRes : chipLabelRes;
            this.kind = kind;
            this.unit = unit;
            this.max = max;
            this.cell = cell;
            this.read = read;
            this.write = write;
            this.previewScopes = previewScopes;
        }

        /** The footnote's lowercase noun for this control, or 0 where it has no link to explain. */
        @StringRes
        public int nounRes() {
            return cell == null ? 0 : cell.nounRes;
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

    /** A cell of the inheritance model, taking its clamp and ceiling from the row table. */
    private static Control cell(@NonNull String id, @NonNull SurfaceSlot slot,
                                @NonNull SurfaceProperty property, int previewScopes) {
        return cell(id, slot, property, 0, previewScopes);
    }

    private static Control cell(@NonNull String id, @NonNull SurfaceSlot slot,
                                @NonNull SurfaceProperty property, @StringRes int chipLabelRes,
                                int previewScopes) {
        SurfaceEditorRows.Row row = SurfaceEditorRows.forCell(slot, property);
        if (row == null)
            throw new IllegalArgumentException("no row for " + slot + "/" + property);
        return new Control(id, row.labelRes, chipLabelRes, Kind.SLIDER,
            row.dp ? Unit.DP : Unit.PERCENT, row.max, row, null, null, previewScopes);
    }

    /** A control outside the cascade: its own accessors, no link and no footnote. */
    private static Control own(@NonNull String id, @StringRes int labelRes, Kind kind, Unit unit,
                               int max,
                               @Nullable ToIntFunction<TermuxAppSharedPreferences> read,
                               @Nullable ObjIntConsumer<TermuxAppSharedPreferences> write,
                               int previewScopes) {
        return new Control(id, labelRes, 0, kind, unit, max, null, read, write, previewScopes);
    }

    public static final String ID_LOOK = "look";
    public static final String ID_CORNERS = "corners";
    public static final String ID_GAP = "gap";
    public static final String ID_SIZE = "size";
    public static final String ID_BORDER = "border";
    public static final String ID_APPS = "apps";
    public static final String ID_KEYBOARD_HEIGHT = "keyboard_height";
    public static final String ID_KEYBOARD_SPACING = "keyboard_spacing";
    public static final String ID_KEYBOARD_KEY_RADIUS = "keyboard_key_radius";
    public static final String ID_KEYBOARD_KEY_OPACITY = "keyboard_key_opacity";
    public static final String ID_KEYBOARD_COLORS = "keyboard_colors";
    public static final String ID_CLOCK = "clock";
    public static final String ID_CHIP_RADIUS = "chip_radius";
    public static final String ID_TERMINAL_RADIUS = "terminal_radius";
    public static final String ID_TERMINAL_GAP = "terminal_gap";
    public static final String ID_WALLPAPER = "wallpaper";
    /** Raw blur, opacity and grain for one surface, behind Look's Fine. */
    public static final String ID_FINE_BLUR = "fine_blur";
    public static final String ID_FINE_OPACITY = "fine_opacity";
    public static final String ID_FINE_GRAIN = "fine_grain";

    /** Look is the same control on every surface: the family and intensity of that surface's glass. */
    private static Control look() {
        return own(ID_LOOK, R.string.termux_surface_editor_look, Kind.LOOK, Unit.PERCENT, 100,
            null, null, PREVIEW_GLASS | PREVIEW_SURFACES | PREVIEW_KEYBOARD);
    }

    private static final EnumMap<SurfaceSlot, List<Control>> CHIPS =
        new EnumMap<>(SurfaceSlot.class);
    private static final EnumMap<SurfaceSlot, List<Control>> MORE =
        new EnumMap<>(SurfaceSlot.class);
    private static final EnumMap<SurfaceSlot, List<Control>> FINE =
        new EnumMap<>(SurfaceSlot.class);

    static {
        CHIPS.put(SurfaceSlot.DOCK, Collections.unmodifiableList(Arrays.asList(
            look(),
            cell(ID_CORNERS, SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
                R.string.termux_surface_editor_chip_corners,
                PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            cell(ID_GAP, SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_editor_chip_gap,
                PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            own(ID_SIZE, R.string.termux_dock_tuning_size, Kind.SLIDER, Unit.DOCK_SIZE,
                DockLayoutPolicy.sizePresetCount() - 1,
                prefs -> DockLayoutPolicy.nearestSizePresetIndex(
                    prefs.getAppLauncherBarHeightScale()),
                (prefs, value) -> prefs.setAppLauncherBarHeightScale(
                    DockLayoutPolicy.sizePreset(value)),
                PREVIEW_GEOMETRY))));

        CHIPS.put(SurfaceSlot.KEYBOARD, Collections.unmodifiableList(Arrays.asList(
            look(),
            cell(ID_GAP, SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_editor_chip_gap,
                PREVIEW_GEOMETRY | PREVIEW_SURFACES))));

        CHIPS.put(SurfaceSlot.STATUS, Collections.unmodifiableList(Arrays.asList(
            look(),
            cell(ID_CORNERS, SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
                R.string.termux_surface_editor_chip_corners,
                PREVIEW_GEOMETRY | PREVIEW_SURFACES),
            cell(ID_GAP, SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
                R.string.termux_surface_editor_chip_gap,
                PREVIEW_GEOMETRY | PREVIEW_SURFACES))));

        CHIPS.put(SurfaceSlot.CANVAS, Collections.unmodifiableList(Arrays.asList(
            look(),
            own(ID_BORDER, R.string.termux_dock_tuning_terminal_border, Kind.SWITCH, Unit.NONE, 1,
                prefs -> prefs.isTerminalBorderEnabled() ? 1 : 0,
                (prefs, value) -> prefs.setTerminalBorderEnabled(value != 0),
                PREVIEW_ALL | PREVIEW_GEOMETRY_COMMIT))));

        MORE.put(SurfaceSlot.DOCK, Collections.unmodifiableList(Collections.singletonList(
            own(ID_APPS, R.string.termux_dock_tuning_icons, Kind.SLIDER, Unit.COUNT, 20,
                TermuxAppSharedPreferences::getAppLauncherButtonCount,
                (prefs, value) -> prefs.setAppLauncherButtonCount(Math.max(1, value)),
                PREVIEW_GEOMETRY))));

        MORE.put(SurfaceSlot.KEYBOARD, Collections.unmodifiableList(Arrays.asList(
            own(ID_KEYBOARD_HEIGHT, R.string.termux_surface_tuning_keyboard_height, Kind.SLIDER,
                Unit.PERCENT, 100,
                prefs -> SurfaceEditorController.keyboardEditorProgress(
                    prefs.getInAppKeyboardHeightScale(),
                    TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE),
                (prefs, value) -> prefs.setInAppKeyboardHeightScale(
                    SurfaceEditorController.keyboardEditorValue(value,
                        TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                        TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE)),
                0),
            own(ID_KEYBOARD_SPACING, R.string.termux_surface_tuning_keyboard_spacing, Kind.SLIDER,
                Unit.PERCENT, 100,
                prefs -> SurfaceEditorController.keyboardEditorProgress(
                    prefs.getInAppKeyboardKeyMarginScale(),
                    TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE),
                (prefs, value) -> prefs.setInAppKeyboardKeyMarginScale(
                    SurfaceEditorController.keyboardEditorValue(value,
                        TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                        TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE)),
                0),
            // A tenth of a dp per step, so the track is fine enough to find a key shape by eye.
            own(ID_KEYBOARD_KEY_RADIUS, R.string.termux_surface_editor_key_radius, Kind.SLIDER,
                Unit.DP_TENTHS, 240,
                prefs -> Math.round(prefs.getInAppKeyboardKeyCornerRadiusDp() * 10f),
                (prefs, value) -> prefs.setInAppKeyboardKeyCornerRadiusDp(value / 10f),
                0),
            own(ID_KEYBOARD_KEY_OPACITY, R.string.termux_surface_tuning_keyboard_key_opacity,
                Kind.SLIDER, Unit.PERCENT, 100,
                TermuxAppSharedPreferences::getInAppKeyboardKeyOpacity,
                TermuxAppSharedPreferences::setInAppKeyboardKeyOpacity,
                0),
            own(ID_KEYBOARD_COLORS, R.string.settings_keyboard_colors_title, Kind.PICKER,
                Unit.NONE, 0, null, null, 0))));

        MORE.put(SurfaceSlot.STATUS, Collections.unmodifiableList(Arrays.asList(
            own(ID_CLOCK, R.string.termux_surface_tuning_clock, Kind.PICKER, Unit.NONE, 0,
                null, null, 0),
            own(ID_CHIP_RADIUS, R.string.termux_surface_tuning_indicator_radius, Kind.SLIDER,
                Unit.DP, TERMUX_APP.MAX_STATUS_INDICATOR_CORNER_RADIUS,
                TermuxAppSharedPreferences::getStatusIndicatorCornerRadius,
                TermuxAppSharedPreferences::setStatusIndicatorCornerRadius,
                0))));

        MORE.put(SurfaceSlot.CANVAS, Collections.unmodifiableList(Arrays.asList(
            own(ID_TERMINAL_RADIUS, R.string.termux_dock_tuning_radius, Kind.SLIDER, Unit.DP, 40,
                TermuxAppSharedPreferences::getTerminalCornerRadius,
                TermuxAppSharedPreferences::setTerminalCornerRadius,
                PREVIEW_SURFACES),
            own(ID_TERMINAL_GAP, R.string.termux_dock_tuning_terminal_inner_padding, Kind.SLIDER,
                Unit.DP, 24,
                TermuxAppSharedPreferences::getTerminalPaneGap,
                TermuxAppSharedPreferences::setTerminalPaneGap,
                PREVIEW_SURFACES),
            own(ID_WALLPAPER, R.string.termux_surface_editor_wallpaper_dim, Kind.SLIDER,
                Unit.PERCENT, 100,
                TermuxAppSharedPreferences::getWallpaperBackdropDim,
                TermuxAppSharedPreferences::setWallpaperBackdropDim,
                PREVIEW_SURFACES))));

        for (SurfaceSlot slot : SurfaceSlot.values()) {
            List<Control> fine = new ArrayList<>(3);
            addFine(fine, slot, SurfaceProperty.BLUR, ID_FINE_BLUR, PREVIEW_BLUR | PREVIEW_SURFACES);
            addFine(fine, slot, SurfaceProperty.OPACITY, ID_FINE_OPACITY,
                PREVIEW_SURFACES | PREVIEW_KEYBOARD);
            addFine(fine, slot, SurfaceProperty.GRAIN, ID_FINE_GRAIN, PREVIEW_SURFACES);
            FINE.put(slot, Collections.unmodifiableList(fine));
            if (!MORE.containsKey(slot))
                MORE.put(slot, Collections.emptyList());
            if (!CHIPS.containsKey(slot))
                CHIPS.put(slot, Collections.unmodifiableList(
                    Collections.singletonList(look())));
        }
    }

    private static void addFine(@NonNull List<Control> into, @NonNull SurfaceSlot slot,
                                @NonNull SurfaceProperty property, @NonNull String id, int scopes) {
        if (SurfaceEditorRows.forCell(slot, property) != null)
            into.add(cell(id, slot, property, scopes));
    }

    public static final String ID_BASE_INTENSITY = "base_intensity";
    public static final String ID_BASE_CORNERS = "base_corners";
    public static final String ID_BASE_GAP = "base_gap";

    /**
     * The shared layer's own rows, for the Looks sheet.
     *
     * <p>Base is not a surface, so it is not on the pill: nothing on screen would wear its ring, and
     * "change everything" is the question the Looks sheet is already there to answer. The intensity
     * row is the material macro rather than a plain number — the editor intercepts its write — and
     * the two geometry rows move every surface that still follows them.
     */
    private static final List<Control> BASE = Collections.unmodifiableList(Arrays.asList(
        own(ID_BASE_INTENSITY, R.string.termux_surface_tuning_material_intensity, Kind.SLIDER,
            Unit.PERCENT, 100,
            TermuxAppSharedPreferences::getSurfaceMaterialIntensity, null,
            PREVIEW_GLASS | PREVIEW_SURFACES | PREVIEW_KEYBOARD),
        own(ID_BASE_CORNERS, R.string.termux_dock_tuning_radius, Kind.SLIDER, Unit.DP, 40,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS),
            (prefs, value) -> prefs.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, value),
            PREVIEW_ALL_BUT_BLUR),
        own(ID_BASE_GAP, R.string.termux_surface_tuning_edges, Kind.SLIDER, Unit.DP, 48,
            prefs -> prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP),
            (prefs, value) -> prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, value),
            PREVIEW_ALL_BUT_BLUR)));

    @NonNull
    public static List<Control> base() {
        return BASE;
    }

    /** The chips one surface puts on the pill, in order. */
    @NonNull
    public static List<Control> chips(@NonNull SurfaceSlot slot) {
        return CHIPS.get(slot);
    }

    /** What that surface's ⋯ sheet holds; empty where there is nothing behind the chips. */
    @NonNull
    public static List<Control> more(@NonNull SurfaceSlot slot) {
        return MORE.get(slot);
    }

    /** The raw glass triple behind that surface's Look, for its Fine sheet. */
    @NonNull
    public static List<Control> fine(@NonNull SurfaceSlot slot) {
        return FINE.get(slot);
    }

    /** The chip or sheet control with this id on that surface, or null. */
    @Nullable
    public static Control find(@NonNull SurfaceSlot slot, @Nullable String id) {
        if (id == null)
            return null;
        for (Control control : chips(slot)) {
            if (control.id.equals(id)) return control;
        }
        for (Control control : more(slot)) {
            if (control.id.equals(id)) return control;
        }
        for (Control control : fine(slot)) {
            if (control.id.equals(id)) return control;
        }
        return null;
    }
}
