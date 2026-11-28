package com.termux.app.leader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.Locale;

/**
 * A Custom leader as the user wrote it (ADR 0042): two or more keys, all of them modifiers
 * ({@code ctrl+shift}, held while the key is pressed) or modifiers and one normal key
 * ({@code ctrl+b}, a prefix pressed and let go before the key). {@link #parse} refuses what a
 * hardware keyboard can never deliver to the launcher (Android takes anything with Meta or Super,
 * Ctrl+Space, Alt+Tab and Alt+Shift first) and what would eat typing; it allows Ctrl+Shift with a
 * warning, since apps use those chords themselves.
 */
public final class LeaderChord {

    public final boolean ctrl;
    public final boolean alt;
    public final boolean shift;
    /** The normal key's stroke token ({@code b}, {@code space}), or null for modifiers alone. */
    @Nullable public final String key;

    private LeaderChord(boolean ctrl, boolean alt, boolean shift, @Nullable String key) {
        this.ctrl = ctrl;
        this.alt = alt;
        this.shift = shift;
        this.key = key;
    }

    /** Modifiers alone: held while the bound key is pressed. */
    public boolean isHeld() {
        return key == null;
    }

    /** One normal key among the modifiers: pressed and let go, then the bound key. */
    public boolean isPrefix() {
        return key != null;
    }

    /** The modifiers as a stroke prefix, in the resolver's order: {@code ctrl+shift+}. */
    @NonNull
    public String modifierPrefix() {
        StringBuilder prefix = new StringBuilder();
        if (ctrl) prefix.append("ctrl+");
        if (alt) prefix.append("alt+");
        if (shift) prefix.append("shift+");
        return prefix.toString();
    }

    /** The chord as stored and as the resolver spells it: {@code ctrl+shift}, {@code ctrl+b}. */
    @NonNull
    public String spec() {
        String prefix = modifierPrefix();
        return key != null ? prefix + key : prefix.substring(0, prefix.length() - 1);
    }

    @NonNull
    @Override
    public String toString() {
        return spec();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LeaderChord && ((LeaderChord) other).spec().equals(spec());
    }

    @Override
    public int hashCode() {
        return spec().hashCode();
    }

    /** What {@link #parse} made of a chord: the chord, or why not, and a warning to show. */
    public static final class Check {
        @Nullable public final LeaderChord chord;
        /** Why the chord cannot be the leader; null when it can. */
        @Nullable public final String refusal;
        /** Something to tell the user about a chord that is allowed; null for nothing. */
        @Nullable public final String warning;

        private Check(@Nullable LeaderChord chord, @Nullable String refusal,
                      @Nullable String warning) {
            this.chord = chord;
            this.refusal = refusal;
            this.warning = warning;
        }

        public boolean ok() {
            return chord != null;
        }

        static Check refused(@NonNull String reason) {
            return new Check(null, reason, null);
        }
    }

    static final String NEEDS_TWO = "A leader needs two or more keys, such as Ctrl+Shift or Ctrl+B.";
    static final String NEEDS_CTRL_OR_ALT = "A leader needs Ctrl or Alt, or it would get in the way of typing.";
    static final String SHIFTED_LEADER_WARNING = "With Shift in the leader, a shortcut that adds "
        + "Shift and the same shortcut without it are the same keys; the one without Shift is kept.";
    static final String ONE_KEY = "A leader can hold only one key besides Ctrl, Alt and Shift.";
    static final String META = "Android takes Meta and Super from hardware keyboards before Terl sees them.";
    static final String CTRL_SPACE = "Android uses Ctrl+Space to switch keyboard languages.";
    static final String ALT_TAB = "Android uses Alt+Tab to switch apps.";
    static final String ALT_SHIFT = "Android uses Alt+Shift to switch keyboard languages.";
    static final String CTRL_SHIFT_WARNING = "Apps use Ctrl+Shift chords too, such as a terminal's "
        + "copy and paste; the ones you bind will not reach them.";

