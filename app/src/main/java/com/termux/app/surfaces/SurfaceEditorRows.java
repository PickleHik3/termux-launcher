package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Every (surface, property) cell that participates in inheritance, as data.
 *
 * <p>One row per cell, tying it to the label and unit it announces, the ceiling its track runs to,
 * and the preference accessors that own its clamp. {@link SurfaceEditorProperties} builds the
 * editor's panels out of these, so a new surface or property is an entry here rather than a control
 * anywhere.
 *
 * <p>Each surface also has one {@link Page}, which is only its user-facing name — the tab row the
 * pages used to select is gone: a surface is picked by touching it.
 *
 * <p>Pure data, no views and no {@code Context}, so the set is testable on its own.
 */
public final class SurfaceEditorRows {

    private SurfaceEditorRows() {}

    /** One shared control: which surface and property it edits, and how it reads and writes. */
    public static final class Row {
        public final SurfaceSlot slot;
        public final SurfaceProperty property;
        @StringRes public final int labelRes;
        /** dp rather than percent; drives which value format the row prints. */
        public final boolean dp;
        public final int max;
        /** The resolved number the surface renders with, through the getter that owns its clamp. */
        public final ToIntFunction<TermuxAppSharedPreferences> read;
        /** Writes through the setter that owns the clamp; inherit-aware like every setter. */
        public final ObjIntConsumer<TermuxAppSharedPreferences> write;

        Row(SurfaceSlot slot, SurfaceProperty property, @StringRes int labelRes,
            boolean dp, int max,
            ToIntFunction<TermuxAppSharedPreferences> read,
            ObjIntConsumer<TermuxAppSharedPreferences> write) {
            this.slot = slot;
            this.property = property;
            this.labelRes = labelRes;
            this.dp = dp;
            this.max = max;
            this.read = read;
            this.write = write;
        }
    }

    /** One surface, and the label everything announces it by. */
    public static final class Page {
        public final SurfaceSlot slot;
        @StringRes public final int labelRes;

        Page(SurfaceSlot slot, @StringRes int labelRes) {
            this.slot = slot;
            this.labelRes = labelRes;
        }
    }

    private static final EnumMap<SurfaceSlot, Page> PAGES = new EnumMap<>(SurfaceSlot.class);

    static {
        for (Page page : new Page[] {
            new Page(SurfaceSlot.DOCK, R.string.termux_surface_tuning_dock),
            new Page(SurfaceSlot.KEYBOARD, R.string.termux_surface_tuning_keyboard),
            new Page(SurfaceSlot.STATUS, R.string.termux_surface_tuning_status),
            new Page(SurfaceSlot.CANVAS, R.string.termux_surface_tuning_terminal)})
            PAGES.put(page.slot, page);
    }

    private static final List<Row> ROWS = Collections.unmodifiableList(Arrays.asList(
        new Row(SurfaceSlot.DOCK, SurfaceProperty.BLUR,
            R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getExtraKeysBlurRadius,
            TermuxAppSharedPreferences::setExtraKeysBlurRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity,
            false, 100,
            TermuxAppSharedPreferences::getAppBarOpacity,
            TermuxAppSharedPreferences::setAppBarOpacity),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain,
            false, 100,
            TermuxAppSharedPreferences::getDockGlassGrain,
            TermuxAppSharedPreferences::setDockGlassGrain),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
            R.string.termux_dock_tuning_radius,
            true, 40,
            TermuxAppSharedPreferences::getAppLauncherDockCornerRadius,
            TermuxAppSharedPreferences::setAppLauncherDockCornerRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getDockHorizontalInset,
            TermuxAppSharedPreferences::setDockHorizontalInset),

        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity,
            false, 100,
            TermuxAppSharedPreferences::getInAppKeyboardBackgroundOpacity,
            TermuxAppSharedPreferences::setInAppKeyboardBackgroundOpacity),
        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getInAppKeyboardHorizontalInset,
            TermuxAppSharedPreferences::setInAppKeyboardHorizontalInset),

        new Row(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getStatusBarBlurRadius,
            TermuxAppSharedPreferences::setStatusBarBlurRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity,
            false, 100,
            TermuxAppSharedPreferences::getStatusBarOpacity,
            TermuxAppSharedPreferences::setStatusBarOpacity),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain,
            false, 100,
            TermuxAppSharedPreferences::getStatusBarGrain,
            TermuxAppSharedPreferences::setStatusBarGrain),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
            R.string.termux_dock_tuning_radius,
            true, 40,
            TermuxAppSharedPreferences::getStatusBarCornerRadius,
            TermuxAppSharedPreferences::setStatusBarCornerRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, true, 48,
            TermuxAppSharedPreferences::getStatusBarHorizontalInset,
            TermuxAppSharedPreferences::setStatusBarHorizontalInset),

        new Row(SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
            R.string.termux_dock_tuning_blur, true, 30,
            TermuxAppSharedPreferences::getTerminalGlassBlurRadius,
            TermuxAppSharedPreferences::setTerminalGlassBlurRadius),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain,
            false, 100,
            TermuxAppSharedPreferences::getTerminalGlassGrain,
            TermuxAppSharedPreferences::setTerminalGlassGrain),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_terminal,
            false, 100,
            TermuxAppSharedPreferences::getTerminalBackgroundOpacity,
            TermuxAppSharedPreferences::setTerminalBackgroundOpacity)
    ));

    @NonNull
    public static List<Row> rows() {
        return ROWS;
    }

    /**
     * The preference keys a place takes for itself when it overrides a cell: the surface's own
     * value, and its link to Base. Both, because a scoped number the shared link still calls
     * inherited would never be read — a place that overrides a cell overrides its whole answer.
     */
    @NonNull
    public static List<String> scopeKeys(@NonNull Row row) {
        String override = TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property);
        String link = TermuxPreferenceConstants.TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX
            + row.slot.key + "_" + row.property.key;
        return override == null ? Collections.singletonList(link)
            : Collections.unmodifiableList(Arrays.asList(override, link));
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

    /** The user-facing name of a surface. */
    @StringRes
    public static int slotLabel(@NonNull SurfaceSlot slot) {
        return page(slot).labelRes;
    }
}
