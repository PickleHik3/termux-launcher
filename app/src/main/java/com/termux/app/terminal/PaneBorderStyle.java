package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * Which border a pane wears, decided in one place so the views only render the answer.
 *
 * <ul>
 *   <li>{@link Kind#RIM}: the shared glass rim every other surface wears (status bar, dock,
 *       keyboard), so it follows the preset's hairline or gradient look. A lone pane, and every
 *       unfocused pane of a split.</li>
 *   <li>{@link Kind#FOCUS}: the Material active colour, on the focused pane of a split only.</li>
 *   <li>{@link Kind#ATTENTION}: the pane is asking for the user. Outranks focus; the attention
 *       state itself ({@link PaneAttention}) is cleared when the pane is focused on screen.</li>
 * </ul>
 *
 * <p>Pure, so every rule is a JVM test. Colours are handed in, resolved by the view from the
 * theme.
 */
public final class PaneBorderStyle {

    private PaneBorderStyle() {}

    public enum Kind { RIM, FOCUS, ATTENTION }

    /** The two theme colours a border can take; the rim's own colours belong to the shared rim. */
    public static final class Palette {
        public final int focus;
        public final int attention;
        /** The resolved border colour for the rim, or {@link #NO_COLOUR} for the rim's own. */
        public final int rim;
        /** Whether rim and focus borders carry a steady glow, and its colour. */
        public final boolean glow;
        public final int glowColour;

        public Palette(int focus, int attention) {
            this(focus, attention, NO_COLOUR, false, NO_COLOUR);
        }

        public Palette(int focus, int attention, int rim, boolean glow, int glowColour) {
            this.focus = focus;
            this.attention = attention;
            this.rim = rim;
            this.glow = glow;
            this.glowColour = glowColour;
        }
    }

    /** What to draw: the kind, and its colour, or {@link #NO_COLOUR} for the shared rim. */
    public static final class Decision {
        public final Kind kind;
        public final int colour;
        /** A steady glow over a RIM or FOCUS border; never set for ATTENTION. */
        public final boolean glow;
        public final int glowColour;

        Decision(@NonNull Kind kind, int colour) {
            this(kind, colour, false, NO_COLOUR);
        }

        Decision(@NonNull Kind kind, int colour, boolean glow, int glowColour) {
            this.kind = kind;
            this.colour = colour;
            this.glow = glow;
            this.glowColour = glow ? glowColour : NO_COLOUR;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Decision && ((Decision) o).kind == kind
                && ((Decision) o).colour == colour && ((Decision) o).glow == glow
                && ((Decision) o).glowColour == glowColour;
        }

        @Override public int hashCode() {
            return ((kind.hashCode() * 31 + colour) * 31 + (glow ? 1 : 0)) * 31 + glowColour;
        }
    }

    /** The shared rim carries its own colours (preset dependent), so the decision has none. */
    public static final int NO_COLOUR = 0;

    /** The border for a pane of a window holding {@code paneCount} panes. */
    @NonNull
    public static Decision decide(int paneCount, boolean focused, boolean attention,
                                  @NonNull Palette palette) {
        if (attention) return new Decision(Kind.ATTENTION, palette.attention);
        if (paneCount > 1 && focused) {
            return new Decision(Kind.FOCUS, palette.focus, palette.glow, palette.glowColour);
        }
        return new Decision(Kind.RIM, palette.rim, palette.glow, palette.glowColour);
    }

    /** The decision for a page that is alone by construction (Widgets, Display). */
    @NonNull
    public static Decision lone() {
        return new Decision(Kind.RIM, NO_COLOUR);
    }
}
