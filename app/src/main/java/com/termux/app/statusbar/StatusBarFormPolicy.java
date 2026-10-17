package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * The status bar's three forms and the rules that move it between them (phase two of the wall
 * rework, slice 1): the {@link Form#CAPSULE}, a thin capsule at the centre of the bar's edge
 * carrying only the current window's glyph (or Home's house), the clock and a dot count; the
 * {@link Form#COMPACT} row; and the {@link Form#EXPANDED} bar.
 *
 * <ul>
 *   <li><b>Release</b>: a slow status swipe lands on the form nearest to where the finger let go;
 *       a quick one ({@link #QUICK_SWIPE_DP_PER_S} or faster) steps one form from where it
 *       started, the way it was moving.</li>
 *   <li><b>Idle</b>: the bar folds to the capsule by itself {@link #IDLE_FOLD_MS} after the last
 *       touch on it, the last notification to arrive and the last window change; never while a
 *       finger is down on it, a card or sheet hangs off it, another surface holds it, or touch
 *       exploration is on (a hold restarts the quiet stretch once it ends).</li>
 *   <li><b>Minimal mode</b> starts the bar as the capsule; leaving it unfolds a capsule to
 *       compact.</li>
 *   <li><b>Content</b>: the house on Home, else the current window's glyph; the clock always;
 *       one dot per pinned notification or attention cue, up to {@link #MAX_DOTS}.</li>
 * </ul>
 *
 * <p>The capsule only stands on a bar along the top or the bottom: a column down a side stays
 * compact, as it already never expands. The code's other "capsule" — the Floating style's
 * rounded bar ({@code isRoundedDockStyle}) — is a look, not this form.
 *
 * <p>Pure: no views, so every rule is read and tested as arithmetic.
 */
public final class StatusBarFormPolicy {

    private StatusBarFormPolicy() {}

    /** The bar's forms, smallest first: a step is one ordinal. */
    public enum Form {
        CAPSULE, COMPACT, EXPANDED;

        /** The form one step bigger ({@code direction > 0}) or smaller, held to the ends. */
        @NonNull
        public Form step(int direction) {
            int index = ordinal() + Integer.signum(direction);
            Form[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, index))];
        }

        /** The two-form memory the rest of the launcher keeps: the capsule rests compact. */
        @NonNull
        public TopStatusBarState state() {
            return this == EXPANDED ? TopStatusBarState.EXPANDED : TopStatusBarState.COMPACT;
        }

        @NonNull
        public static Form of(@NonNull TopStatusBarState state) {
            return state == TopStatusBarState.EXPANDED ? EXPANDED : COMPACT;
        }
    }

    /** The capsule's height, capped by the band it stands in. */
    public static final float CAPSULE_HEIGHT_DP = 36f;
    /** The capsule's corner radius: half its height, so it reads as a pill. */
    public static final float CAPSULE_RADIUS_DP = 18f;
    /** Inner padding at the capsule's two ends and the gap between its parts. */
    public static final float CAPSULE_PADDING_DP = 12f;
    public static final float CAPSULE_GAP_DP = 8f;
    /** A dot of the count, and the air between two of them. */
    public static final float DOT_DP = 5f;
    public static final float DOT_GAP_DP = 3f;
    /** The most dots the capsule draws; the spoken label carries the real count. */
    public static final int MAX_DOTS = 3;
    /** A swipe at least this fast across the bar steps one form instead of sizing it. */
    public static final float QUICK_SWIPE_DP_PER_S = 600f;
    /** Quiet time on the bar before it folds to the capsule by itself. */
    public static final long IDLE_FOLD_MS = 4000L;
    /**
     * While something holds the bar the timer looks again this often, each look restarting the
     * quiet stretch, so the fold comes a whole {@link #IDLE_FOLD_MS} (less at most this) after
     * the hold ends.
     */
    public static final long HOLD_RECHECK_MS = 500L;

    /** Home's house and the generic window glyph (Nerd Font), the same marks the lens draws. */
    public static final String HOUSE_GLYPH = "";
    public static final String WINDOW_GLYPH = "";

    // ---------------------------------------------------------------- which forms a bar has

    /** Whether a bar standing on this edge can fold to the capsule: a row can, a column cannot. */
    public static boolean capsuleAllowed(@NonNull Edge edge) {
        return !StatusBarGesturePolicy.isVertical(edge);
    }

    /** The smallest form the bar may take. */
    @NonNull
    public static Form lowest(boolean capsuleAllowed) {
        return capsuleAllowed ? Form.CAPSULE : Form.COMPACT;
    }

    /** The biggest form the bar may take. */
    @NonNull
    public static Form highest(boolean expandedAllowed) {
        return expandedAllowed ? Form.EXPANDED : Form.COMPACT;
    }

    @NonNull
    public static Form clamp(@NonNull Form form, @NonNull Form lowest, @NonNull Form highest) {
        if (form.ordinal() < lowest.ordinal()) return lowest;
        if (form.ordinal() > highest.ordinal()) return highest;
        return form;
    }

    // ---------------------------------------------------------------- release and step

    /**
     * Where a status swipe lands. {@code nearest} is the form closest to where the finger let go
     * ({@link StatusBarFoldMotion#nearest}); {@code velocityDpPerS} is the finger's speed across
     * the bar at release, positive the way the bar opens. Slower than
     * {@link #QUICK_SWIPE_DP_PER_S}: the nearest form. As fast or faster: one step from
     * {@code start} the way the finger was moving. Either is held to the forms the bar has.
     */
    @NonNull
    public static Form release(@NonNull Form start, @NonNull Form nearest, float velocityDpPerS,
                               @NonNull Form lowest, @NonNull Form highest) {
        if (isQuick(velocityDpPerS)) return step(start, velocityDpPerS, lowest, highest);
        return clamp(nearest, lowest, highest);
    }

    /** Whether a release this fast is a quick swipe. */
    public static boolean isQuick(float velocityDpPerS) {
        return !Float.isNaN(velocityDpPerS) && Math.abs(velocityDpPerS) >= QUICK_SWIPE_DP_PER_S;
    }

    /** One form up ({@code direction > 0}) or down from {@code from}, held to the bar's forms. */
    @NonNull
    public static Form step(@NonNull Form from, float direction, @NonNull Form lowest,
                            @NonNull Form highest) {
        if (Float.isNaN(direction) || direction == 0f) return clamp(from, lowest, highest);
        return clamp(from.step(direction > 0f ? 1 : -1), lowest, highest);
    }

    // ---------------------------------------------------------------- idle

    /** What the idle timer does next. */
    public static final class Idle {
        /** Fold to the capsule now. */
        public final boolean fold;
        /**
         * When to ask again, in ms: the time left on the quiet stretch,
         * {@link #HOLD_RECHECK_MS} while something holds the bar, or {@code -1} for never
         * (nothing to fold, or touch exploration is on).
         */
        public final long delayMs;
        /**
         * Something holds the bar right now (finger, card, another surface): the quiet stretch
         * starts again from now, so a card that closes gets the whole idle time after it.
         */
        public final boolean held;

        Idle(boolean fold, long delayMs, boolean held) {
            this.fold = fold;
            this.delayMs = delayMs;
            this.held = held;
        }

        @NonNull @Override public String toString() {
            return "Idle{fold=" + fold + ", delay=" + delayMs + ", held=" + held + "}";
        }
    }

    /**
     * The idle rule. The bar folds to the capsule {@link #IDLE_FOLD_MS} after the latest of the
     * last touch on it, the last notification to arrive and the last window change. It never
     * folds a bar that is already the capsule or cannot be one, nor while touch exploration is on,
     * and holds (restarting the quiet stretch) while a finger is down on it, a card or sheet hangs
     * off it, or another surface holds it (the palette, the drawer, an editor).
     */
    @NonNull
    public static Idle idle(@NonNull Form form, boolean capsuleAllowed, long lastTouchMs,
                            long lastNotificationMs, long lastWindowChangeMs, long nowMs,
                            boolean fingerDown, boolean cardOpen, boolean blocked,
                            boolean touchExploration) {
        // Touch exploration reads the bar's chips one by one; a bar that folds them away under the
        // reader is a bar it cannot read. It waits for the next interaction to ask again.
        if (form == Form.CAPSULE || !capsuleAllowed || touchExploration) {
            return new Idle(false, -1L, false);
        }
        if (fingerDown || cardOpen || blocked) return new Idle(false, HOLD_RECHECK_MS, true);
        long last = Math.max(lastTouchMs, Math.max(lastNotificationMs, lastWindowChangeMs));
        long quiet = nowMs - last;
        if (quiet >= IDLE_FOLD_MS) return new Idle(true, -1L, false);
        return new Idle(false, IDLE_FOLD_MS - Math.max(0L, quiet), false);
    }

    /**
     * Whether a new pinned feed brings a notification that was not there before: a key the old
     * feed did not have, or one posted later than anything it had. A feed that only lost items or
     * kept them as they were is not an arrival, so a dismissal does not hold the bar open.
     */
    public static boolean notificationArrived(@NonNull java.util.Set<String> beforeKeys,
                                              long beforeLatestPostMs,
                                              @NonNull java.util.Set<String> afterKeys,
                                              long afterLatestPostMs) {
        if (afterLatestPostMs > beforeLatestPostMs) return true;
        for (String key : afterKeys) {
            if (!beforeKeys.contains(key)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Minimal mode

    /** The form the bar starts in: the capsule in Minimal mode, the remembered form otherwise. */
    @NonNull
    public static Form defaultForm(boolean minimal, @NonNull TopStatusBarState remembered,
                                   boolean capsuleAllowed) {
        if (minimal && capsuleAllowed) return Form.CAPSULE;
        return Form.of(remembered);
    }

    /**
     * The form after Minimal mode turns on or off: on, the capsule; off, a capsule unfolds to
     * compact and any other form stays.
     */
    @NonNull
    public static Form formOnMinimalChange(boolean minimal, @NonNull Form current,
                                           boolean capsuleAllowed) {
        if (minimal) return capsuleAllowed ? Form.CAPSULE : current;
        return current == Form.CAPSULE ? Form.COMPACT : current;
    }

    // ---------------------------------------------------------------- content

    /** What the capsule shows. */
    public static final class Content {
        /** The leading glyph: Home's house, or the current window's. */
        @NonNull public final String glyph;
        public final boolean home;
        /** Dots drawn: one per notification or cue, at most {@link #MAX_DOTS}. */
        public final int dots;
        /** The real count, for the spoken label. */
        public final int count;

        Content(@NonNull String glyph, boolean home, int dots, int count) {
            this.glyph = glyph;
            this.home = home;
            this.dots = dots;
            this.count = count;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Content)) return false;
            Content that = (Content) other;
            return home == that.home && dots == that.dots && count == that.count
                && glyph.equals(that.glyph);
        }

        @Override public int hashCode() {
            return ((glyph.hashCode() * 31 + (home ? 1 : 0)) * 31 + dots) * 31 + count;
        }

        @NonNull @Override public String toString() {
            return "Content{glyph=" + glyph + ", home=" + home + ", dots=" + dots
                + ", count=" + count + "}";
        }
    }

    /**
     * The capsule's content rule: on Home the house; elsewhere the current window's own glyph
     * (the label's leading Nerd Font glyph), or the generic window glyph for a label without one.
     * The clock always shows, so it is not part of the rule. The dots count the pinned
     * notifications and the attention cues together.
     */
    @NonNull
    public static Content content(boolean onHome, @Nullable String currentWindowLabel,
                                  int pinnedCount, int attentionCount) {
        int count = Math.max(0, pinnedCount) + Math.max(0, attentionCount);
        int dots = Math.min(MAX_DOTS, count);
        if (onHome) return new Content(HOUSE_GLYPH, true, dots, count);
        String glyph = currentWindowLabel == null ? null : leadingGlyph(currentWindowLabel);
        return new Content(glyph == null ? WINDOW_GLYPH : glyph, false, dots, count);
    }

    /**
     * The process glyph a window's label starts with (a private-use code point, alone or followed
     * by a space), or null. The same reading the window bar gives its chips.
     */
    @Nullable
    public static String leadingGlyph(@NonNull String label) {
        if (label.isEmpty()) return null;
        int codePoint = label.codePointAt(0);
        if (Character.getType(codePoint) != Character.PRIVATE_USE) return null;
        int end = Character.charCount(codePoint);
        if (end == label.length() || label.charAt(end) == ' ') return label.substring(0, end);
        return null;
    }

    /**
     * The capsule's width: its padding at both ends, the glyph, the clock and, with any, the
     * dots, each part after the first behind a gap; never wider than {@code maxPx}.
     */
    public static int capsuleWidthPx(float density, float glyphPx, float clockPx, int dots,
                                     int maxPx) {
        float padding = CAPSULE_PADDING_DP * density;
        float gap = CAPSULE_GAP_DP * density;
        float width = 2f * padding + glyphPx + gap + clockPx;
        if (dots > 0) width += gap + dotsWidthPx(density, dots);
        return Math.max(0, Math.min(maxPx, Math.round(width)));
    }

    /** The row of {@code dots} dots. */
    public static float dotsWidthPx(float density, int dots) {
        if (dots <= 0) return 0f;
        return dots * DOT_DP * density + (dots - 1) * DOT_GAP_DP * density;
    }

    /** The capsule's height in a band {@code bandPx} thick: 36 dp, or the band if thinner. */
    public static int capsuleHeightPx(float density, int bandPx) {
        return Math.max(0, Math.min(Math.round(CAPSULE_HEIGHT_DP * density), bandPx));
    }

    /** Its corner radius: 18 dp, or half a thinner capsule. */
    public static float capsuleRadiusPx(float density, int capsuleHeightPx) {
        return Math.min(CAPSULE_RADIUS_DP * density, capsuleHeightPx / 2f);
    }
}
