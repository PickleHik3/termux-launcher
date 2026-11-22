package com.termux.app.launcher.drawer;

/**
 * The drawer's content appears only over the card's material. The plane's glass is a translucent
 * slab over a picture of what is beneath it ({@code PanelBackdrop}); a Snapshot picture is drawn on
 * the render thread and lands a few frames after the open starts, and until then the slab alone
 * shows the Terminal through it. The content's fade is multiplied by this gate, which stays shut
 * until the picture is up and then opens over {@link #FADE_S}.
 *
 * <p>A gate on drawing needs a fail-safe: it opens after {@link #BACKSTOP_S} whether or not the
 * picture has landed, so a picture that never arrives costs the look, never the drawer.
 */
final class AppDrawerContentGate {

    /** The longest the content waits for the material. */
    static final float BACKSTOP_S = 0.16f;
    /** How long the content takes to come in once the material is up. */
    static final float FADE_S = 0.10f;

    private AppDrawerContentGate() {}

    /**
     * The gate one frame later.
     *
     * @param gate       the gate now, 0 shut .. 1 open
     * @param materialUp the card's picture is up (or the card has no picture to wait for)
     * @param elapsedS   time since the open began waiting
     * @param dtS        this frame's delta
     */
    static float step(float gate, boolean materialUp, float elapsedS, float dtS) {
        if (gate >= 1f) return 1f;
        if (!materialUp && elapsedS < BACKSTOP_S) return gate;
        return Math.min(1f, gate + Math.max(0f, dtS) / FADE_S);
    }
}
