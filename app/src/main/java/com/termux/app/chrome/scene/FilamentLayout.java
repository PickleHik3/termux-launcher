package com.termux.app.chrome.scene;

/**
 * The pure rules of the Filament scene's nixie tubes: how a window name maps to atlas glyphs and
 * is dealt across the drifting tubes, and the timing of the cathode "slot machine" cycle
 * that plays when focus moves to another window. No Android types, so it runs on the JVM.
 */
final class FilamentLayout {

    /** The glyphs of the atlas, in cell order: A-Z, 0-9 and a few symbols. */
    static final String GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.:/+*#@~>?";
    static final int GLYPH_COUNT = GLYPHS.length();
    static final int MIN_TUBES = 6;
    static final int MAX_TUBES = 10;
    /** A tube with nothing lit in it. */
    static final int UNLIT = -1;

    /** Where the cycle's first and last tube settle on the new name, ms after the focus change. */
    static final long SETTLE_FIRST_MS = 400L;
    static final long SETTLE_LAST_MS = 600L;
    /** A cycling tube shows a new glyph this often. */
    static final long CYCLE_STEP_MS = 90L;
    /** How long a cycle lasts in all: the slot a focus event holds. */
    static final long CYCLE_MS = SETTLE_LAST_MS + 50L;
    /** A focus change this soon after the last one does not restart the cycle. */
    static final long CYCLE_MIN_GAP_MS = 333L;

    private FilamentLayout() {}

    /** How many tubes a name of {@code length} characters gets: 6 to 10. */
    static int tubeCount(int length) {
        return Math.max(MIN_TUBES, Math.min(MAX_TUBES, length));
    }

    /** The atlas cell of a character (case-folded); {@link #UNLIT} for a space, '?' for anything unknown. */
    static int glyphIndex(char c) {
        if (c == ' ') return UNLIT;
        int i = GLYPHS.indexOf(Character.toUpperCase(c));
        return i >= 0 ? i : GLYPHS.indexOf('?');
    }

    /**
     * Writes the glyph of each tube for {@code name} into {@code out} (room for {@link #MAX_TUBES})
     * and returns the tube count. The name is trimmed, upper-cased and centred, padded with unlit
     * tubes (the extra one on the right); a name longer than ten is cut. A null or empty name
     * leaves six unlit tubes.
     */
    static int layout(String name, int[] out) {
        String s = clean(name);
        int len = Math.min(s.length(), MAX_TUBES);
        int n = tubeCount(len);
        int pad = (n - len) / 2;
        for (int i = 0; i < n; i++) {
            int k = i - pad;
            out[i] = k >= 0 && k < len ? glyphIndex(s.charAt(k)) : UNLIT;
        }
        return n;
    }

    /**
     * A chip's text as tubes spell it: the process glyph the chip starts with (a private-use
     * character of the icon font, or a pair of surrogates) is dropped, whitespace trimmed.
     */
    static String clean(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            int type = Character.getType(c);
            if (type == Character.PRIVATE_USE || type == Character.SURROGATE) continue;
            b.append(c);
        }
        return b.toString().trim();
    }

    /** The moment tube {@code i} of {@code n} stops cycling, ms after the focus change: left to right. */
    static long settleMs(int i, int n) {
        if (n <= 1) return SETTLE_LAST_MS;
        return SETTLE_FIRST_MS + (SETTLE_LAST_MS - SETTLE_FIRST_MS) * i / (n - 1);
    }

    /**
     * The glyph tube {@code i} of {@code n} shows {@code ageMs} after a focus change, given the
     * glyph it settles on. Unlit tubes stay unlit; a negative age (no cycle running) shows the
     * target. While cycling it is a deterministic scramble of (tube, step), different from the
     * step before so the cathodes visibly change.
     */
    static int shownGlyph(int i, int n, long ageMs, int target) {
        if (target == UNLIT || ageMs < 0L || ageMs >= settleMs(i, n)) return target;
        return scramble(i, ageMs / CYCLE_STEP_MS);
    }

    private static int scramble(int tube, long step) {
        int g = glyphAt(tube, step);
        if (step > 0 && g == glyphAt(tube, step - 1)) g = (g + 1) % GLYPH_COUNT;
        return g;
    }

    private static int glyphAt(int tube, long step) {
        long h = (step + 1) * 2654435761L + (tube + 1) * 40503L;
        h ^= h >>> 15;
        h *= 2246822519L;
        h ^= h >>> 13;
        return (int) Math.floorMod(h, (long) GLYPH_COUNT);
    }

    /** A gentle dip in tube brightness while cycling, 1 when at rest: smooth, never below 0.8. */
    static float cycleBrightness(long ageMs) {
        if (ageMs < 0L || ageMs >= CYCLE_MS) return 1f;
        double t = ageMs / (double) CYCLE_MS;
        return (float) (1.0 - 0.2 * Math.sin(Math.PI * t));
    }

    /**
     * Deals the lit glyphs of {@code target[0..n)} (a {@link #layout} result) to {@code slots}
     * nixies, written to {@code out}: in order, each letter once while there are enough nixies,
     * then repeating from the first; all {@link #UNLIT} when the name has no letters.
     */
    static void spread(int[] target, int n, int slots, int[] out) {
        int lit = 0;
        for (int i = 0; i < n; i++) if (target[i] != UNLIT) lit++;
        for (int s = 0; s < slots; s++) {
            if (lit == 0) {
                out[s] = UNLIT;
                continue;
            }
            int want = s % lit;
            for (int i = 0, seen = 0; i < n; i++) {
                if (target[i] == UNLIT) continue;
                if (seen++ == want) {
                    out[s] = target[i];
                    break;
                }
            }
        }
    }
}
