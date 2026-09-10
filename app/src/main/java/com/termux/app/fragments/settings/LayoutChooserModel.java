package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.place.KeyboardOnEnter;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.List;

/**
 * What one element's chooser offers and what a row says it is set to, read straight off
 * {@link PlaceLayoutStore}. Pure: it builds descriptions and writers, draws nothing and holds no
 * view, so the sheet is only the rendering of this and both are testable on their own.
 *
 * <p>Portrait and landscape are separate groups because they are separate stored values; the
 * settings a place remembers once — the keyboard on enter — get a single group.
 */
public final class LayoutChooserModel {

    private LayoutChooserModel() {}

    /** A chooser writes as the user picks, so a group carries the write it will make. */
    public interface StringWriter {
        void write(@NonNull String value);
    }

    public interface IntWriter {
        void write(int value);
    }

    /** One labelled control in a chooser. The label is null when the group needs no heading. */
    public abstract static class Group {
        @Nullable public final String label;

        Group(@Nullable String label) {
            this.label = label;
        }
    }

    /** A segmented pill: two to four values, one of them current. */
    public static final class Pills extends Group {
        @NonNull public final String[] values;
        @NonNull public final int[] labelResIds;
        @NonNull public final String selected;
        @NonNull public final StringWriter writer;

        Pills(@Nullable String label, @NonNull String[] values, @NonNull int[] labelResIds,
              @NonNull String selected, @NonNull StringWriter writer) {
            super(label);
            this.values = values;
            this.labelResIds = labelResIds;
            this.selected = selected;
            this.writer = writer;
        }
    }

    /** A count with a range — the widget grid's columns and rows. */
    public static final class Counter extends Group {
        @StringRes public final int titleRes;
        public final int min;
        public final int max;
        public final int value;
        @NonNull public final IntWriter writer;

