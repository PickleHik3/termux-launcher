package com.termux.app.surfaces;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.surfaces.cards.DockCard;
import com.termux.app.surfaces.cards.GlobalCard;
import com.termux.app.surfaces.cards.HomeCard;
import com.termux.app.surfaces.cards.KeyboardCard;
import com.termux.app.surfaces.cards.StatusBarCard;
import com.termux.app.surfaces.cards.TerminalCard;
import com.termux.app.surfaces.cards.WorkspaceCard;
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
        /**
         * How strongly the icons' reflections show on the Peek glass card, 0 (off) to strong;
         * the Dock card shows it only while Peek glass is the design.
         */
        REFLECTION(Kind.STRIP, R.string.appearance_row_reflection,
            R.drawable.ic_symbol_dock_peek_glass),
        TEXT_SIZE(Kind.STRIP, R.string.appearance_row_text_size, R.drawable.ic_editor_text_size),
        CONTRAST(Kind.SEGMENTS, R.string.appearance_row_contrast, R.drawable.ic_editor_contrast),
        CURSOR_TRAIL(Kind.MENU, R.string.appearance_row_cursor_trail, R.drawable.ic_symbol_tune),
        EFFECT(Kind.MENU, R.string.appearance_row_effect, R.drawable.ic_symbol_tune),
        SPLIT_GAP(Kind.STRIP, R.string.appearance_row_split_gap, R.drawable.ic_editor_margin),
        LABEL_SIZE(Kind.SEGMENTS, R.string.appearance_row_label_size, R.drawable.ic_editor_text_size),
        GLASS_WIDGETS(Kind.SWITCH, R.string.appearance_row_glass_widgets, R.drawable.ic_symbol_stacks),
        TWO_LINE(Kind.SWITCH, R.string.appearance_row_two_line, R.drawable.ic_editor_text_size),
        LABEL_SHADOW(Kind.SWITCH, R.string.appearance_row_label_shadow, R.drawable.ic_editor_contrast),
        FOLDERS(Kind.SEGMENTS, R.string.appearance_row_folders, R.drawable.ic_symbol_folder),
        PAGE_TRANSITION(Kind.SEGMENTS, R.string.appearance_row_page_transition,
            R.drawable.ic_ed_transition_slide),
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
        KEYBOARD_THEME(Kind.LINK, R.string.appearance_row_keyboard_theme, R.drawable.ic_editor_dark_mode),
        KEY_OPACITY(Kind.STRIP, R.string.appearance_row_key_opacity, R.drawable.ic_editor_opacity),
        KEY_CORNERS(Kind.STRIP, R.string.appearance_row_key_corners, R.drawable.ic_editor_corners),
        KEY_SPACING(Kind.STRIP, R.string.appearance_row_key_spacing, R.drawable.ic_editor_margin),
        FORM(Kind.SEGMENTS, R.string.appearance_row_form, R.drawable.ic_symbol_keyboard_floating),
        LOOK(Kind.LOOK, R.string.appearance_row_look, R.drawable.ic_ed_look),
        THEME(Kind.SEGMENTS, R.string.appearance_row_theme, R.drawable.ic_editor_dark_mode),
        ICON_PACK(Kind.EXPANDER, R.string.appearance_row_icon_pack, R.drawable.ic_symbol_icon_pack),
        WALLPAPER_COLOURS(Kind.SWITCH, R.string.appearance_row_wallpaper_colours,
            R.drawable.ic_ed_wallpaper_colours),
        FULL_SCREEN(Kind.SWITCH, R.string.appearance_row_full_screen, R.drawable.ic_editor_full_screen),
        MINIMAL(Kind.SWITCH, R.string.appearance_row_minimal, R.drawable.ic_corner_minimal_enter),
        VIBRANCY(Kind.STRIP, R.string.appearance_row_vibrancy, R.drawable.ic_editor_tint),
        TINT_HUE(Kind.MENU, R.string.appearance_row_tint_hue, R.drawable.ic_editor_tint),
        GLASS_EDGE(Kind.STRIP, R.string.appearance_row_glass_edge, R.drawable.ic_editor_depth),
        TOOL_COLOURS(Kind.LINK, R.string.appearance_row_tool_colours, R.drawable.ic_symbol_terminal),
        LINK_GESTURES(Kind.LINK, R.string.appearance_row_gestures, R.drawable.ic_symbol_tune),
        LINK_PAGES(Kind.LINK, R.string.appearance_row_pages, R.drawable.ic_symbol_grid_view),
        LINK_FONT(Kind.LINK, R.string.appearance_row_font, R.drawable.ic_editor_text_size),
        LINK_LAYOUTS(Kind.LINK, R.string.appearance_row_layouts, R.drawable.ic_symbol_keyboard),
        // The Status bar and Terminal cards' own rows (editor-redesign §2): each is drawn by its
        // card from the row kinds (EditorCardSheet.Model#rowView); the Kind is the sheet's
        // stand-in where no card draws it.
        /** The status bar's edge, Top or Bottom, as glyph segments. */
        STATUS_POSITION(Kind.MENU, R.string.editor_st_position, R.drawable.ic_editor_position),
        /** The header over a card's glass and shape strip: "Glass", noted "Shape". */
        GLASS_SECTION(Kind.MENU, R.string.editor_st_section_glass, R.drawable.ic_editor_blur),
        /** The status clock's place in the bar, Left, Center or Right, as glyph segments. */
        CLOCK_POSITION(Kind.MENU, R.string.editor_st_clock_position, R.drawable.ic_editor_clock),
        /** The status bar's Options tiles: 12-hour, Seconds, °F weather, Camera gap. */
        STATUS_OPTIONS(Kind.SWITCH, R.string.editor_st_section_options, R.drawable.ic_ms_tune),
        /** The status bar's Indicators tiles: CPU, RAM, Weather, Notifications, Media. */
        STATUS_INDICATORS(Kind.SWITCH, R.string.editor_st_section_indicators,
            R.drawable.ic_symbol_weather),
        // The Dock card's own rows (editor-redesign §2 Dock), drawn by cards/DockCard through
        // EditorCardSheet.Model#rowView like the rows above; the Kind is again the stand-in.
        /** The dock's Style, Docked or Floating, as glyph segments. */
        DOCK_STYLE(Kind.MENU, R.string.editor_dock_row_style, R.drawable.ic_style_floating),
        /** The Bars list: the dock's bars in order, each shown or not, each opening its settings. */
        DOCK_BARS(Kind.MENU, R.string.editor_dock_row_bars, R.drawable.ic_drag_indicator_24),
        /** The Dock type tiles, one chosen at a time. */
        DOCK_TYPE(Kind.MENU, R.string.editor_dock_row_type, R.drawable.ic_symbol_dock_glass_tray),
        /** The dock's Options tiles. */
        DOCK_OPTIONS(Kind.SWITCH, R.string.editor_dock_row_options, R.drawable.ic_symbol_tune),
        // The Keyboard, Home, Global and Workspace cards' own rows (editor-redesign §2), drawn by
        // CardBody through EditorCardSheet.Model#rowView like the rows above; where the Kind is
        // not the row's own (a section's header, a stepper, the opacity link) it is the stand-in.
        /** The air under the keyboard's last key row (the layout's chin), in dp. */
        BOTTOM_PADDING(Kind.STRIP, R.string.editor_row_bottom_padding,
            R.drawable.ic_ed_bottom_padding),
        /** The keyboard's two opacities move together, keeping their difference. */
        OPACITY_LINK(Kind.SWITCH, R.string.editor_row_opacity_link, R.drawable.ic_ms_link),
        /** Home's widget grid, in cells, each a stepper (− value +). */
        HOME_COLUMNS(Kind.MENU, R.string.editor_row_columns, R.drawable.ic_ed_grid_columns),
        HOME_ROWS(Kind.MENU, R.string.editor_row_rows, R.drawable.ic_ed_grid_rows),
        /** Home's icon size: Small, Default or Large ({@code HomeIconSize}). */
        HOME_ICON_SIZE(Kind.SEGMENTS, R.string.editor_row_home_icon_size, R.drawable.ic_ms_apps),
        /**
         * A shortcut tile that never reads on: it opens the wallpaper picker (Change wallpaper).
         */
        CHANGE_WALLPAPER(Kind.SWITCH, R.string.editor_row_change_wallpaper,
            R.drawable.ic_ms_wallpaper),
        /** The wallpaper behind both screens, shown or not. */
        SHOW_WALLPAPER(Kind.SWITCH, R.string.editor_row_show_wallpaper,
            R.drawable.ic_ed_show_wallpaper),
        /** A section's header: its name, and the model's summary as the note at its end. */
        SECTION_GLASS(Kind.MENU, R.string.editor_section_glass, 0),
        SECTION_SHAPE(Kind.MENU, R.string.editor_section_shape, 0),
        SECTION_MORE(Kind.MENU, R.string.editor_section_more, 0),
        SECTION_GRID(Kind.MENU, R.string.editor_section_grid, 0),
        SECTION_LOOK(Kind.MENU, R.string.editor_section_look, 0),
        SECTION_LABELS_FOLDERS(Kind.MENU, R.string.editor_section_labels_folders, 0),
        SECTION_OPTIONS(Kind.MENU, R.string.editor_section_options, 0),
        SECTION_WALLPAPER(Kind.MENU, R.string.editor_section_wallpaper, 0),
        SECTION_COLOUR(Kind.MENU, R.string.editor_section_colour, 0),
        SECTION_GLASS_MATERIAL(Kind.MENU, R.string.editor_section_glass_material, 0),
        /** Small print under the glass material strips, where Edge and Depth show: the name. */
        HINT_GLASS_MATERIAL(Kind.MENU, R.string.editor_hint_glass_material, 0),
        /**
         * The Elements list at the end of a screen's card (cards/ElementsList): every element of
         * the screen, its eye and the sliders glyph that opens its card. Drawn by the editor
         * through EditorCardSheet.Model#rowView; the Kind is the stand-in.
         */
        ELEMENTS(Kind.MENU, R.string.editor_section_elements, R.drawable.ic_symbol_tune);

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
     * and the terminal effect (only where its shader runs). Use as takes the Match rows away
     * under Terminal only ({@code homeShown} false, {@link com.termux.app.wall.UseAs#shows}).
     */
    public static final class Offers {
        public final boolean glassEdge;
        public final boolean terminalEffect;
        /** Whether Home is a screen at all: false under Use as Terminal only. */
        public final boolean homeShown;

        public Offers(boolean glassEdge, boolean terminalEffect) {
            this(glassEdge, terminalEffect, true);
        }

        /** Whether the page overview is up: Home's Edit pages chip is not offered under it. */
        public final boolean overviewUp;

        public Offers(boolean glassEdge, boolean terminalEffect, boolean homeShown) {
            this(glassEdge, terminalEffect, homeShown, false);
        }

        public Offers(boolean glassEdge, boolean terminalEffect, boolean homeShown,
                      boolean overviewUp) {
            this.overviewUp = overviewUp;
            this.glassEdge = glassEdge;
            this.terminalEffect = terminalEffect;
            this.homeShown = homeShown;
        }
    }

    /**
     * Every row of {@code card}, tier by tier, in the order the card shows them. Each card's list
     * is its own file under {@code surfaces/cards} (editor-redesign §10, wave 2), so a wave that
     * reshapes one card edits one file.
     */
    @NonNull
    public static List<Spec> rows(@NonNull EditorCard card, @NonNull Offers offers) {
        switch (card) {
            case HOME: return HomeCard.rows(offers);
            case WORKSPACE: return WorkspaceCard.rows(offers);
            case STATUS: return StatusBarCard.rows(offers);
            case TERMINAL: return TerminalCard.rows(offers);
            case DOCK: return DockCard.rows(offers);
            case KEYBOARD: return KeyboardCard.rows(offers);
            case APPEARANCE:
            default:
                return GlobalCard.rows(offers);
        }
    }

    /**
     * How a card file lists its rows: tier by tier, in the order the card shows them. The list
     * it builds is unmodifiable.
     */
    public static final class CardRows {
        @NonNull private final List<Spec> mOut = new ArrayList<>();

        /** Appends {@code rows} to {@code tier} (1, 2 or 3). */
        @NonNull
        public CardRows tier(int tier, @NonNull Row... rows) {
            for (Row row : rows)
                mOut.add(new Spec(row, tier));
            return this;
        }

        @NonNull
        public List<Spec> build() {
            return Collections.unmodifiableList(new ArrayList<>(mOut));
        }
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
     * Appearance card's Frost, Opacity and Tint write, follow the frame; the Home card's Look and glass are
     * the Home half's whatever the frame shows, and the Workspace card's Look and glass the Workspace
     * half's; the terminal's numbers (it stands on the Workspace
     * alone), corners, margins, heights, places, the Dock's style and design, and every app-wide
     * choice do not.
     */
    @Nullable
    public static WallHalf halfFor(@NonNull EditorCard card, @NonNull Row row,
                                   @NonNull WallHalf frameHalf) {
        if (card == EditorCard.HOME)
            return row == Row.LOOK || row.property() != null ? WallHalf.HOME : null;
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
