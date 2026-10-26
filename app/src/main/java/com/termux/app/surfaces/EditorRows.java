package com.termux.app.surfaces;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What every card of the Appearance editor holds, as data (appearance-and-settings-rework §2.3,
 * §3, §7 and §8): one table of rows per {@link EditorCard}, each row in one of three tiers, so a
 * control's position teaches how often it is touched. Tiers only order the rows: every row of a
 * card is always shown, and the sheet scrolls. Every value slider of a card is a column of its
 * glass strip ({@link Kind#STRIP}), all of them in one run, first in the card; the Appearance
 * card's Look is the one horizontal slider.
 *
 * <p>It also answers where a row writes ({@link #halfFor}): the glass numbers of the dock, the
 * status bar and the keyboard, and the Look slider, land on the half the frame shows; everything
 * else (shapes, heights, places, the terminal's own numbers and every app-wide choice) is one
 * value for the wall.</p>
 *
 * <p>No views and no {@code Context}, so the table is held by JVM tests.</p>
 */
public final class EditorRows {

    private EditorRows() {}

    /** How a row is drawn and what it reports. */
    public enum Kind {
        /**
         * A vertical slider in the card's strip ({@link LegendSlider}): its name and value read
         * upward beside the track ("Frost · 20%", "Corners · 16 dp"). Every value slider is one,
         * the glass and the sizes alike; strip rows that follow one another stand side by side
         * as one strip, as the previous editor's Custom row did.
         */
        STRIP,
        /** A switch. */
        SWITCH,
        /** A connected button group: one of a few named choices. */
        SEGMENTS,
        /** Glyph chips: the Dock design. */
        CHIPS,
        /** The current choice, which opens a menu of the rest. */
        MENU,
        /** A row that opens a page of its own, in Settings or over the editor. */
        LINK,
        /** The Look slider with its stops' names under it. */
        LOOK,
        /** The wallpaper: shown or not, and Change. */
        WALLPAPER,
        /** A row that opens a panel under it in the card: the icon packs. */
        EXPANDER,
        /**
         * A row that does one thing in place, named by a word at its end: the Match rows' Copy,
         * which reads Undo while the copy can still be taken back.
         */
        ACTION
    }

    /** Every row any card has. */
    public enum Row {
        OPACITY(Kind.STRIP, R.string.appearance_row_opacity, R.drawable.ic_editor_opacity),
        TINT(Kind.STRIP, R.string.appearance_row_tint, R.drawable.ic_editor_tint),
        HEIGHT(Kind.STRIP, R.string.appearance_row_height, R.drawable.ic_editor_height),
        VISIBLE(Kind.SWITCH, R.string.appearance_row_visible, R.drawable.ic_editor_visible),
        STYLE(Kind.SEGMENTS, R.string.appearance_row_style, R.drawable.ic_style_floating),
        DESIGN(Kind.CHIPS, R.string.appearance_row_design, R.drawable.ic_symbol_dock_glass_tray),
        /**
         * Frost: blur and grain together along {@link GlassControls}' curve, the one glass slider
         * the cards have (BLUR and GRAIN are still the stored properties underneath).
         */
        FROST(Kind.STRIP, R.string.appearance_row_frost, R.drawable.ic_editor_blur),
        CORNERS(Kind.STRIP, R.string.appearance_row_corners, R.drawable.ic_editor_corners),
        MARGIN(Kind.STRIP, R.string.appearance_row_margin, R.drawable.ic_editor_margin),
        DEPTH(Kind.STRIP, R.string.appearance_row_depth, R.drawable.ic_editor_depth),
        SEARCH_FIELD(Kind.SWITCH, R.string.appearance_row_search_field, R.drawable.ic_symbol_search),
        DRAWER_ICON(Kind.SWITCH, R.string.layout_editor_drawer_tile, R.drawable.ic_symbol_apps),
        /**
         * How strongly the icons' reflections show on the Peek glass card, 0 (off) to strong;
         * the Dock card shows it only while Peek glass is the design.
         */
        REFLECTION(Kind.STRIP, R.string.appearance_row_reflection,
            R.drawable.ic_symbol_dock_peek_glass),
        APP_ICONS(Kind.STRIP, R.string.appearance_row_app_icons, R.drawable.ic_symbol_apps),
        TEXT_SIZE(Kind.STRIP, R.string.appearance_row_text_size, R.drawable.ic_editor_text_size),
        CONTRAST(Kind.SEGMENTS, R.string.appearance_row_contrast, R.drawable.ic_editor_contrast),
        CURSOR_TRAIL(Kind.MENU, R.string.appearance_row_cursor_trail, R.drawable.ic_symbol_tune),
        EFFECT(Kind.MENU, R.string.appearance_row_effect, R.drawable.ic_symbol_tune),
        SPLIT_GAP(Kind.STRIP, R.string.appearance_row_split_gap, R.drawable.ic_editor_margin),
        ICON_SIZE(Kind.STRIP, R.string.appearance_row_icon_size, R.drawable.ic_symbol_grid_view),
        LABEL_SIZE(Kind.SEGMENTS, R.string.appearance_row_label_size, R.drawable.ic_editor_text_size),
        GLASS_WIDGETS(Kind.SWITCH, R.string.appearance_row_glass_widgets, R.drawable.ic_symbol_stacks),
        TWO_LINE(Kind.SWITCH, R.string.appearance_row_two_line, R.drawable.ic_editor_text_size),
        LABEL_SHADOW(Kind.SWITCH, R.string.appearance_row_label_shadow, R.drawable.ic_editor_contrast),
        FOLDERS(Kind.SEGMENTS, R.string.appearance_row_folders, R.drawable.ic_symbol_folder),
        NUMERALS(Kind.SEGMENTS, R.string.appearance_row_numerals, R.drawable.ic_editor_text_size),
        PAGE_DOTS(Kind.SWITCH, R.string.appearance_row_page_dots, R.drawable.ic_editor_grain),
        WALLPAPER_SCROLL(Kind.SWITCH, R.string.appearance_row_wallpaper_scroll,
            R.drawable.ic_symbol_wallpaper),
        /** Home's card: Home takes the Workspace's layout and glass ({@link ScreenMatch}). */
        MATCH_WORKSPACE(Kind.ACTION, R.string.appearance_row_match_workspace,
            R.drawable.ic_ms_swap_horiz),
        /** The Workspace's card: the Workspace takes Home's layout and glass. */
        MATCH_HOME(Kind.ACTION, R.string.appearance_row_match_home, R.drawable.ic_ms_swap_horiz),
        /** A screen's card to the card both screens share (the palette's). */
        LINK_GLOBAL(Kind.LINK, R.string.appearance_row_global, R.drawable.ic_symbol_palette),
        CLOCK_12H(Kind.SWITCH, R.string.appearance_row_clock_12h, R.drawable.ic_editor_clock),
        CLOCK_FACE(Kind.MENU, R.string.appearance_row_clock_face, R.drawable.ic_editor_clock),
        KEYBOARD_THEME(Kind.LINK, R.string.appearance_row_keyboard_theme, R.drawable.ic_symbol_palette),
        KEY_OPACITY(Kind.STRIP, R.string.appearance_row_key_opacity, R.drawable.ic_editor_opacity),
        KEY_CORNERS(Kind.STRIP, R.string.appearance_row_key_corners, R.drawable.ic_editor_corners),
        KEY_SPACING(Kind.STRIP, R.string.appearance_row_key_spacing, R.drawable.ic_editor_margin),
        FORM(Kind.SEGMENTS, R.string.appearance_row_form, R.drawable.ic_symbol_keyboard_floating),
        LOOK(Kind.LOOK, R.string.appearance_row_look, R.drawable.ic_editor_contrast),
        WALLPAPER(Kind.WALLPAPER, R.string.appearance_row_wallpaper, R.drawable.ic_symbol_wallpaper),
        THEME(Kind.SEGMENTS, R.string.appearance_row_theme, R.drawable.ic_editor_dark_mode),
        ICON_PACK(Kind.EXPANDER, R.string.appearance_row_icon_pack, R.drawable.ic_symbol_icon_pack),
        MONOCHROME(Kind.SWITCH, R.string.appearance_row_monochrome, R.drawable.ic_editor_contrast),
        WALLPAPER_COLOURS(Kind.SWITCH, R.string.appearance_row_wallpaper_colours,
            R.drawable.ic_symbol_palette),
        FULL_SCREEN(Kind.SWITCH, R.string.appearance_row_full_screen, R.drawable.ic_editor_full_screen),
        MINIMAL(Kind.SWITCH, R.string.appearance_row_minimal, R.drawable.ic_corner_minimal_enter),
        VIBRANCY(Kind.STRIP, R.string.appearance_row_vibrancy, R.drawable.ic_symbol_palette),
        TINT_HUE(Kind.MENU, R.string.appearance_row_tint_hue, R.drawable.ic_editor_tint),
        GLASS_EDGE(Kind.STRIP, R.string.appearance_row_glass_edge, R.drawable.ic_editor_depth),
        TOOL_COLOURS(Kind.LINK, R.string.appearance_row_tool_colours, R.drawable.ic_symbol_terminal),
        LINK_GESTURES(Kind.LINK, R.string.appearance_row_gestures, R.drawable.ic_symbol_tune),
        LINK_PAGES(Kind.LINK, R.string.appearance_row_pages, R.drawable.ic_symbol_grid_view),
        LINK_INDICATORS(Kind.LINK, R.string.appearance_row_indicators, R.drawable.ic_symbol_weather),
        LINK_FONT(Kind.LINK, R.string.appearance_row_font, R.drawable.ic_editor_text_size),
        LINK_DOCK_ITEMS(Kind.LINK, R.string.appearance_row_dock_items, R.drawable.ic_symbol_apps),
        LINK_KEYBOARD_SIZE(Kind.LINK, R.string.appearance_row_keyboard_size,
            R.drawable.ic_editor_height),
        LINK_LAYOUTS(Kind.LINK, R.string.appearance_row_layouts, R.drawable.ic_symbol_keyboard);

        @NonNull public final Kind kind;
        @StringRes public final int labelRes;
        @DrawableRes public final int iconRes;

        Row(@NonNull Kind kind, @StringRes int labelRes, @DrawableRes int iconRes) {
            this.kind = kind;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }

        /**
         * The surface property the row tunes on a surface's card (and, on the Appearance card,
         * the shared value), or null for any other row. Frost answers BLUR, the property that
         * decides where it is held; it writes GRAIN beside it.
         */
        @Nullable
        public SurfaceProperty property() {
            switch (this) {
                case OPACITY: return SurfaceProperty.OPACITY;
                case TINT: return SurfaceProperty.TINT;
                case FROST: return SurfaceProperty.BLUR;
                default: return null;
            }
        }
    }

    /** One row of one card, in its tier (1, 2 or 3). */
    public static final class Spec {
        @NonNull public final Row row;
        public final int tier;

        Spec(@NonNull Row row, int tier) {
            this.row = row;
            this.tier = tier;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Spec && ((Spec) o).row == row && ((Spec) o).tier == tier;
        }

        @Override public int hashCode() {
            return row.hashCode() * 31 + tier;
        }

        @NonNull @Override public String toString() {
            return row + "@" + tier;
        }
    }

    /**
     * What the device and the build offer, which takes a few rows away: the glass edge (Depth and
     * the glass edge's colour, drawn only on Android 13 with a wallpaper set from the launcher),
     * and the terminal effect (only where its shader runs).
     */
    public static final class Offers {
        public final boolean glassEdge;
        public final boolean terminalEffect;

        public Offers(boolean glassEdge, boolean terminalEffect) {
            this.glassEdge = glassEdge;
            this.terminalEffect = terminalEffect;
        }
    }

    /** Every row of {@code card}, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull EditorCard card, @NonNull Offers offers) {
        List<Spec> out = new ArrayList<>();
        switch (card) {
            case HOME:
                // Home's glass first: the Home half's Base, which the widgets, the status bar
                // and the dock on Home wear while they follow it.
                add(out, 1, Row.FROST, Row.OPACITY, Row.TINT, Row.ICON_SIZE, Row.LABEL_SIZE,
                    Row.GLASS_WIDGETS);
                add(out, 2, Row.TWO_LINE, Row.LABEL_SHADOW, Row.FOLDERS, Row.NUMERALS,
                    Row.PAGE_DOTS);
                add(out, 3, Row.WALLPAPER_SCROLL, Row.MATCH_WORKSPACE, Row.LINK_GESTURES,
                    Row.LINK_PAGES);
                break;
            case WORKSPACE:
                // Home's twin (editor-redesign §2): the Workspace half's Look and Base, which the
                // bars on the Workspace wear while they follow it.
                add(out, 1, Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT);
                add(out, 3, Row.MATCH_HOME, Row.LINK_GLOBAL);
                break;
            case STATUS:
                add(out, 1, Row.FROST, Row.OPACITY, Row.TINT, Row.CORNERS, Row.MARGIN,
                    Row.VISIBLE);
                add(out, 2, Row.CLOCK_12H, Row.CLOCK_FACE);
                add(out, 3, Row.LINK_INDICATORS);
                break;
            case TERMINAL:
                add(out, 1, Row.FROST, Row.OPACITY, Row.TINT, Row.TEXT_SIZE, Row.CORNERS,
                    Row.MARGIN, Row.SPLIT_GAP);
                add(out, 2, Row.CONTRAST);
                add(out, 3, Row.CURSOR_TRAIL);
                if (offers.terminalEffect) add(out, 3, Row.EFFECT);
                add(out, 3, Row.LINK_FONT);
                break;
            case DOCK:
                add(out, 1, Row.FROST, Row.OPACITY, Row.TINT, Row.HEIGHT, Row.CORNERS,
                    Row.MARGIN, Row.APP_ICONS, Row.REFLECTION, Row.VISIBLE);
                add(out, 2, Row.STYLE, Row.DESIGN);
                add(out, 3, Row.SEARCH_FIELD, Row.DRAWER_ICON, Row.LINK_DOCK_ITEMS);
                break;
            case KEYBOARD:
                // Key opacity stands beside Opacity, the pair the opacity link moves together.
                add(out, 1, Row.FROST, Row.OPACITY, Row.KEY_OPACITY, Row.TINT, Row.KEY_CORNERS,
                    Row.KEY_SPACING, Row.HEIGHT, Row.CORNERS, Row.MARGIN, Row.VISIBLE);
                add(out, 2, Row.KEYBOARD_THEME);
                add(out, 3, Row.FORM, Row.LINK_KEYBOARD_SIZE, Row.LINK_LAYOUTS);
                break;
            case APPEARANCE:
            default:
                // All surfaces: the shared glass, the glass edge where it is drawn, and the
                // shared text size, in the order the Look page's Custom row had them.
                add(out, 1, Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT);
                if (offers.glassEdge) add(out, 1, Row.GLASS_EDGE);
                add(out, 1, Row.VIBRANCY);
                if (offers.glassEdge) add(out, 1, Row.DEPTH);
                add(out, 1, Row.TEXT_SIZE, Row.WALLPAPER, Row.THEME);
                add(out, 2, Row.ICON_PACK, Row.MONOCHROME, Row.WALLPAPER_COLOURS, Row.CONTRAST,
                    Row.FULL_SCREEN, Row.MINIMAL);
                add(out, 3, Row.TINT_HUE, Row.TOOL_COLOURS);
                break;
        }
        return Collections.unmodifiableList(out);
    }

    /** The rows of one tier of {@code card}. */
    @NonNull
    public static List<Row> tier(@NonNull EditorCard card, @NonNull Offers offers, int tier) {
        List<Row> out = new ArrayList<>();
        for (Spec spec : rows(card, offers)) {
            if (spec.tier == tier)
                out.add(spec.row);
        }
        return out;
    }

    private static void add(@NonNull List<Spec> out, int tier, @NonNull Row... rows) {
        for (Row row : rows)
            out.add(new Spec(row, tier));
    }

    /**
     * Whether {@code row} is one of the Appearance card's All surfaces columns, the strip that
     * stands only at the Custom stop: the shared glass, the glass edge and the shared text size.
     */
    public static boolean isAllSurfaces(@NonNull Row row) {
        return row.kind == Kind.STRIP;
    }

    /**
     * The half a row of {@code card} writes while the frame shows {@code frameHalf}, or null
     * where it writes one value for the wall (ADR 0013 §4): the per-half glass numbers of the
     * dock, the status bar and the keyboard, the Look slider and the shared glass the
     * Appearance card's Frost, Opacity and Tint write, follow the frame; Home's glass is the Home
     * half's Base whatever the frame shows, and the Workspace card's Look and glass the Workspace
     * half's; the terminal's numbers (it stands on the Workspace
     * alone), corners, margins, heights, places, the Dock's style and design, and every app-wide
     * choice do not.
     */
    @Nullable
    public static WallHalf halfFor(@NonNull EditorCard card, @NonNull Row row,
                                   @NonNull WallHalf frameHalf) {
        if (card == EditorCard.HOME)
            return row.property() != null ? WallHalf.HOME : null;
        if (card == EditorCard.WORKSPACE)
            return row == Row.LOOK || row.property() != null ? WallHalf.WORKSPACE : null;
        if (card == EditorCard.APPEARANCE)
            return row == Row.LOOK || row.property() != null ? frameHalf : null;
        SurfaceProperty property = row.property();
        if (card.slot == null || property == null)
            return null;
        return TermuxAppSharedPreferences.isPerHalf(card.slot, property) ? frameHalf : null;
    }

    /**
     * Whether a slider row carries the "set here" dot: the surface's own value for one of its
     * glass numbers, where Reset would take it back to the Look.
     */
    public static boolean marksOwnValue(@NonNull EditorCard card, @NonNull Row row) {
        return card.slot != null && row.property() != null
            && TermuxAppSharedPreferences.hasSurfaceProperty(card.slot, row.property());
    }
}
