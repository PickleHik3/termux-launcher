package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;

/**
 * What one element of the arrangement offers, for one orientation, and what a pick writes. Pure: it
 * builds descriptions and writers, draws nothing and holds no view, so the rows under the Layout
 * editor's miniature are only a rendering of this, and both are testable on their own.
 *
 * <p>One orientation at a time is the whole point: the editor stands on a picture of a single
 * orientation, so a row offers what that one holds and a pick writes only its key. The arrangement
 * is shared by every place (ADR 0003), so a pick lands everywhere; the place is asked for only to
 * decide what is worth offering — the widget grid exists on Home alone, and only the display reads
 * how the keyboard stands over it.
 */
public final class PlaceArrangeModel {

    private PlaceArrangeModel() {}

    /** One element of the arrangement, as the editor's cards divide them up. */
    public enum Element {
        STATUS_BAR, PINNED_APPS, AZ_INDEX, EXTRA_KEYS, KEYBOARD, WIDGET_GRID;

        /** Whether the element is drawn on a place at all, and so worth offering there. */
        public boolean isOn(@NonNull PaneWallPage place) {
            return this != WIDGET_GRID || place == PaneWallPage.WIDGETS;
        }
    }

    /** A chooser writes as the user picks, so a group carries the write it will make. */
    public interface StringWriter {
        void write(@NonNull String value);
    }

    public interface IntWriter {
        void write(int value);
    }

    /** One labelled control in the section. */
    public abstract static class Group {
        @StringRes public final int labelRes;

        Group(@StringRes int labelRes) {
            this.labelRes = labelRes;
        }
    }

    /** A segmented pill: two to four values, one of them current. */
    public static final class Pills extends Group {
        @NonNull public final String[] values;
        @NonNull public final int[] labelResIds;
        @NonNull public final String selected;
        @NonNull public final StringWriter writer;

        Pills(@StringRes int labelRes, @NonNull String[] values, @NonNull int[] labelResIds,
              @NonNull String selected, @NonNull StringWriter writer) {
            super(labelRes);
            this.values = values;
            this.labelResIds = labelResIds;
            this.selected = selected;
            this.writer = writer;
        }

