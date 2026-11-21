package com.termux.app.notice;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * One queued notice: what the chip shows for a single message.
 *
 * <p>Immutable and view-free on purpose — notices are raised from services, preference data stores
 * and background callbacks long before anyone knows which activity (if any) will draw them.
 */
public final class AppNoticeItem {

    /** Severity, which picks the glyph and the accent the progress hairline is drawn in. */
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    /**
     * How long the pill keeps a notice, chosen by what the notice <em>is</em> rather than by how
     * severe it is.
     *
     * <p>The app used to have three separate notice surfaces with three unrelated sets of timings —
     * a terminal chip at 1400ms, a chord label at 950 and 2400, and this pill at 2600 and 3800 — so
     * how long a message stayed depended on which corner it had been written for. One surface needs
     * one scale, and the scale is the reading: a read-out is glanced at, a refusal is read.
     */
    public enum Hold {
        /** What a key or a tool just did. Gone by the time the eye is back on the shell. */
        READOUT(1000L),
        /** Something the user asked for, done: copied, saved. */
        CONFIRM(1600L),
        /** News the user did not ask for: a bell, a shell that exited, a setting that changed. */
        INFO(2600L),
        /** Why something did not happen. The one kind that has to survive being read twice. */
        REFUSAL(3800L),
        /** A write whose only way back is the tap on the pill. */
        UNDO(9000L),
        /** Until whatever it reports is resolved: a multi-stroke binding waiting for its next key. */
        STICKY(0L);

        /** The hold in ms, or 0 for {@link #STICKY}, which has none. */
        public final long ms;

        Hold(long ms) {
            this.ms = ms;
        }
    }

    @NonNull public final Kind kind;
    @NonNull public final CharSequence title;
    @Nullable public final CharSequence sub;
    /** Overrides the kind's default glyph when a caller has a better one for the action. */
    @Nullable public final String glyph;
    /**
     * The icon the mark draws, 0 for none. Outranks {@link #glyph}; with neither, the kind's
     * default icon draws (see {@link #resolvedIcon}).
     */
    @DrawableRes public final int icon;
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
        this(kind, title, sub, glyph, 0, durationMs, onActivate, attention, actionHint, fleeting);
    }

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph,
                         @DrawableRes int icon, long durationMs,
                         @Nullable Runnable onActivate, boolean attention,
                         @Nullable CharSequence actionHint, boolean fleeting) {
        this.kind = kind;
        this.title = title;
        this.sub = sub;
        this.glyph = glyph;
        this.icon = icon;
        this.durationMs = durationMs;
        this.onActivate = onActivate;
        this.attention = attention;
        this.actionHint = actionHint;
        this.fleeting = fleeting;
    }

    /**
     * True for a notice that holds until it is taken down rather than until a timer runs out. The
     * pending-chord report is the only one: it is the state of the keyboard, and a keyboard state
     * that expired on its own would be a lie.
     */
    public boolean isSticky() {
        return durationMs <= 0L;
    }

    /**
     * The icon the mark draws, or 0 when it draws text or nothing: the caller's icon, else (when
     * the caller gave text, or the shell is asking for attention) none, else the kind's own.
     * Plain INFO wears the generic info mark.
     */
    @DrawableRes
    public int resolvedIcon() {
        if (icon != 0) return icon;
        if (attention || (glyph != null && !glyph.isEmpty())) return 0;
        switch (kind) {
            case SUCCESS: return R.drawable.ic_rl_success;
            case WARNING: return R.drawable.ic_rl_warning;
            case ERROR: return R.drawable.ic_rl_error;
            case INFO:
            default: return R.drawable.ic_rl_info;
        }
    }

    /**
     * The text mark drawn when {@link #resolvedIcon} is 0: the caller's glyph, "!" for attention,
     * otherwise empty (no mark).
     */
    @NonNull
    public String resolvedGlyph() {
        if (icon != 0) return "";
        if (glyph != null && !glyph.isEmpty()) return glyph;
        return attention ? "!" : "";
    }

    /** Whether the pill draws a mark in front of the title. */
    public boolean hasMark() {
        return resolvedIcon() != 0 || !resolvedGlyph().isEmpty();
    }
}
