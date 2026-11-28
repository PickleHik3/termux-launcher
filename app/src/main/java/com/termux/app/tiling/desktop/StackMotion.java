package com.termux.app.tiling.desktop;

/**
 * The one motion for every move through the stack (#434): a place up or down, the strip sideways,
 * Home to a Workspace and back, Overview's tap-to-go, the Workspace list's go. They all end in
 * {@link TilingDesktopView}, which reads these constants and nothing else. With Android's Remove
 * animations (animator scale 0) the move is a short fade in place instead: not a jump, not a slide.
 */
public final class StackMotion {

    private StackMotion() {}

    /** What a move looks like. */
    public enum Kind {
        /** The places and the strip slide along the stack. */
        SLIDE,
        /** The move lands at once and the stack fades in over {@link #FADE_MS}. */
        FADE
    }

    /** How long a slide takes, in ms. */
    public static final long MOVE_MS = 260L;
    /** How long the fade that replaces a slide takes, in ms. */
    public static final long FADE_MS = 120L;
    /** The alpha a fade starts from. */
    public static final float FADE_FROM = 0f;
    /** The slide's easing: a cubic Bezier through these control points (x1, y1, x2, y2). */
    public static final float[] EASE = {0.4f, 0f, 0.2f, 1f};

    /** The motion a move has: a fade when Android's animations are off, else the slide. */
    public static Kind kindFor(boolean reducedMotion) {
        return reducedMotion ? Kind.FADE : Kind.SLIDE;
    }

    /**
     * The motion of one move from {@code fromPosition} to {@code toLevel} when moves look like
     * {@code kind}: a jump across several places (more than one level away) is a short fade to the
     * destination, so the places in between are never shown and stay cold (live window residency,
     * the developer's round 2). A move to a neighbour keeps {@code kind}.
     */
    public static Kind kindForMove(Kind kind, float fromPosition, int toLevel) {
        if (kind == Kind.FADE) return Kind.FADE;
        return Math.abs(toLevel - fromPosition) > 1f ? Kind.FADE : kind;
    }

    /** How long a move of {@code kind} runs, in ms. */
    public static long durationMs(Kind kind) {
        return kind == Kind.FADE ? FADE_MS : MOVE_MS;
    }
}
