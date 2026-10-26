package com.termux.app.surfaces;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

/**
 * The objects the Appearance editor has a card for (appearance-and-settings-rework §2–§3): the
 * five surfaces a tap in the frame lands on, the Workspace screen's own card (editor-redesign §1,
 * Home's twin), and the Appearance card the editor bar's palette opens. One card per object; what
 * each card holds is {@link EditorRows}.
 *
 * <p>{@link #key} is the name a door passes ({@link AppearanceEditorEntry}): the section names
 * Settings' deep links have always used ({@code dock}, {@code keyboard}, {@code status},
 * {@code canvas}) are read as the same objects.</p>
 */
public enum EditorCard {
    HOME("home", null, R.string.appearance_card_home, R.drawable.ic_symbol_home),
    /**
     * The Workspace screen: its Look and its glass (the Workspace half's Base, which the bars on
     * the Workspace wear while they follow it), and Match Home styling.
     */
    WORKSPACE("workspace", null, R.string.appearance_card_workspace,
        R.drawable.ic_symbol_desktop_windows),
    STATUS("status", SurfaceSlot.STATUS, R.string.appearance_card_status,
        R.drawable.ic_editor_status_bar),
    TERMINAL("terminal", SurfaceSlot.CANVAS, R.string.appearance_card_terminal,
        R.drawable.ic_symbol_terminal),
    DOCK("dock", SurfaceSlot.DOCK, R.string.appearance_card_dock,
        R.drawable.ic_symbol_dock_glass_tray),
    KEYBOARD("keyboard", SurfaceSlot.KEYBOARD, R.string.appearance_card_keyboard,
        R.drawable.ic_symbol_keyboard),
    APPEARANCE("appearance", null, R.string.appearance_row_global,
        R.drawable.ic_symbol_palette);

    /** The name a door and a deep link use. */
    @NonNull public final String key;
    /** The surface the card tunes, or null for the two screens' cards and the Appearance card. */
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
     * Whether the card's values are held per half: the dock, the status bar and the keyboard (their glass), and the Appearance card (the half's
     * Look). The terminal stands only on the Workspace and Home only on Home; the two screens'
     * cards meet through their Match rows instead.
     */
    public boolean perHalf() {
        return this == STATUS || this == DOCK || this == KEYBOARD || this == APPEARANCE;
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
     * Whether the card's object stands on {@code half}: Home only on Home, the Workspace and the
     * terminal only on the Workspace, everything else on both.
     */
    public boolean standsOn(@NonNull WallHalf half) {
        if (this == HOME) return half == WallHalf.HOME;
        if (this == TERMINAL || this == WORKSPACE) return half == WallHalf.WORKSPACE;
        return true;
    }

    /** The card of the screen {@code half} is: Home's on Home, the Workspace's on the Workspace. */
    @NonNull
    public static EditorCard forScreen(@NonNull WallHalf half) {
        return half == WallHalf.HOME ? HOME : WORKSPACE;
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
     * section names are kept: {@code canvas} and {@code other} were the terminal's, and
     * {@code sessions} the status bar's.
     */
    @Nullable
    public static EditorCard forKey(@Nullable String key) {
        if (key == null)
            return null;
        String name = key.trim().toLowerCase(java.util.Locale.ROOT);
        switch (name) {
            case "canvas":
            case "other":
                return TERMINAL;
            case "sessions":
                return STATUS;
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