    /** Reads a chord such as {@code Ctrl+Shift} or {@code ctrl+b}, and judges it. */
    @NonNull
    public static Check parse(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return Check.refused(NEEDS_TWO);
        boolean ctrl = false, alt = false, shift = false, meta = false;
        String key = null;
        for (String raw : text.trim().split("\\+")) {
            String part = raw.trim().toLowerCase(Locale.ROOT);
            if (part.isEmpty()) continue;
            switch (part) {
                case "ctrl": case "control": ctrl = true; break;
                case "alt": case "option": alt = true; break;
                case "shift": shift = true; break;
                case "meta": case "super": case "win": case "windows": case "cmd": case "command":
                    meta = true; break;
                default:
                    String token = keyToken(part);
                    if (token == null) return Check.refused("\"" + raw.trim() + "\" is not a key a leader can use.");
                    if (key != null && !key.equals(token)) return Check.refused(ONE_KEY);
                    key = token;
            }
        }
        if (meta) return Check.refused(META);
        // A key named twice (Ctrl+Control) is still one key.
        int keys = (ctrl ? 1 : 0) + (alt ? 1 : 0) + (shift ? 1 : 0) + (key != null ? 1 : 0);
        if (keys < 2) return Check.refused(NEEDS_TWO);
        // Without Ctrl or Alt the chord is Shift and a key: fine for one that types nothing (F2),
        // but Shift with a character is how that character is typed.
        if (!ctrl && !alt && (key == null || typesText(key)))
            return Check.refused(NEEDS_CTRL_OR_ALT);
        String taken = reservation(ctrl, alt, shift, key);
        if (taken != null) return Check.refused(taken);
        LeaderChord chord = new LeaderChord(ctrl, alt, shift, key);
        String warning = ctrl && shift && !alt ? CTRL_SHIFT_WARNING
            : shift && key == null ? SHIFTED_LEADER_WARNING : null;
        return new Check(chord, null, warning);
    }

    /**
     * Why Android takes this chord from a hardware keyboard before Terl sees it, or null when it
     * does not. The leader and every binding typed under it answer to the same list.
     */
    @Nullable
    public static String reservation(boolean ctrl, boolean alt, boolean shift, @Nullable String key) {
        // Android's app switch is Alt+Tab with Alt the only modifier but Shift: Ctrl+Alt+Tab is free.
        if (alt && !ctrl && "tab".equals(key)) return ALT_TAB;
        if (ctrl && "space".equals(key)) return CTRL_SPACE;
        if (alt && shift && !ctrl) return ALT_SHIFT;
        return null;
    }

    /** Whether Shift with {@code token} types a character rather than being a shortcut. */
    private static boolean typesText(@NonNull String token) {
        return token.length() == 1 || "space".equals(token) || "minus".equals(token)
            || "equals".equals(token) || "plus".equals(token);
    }

    /** {@code part} as the resolver's stroke token, or null when no binding could name it. */
    @Nullable
    private static String keyToken(@NonNull String part) {
        String token = part.length() == 1
            ? KeyTokens.tokenForChar(part.charAt(0)) : part;
        return KeyTokens.keyCodeForToken(token) != null ? token : null;
    }

    /**
     * {@link #reservation} for a chord written the way the resolver types one ({@code ctrl+alt+tab},
     * or a sequence, whose first stroke counts). The one place that parses a typed stroke for it.
     */
    @Nullable
    public static String reservationOfTyped(@NonNull String typed) {
        String first = typed.indexOf('>') < 0 ? typed : typed.substring(0, typed.indexOf('>'));
        boolean ctrl = false, alt = false, shift = false;
        String key = null;
        for (String part : first.split("\\+")) {
            switch (part) {
                case "ctrl": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                default: key = part;
            }
        }
        return reservation(ctrl, alt, shift, key);
    }
}
