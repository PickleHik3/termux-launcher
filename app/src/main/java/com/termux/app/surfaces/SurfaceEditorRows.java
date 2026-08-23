package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import java.util.Collections;
import java.util.List;

/**
 * The surface editor's shared rows, as data.
 *
 * <p>Every control that participates in inheritance appears here exactly once, tying a
 * (surface, property) pair to the three views that render it — slider, value read-out and link
 * chip — plus the label and unit it should announce. The editor iterates this table instead of
 * repeating the same wiring fifteen times, which is also what keeps the tab badges, the link chips
 * and the sliders from drifting apart: they are all driven from the same list.
 *
 * <p>Pure data, no views and no {@code Context}, so the set is testable on its own.
 */
public final class SurfaceEditorRows {

    private SurfaceEditorRows() {}

    /** One shared control: which surface and property it edits, and the views that show it. */
    public static final class Row {
        public final SurfaceSlot slot;
        public final SurfaceProperty property;
        public final int sliderId;
        public final int valueId;
        public final int chipId;
        @StringRes public final int labelRes;
        /** dp rather than percent; drives which value format the row prints. */
        public final boolean dp;
        public final int max;

        Row(SurfaceSlot slot, SurfaceProperty property, int sliderId, int valueId, int chipId,
            @StringRes int labelRes, boolean dp, int max) {
            this.slot = slot;
            this.property = property;
            this.sliderId = sliderId;
            this.valueId = valueId;
            this.chipId = chipId;
            this.labelRes = labelRes;
            this.dp = dp;
            this.max = max;
        }
    }

    private static final List<Row> ROWS = Collections.unmodifiableList(java.util.Arrays.asList(
        new Row(SurfaceSlot.DOCK, SurfaceProperty.BLUR,
            R.id.dock_tuning_blur_slider, R.id.dock_tuning_blur_value,
            R.id.surface_link_dock_blur, R.string.termux_dock_tuning_blur, true, 30),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            R.id.dock_tuning_opacity_slider, R.id.dock_tuning_opacity_value,
            R.id.surface_link_dock_opacity, R.string.termux_dock_tuning_opacity, false, 100),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            R.id.dock_tuning_grain_slider, R.id.dock_tuning_grain_value,
            R.id.surface_link_dock_grain, R.string.termux_dock_tuning_grain, false, 100),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
            R.id.dock_tuning_radius_slider, R.id.dock_tuning_radius_value,
            R.id.surface_link_dock_radius, R.string.termux_dock_tuning_radius, true, 40),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
            R.id.surface_tuning_dock_inset_slider, R.id.surface_tuning_dock_inset_value,
            R.id.surface_link_dock_gap, R.string.termux_surface_tuning_edges, true, 48),

        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            R.id.surface_tuning_keyboard_bg_opacity_slider,
            R.id.surface_tuning_keyboard_bg_opacity_value,
            R.id.surface_link_keyboard_opacity,
            R.string.termux_surface_tuning_keyboard_bg_opacity, false, 100),
        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
            R.id.surface_tuning_keyboard_inset_slider, R.id.surface_tuning_keyboard_inset_value,
            R.id.surface_link_keyboard_gap, R.string.termux_surface_tuning_edges, true, 48),

        new Row(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            R.id.surface_tuning_status_blur_slider, R.id.surface_tuning_status_blur_value,
            R.id.surface_link_status_blur, R.string.termux_dock_tuning_blur, true, 30),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            R.id.surface_tuning_status_opacity_slider, R.id.surface_tuning_status_opacity_value,
            R.id.surface_link_status_opacity, R.string.termux_dock_tuning_opacity, false, 100),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            R.id.surface_tuning_status_grain_slider, R.id.surface_tuning_status_grain_value,
            R.id.surface_link_status_grain, R.string.termux_dock_tuning_grain, false, 100),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
            R.id.surface_tuning_status_radius_slider, R.id.surface_tuning_status_radius_value,
            R.id.surface_link_status_radius, R.string.termux_dock_tuning_radius, true, 40),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
            R.id.surface_tuning_status_inset_slider, R.id.surface_tuning_status_inset_value,
            R.id.surface_link_status_gap, R.string.termux_surface_tuning_edges, true, 48),

        new Row(SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
            R.id.dock_tuning_terminal_blur_slider, R.id.dock_tuning_terminal_blur_value,
            R.id.surface_link_canvas_blur, R.string.termux_dock_tuning_blur, true, 30),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
            R.id.dock_tuning_terminal_grain_slider, R.id.dock_tuning_terminal_grain_value,
            R.id.surface_link_canvas_grain, R.string.termux_dock_tuning_grain, false, 100),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            R.id.dock_tuning_terminal_slider, R.id.dock_tuning_terminal_value,
            R.id.surface_link_canvas_opacity, R.string.termux_dock_tuning_terminal, false, 100)
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

    /** The user-facing name of a surface, for the link chips' spoken descriptions. */
    @StringRes
    public static int slotLabel(@NonNull SurfaceSlot slot) {
        switch (slot) {
            case KEYBOARD: return R.string.termux_surface_tuning_keyboard;
            case STATUS: return R.string.termux_surface_tuning_status;
            case CANVAS: return R.string.termux_surface_tuning_terminal;
            default: return R.string.termux_surface_tuning_dock;
        }
    }
}
