package com.termux.app.surfaces;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * The Appearance editor's six cards (appearance-one-look spec §3): Global (the palette's
 * {@link #APPEARANCE}), the two page cards Home and Terminal, and the Status bar, Dock and
 * Keyboard. One card per object; what each card holds is {@link EditorRows}.
 *
 * <p>{@link #key} is the name a door passes ({@link AppearanceEditorEntry}): the section names
 * Settings' deep links have always used ({@code dock}, {@code keyboard}, {@code status},
 * {@code canvas}) are read as the same objects.</p>
 */
public enum EditorCard {
    HOME("home", null, R.string.appearance_card_home, R.drawable.ic_symbol_home),
    STATUS("status", SurfaceSlot.STATUS, R.string.appearance_card_status,
        R.drawable.ic_editor_status_bar),
    /**
     * The Terminal's pages (Terminal, Display, Assistant): the terminal's own rows and the page's
     * (its Layout, Minimal layout). The old Workspace card merged into it (spec §3).
     */
    TERMINAL("terminal", SurfaceSlot.CANVAS, R.string.appearance_card_terminal,
        R.drawable.ic_symbol_terminal),
    DOCK("dock", SurfaceSlot.DOCK, R.string.appearance_card_dock,
        R.drawable.ic_symbol_dock_glass_tray),
    KEYBOARD("keyboard", SurfaceSlot.KEYBOARD, R.string.appearance_card_keyboard,
        R.drawable.ic_symbol_keyboard),
    APPEARANCE("appearance", null, R.string.appearance_card_customization,
        R.drawable.ic_symbol_palette);

    /** The name a door and a deep link use. */
    @NonNull public final String key;
    /** The surface the card tunes (the terminal's for Terminal), or null for Home and Global. */
    @Nullable public final SurfaceSlot slot;
    @StringRes public final int titleRes;
    @DrawableRes public final int iconRes;

    EditorCard(@NonNull String key, @Nullable SurfaceSlot slot, @StringRes int titleRes,
               @DrawableRes int iconRes) {
        this.key = key;
        this.slot = slot;
        this.titleRes = titleRes;
        this.iconRes = iconRes;
    }

    /**
     * Whether the card's header carries the eye that shows or hides its object (editor-redesign
     * §2): the status bar, the dock and the keyboard. The terminal, the two screens and the
     * palette's card cannot be hidden. The eye reads and writes {@link EditorRows.Row#VISIBLE}
     * through the card's model; it is not one of the card's rows.
     */
    public boolean hasEye() {
        return this == STATUS || this == DOCK || this == KEYBOARD;
    }

    /**
     * Whether the card's object stands on the {@code screen} kind of page ({@link
     * PageKind#HOME} is Home's, anything else the Terminal's): Home only on Home, the Terminal
     * only on the Terminal's, everything else on both.
     */
    public boolean standsOn(@NonNull PageKind screen) {
        if (this == HOME) return screen == PageKind.HOME;
        if (this == TERMINAL) return screen != PageKind.HOME;
        return true;
    }

    /** Whether the card is a page's own: Home's or the Terminal's. */
    public boolean isPageCard() {
        return this == HOME || this == TERMINAL;
    }

    /** The card of the screen kind: Home's on Home, the Terminal's on the Terminal's. */
    @NonNull
    public static EditorCard forScreen(@NonNull PageKind screen) {
        return screen == PageKind.HOME ? HOME : TERMINAL;
    }

    /** The card a surface opens. */
    @Nullable
    public static EditorCard forSlot(@Nullable SurfaceSlot slot) {
        if (slot == null)
            return null;
        for (EditorCard card : values()) {
            if (card.slot == slot)
                return card;
        }
        return null;
    }

    /**
     * The card a door or a deep link names, or null for none (the editor opens at rest). The old
     * section names are kept: {@code canvas}, {@code other}, {@code workspace} and
     * {@code display} are the Terminal's, {@code sessions} the status bar's, {@code widgets}
     * Home's (Glass widgets), and {@code sheets} Global's (menus wear Global).
     */
    @Nullable
    public static EditorCard forKey(@Nullable String key) {
        if (key == null)
            return null;
        String name = key.trim().toLowerCase(java.util.Locale.ROOT);
        switch (name) {
            case "canvas":
            case "other":
            case "workspace":
            case "display":
                return TERMINAL;
            case "sessions":
                return STATUS;
            case "widgets":
                return HOME;
            case "sheets":
                return APPEARANCE;
            default:
                break;
        }
        for (EditorCard card : values()) {
            if (card.key.equals(name))
                return card;
        }
        return null;
    }
}
