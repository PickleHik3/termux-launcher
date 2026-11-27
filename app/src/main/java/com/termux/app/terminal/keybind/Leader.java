package com.termux.app.terminal.keybind;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What starts every desktop binding (ADR 0042): the Terl key (the default), Ctrl+Alt, or a Custom
 * {@link LeaderChord}. Bindings are written against the leader, so changing it keeps every one.
 *
 * <p>Bindings are stored as they always were, with Ctrl+Alt standing for the leader: a stroke that
 * starts with {@code ctrl+alt+} is leader-relative, so a v1 bindings file keeps its meaning and
 * reads the same. {@link #physicalSequence} spells such a stroke as the active leader is typed on
 * a hardware keyboard. The Terl key and Ctrl+Alt spell it the same way: a hardware keyboard has no
 * Terl key, so Ctrl+Alt plays its part there; the Terl key itself never reaches the resolver as a
 * stroke, it asks for the leader-relative binding directly.
 */
public final class Leader {

    public enum Kind { TERL_KEY, CTRL_ALT, CUSTOM }

    /** The stored names of the three choices, as {@code keys.leader} holds them. */
    public static final String TERL_KEY_NAME = "terl";
    public static final String CTRL_ALT_NAME = "ctrl+alt";
    public static final String CUSTOM_NAME = "custom";

    /** How leader-relative bindings are stored: Ctrl+Alt stands for the leader. */
    public static final String CANONICAL_PREFIX = "ctrl+alt+";

    public static final Leader TERL_KEY = new Leader(Kind.TERL_KEY, null);
    public static final Leader CTRL_ALT = new Leader(Kind.CTRL_ALT, null);

    @NonNull public final Kind kind;
    /** The Custom chord; null for the other two. */
    @Nullable public final LeaderChord chord;

    private Leader(@NonNull Kind kind, @Nullable LeaderChord chord) {
        this.kind = kind;
        this.chord = chord;
    }

    /** A Custom leader of {@code chord}. */
    @NonNull
    public static Leader custom(@NonNull LeaderChord chord) {
        return new Leader(Kind.CUSTOM, chord);
    }

    /**
     * The leader the two settings name. Custom with no usable chord, or a name it does not know,
     * is the Terl key: the leader must always be something that works.
     */
    @NonNull
    public static Leader of(@Nullable String choice, @Nullable String customChord) {
        if (CTRL_ALT_NAME.equals(choice)) return CTRL_ALT;
        if (CUSTOM_NAME.equals(choice)) {
            LeaderChord.Check check = LeaderChord.parse(customChord);
            if (check.chord != null) return custom(check.chord);
        }
        return TERL_KEY;
    }

    /** Whether the leader is a prefix (pressed and let go, then the key). */
    public boolean isPrefix() {
        return chord != null && chord.isPrefix();
    }

    /**
     * {@code canonical} as it is typed under this leader: a sequence whose first stroke starts with
     * {@link #CANONICAL_PREFIX} gets that part replaced by the leader ({@code ctrl+alt+shift+left}
     * is {@code ctrl+shift+left} under Ctrl+Shift and {@code ctrl+b>shift+left} under Ctrl+B); any
     * other sequence is returned unchanged.
     */
    @NonNull
    public String physicalSequence(@NonNull String canonical) {
        if (chord == null || !canonical.startsWith(CANONICAL_PREFIX)) return canonical;
        String rest = canonical.substring(CANONICAL_PREFIX.length());
        if (chord.isPrefix()) return chord.spec() + ">" + rest;
        int end = rest.indexOf('>');
        String tail = end < 0 ? "" : rest.substring(end);
        String stroke = end < 0 ? rest : rest.substring(0, end);
        boolean shift = chord.shift;
        if (stroke.startsWith("shift+")) {
            shift = true;
            stroke = stroke.substring("shift+".length());
        }
        StringBuilder physical = new StringBuilder();
        if (chord.ctrl) physical.append("ctrl+");
        if (chord.alt) physical.append("alt+");
        if (shift) physical.append("shift+");
        return physical.append(stroke).append(tail).toString();
    }

    /**
     * The stroke prefix the hint card documents while a keyboard holds {@code ctrl}, {@code alt}
     * and {@code shift}, or null when that is not this leader held down. Shift beyond the leader's
     * own is reported by {@link Held#shift}, as the card's Shift layer.
     */
    @Nullable
    public Held held(boolean ctrl, boolean alt, boolean shift) {
        if (chord == null) {
            return ctrl && alt ? new Held(CANONICAL_PREFIX, shift) : null;
        }
        if (chord.isPrefix()) return null;
        if (ctrl != chord.ctrl || alt != chord.alt) return null;
        if (chord.shift && !shift) return null;
        return new Held(chord.modifierPrefix(), shift && !chord.shift);
    }

    /** A held leader: the prefix its bindings start with and whether Shift is added. */
    public static final class Held {
        @NonNull public final String prefix;
        public final boolean shift;

        Held(@NonNull String prefix, boolean shift) {
            this.prefix = prefix;
            this.shift = shift;
        }
    }

    /** The stored name of this leader's choice. */
    @NonNull
    public String choiceName() {
        switch (kind) {
            case CTRL_ALT: return CTRL_ALT_NAME;
            case CUSTOM: return CUSTOM_NAME;
            default: return TERL_KEY_NAME;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Leader)) return false;
        Leader that = (Leader) other;
        return kind == that.kind
            && (chord == null ? that.chord == null : chord.equals(that.chord));
    }

    @Override
    public int hashCode() {
        return kind.hashCode() * 31 + (chord == null ? 0 : chord.hashCode());
    }

    @NonNull
    @Override
    public String toString() {
        return chord == null ? choiceName() : CUSTOM_NAME + ":" + chord.spec();
    }
}
