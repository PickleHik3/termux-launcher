package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * The surface editor's shared rows, as data.
 *
 * <p>Every control that participates in inheritance appears here exactly once, tying a
 * (surface, property) pair to the three views that render it — slider, value read-out and link
 * chip — plus the label and unit it should announce, the container it inflates into, and the
 * preference accessors that own its clamp. The editor iterates this table instead of repeating
 * the same wiring fifteen times, which is also what keeps the tab badges, the link chips and the
 * sliders from drifting apart: they are all driven from the same list.
 *
 * <p>Each surface also has one {@link Page}: the pill that picks it, the group that page shows,
 * and the whole-surface ↺ on its header.
 *
 * <p>Pure data, no views and no {@code Context}, so the set is testable on its own.
 */
public final class SurfaceEditorRows {

    private SurfaceEditorRows() {}

    /** One shared control: which surface and property it edits, and the views that show it. */
    public static final class Row {
        public final SurfaceSlot slot;
        public final SurfaceProperty property;
        /** The group the row is inflated into; the terminal's glass rows sit behind its Border switch. */
        public final int containerId;
        public final int sliderId;
        public final int valueId;
        public final int chipId;
        @StringRes public final int labelRes;
        /** dp rather than percent; drives which value format the row prints. */
        public final boolean dp;
        public final int max;
        /** The resolved number the surface renders with, through the getter that owns its clamp. */
        public final ToIntFunction<TermuxAppSharedPreferences> read;
        /** Writes through the setter that owns the clamp; inherit-aware like every setter. */
        public final ObjIntConsumer<TermuxAppSharedPreferences> write;

        Row(SurfaceSlot slot, SurfaceProperty property, int containerId, int sliderId, int valueId,
            int chipId, @StringRes int labelRes, boolean dp, int max,
            ToIntFunction<TermuxAppSharedPreferences> read,
            ObjIntConsumer<TermuxAppSharedPreferences> write) {
            this.slot = slot;
            this.property = property;
            this.containerId = containerId;
            this.sliderId = sliderId;
            this.valueId = valueId;
            this.chipId = chipId;
            this.labelRes = labelRes;
            this.dp = dp;
            this.max = max;
            this.read = read;
            this.write = write;
        }
    }

    /** One surface's editor page, and the label the link chips announce it by. */
    public static final class Page {
        public final SurfaceSlot slot;
        public final int chipId;
        public final int groupId;
        public final int reattachId;
        @StringRes public final int labelRes;

        Page(SurfaceSlot slot, int chipId, int groupId, int reattachId, @StringRes int labelRes) {
            this.slot = slot;
            this.chipId = chipId;
            this.groupId = groupId;
            this.reattachId = reattachId;
            this.labelRes = labelRes;
        }
    }

    private static final EnumMap<SurfaceSlot, Page> PAGES = new EnumMap<>(SurfaceSlot.class);

    static {
        for (Page page : new Page[] {
            new Page(SurfaceSlot.DOCK, R.id.surface_editor_chip_dock,
                R.id.surface_editor_group_dock, R.id.surface_editor_reattach_dock,
                R.string.termux_surface_tuning_dock),
            new Page(SurfaceSlot.KEYBOARD, R.id.surface_editor_chip_keyboard,
                R.id.surface_editor_group_keyboard, R.id.surface_editor_reattach_keyboard,
                R.string.termux_surface_tuning_keyboard),
            new Page(SurfaceSlot.STATUS, R.id.surface_editor_chip_status,
                R.id.surface_editor_group_status, R.id.surface_editor_reattach_status,
                R.string.termux_surface_tuning_status),
            new Page(SurfaceSlot.CANVAS, R.id.surface_editor_chip_terminal,
                R.id.surface_editor_group_terminal, R.id.surface_editor_reattach_canvas,
                R.string.termux_surface_tuning_terminal)})
            PAGES.put(page.slot, page);
    }

