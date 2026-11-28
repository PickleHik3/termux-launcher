package com.termux.app.terminal;

import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.AppOpen;
import com.termux.app.launcher.model.LauncherAppEntry;

/**
 * What the palette asks of the Terl desktop when the control bar's search opens it
 * ({@link CommandPaletteContext#CONTROL_BAR}): how an app opens there, where the bar stands, the
 * keyboard it types on and the glass it wears. Every method defaults to doing nothing, so the
 * palette builds and tests without the activity; the activity installs a real one with
 * {@link TerminalCommandPaletteController#setTerlHost} while Terl is on.
 */
public interface CommandPaletteTerlHost {

    /** The host that does nothing. */
    CommandPaletteTerlHost NONE = new CommandPaletteTerlHost() { };

    /** Whether a window of {@code entry} is open on the desktop. */
    default boolean windowOpen(@NonNull AppOpen.Target target, @NonNull LauncherAppEntry entry) {
        return false;
    }

    /** Opens {@code entry} as the desktop opens an app ({@link AppOpen}): its open window, if any. */
    default void open(@NonNull LauncherAppEntry entry) { }

    /** Opens edit mode (#442). */
    default void openEditMode() { }

    /** Opens the drawer sheet (All apps). */
    default void openDrawer() { }

    /** Whether a terminal window is in front: the terminal's own actions are offered then. */
    default boolean terminalInFront() {
        return false;
    }

    /** The control bar's bounds on screen; false when it is not shown. */
    default boolean barOnScreen(@NonNull Rect out) {
        return false;
    }

    /**
     * Raises a keyboard to type into the palette when none is up.
     *
     * @return whether one was raised, which {@link #lowerKeyboard} then puts away with the palette
     */
    default boolean raiseKeyboard() {
        return false;
    }

    /** Puts away the keyboard {@link #raiseKeyboard} raised. */
    default void lowerKeyboard() { }

    /** The glass the palette wears under Terl, or null for v1's. */
    @Nullable
    default Material material() {
        return null;
    }

    /**
     * Global's look as a sheet wears it (ADR 0031 section 2): the tint's colour and alpha at the
     * sheets' floor, the blur of what is beneath it, and Global's Corners.
     */
    final class Material {
        @ColorInt public final int tintColor;
        public final int tintAlpha;
        public final int blurDp;
        public final float cornerRadiusPx;

        public Material(@ColorInt int tintColor, int tintAlpha, int blurDp, float cornerRadiusPx) {
            this.tintColor = tintColor;
            this.tintAlpha = tintAlpha;
            this.blurDp = blurDp;
            this.cornerRadiusPx = cornerRadiusPx;
        }
    }
}
