package com.termux.app.launcher.drawer;

/**
 * The close of a drawer whose search raised the built-in keyboard, which goes down with the
 * drawer. The open laid the accessory stack out for that keyboard, so the dock rests above it;
 * the stack is laid out again with the keyboard down only once the close has finished. Until then
 * this policy owns where the dock, the extra keys and the keyboard are drawn, as translations from
 * where that layout put them, so the close lands on the keyboard-down rest with nothing left to
 * jump when the layout catches up.
 *
 * <p>One number, {@link #loweredFraction}, runs from 0 at {@code p = 1} to 1 at the glass
 * hand-off's end and stays there: the open's reveal brings the keyboard up after the plane has
 * grown, so the close takes it down before the plane has shrunk, and the dock has reached its rest
 * by the time it fades back in. Every value below is that fraction of its whole distance.
 */
final class AppDrawerKeyboardLowering {

    private AppDrawerKeyboardLowering() {}

    /** How much of the lowering is done at progress {@code p}: 0 open, 1 from the hand-off down. */
    static float loweredFraction(float progress) {
        return 1f - AppDrawerTransitionGeometry.ramp(progress, AppDrawerController.GLASS_FADE_END,
            1f);
    }

    /**
     * The translation that moves something laid out at {@code laidOutTopPx} toward where it rests
     * once the keyboard is down, {@code restTopPx}: the dock, the extra keys and the plane's seed.
     */
    static float offsetPx(float laidOutTopPx, float restTopPx, float progress) {
        return (restTopPx - laidOutTopPx) * loweredFraction(progress);
    }

    /** The keyboard's own way out: down past the host's bottom edge. */
    static float keyboardExitPx(float keyboardTopPx, float hostBottomPx, float progress) {
        return Math.max(0f, hostBottomPx - keyboardTopPx) * loweredFraction(progress);
    }

    /** The search reveal, capped so it is gone by the time the keyboard is. */
    static float reveal(float reveal, float progress) {
        return Math.min(AppDrawerTransitionGeometry.clamp01(reveal),
            1f - loweredFraction(progress));
    }

    /** The keyboard's alpha, faded out with its way out. */
    static float keyboardAlpha(float alpha, float progress) {
        return alpha * (1f - loweredFraction(progress));
    }
}
