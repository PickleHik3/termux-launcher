package com.termux.app.surfaces;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;

import java.util.Locale;

/**
 * Every door into the Appearance editor, and the card each one opens (editor-redesign §5, the
 * Door · Today · After table). The rule: a screen's door opens that screen's card, and Global (the
 * palette's card, {@link EditorCard#APPEARANCE}) is always one tap further.
 *
 * <p>Every door logs one line under {@link #LOG_TAG} naming itself and the card, so a stray open
 * on a phone names its door:</p>
 *
 * <pre>
 *   EditorCard card = EditorDoor.HOME_SHEET_APPEARANCE.route(null, null);
 *   // logs "home-sheet-appearance -> HOME"
 * </pre>
 *
 * <p>Doors that cross from Settings carry their name in the editor's intent
 * ({@link AppearanceEditorEntry#EXTRA_DOOR}), so the line is logged once, where the editor opens.
 * No views and no {@code Context}: the table is held by JVM tests.</p>
 */
public enum EditorDoor {
    /** Long-press Home, then the Home sheet's Appearance. */
    HOME_SHEET_APPEARANCE("home-sheet-appearance"),
    /** Long-press Home, then the Home sheet's Wallpaper. */
    HOME_SHEET_WALLPAPER("home-sheet-wallpaper"),
    /**
     * The terminal's action sheet, then Appearance: what a long-press on the terminal raises and
     * what the menu key (the terminal's More) raises, one sheet and one handler.
     */
    TERMINAL_SHEET_APPEARANCE("terminal-sheet-appearance"),
    /** The legacy terminal context-menu item for the editor, which no sheet lists today. */
    TERMINAL_CONTEXT_MENU("terminal-context-menu"),
    /** A pane's or window's controls, then Appearance: the page's own card. */
    PANE_CONTROLS("pane-controls-appearance"),
    /** {@code launcherctl appearance.surface_editor}: the page's own card. */
    LAUNCHERCTL("launcherctl-surface-editor"),
    /** {@code launcherctl appearance.set_wallpaper}: Global, whose Wallpaper row leads on. */
    LAUNCHERCTL_WALLPAPER("launcherctl-wallpaper"),
    /** In the editor: the palette pill. */
    EDITOR_PALETTE("editor-palette"),
    /**
     * In the editor: a tap on a surface's outline, or on its element in the other orientation's
     * canvas; the card is the surface's.
     */
    EDITOR_OUTLINE("editor-outline"),
    /** In the editor: a hidden bar's pill, brought back with its card; the card is the bar's. */
    EDITOR_HIDDEN_BAR("editor-hidden-bar"),
    /** In the editor: a tap on the page label under the frame, Home or Workspace. */
    EDITOR_PAGE_LABEL("editor-page-label"),
    /** In the editor: the Global row on a screen's card. */
    EDITOR_GLOBAL_CHIP("editor-global-chip"),
    /** Settings › Home › Edit on screen. */
    SETTINGS_HOME("settings-home"),
    /** Settings › Terminal › Edit on screen. */
    SETTINGS_TERMINAL("settings-terminal"),
    /** Settings › Keyboard › Edit on screen. */
    SETTINGS_KEYBOARD("settings-keyboard"),
    /** Settings › Look (the root's Appearance row, and the row at the top of its page). */
    SETTINGS_LOOK("settings-look"),
    /** A Settings search hit on a row the editor holds; the card is the row's. */
    SETTINGS_SEARCH("settings-search"),
    /**
     * The Layout-mode door ({@code EXTRA_LAYOUT_EDITOR}, through {@code openLayoutEditor}): the
     * live editor at rest on the place it names, until Layout mode is retired.
     */
    LAYOUT_EDITOR("layout-editor"),
    /** The editor's intent naming a card by its key (or none), from anywhere else. */
    SECTION_INTENT("section-intent");

    /** The tag every door's line is logged under. */
    public static final String LOG_TAG = "EditorDoor";

    /** The name the door's line and its intent extra carry. */
    @NonNull public final String doorName;

    EditorDoor(@NonNull String doorName) {
        this.doorName = doorName;
    }

    /**
     * The card this door opens, or null for the editor at rest.
     *
     * @param onScreen the page the door was used from (for {@link #EDITOR_PAGE_LABEL}, the page
     *     whose label was tapped); only the page-led doors read it
     * @param named the card the door names itself (an outline's surface, a search hit's row, an
     *     intent's section); only those doors read it
     */
    @Nullable
    public EditorCard cardFor(@Nullable PaneWallPage onScreen, @Nullable EditorCard named) {
        switch (this) {
            case HOME_SHEET_APPEARANCE:
            case SETTINGS_HOME:
                return EditorCard.HOME;
            case HOME_SHEET_WALLPAPER:
            case LAUNCHERCTL_WALLPAPER:
            case EDITOR_PALETTE:
            case EDITOR_GLOBAL_CHIP:
            case SETTINGS_LOOK:
                return EditorCard.APPEARANCE;
            case TERMINAL_SHEET_APPEARANCE:
                return EditorCard.WORKSPACE;
            case SETTINGS_TERMINAL:
                return EditorCard.TERMINAL;
            case SETTINGS_KEYBOARD:
                return EditorCard.KEYBOARD;
            case EDITOR_PAGE_LABEL:
                return onScreen == PaneWallPage.WIDGETS ? EditorCard.HOME : EditorCard.WORKSPACE;
            case TERMINAL_CONTEXT_MENU:
            case PANE_CONTROLS:
            case LAUNCHERCTL:
                return pageCard(onScreen);
            case LAYOUT_EDITOR:
                return null;
            case EDITOR_OUTLINE:
            case EDITOR_HIDDEN_BAR:
            case SETTINGS_SEARCH:
            case SECTION_INTENT:
            default:
                return named;
        }
    }

    /**
     * The page's own object, as the page-led doors have always opened it: Home's card on Home,
     * the terminal's over a terminal, the editor at rest over the display.
     */
    @Nullable
    static EditorCard pageCard(@Nullable PaneWallPage page) {
        if (page == PaneWallPage.WIDGETS) return EditorCard.HOME;
        if (page == PaneWallPage.TERMINAL) return EditorCard.TERMINAL;
        return null;
    }

    /** {@link #cardFor}, with the door's line logged. */
    @Nullable
    public EditorCard route(@Nullable PaneWallPage onScreen, @Nullable EditorCard named) {
        EditorCard card = cardFor(onScreen, named);
        log(card);
        return card;
    }

    /** Logs this door's line for {@code card}. */
    public void log(@Nullable EditorCard card) {
        Log.i(LOG_TAG, line(card));
    }

    /** The line a door logs: {@code "<door> -> <CARD>"}, {@code REST} for no card. */
    @NonNull
    public String line(@Nullable EditorCard card) {
        return doorName + " -> " + (card == null ? "REST" : card.name());
    }

    /** The door an intent extra names, or {@code fallback} for none or a name not known. */
    @NonNull
    public static EditorDoor forName(@Nullable String name, @NonNull EditorDoor fallback) {
        if (name == null)
            return fallback;
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (EditorDoor door : values()) {
            if (door.doorName.equals(wanted))
                return door;
        }
        return fallback;
    }
}
