package com.termux.app.chrome;

import androidx.annotation.Nullable;

/**
 * Whether a surface may keep the {@link SharedFrameDrawable} it was last built with instead of
 * making a new one. A new one compiles its own refraction program (a RuntimeShader, about 10 ms
 * of the main thread on pong), so a surface re-glazed on every chrome apply keeps one: the same
 * view, sampled through the same wallpaper parallax, is the same drawable whatever frame it is
 * pointed at next ({@link SharedFrameDrawable#setFrame} and the look setters are idempotent).
 * Anything else, or nothing held, builds afresh. Pure.
 */
public final class SharedFrameReuse {

    private SharedFrameReuse() {}

    /**
     * @param held       the drawable kept from the last build; null for none
     * @param heldHost   the view it was anchored to
     * @param host       the view this build anchors to
     * @param heldParallax the parallax it samples through
     * @param parallax   the parallax this build samples through
     */
    public static boolean reuses(@Nullable Object held, @Nullable Object heldHost,
                                 @Nullable Object host, @Nullable Object heldParallax,
                                 @Nullable Object parallax) {
        return held != null && host != null && heldHost == host && heldParallax == parallax;
    }
}
