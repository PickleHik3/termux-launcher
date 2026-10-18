package com.termux.app.terminal.inappkeyboard.touch;

/**
 * One released press, as the keyboard reports it. Positions and contact size are in key widths
 * on the grid's {@link juloo.keyboard2.TapGeometry}; times are milliseconds on one monotonic
 * clock.
 */
public final class Tap {

    /** The key whose rectangle held the finger-down point. */
    public final int rawIndex;
    /** The key that was sent; differs from {@link #rawIndex} only on a corrected tap. */
    public final int committedIndex;
    /** The finger-down point. */
    public final float x;
    public final float y;
    /** Released where it went down: no swipe, long press, key repeat or slide. */
    public final boolean plain;
    /** Ctrl or Alt was held. */
    public final boolean modified;
    public final long downAtMs;
    public final long upAtMs;
    /** Contact size at finger-down, in key widths; 0 when the screen does not report one. */
    public final float contactSize;

    public Tap(int rawIndex, int committedIndex, float x, float y, boolean plain,
               boolean modified, long downAtMs, long upAtMs, float contactSize) {
        this.rawIndex = rawIndex;
        this.committedIndex = committedIndex;
        this.x = x;
        this.y = y;
        this.plain = plain;
        this.modified = modified;
        this.downAtMs = downAtMs;
        this.upAtMs = upAtMs;
        this.contactSize = contactSize;
    }

    /** A plain tap with nothing held, released at once. */
    public static Tap plain(int index, float x, float y, long atMs) {
        return new Tap(index, index, x, y, true, false, atMs, atMs, 0f);
    }

    /** A plain tap the model moved from {@code rawIndex} to {@code committedIndex}. */
    public static Tap corrected(int rawIndex, int committedIndex, float x, float y, long atMs) {
        return new Tap(rawIndex, committedIndex, x, y, true, false, atMs, atMs, 0f);
    }
}
