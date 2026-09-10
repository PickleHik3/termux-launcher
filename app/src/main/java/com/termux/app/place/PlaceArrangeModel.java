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
 * What one element of a place's arrangement offers, for one orientation, and what a pick writes.
 * Pure: it builds descriptions and writers, draws nothing and holds no view, so the Place section
 * on the surface editor's cards is only the rendering of this and both are testable on their own.
 *
 * <p>The Layout page asks the same questions of the same store through
 * {@code LayoutChooserModel}, which answers for both orientations at once because its rows show
 * both. The editor is standing on the live screen, so it only ever offers the orientation that is
 * on it — hence a model of its own, with the value sets and labels held against the page's by
 * {@code PlaceArrangeModelTest} so the two cannot drift.
 */
public final class PlaceArrangeModel {

    private PlaceArrangeModel() {}

    /** One element of the arrangement, as the editor's cards divide them up. */
    public enum Element {
        STATUS_BAR, PINNED_APPS, AZ_INDEX, EXTRA_KEYS, KEYBOARD, WIDGET_GRID;

        /** Whether the element exists on a place at all. */
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

    /** A count with a range — the widget grid's columns and rows. */
    public static final class Counter extends Group {
        public final int min;
        public final int max;
        public final int value;
        @NonNull public final IntWriter writer;

        Counter(@StringRes int labelRes, int min, int max, int value, @NonNull IntWriter writer) {
            super(labelRes);
            this.min = min;
            this.max = max;
            this.value = value;
            this.writer = writer;
        }
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

    private static final String[] ON_ENTER_VALUES = {"as_left", "open", "closed"};
    private static final int[] ON_ENTER_LABELS = {
        R.string.settings_layout_keyboard_on_enter_as_left,
        R.string.settings_layout_keyboard_on_enter_open,
        R.string.settings_layout_keyboard_on_enter_closed};

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
     * Everything one element offers on this place in this orientation, rebuilt whenever a pick
     * lands. Empty for an element the place does not have.
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
                    places.statusBarEdge(place, orientation),
                    value -> places.setStatusBarEdge(place, orientation,
                        Edge.parse(value, Edge.TOP))));
                return groups;
            case PINNED_APPS:
                groups.add(rowPills(R.string.settings_show_pinned_apps_title, orientation,
                    places.appsRow(place, orientation),
                    value -> places.setAppsRow(place, orientation,
                        RowPlacement.parse(value, RowPlacement.BOTTOM))));
                return groups;
            case EXTRA_KEYS:
                groups.add(rowPills(R.string.settings_layout_miniature_keys, orientation,
                    places.extraKeys(place, orientation),
                    value -> places.setExtraKeys(place, orientation,
                        RowPlacement.parse(value, RowPlacement.BOTTOM))));
                return groups;
            case AZ_INDEX: {
                boolean shown = places.azRowShown(place, orientation);
                groups.add(new Pills(R.string.settings_layout_miniature_alphabets, AZ_VALUES,
                    AZ_LABELS, shown ? AZ_SHOWN : AZ_HIDDEN,
                    value -> places.setAzRowShown(place, orientation, AZ_SHOWN.equals(value))));
                // Standing on its own the bar picks its own edge; under the pinned apps it rides
                // with them and the stored edge is not what happens.
                if (shown && PlaceChromePolicy.azIndexStandsAlone(places.resolve(place, orientation)))
                    groups.add(edgePills(R.string.settings_layout_alphabets_edge_title, orientation,
                        places.azBarEdge(place, orientation),
                        value -> places.setAzBarEdge(place, orientation,
                            Edge.parse(value, Edge.BOTTOM))));
                return groups;
            }
            case KEYBOARD:
                groups.add(new Pills(R.string.termux_surface_editor_place_keyboard_type,
                    FORM_VALUES, FORM_LABELS,
                    places.keyboardForm(place, orientation).storageValue(),
                    value -> places.setKeyboardForm(place, orientation,
                        KeyboardForm.parse(value, KeyboardForm.DOCKED))));
                groups.add(new Pills(R.string.termux_surface_editor_place_keyboard_on_enter,
                    ON_ENTER_VALUES, ON_ENTER_LABELS, places.keyboardOnEnter(place).storageValue(),
                    value -> places.setKeyboardOnEnter(place,
                        KeyboardOnEnter.parse(value, KeyboardOnEnter.AS_LEFT))));
                // Only the display has a screen of its own for a keyboard to float over.
                if (place == PaneWallPage.DISPLAY)
                    groups.add(new Pills(R.string.settings_layout_keyboard_mode_title, MODE_VALUES,
                        MODE_LABELS, places.keyboardMode(place, orientation).storageValue(),
                        value -> places.setKeyboardMode(place, orientation,
                            KeyboardMode.parse(value, KeyboardMode.RESIZE))));
                return groups;
            case WIDGET_GRID:
            default:
                groups.add(new Counter(R.string.settings_widget_grid_columns_title,
                    TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                    places.widgetColumns(place, orientation),
                    value -> places.setWidgetColumns(place, orientation, value)));
                groups.add(new Counter(R.string.settings_widget_grid_rows_title,
                    TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                    TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS,
                    places.widgetRows(place, orientation),
                    value -> places.setWidgetRows(place, orientation, value)));
                return groups;
        }
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
