package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How a pane's border colours are chosen: a role of the theme, the border's own colour, or a
 * custom ARGB. Pure, so every mode is a JVM test; the view fills {@link Roles} from the theme.
 */
public final class PaneBorderColours {

    private PaneBorderColours() {}

    public enum Mode {
        OUTLINE("outline"), PRIMARY("primary"), SECONDARY("secondary"), TERTIARY("tertiary"),
        CUSTOM("custom"), SAME_AS_BORDER("same_as_border");

        public final String id;

        Mode(String id) {
            this.id = id;
        }
    }

    /** A mode, plus the stored ARGB that only {@link Mode#CUSTOM} reads. */
    public static final class ColourChoice {
        public final Mode mode;
        public final int custom;

        public ColourChoice(@NonNull Mode mode, int custom) {
            this.mode = mode;
            this.custom = custom;
        }

        /** The choice stored as {@code id}; {@code fallback} for null or an id nobody knows. */
        @NonNull
        public static ColourChoice fromId(@Nullable String id, int custom, @NonNull Mode fallback) {
            for (Mode m : Mode.values()) {
                if (m.id.equals(id)) return new ColourChoice(m, custom);
            }
            return new ColourChoice(fallback, custom);
        }

        @Override public boolean equals(Object o) {
            return o instanceof ColourChoice && ((ColourChoice) o).mode == mode
                && ((ColourChoice) o).custom == custom;
        }

        @Override public int hashCode() {
            return mode.hashCode() * 31 + custom;
        }
    }

    /** The theme roles a choice can name, resolved by the caller. */
    public static final class Roles {
        public final int outline;
        public final int primary;
        public final int secondary;
        public final int tertiary;

        public Roles(int outline, int primary, int secondary, int tertiary) {
            this.outline = outline;
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
        }
    }

    /**
     * The ARGB {@code choice} stands for. {@link Mode#SAME_AS_BORDER} is {@code borderColour};
     * {@link Mode#CUSTOM} is the stored value untouched, alpha included.
     */
    public static int resolve(@NonNull ColourChoice choice, @NonNull Roles roles,
                              int borderColour) {
        switch (choice.mode) {
            case PRIMARY: return roles.primary;
            case SECONDARY: return roles.secondary;
            case TERTIARY: return roles.tertiary;
            case CUSTOM: return choice.custom;
            case SAME_AS_BORDER: return borderColour;
            default: return roles.outline;
        }
    }

    /**
     * The palette for these choices. The rim stays {@link PaneBorderStyle#NO_COLOUR} while the
     * border is the outline, so the shared rim draws exactly as it always did.
     */
    @NonNull
    public static PaneBorderStyle.Palette palette(@NonNull ColourChoice border,
                                                  @NonNull ColourChoice focus,
                                                  boolean glow, @NonNull ColourChoice glowColour,
                                                  @NonNull Roles roles, int attention) {
        int borderColour = resolve(border, roles, roles.outline);
        int rim = border.mode == Mode.OUTLINE ? PaneBorderStyle.NO_COLOUR : borderColour;
        return new PaneBorderStyle.Palette(resolve(focus, roles, borderColour), attention, rim,
            glow, resolve(glowColour, roles, borderColour));
    }
}