    private static final List<Row> ROWS = Collections.unmodifiableList(Arrays.asList(
        new Row(SurfaceSlot.DOCK, SurfaceProperty.BLUR, R.id.surface_editor_rows_dock,
            R.id.dock_tuning_blur_slider, R.id.dock_tuning_blur_value,
            R.id.surface_link_dock_blur, R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getExtraKeysBlurRadius,
            TermuxAppSharedPreferences::setExtraKeysBlurRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, R.id.surface_editor_rows_dock,
            R.id.dock_tuning_opacity_slider, R.id.dock_tuning_opacity_value,
            R.id.surface_link_dock_opacity, R.string.termux_dock_tuning_opacity, false, 100,
            TermuxAppSharedPreferences::getAppBarOpacity,
            TermuxAppSharedPreferences::setAppBarOpacity),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.GRAIN, R.id.surface_editor_rows_dock,
            R.id.dock_tuning_grain_slider, R.id.dock_tuning_grain_value,
            R.id.surface_link_dock_grain, R.string.termux_dock_tuning_grain, false, 100,
            TermuxAppSharedPreferences::getDockGlassGrain,
            TermuxAppSharedPreferences::setDockGlassGrain),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS, R.id.surface_editor_rows_dock,
            R.id.dock_tuning_radius_slider, R.id.dock_tuning_radius_value,
            R.id.surface_link_dock_radius, R.string.termux_dock_tuning_radius, true, 40,
            TermuxAppSharedPreferences::getAppLauncherDockCornerRadius,
            TermuxAppSharedPreferences::setAppLauncherDockCornerRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP, R.id.surface_editor_rows_dock,
            R.id.surface_tuning_dock_inset_slider, R.id.surface_tuning_dock_inset_value,
            R.id.surface_link_dock_gap, R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getDockHorizontalInset,
            TermuxAppSharedPreferences::setDockHorizontalInset),

        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY, R.id.surface_editor_rows_keyboard,
            R.id.surface_tuning_keyboard_bg_opacity_slider,
            R.id.surface_tuning_keyboard_bg_opacity_value,
            R.id.surface_link_keyboard_opacity,
            R.string.termux_surface_tuning_keyboard_bg_opacity, false, 100,
            TermuxAppSharedPreferences::getInAppKeyboardBackgroundOpacity,
            TermuxAppSharedPreferences::setInAppKeyboardBackgroundOpacity),
        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP, R.id.surface_editor_rows_keyboard,
            R.id.surface_tuning_keyboard_inset_slider, R.id.surface_tuning_keyboard_inset_value,
            R.id.surface_link_keyboard_gap, R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getInAppKeyboardHorizontalInset,
            TermuxAppSharedPreferences::setInAppKeyboardHorizontalInset),

        new Row(SurfaceSlot.STATUS, SurfaceProperty.BLUR, R.id.surface_editor_rows_status,
            R.id.surface_tuning_status_blur_slider, R.id.surface_tuning_status_blur_value,
            R.id.surface_link_status_blur, R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getStatusBarBlurRadius,
            TermuxAppSharedPreferences::setStatusBarBlurRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.OPACITY, R.id.surface_editor_rows_status,
            R.id.surface_tuning_status_opacity_slider, R.id.surface_tuning_status_opacity_value,
            R.id.surface_link_status_opacity, R.string.termux_dock_tuning_opacity, false, 100,
            TermuxAppSharedPreferences::getStatusBarOpacity,
            TermuxAppSharedPreferences::setStatusBarOpacity),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, R.id.surface_editor_rows_status,
            R.id.surface_tuning_status_grain_slider, R.id.surface_tuning_status_grain_value,
            R.id.surface_link_status_grain, R.string.termux_dock_tuning_grain, false, 100,
            TermuxAppSharedPreferences::getStatusBarGrain,
            TermuxAppSharedPreferences::setStatusBarGrain),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS, R.id.surface_editor_rows_status,
            R.id.surface_tuning_status_radius_slider, R.id.surface_tuning_status_radius_value,
            R.id.surface_link_status_radius, R.string.termux_dock_tuning_radius, true, 40,
            TermuxAppSharedPreferences::getStatusBarCornerRadius,
            TermuxAppSharedPreferences::setStatusBarCornerRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP, R.id.surface_editor_rows_status,
            R.id.surface_tuning_status_inset_slider, R.id.surface_tuning_status_inset_value,
            R.id.surface_link_status_gap, R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getStatusBarHorizontalInset,
            TermuxAppSharedPreferences::setStatusBarHorizontalInset),

        new Row(SurfaceSlot.CANVAS, SurfaceProperty.BLUR, R.id.surface_editor_rows_canvas_glass,
            R.id.dock_tuning_terminal_blur_slider, R.id.dock_tuning_terminal_blur_value,
            R.id.surface_link_canvas_blur, R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getTerminalGlassBlurRadius,
            TermuxAppSharedPreferences::setTerminalGlassBlurRadius),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN, R.id.surface_editor_rows_canvas_glass,
            R.id.dock_tuning_terminal_grain_slider, R.id.dock_tuning_terminal_grain_value,
            R.id.surface_link_canvas_grain, R.string.termux_dock_tuning_grain, false, 100,
            TermuxAppSharedPreferences::getTerminalGlassGrain,
            TermuxAppSharedPreferences::setTerminalGlassGrain),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, R.id.surface_editor_rows_canvas_top,
            R.id.dock_tuning_terminal_slider, R.id.dock_tuning_terminal_value,
            R.id.surface_link_canvas_opacity, R.string.termux_dock_tuning_terminal, false, 100,
            TermuxAppSharedPreferences::getTerminalBackgroundOpacity,
            TermuxAppSharedPreferences::setTerminalBackgroundOpacity)
    ));

    @NonNull
    public static List<Row> rows() {
        return ROWS;
    }

    /** The row a slider belongs to, or null when that slider is not a shared control. */
    public static Row forSlider(int sliderId) {
        for (Row row : ROWS) {
            if (row.sliderId == sliderId) return row;
        }
        return null;
    }

    /** The row editing one cell, or null where the surface has no such property. */
    public static Row forCell(@NonNull SurfaceSlot slot, @NonNull SurfaceProperty property) {
        for (Row row : ROWS) {
            if (row.slot == slot && row.property == property) return row;
        }
        return null;
    }

    @NonNull
    public static Page page(@NonNull SurfaceSlot slot) {
        return PAGES.get(slot);
    }

    @NonNull
    public static Iterable<Page> pages() {
        return PAGES.values();
    }

    /** The user-facing name of a surface, for the link chips' spoken descriptions. */
    @StringRes
    public static int slotLabel(@NonNull SurfaceSlot slot) {
        return page(slot).labelRes;
    }
}
