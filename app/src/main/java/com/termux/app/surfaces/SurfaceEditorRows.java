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
 * Every (surface, property) cell that participates in inheritance, as data.
 *
 * <p>One row per cell, tying it to the label and unit it announces, the ceiling its track runs to,
 * and the preference accessors that own its clamp. The editor's pill carries a single slider and
 * points it at whichever cell the active chip names, so this table is what makes a new surface or
 * property a row here rather than a control anywhere.
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
        /** The lowercase noun the inheritance footnote calls this property by. */
        @StringRes public final int nounRes;
        /** dp rather than percent; drives which value format the row prints. */
        public final boolean dp;
        public final int max;
        /** The resolved number the surface renders with, through the getter that owns its clamp. */
        public final ToIntFunction<TermuxAppSharedPreferences> read;
        /** Writes through the setter that owns the clamp; inherit-aware like every setter. */
        public final ObjIntConsumer<TermuxAppSharedPreferences> write;

        Row(SurfaceSlot slot, SurfaceProperty property, @StringRes int labelRes,
            @StringRes int nounRes, boolean dp, int max,
            ToIntFunction<TermuxAppSharedPreferences> read,
            ObjIntConsumer<TermuxAppSharedPreferences> write) {
            this.slot = slot;
            this.property = property;
            this.labelRes = labelRes;
            this.nounRes = nounRes;
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
            R.string.termux_dock_tuning_blur, R.string.termux_surface_editor_noun_blur, true, 30,
            TermuxAppSharedPreferences::getExtraKeysBlurRadius,
            TermuxAppSharedPreferences::setExtraKeysBlurRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity, R.string.termux_surface_editor_noun_opacity,
            false, 100,
            TermuxAppSharedPreferences::getAppBarOpacity,
            TermuxAppSharedPreferences::setAppBarOpacity),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain, R.string.termux_surface_editor_noun_grain,
            false, 100,
            TermuxAppSharedPreferences::getDockGlassGrain,
            TermuxAppSharedPreferences::setDockGlassGrain),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS,
            R.string.termux_dock_tuning_radius, R.string.termux_surface_editor_noun_corners,
            true, 40,
            TermuxAppSharedPreferences::getAppLauncherDockCornerRadius,
            TermuxAppSharedPreferences::setAppLauncherDockCornerRadius),
        new Row(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, R.string.termux_surface_editor_noun_gap, true, 48,
            TermuxAppSharedPreferences::getDockHorizontalInset,
            TermuxAppSharedPreferences::setDockHorizontalInset),

        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity, R.string.termux_surface_editor_noun_opacity,
            false, 100,
            TermuxAppSharedPreferences::getInAppKeyboardBackgroundOpacity,
            TermuxAppSharedPreferences::setInAppKeyboardBackgroundOpacity),
        new Row(SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, R.string.termux_surface_editor_noun_gap, true, 48,
            TermuxAppSharedPreferences::getInAppKeyboardHorizontalInset,
            TermuxAppSharedPreferences::setInAppKeyboardHorizontalInset),

        new Row(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            R.string.termux_dock_tuning_blur, R.string.termux_surface_editor_noun_blur, true, 30,
            TermuxAppSharedPreferences::getStatusBarBlurRadius,
            TermuxAppSharedPreferences::setStatusBarBlurRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_opacity, R.string.termux_surface_editor_noun_opacity,
            false, 100,
            TermuxAppSharedPreferences::getStatusBarOpacity,
            TermuxAppSharedPreferences::setStatusBarOpacity),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain, R.string.termux_surface_editor_noun_grain,
            false, 100,
            TermuxAppSharedPreferences::getStatusBarGrain,
            TermuxAppSharedPreferences::setStatusBarGrain),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS,
            R.string.termux_dock_tuning_radius, R.string.termux_surface_editor_noun_corners,
            true, 40,
            TermuxAppSharedPreferences::getStatusBarCornerRadius,
            TermuxAppSharedPreferences::setStatusBarCornerRadius),
        new Row(SurfaceSlot.STATUS, SurfaceProperty.SIDE_GAP,
            R.string.termux_surface_tuning_edges, R.string.termux_surface_editor_noun_gap, true, 48,
            TermuxAppSharedPreferences::getStatusBarHorizontalInset,
            TermuxAppSharedPreferences::setStatusBarHorizontalInset),

        new Row(SurfaceSlot.CANVAS, SurfaceProperty.BLUR,
            R.string.termux_dock_tuning_blur, R.string.termux_surface_editor_noun_blur, true, 30,
            TermuxAppSharedPreferences::getTerminalGlassBlurRadius,
            TermuxAppSharedPreferences::setTerminalGlassBlurRadius),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN,
            R.string.termux_dock_tuning_grain, R.string.termux_surface_editor_noun_grain,
            false, 100,
            TermuxAppSharedPreferences::getTerminalGlassGrain,
            TermuxAppSharedPreferences::setTerminalGlassGrain),
        new Row(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            R.string.termux_dock_tuning_terminal, R.string.termux_surface_editor_noun_opacity,
            false, 100,
            TermuxAppSharedPreferences::getTerminalBackgroundOpacity,
            TermuxAppSharedPreferences::setTerminalBackgroundOpacity)
    ));

    @NonNull
    public static List<Row> rows() {
        return ROWS;
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

    /** The user-facing name of a surface. */
    @StringRes
    public static int slotLabel(@NonNull SurfaceSlot slot) {
        return page(slot).labelRes;
    }
}