        Counter(@Nullable String label, @StringRes int titleRes, int min, int max, int value,
                @NonNull IntWriter writer) {
            super(label);
            this.titleRes = titleRes;
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

    private static final PlaceOrientation[] BOTH =
        {PlaceOrientation.PORTRAIT, PlaceOrientation.LANDSCAPE};

    // ---- Rows ----------------------------------------------------------------------------------

    /** What the row says the element is set to: the portrait value, then the landscape one. */
    @NonNull
    public static String summary(@NonNull Context context, @NonNull PlaceLayoutStore places,
                                 @NonNull PaneWallPage place, @NonNull LayoutElement element) {
        return context.getString(R.string.settings_layout_row_values_format,
            value(context, places, place, element, PlaceOrientation.PORTRAIT),
            value(context, places, place, element, PlaceOrientation.LANDSCAPE));
    }

    @NonNull
    private static String value(@NonNull Context context, @NonNull PlaceLayoutStore places,
                                @NonNull PaneWallPage place, @NonNull LayoutElement element,
                                @NonNull PlaceOrientation orientation) {
        switch (element) {
            case STATUS_BAR:
                return context.getString(edgeLabel(places.statusBarEdge(place, orientation)));
            case PINNED_APPS:
                return context.getString(rowLabel(places.appsRow(place, orientation)));
            case AZ_INDEX:
                return azValue(context, places, place, orientation);
            case EXTRA_KEYS:
                return context.getString(rowLabel(places.extraKeys(place, orientation)));
            case KEYBOARD:
                return context.getString(formLabel(places.keyboardForm(place, orientation)));
            case WIDGET_GRID:
            default:
                return context.getString(R.string.settings_layout_grid_size_format,
                    places.widgetColumns(place, orientation),
                    places.widgetRows(place, orientation));
        }
    }

    /**
     * The A–Z index reads as its own edge only while it stands on its own; riding under the
     * pinned apps it goes wherever they go, so the row says no more than that it is there.
     */
    @NonNull
    private static String azValue(@NonNull Context context, @NonNull PlaceLayoutStore places,
                                  @NonNull PaneWallPage place,
                                  @NonNull PlaceOrientation orientation) {
        if (!places.azRowShown(place, orientation)) {
            return context.getString(R.string.settings_layout_row_hidden);
        }
        PlaceLayout resolved = places.resolve(place, orientation);
        return PlaceChromePolicy.azIndexStandsAlone(resolved)
            ? context.getString(edgeLabel(places.azBarEdge(place, orientation)))
            : context.getString(R.string.settings_layout_row_shown);
    }

    // ---- Choosers ------------------------------------------------------------------------------

    /** Everything the element's chooser offers, rebuilt whenever a pick lands. */
    @NonNull
    public static List<Group> groups(@NonNull Context context, @NonNull PlaceLayoutStore places,
                                     @NonNull PaneWallPage place, @NonNull LayoutElement element) {
        List<Group> groups = new ArrayList<>();
        switch (element) {
            case STATUS_BAR:
                for (PlaceOrientation orientation : BOTH) {
                    groups.add(edgePills(context, orientation, orientation(context, orientation),
                        places.statusBarEdge(place, orientation),
                        value -> places.setStatusBarEdge(place, orientation,
                            Edge.parse(value, Edge.TOP))));
                }
                return groups;
            case PINNED_APPS:
                for (PlaceOrientation orientation : BOTH) {
                    groups.add(rowPills(context, orientation, orientation(context, orientation),
                        places.appsRow(place, orientation),
                        value -> places.setAppsRow(place, orientation,
                            RowPlacement.parse(value, RowPlacement.BOTTOM))));
                }
                return groups;
            case EXTRA_KEYS:
                for (PlaceOrientation orientation : BOTH) {
                    groups.add(rowPills(context, orientation, orientation(context, orientation),
                        places.extraKeys(place, orientation),
                        value -> places.setExtraKeys(place, orientation,
                            RowPlacement.parse(value, RowPlacement.BOTTOM))));
                }
                return groups;
            case AZ_INDEX:
                for (PlaceOrientation orientation : BOTH) {
                    boolean shown = places.azRowShown(place, orientation);
                    groups.add(new Pills(orientation(context, orientation), AZ_VALUES, AZ_LABELS,
                        shown ? AZ_SHOWN : AZ_HIDDEN,
                        value -> places.setAzRowShown(place, orientation, AZ_SHOWN.equals(value))));
                    // Standing on its own the bar picks its own edge; under the pinned apps it
                    // rides with them and the stored edge is not what happens.
                    if (shown && PlaceChromePolicy.azIndexStandsAlone(
                        places.resolve(place, orientation))) {
                        groups.add(edgePills(context, orientation,
                            group(context, R.string.settings_layout_alphabets_edge_title, orientation),
                            places.azBarEdge(place, orientation),
                            value -> places.setAzBarEdge(place, orientation,
                                Edge.parse(value, Edge.BOTTOM))));
                    }
                }
                return groups;
            case KEYBOARD:
                for (PlaceOrientation orientation : BOTH) {
                    groups.add(new Pills(
                        group(context, R.string.settings_layout_keyboard_form_title, orientation),
                        FORM_VALUES, FORM_LABELS,
                        places.keyboardForm(place, orientation).storageValue(),
                        value -> places.setKeyboardForm(place, orientation,
                            KeyboardForm.parse(value, KeyboardForm.DOCKED))));
                }
                groups.add(new Pills(
                    context.getString(R.string.settings_layout_keyboard_on_enter_title),
                    ON_ENTER_VALUES, ON_ENTER_LABELS, places.keyboardOnEnter(place).storageValue(),
                    value -> places.setKeyboardOnEnter(place,
                        KeyboardOnEnter.parse(value, KeyboardOnEnter.AS_LEFT))));
                // Only the display has a screen of its own for a keyboard to float over.
                if (place == PaneWallPage.DISPLAY) {
                    for (PlaceOrientation orientation : BOTH) {
                        groups.add(new Pills(
                            group(context, R.string.settings_layout_keyboard_mode_title, orientation),
                            MODE_VALUES, MODE_LABELS,
                            places.keyboardMode(place, orientation).storageValue(),
                            value -> places.setKeyboardMode(place, orientation,
                                KeyboardMode.parse(value, KeyboardMode.RESIZE))));
                    }
                }
                return groups;
            case WIDGET_GRID:
            default:
                for (PlaceOrientation orientation : BOTH) {
                    groups.add(new Counter(orientation(context, orientation),
                        R.string.settings_widget_grid_columns_title,
                        TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                        TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                        places.widgetColumns(place, orientation),
                        value -> places.setWidgetColumns(place, orientation, value)));
                    groups.add(new Counter(null, R.string.settings_widget_grid_rows_title,
                        TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                        TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS,
                        places.widgetRows(place, orientation),
                        value -> places.setWidgetRows(place, orientation, value)));
                }
                return groups;
        }
    }

    // ---- Drops ---------------------------------------------------------------------------------

    /**
     * A bar dropped on the miniature, written through the same keys its chooser writes: the edge
     * it landed on, or {@code null} for the tray, which is where a bar goes to be hidden.
     *
     * @return whether anything was written — a bar dropped somewhere it cannot stand writes
     *     nothing, so the picture springs it back instead.
     */
    public static boolean applyDrop(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                    @NonNull PlaceOrientation orientation,
                                    @NonNull MiniatureDragPolicy.Bar bar, @Nullable Edge edge) {
        switch (bar) {
            case STATUS_BAR:
                // The status bar is never hidden, so the tray is not one of its targets.
                if (edge == null) return false;
                places.setStatusBarEdge(place, orientation, edge);
                return true;
            case APPS_ROW: {
                RowPlacement placement = rowPlacement(edge);
                if (placement == null) return false;
                places.setAppsRow(place, orientation, placement);
                return true;
            }
            case EXTRA_KEYS: {
                RowPlacement placement = rowPlacement(edge);
                if (placement == null) return false;
                places.setExtraKeys(place, orientation, placement);
                return true;
            }
            case AZ_INDEX:
                if (edge == null) {
                    places.setAzRowShown(place, orientation, false);
                    return true;
                }
                // Brought back out of the tray onto an edge, the index is both shown again and
                // standing where it was dropped.
                places.setAzRowShown(place, orientation, true);
                places.setAzBarEdge(place, orientation, edge);
                return true;
            default:
                return false;
        }
    }

    /** The row placement an edge means, or null for the tray; a row has no top position. */
    @Nullable
    private static RowPlacement rowPlacement(@Nullable Edge edge) {
        if (edge == null) return RowPlacement.HIDDEN;
        switch (edge) {
            case BOTTOM: return RowPlacement.BOTTOM;
            case LEFT: return RowPlacement.LEFT;
            case RIGHT: return RowPlacement.RIGHT;
            case TOP:
            default: return null;
        }
    }

    /** Where a bar may stand: every edge in landscape, and only top or bottom in portrait. */
    @NonNull
    private static Pills edgePills(@NonNull Context context, @NonNull PlaceOrientation orientation,
                                   @Nullable String label, @NonNull Edge selected,
                                   @NonNull StringWriter writer) {
        boolean portrait = orientation == PlaceOrientation.PORTRAIT;
        return new Pills(label, portrait ? EDGE_VALUES_PORTRAIT : EDGE_VALUES,
            portrait ? EDGE_LABELS_PORTRAIT : EDGE_LABELS, selected.storageValue(), writer);
    }

    /** Where a row may stand: a column down a side is landscape's alone. */
    @NonNull
    private static Pills rowPills(@NonNull Context context, @NonNull PlaceOrientation orientation,
                                  @Nullable String label, @NonNull RowPlacement selected,
                                  @NonNull StringWriter writer) {
        boolean portrait = orientation == PlaceOrientation.PORTRAIT;
        return new Pills(label, portrait ? ROW_VALUES_PORTRAIT : ROW_VALUES,
            portrait ? ROW_LABELS_PORTRAIT : ROW_LABELS, selected.storageValue(), writer);
    }

    @NonNull
    private static String orientation(@NonNull Context context,
                                      @NonNull PlaceOrientation orientation) {
        return context.getString(orientation == PlaceOrientation.LANDSCAPE
            ? R.string.settings_layout_orientation_landscape
            : R.string.settings_layout_orientation_portrait);
    }

    @NonNull
    private static String group(@NonNull Context context, @StringRes int titleRes,
                                @NonNull PlaceOrientation orientation) {
        return context.getString(R.string.settings_layout_chooser_group_format,
            context.getString(titleRes), orientation(context, orientation));
    }

    /** The word for an edge. Shared with the miniature, which names the same positions. */
    @StringRes
    static int edgeLabel(@NonNull Edge edge) {
        switch (edge) {
            case BOTTOM: return R.string.settings_x11_extra_keys_side_bottom;
            case LEFT: return R.string.settings_dock_rail_side_left;
            case RIGHT: return R.string.settings_dock_rail_side_right;
            case TOP:
            default: return R.string.settings_layout_edge_top;
        }
    }

    /** The word for a row's placement, hidden included. Shared with the miniature. */
    @StringRes
    static int rowLabel(@NonNull RowPlacement placement) {
        switch (placement) {
            case LEFT: return R.string.settings_dock_rail_side_left;
            case RIGHT: return R.string.settings_dock_rail_side_right;
            case HIDDEN: return R.string.settings_layout_row_hidden;
            case BOTTOM:
            default: return R.string.settings_x11_extra_keys_side_bottom;
        }
    }

    @StringRes
    private static int formLabel(@NonNull KeyboardForm form) {
        switch (form) {
            case FLOATING: return R.string.settings_layout_keyboard_form_floating;
            case SPLIT: return R.string.settings_layout_keyboard_form_split;
            case DOCKED:
            default: return R.string.settings_layout_keyboard_form_docked;
        }
    }
}
