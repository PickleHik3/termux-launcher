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

    public AppNoticeItem(@NonNull Kind kind, @NonNull CharSequence title,
                         @Nullable CharSequence sub, @Nullable String glyph, long durationMs) {
        this.kind = kind;
        this.title = title;
        this.sub = sub;
        this.glyph = glyph;
        this.durationMs = durationMs;
    }

    /** The glyph actually drawn: the caller's, or the kind's default. */
    @NonNull
    public String resolvedGlyph() {
        if (glyph != null && !glyph.isEmpty()) return glyph;
        switch (kind) {
            case SUCCESS: return "✓";
            case WARNING: return "⚠";
            case ERROR: return "✕";
            case INFO:
            default: return "›";
        }
    }
}