        /** Where the current value sits, or -1 when nothing stored matches what is offered. */
        public int selectedIndex() {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(selected)) return i;
            }
            return -1;
        }
    }

    /** How a number on a track reads out beside it. */
    public enum Unit {
        /** A bare count — the grid's cells. */
        COUNT,
        /** How far along its own range a size stands. */
        PERCENT,
        /** A length, printed in dp. */
        DP
    }

    /**
     * A number on a track: where it stands between a floor and a ceiling, and what a drag writes.
     * The two kinds below are the same row on the card and differ only in what the number means.
     */
    public abstract static class Track extends Group {
        @NonNull public final Unit unit;
        public final int min;
        public final int max;
        public final int value;
        @NonNull public final IntWriter writer;

        Track(@StringRes int labelRes, @NonNull Unit unit, int min, int max, int value,
              @NonNull IntWriter writer) {
            super(labelRes);
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.value = value;
            this.writer = writer;
        }
    }

    /** A count with a range — the widget grid's columns and rows. */
    public static final class Counter extends Track {
        Counter(@StringRes int labelRes, int min, int max, int value, @NonNull IntWriter writer) {
            super(labelRes, Unit.COUNT, min, max, value, writer);
        }
    }

    /**
     * One of the three sizes the layout keeps per orientation: how tall the dock stands, how tall
     * the keyboard stands, and how much air sits under the last key row.
     *
     * <p>The two heights are stored as scales and stand here as {@value #SCALE_STEPS} steps of
     * their own range, so one row kind covers all three and the mapping lives here rather than in
     * whatever is drawing the track.
     */
    public static final class Size extends Track {
        Size(@StringRes int labelRes, @NonNull Unit unit, int min, int max, int value,
             @NonNull IntWriter writer) {
            super(labelRes, unit, min, max, value, writer);
        }
    }

    /** A size stored as a scale is written back as a scale. */
    public interface FloatWriter {
        void write(float value);
    }

    /** How many steps a scale's track has between its floor and its ceiling. */
    public static final int SCALE_STEPS = 100;

    /** Where a scale stands on its track, as a step between 0 and {@link #SCALE_STEPS}. */
    public static int scaleProgress(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value) || max <= min)
            return 0;
        return Math.max(0, Math.min(SCALE_STEPS,
            Math.round((value - min) / (max - min) * SCALE_STEPS)));
    }

    /** The scale one step on the track stands for. */
    public static float scaleValue(int progress, float min, float max) {
        int step = Math.max(0, Math.min(SCALE_STEPS, progress));
        return min + (max - min) * step / SCALE_STEPS;
    }

    /** The value the A–Z index's shown/hidden pill stores; the edge is a value of its own. */
    private static final String AZ_SHOWN = "shown";
    private static final String AZ_HIDDEN = "hidden";

    private static final String[] EDGE_VALUES = {"top", "bottom", "left", "right"};
    private static final int[] EDGE_LABELS = {
        R.string.settings_layout_edge_top, R.string.settings_x11_extra_keys_side_bottom,
        R.string.settings_dock_rail_side_left, R.string.settings_dock_rail_side_right};
    private static final String[] EDGE_VALUES_PORTRAIT = {"top", "bottom"};
    private static final int[] EDGE_LABELS_PORTRAIT = {
        R.string.settings_layout_edge_top, R.string.settings_x11_extra_keys_side_bottom};

    private static final String[] ROW_VALUES = {"bottom", "left", "right", "hidden"};
    private static final int[] ROW_LABELS = {
        R.string.settings_x11_extra_keys_side_bottom, R.string.settings_dock_rail_side_left,
        R.string.settings_dock_rail_side_right, R.string.settings_layout_row_hidden};
    private static final String[] ROW_VALUES_PORTRAIT = {"bottom", "hidden"};
    private static final int[] ROW_LABELS_PORTRAIT = {
        R.string.settings_x11_extra_keys_side_bottom, R.string.settings_layout_row_hidden};

    private static final String[] AZ_VALUES = {AZ_SHOWN, AZ_HIDDEN};
    private static final int[] AZ_LABELS = {
        R.string.settings_layout_row_shown, R.string.settings_layout_row_hidden};

    private static final String[] FORM_VALUES = {"docked", "floating", "split"};
    private static final int[] FORM_LABELS = {
        R.string.settings_layout_keyboard_form_docked,
        R.string.settings_layout_keyboard_form_floating,
        R.string.settings_layout_keyboard_form_split};

    private static final String[] MODE_VALUES = {"resize", "overlay"};
    private static final int[] MODE_LABELS = {
        R.string.settings_layout_keyboard_mode_resize,
        R.string.settings_layout_keyboard_mode_overlay};

    /**
     * Everything one element offers in this orientation, as seen from this place, rebuilt whenever
     * a pick lands. Empty for an element the place does not draw.
     */
    @NonNull
    public static List<Group> groups(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                     @NonNull PlaceOrientation orientation,
                                     @NonNull Element element) {
        List<Group> groups = new ArrayList<>(2);
        if (!element.isOn(place)) return groups;
        switch (element) {
            case STATUS_BAR:
                groups.add(edgePills(R.string.settings_layout_status_bar_title, orientation,
                    places.statusBarEdge(orientation),
                    value -> places.setStatusBarEdge(orientation,
                        Edge.parse(value, Edge.TOP))));
                return groups;
            case PINNED_APPS:
                groups.add(rowPills(R.string.settings_show_pinned_apps_title, orientation,
                    places.appsRow(orientation),
                    value -> places.setAppsRow(orientation,
                        RowPlacement.parse(value, RowPlacement.BOTTOM))));
                return groups;
            case EXTRA_KEYS:
                groups.add(rowPills(R.string.settings_layout_miniature_keys, orientation,
                    places.extraKeys(orientation),
                    value -> places.setExtraKeys(orientation,
                        RowPlacement.parse(value, RowPlacement.BOTTOM))));
                return groups;
            case AZ_INDEX: {
                boolean shown = places.azRowShown(orientation);
                groups.add(new Pills(R.string.settings_layout_miniature_alphabets, AZ_VALUES,
                    AZ_LABELS, shown ? AZ_SHOWN : AZ_HIDDEN,
                    value -> places.setAzRowShown(orientation, AZ_SHOWN.equals(value))));
                // The edge is the bar's own wherever it stands: on the pinned apps row's edge it
                // rides that row, on any other it gets a bar of its own, and this is the control
                // that moves it between the two.
                if (shown)
                    groups.add(edgePills(R.string.settings_layout_alphabets_edge_title, orientation,
                        places.azBarEdge(orientation),
                        value -> places.setAzBarEdge(orientation,
                            Edge.parse(value, Edge.BOTTOM))));
                return groups;
            }
            case KEYBOARD:
                groups.add(new Pills(R.string.termux_surface_editor_place_keyboard_type,
                    FORM_VALUES, FORM_LABELS,
                    places.keyboardForm(orientation).storageValue(),
                    value -> places.setKeyboardForm(orientation,
                        KeyboardForm.parse(value, KeyboardForm.DOCKED))));
                // Only the display has a screen of its own for a keyboard to float over.
                if (place == PaneWallPage.DISPLAY)
                    groups.add(new Pills(R.string.settings_layout_keyboard_mode_title, MODE_VALUES,
                        MODE_LABELS, places.keyboardMode(orientation).storageValue(),
                        value -> places.setKeyboardMode(orientation,
                            KeyboardMode.parse(value, KeyboardMode.RESIZE))));
                return groups;
            case WIDGET_GRID:
            default:
                groups.add(new Counter(R.string.settings_widget_grid_columns_title,
                    TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                    places.widgetColumns(orientation),
                    value -> places.setWidgetColumns(orientation, value)));
                groups.add(new Counter(R.string.settings_widget_grid_rows_title,
                    TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS,
                    places.widgetRows(orientation),
                    value -> places.setWidgetRows(orientation, value)));
                return groups;
        }
    }

    /**
     * The sizes one element owns in this orientation: the dock's height, and the keyboard's height
     * and the air under its last key row. Empty for everything else, because everything else about
     * the arrangement is a position rather than a size.
     *
     * <p>Kept apart from {@link #groups} because the two answer different questions of the same
     * element — where the dock stands, and how tall it is — and only the Layout editor asks the
     * second. Both write through the store, which owns the clamps, so a step at either end of a
     * track is the value the layout actually takes.
     */
    @NonNull
    public static List<Group> sizes(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull Element element) {
        List<Group> groups = new ArrayList<>(2);
        if (!element.isOn(place)) return groups;
        switch (element) {
            case PINNED_APPS:
                groups.add(scale(R.string.termux_layout_editor_height,
                    places.dockHeightScale(orientation),
                    TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT,
                    TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT,
                    value -> places.setDockHeightScale(orientation, value)));
                return groups;
            case KEYBOARD:
                groups.add(scale(R.string.termux_layout_editor_height,
                    places.keyboardHeightScale(orientation),
                    TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    value -> places.setKeyboardHeightScale(orientation, value)));
                groups.add(new Size(R.string.termux_surface_tuning_peek_keyboard_chin, Unit.DP,
                    TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING,
                    TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING,
                    places.keyboardChinDp(orientation),
                    value -> places.setKeyboardChinDp(orientation, value)));
                return groups;
            default:
                return groups;
        }
    }

    /** One scale on a track of {@value #SCALE_STEPS} steps, written back in its own units. */
    @NonNull
    private static Size scale(@StringRes int labelRes, float value, float min, float max,
                              @NonNull FloatWriter writer) {
        return new Size(labelRes, Unit.PERCENT, 0, SCALE_STEPS, scaleProgress(value, min, max),
            progress -> writer.write(scaleValue(progress, min, max)));
    }

    /** Where a bar may stand: every edge in landscape, and only top or bottom in portrait. */
    @NonNull
    private static Pills edgePills(@StringRes int labelRes, @NonNull PlaceOrientation orientation,
                                   @NonNull Edge selected, @NonNull StringWriter writer) {
        boolean portrait = orientation == PlaceOrientation.PORTRAIT;
        return new Pills(labelRes, portrait ? EDGE_VALUES_PORTRAIT : EDGE_VALUES,
            portrait ? EDGE_LABELS_PORTRAIT : EDGE_LABELS, selected.storageValue(), writer);
    }

    /** Where a row may stand: a column down a side is landscape's alone. */
    @NonNull
    private static Pills rowPills(@StringRes int labelRes, @NonNull PlaceOrientation orientation,
                                  @NonNull RowPlacement selected, @NonNull StringWriter writer) {
        boolean portrait = orientation == PlaceOrientation.PORTRAIT;
        return new Pills(labelRes, portrait ? ROW_VALUES_PORTRAIT : ROW_VALUES,
            portrait ? ROW_LABELS_PORTRAIT : ROW_LABELS, selected.storageValue(), writer);
    }
}
