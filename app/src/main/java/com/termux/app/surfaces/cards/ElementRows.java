package com.termux.app.surfaces.cards;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.surfaces.EditorCard;
import com.termux.app.surfaces.EditorRows.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Elements list at the end of a screen's card (editor-redesign, developer changes 10-26
 * evening): which rows each screen lists, the card each row's sliders glyph opens, and what its
 * eye writes. Hidden elements come back from here; there is no tray of hidden pills.
 *
 * <p>The eye writes what the element's own card writes, so the two stay in step and Undo covers
 * both: the status bar and the dock's three bars their slot's shown flag ({@link
 * Visibility#ELEMENT}, the Bars list's), the keyboard the layout's keyboard switch (down and
 * off), and the dock's Search field, Drawer icon and Command palette tile their Options tiles'
 * keys. No views and no {@code Context}: held by JVM tests.</p>
 */
public final class ElementRows {

    private ElementRows() {}

    /** What a row's eye writes. */
    public enum Visibility {
        /** The element's slot shown or put away: {@code layout.setShown}, the Bars list's eye. */
        ELEMENT,
        /** The keyboard on or off: the Keyboard card's eye. */
        KEYBOARD,
        /** The dock's Search field tile ({@code search_field}). */
        SEARCH_FIELD,
        /** The dock's Drawer icon tile ({@code drawer_tile}). */
        DRAWER_TILE,
        /** The dock's Command palette tile ({@link DockCard#KEY_COMMAND_PALETTE_BUTTON}). */
        COMMAND_PALETTE_BUTTON,
        /** The dock's New float terminal tile ({@link DockCard#KEY_FLOAT_TERMINAL_BUTTON}). */
        FLOAT_TERMINAL_BUTTON,
        /** The page bar on Home: {@code app_launcher_home_page_indicator} "dots" or "off". */
        PAGE_BAR,
        /** No eye. */
        NONE
    }

    /** One row of the list. */
    public enum Item {
        STATUS_BAR(R.string.editor_elements_status_bar, R.drawable.ic_editor_status_bar,
            EditorCard.STATUS, Element.STATUS, null, Visibility.ELEMENT),
        PAGE_BAR(R.string.editor_elements_page_bar, R.drawable.ic_ms_more_horiz,
            EditorCard.HOME, null, null, Visibility.PAGE_BAR),
        APPS_ROW(R.string.editor_elements_apps_row, R.drawable.ic_symbol_apps,
            EditorCard.DOCK, Element.APPS, null, Visibility.ELEMENT),
        AZ(R.string.editor_elements_az, R.drawable.ic_ms_sort_by_alpha,
            EditorCard.DOCK, Element.AZ, null, Visibility.ELEMENT),
        EXTRA_KEYS(R.string.editor_elements_extra_keys, R.drawable.ic_ms_keyboard_command_key,
            EditorCard.DOCK, Element.EXTRA_KEYS, null, Visibility.ELEMENT),
        KEYBOARD(R.string.editor_elements_keyboard, R.drawable.ic_symbol_keyboard,
            EditorCard.KEYBOARD, null, null, Visibility.KEYBOARD),
        SEARCH_FIELD(R.string.editor_elements_search_field, R.drawable.ic_symbol_search,
            EditorCard.DOCK, null, Row.DOCK_OPTIONS, Visibility.SEARCH_FIELD),
        DRAWER_ICON(R.string.editor_elements_drawer_icon, R.drawable.ic_symbol_apps,
            EditorCard.DOCK, null, Row.DOCK_OPTIONS, Visibility.DRAWER_TILE),
        COMMAND_PALETTE(R.string.editor_elements_command_palette, R.drawable.ic_symbol_terminal,
            EditorCard.DOCK, null, Row.DOCK_OPTIONS, Visibility.COMMAND_PALETTE_BUTTON),
        FLOAT_TERMINAL(R.string.editor_elements_float_terminal, R.drawable.ic_status_bar_add_window,
            EditorCard.DOCK, null, Row.DOCK_OPTIONS, Visibility.FLOAT_TERMINAL_BUTTON);

        @StringRes public final int nameRes;
        @DrawableRes public final int glyphRes;
        /** The card the sliders glyph opens. */
        @NonNull public final EditorCard card;
        /**
         * The element the row stands for: the slot its eye shows or hides, and on the Dock card
         * the bar whose settings open expanded. Null for the rest.
         */
        @Nullable public final Element element;
        /** The row of {@link #card} the card opens scrolled to, or null for its top. */
        @Nullable public final Row section;
        @NonNull public final Visibility visibility;

        Item(@StringRes int nameRes, @DrawableRes int glyphRes, @NonNull EditorCard card,
             @Nullable Element element, @Nullable Row section, @NonNull Visibility visibility) {
            this.nameRes = nameRes;
            this.glyphRes = glyphRes;
            this.card = card;
            this.element = element;
            this.section = section;
            this.visibility = visibility;
        }

        /** Whether the row opens a card: the page bar's settings are the row's own switch. */
        public boolean opensCard() {
            return visibility != Visibility.PAGE_BAR;
        }

        /** Whether the row carries an eye. */
        public boolean hasEye() {
            return visibility != Visibility.NONE;
        }

        /** The Dock bar the Dock card opens expanded on, or null for none. */
        @Nullable
        public Element expandedBar() {
            return card == EditorCard.DOCK && element != Element.STATUS ? element : null;
        }

        /**
         * Whether the row is one of the dock's own pieces, which stand only where the page wears
         * the apps row: the Search field, the Drawer icon and the Command palette tile.
         */
        public boolean ridesTheDock() {
            return visibility == Visibility.SEARCH_FIELD || visibility == Visibility.DRAWER_TILE
                || visibility == Visibility.COMMAND_PALETTE_BUTTON
                || visibility == Visibility.FLOAT_TERMINAL_BUTTON;
        }
    }

    /** The rows both page cards list, top to bottom (Home adds the page bar). */
    private static final List<Item> ROWS = Collections.unmodifiableList(java.util.Arrays.asList(
        Item.STATUS_BAR, Item.APPS_ROW, Item.AZ, Item.EXTRA_KEYS, Item.KEYBOARD,
        Item.COMMAND_PALETTE, Item.FLOAT_TERMINAL));

    /** Home's rows: the page bar stands under the status bar, above the dock's bars it sits over. */
    private static final List<Item> HOME_ROWS;
    static {
        List<Item> rows = new ArrayList<>(ROWS);
        rows.add(rows.indexOf(Item.APPS_ROW), Item.PAGE_BAR);
        HOME_ROWS = Collections.unmodifiableList(rows);
    }

    /**
     * The rows {@code screen}'s card lists, top to bottom: the same list on Home and the
     * Terminal, but for Home's page bar, each written to its own page's layout. A row the page never wears is left out
     * by the editor's host ({@code offers}).
     */
    @NonNull
    public static List<Item> rows(@NonNull PageKind screen) {
        return screen == PageKind.HOME ? HOME_ROWS : ROWS;
    }

    /** The page card that holds the list: Home's on Home, the Terminal's on the Terminal's. */
    @Nullable
    public static PageKind screenOf(@NonNull EditorCard card) {
        if (card == EditorCard.HOME) return PageKind.HOME;
        if (card == EditorCard.TERMINAL) return PageKind.TERMINAL;
        return null;
    }
}
