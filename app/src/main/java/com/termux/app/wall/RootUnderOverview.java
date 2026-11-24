package com.termux.app.wall;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * The launcher's root while the page overview stands over it: hidden (alpha 0) so only the
 * overview's cards show, shown again when the Home exit starts so the fade reveals the page and
 * not the ground, and at rest when the overview ends however it ends. Alpha only.
 */
final class RootUnderOverview {

    private float mRestAlpha = 1f;

    /** The overview opens: remember the root's alpha and hide it. */
    void hide(@NonNull View root) {
        mRestAlpha = root.getAlpha();
        root.setAlpha(0f);
    }

    /** The Home exit starts: the page the press lands on shows under the fading overview. */
    void reveal(@NonNull View root) {
        root.setAlpha(mRestAlpha);
    }

    /** The overview is gone: the root is at the alpha it had before. */
    void restore(@NonNull View root) {
        root.setAlpha(mRestAlpha);
    }
}
