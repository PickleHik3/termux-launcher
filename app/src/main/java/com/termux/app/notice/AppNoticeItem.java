package com.termux.app.notice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One queued notice: what the chip shows for a single message.
 *
 * <p>Immutable and view-free on purpose — notices are raised from services, preference data stores
 * and background callbacks long before anyone knows which activity (if any) will draw them.
 */
public final class AppNoticeItem {

    /** Severity, which picks the glyph and the accent the progress hairline is drawn in. */
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    @NonNull public final Kind kind;
    @NonNull public final CharSequence title;
    @Nullable public final CharSequence sub;
    /** Overrides the kind's default glyph when a caller has a better one for the action. */
    @Nullable public final String glyph;
    public final long durationMs;
    /**
     * What tapping the chip does, when the notice is about somewhere the user can be taken —
     * the pane or window it came from. Null for a notice with nowhere to go, where a tap simply
     * dismisses.
     */
    @Nullable public final Runnable onActivate;
    /**
     * True when the shell this notice is about is waiting on the user — a bell, or a prompt in a
     * window they are not looking at. Drawn in its own accent so it is distinguishable at a glance
     * from the ordinary run of confirmations.
     */
    public final boolean attention;
    /**
     * What the tap does, in the caller's own words, for the chip's accessibility node — "tap to
     * undo" rather than the default "tap to open". Null when the generic wording is right, and
     * meaningless without {@link #onActivate}.
     */
    @Nullable public final CharSequence actionHint;
    /**
     * A read-out rather than a message: "what did that key just do". It never holds anything up —
     * whatever is raised over it takes its place at once, another read-out or a real notice — and
     * one raised while a real notice is up is dropped, since by the time the pill is free it would
     * be describing an action the user has forgotten.
     */
    public final boolean fleeting;

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph, long durationMs) {
        this(kind, title, sub, glyph, durationMs, null, false);
    }

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph, long durationMs,
                         @Nullable Runnable onActivate, boolean attention) {
        this(kind, title, sub, glyph, durationMs, onActivate, attention, null);
    }

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph, long durationMs,
                         @Nullable Runnable onActivate, boolean attention,
                         @Nullable CharSequence actionHint) {
        this(kind, title, sub, glyph, durationMs, onActivate, attention, actionHint, false);
    }

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph, long durationMs,
                         @Nullable Runnable onActivate, boolean attention,
                         @Nullable CharSequence actionHint, boolean fleeting) {
        this.kind = kind;
        this.title = title;
        this.sub = sub;
        this.glyph = glyph;
        this.durationMs = durationMs;
        this.onActivate = onActivate;
        this.attention = attention;
        this.actionHint = actionHint;
        this.fleeting = fleeting;
    }

    /** The glyph actually drawn: the caller's, or the kind's default. */
    @NonNull
    public String resolvedGlyph() {
        if (glyph != null && !glyph.isEmpty()) return glyph;
        if (attention) return "!";
        switch (kind) {
            case SUCCESS: return "✓";
            case WARNING: return "⚠";
            case ERROR: return "✕";
            case INFO:
            default: return "›";
        }
    }
}
