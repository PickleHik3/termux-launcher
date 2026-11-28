package com.termux.app.overview;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The pages and chrome layers while Overview stands over them in the sheets layer: hidden (alpha
 * 0) so the page, its status row, its keyboard and the Home pill neither show through the sheet
 * material nor draw over it, and only the ground (the wallpaper and its dim) is under Overview.
 * Shown again when the Home exit starts, so the fade reveals the page, and at rest whenever
 * Overview goes. Alpha only, as Edit pages hides the shell ({@code RootUnderOverview}).
 */
final class LayersUnderOverview {

    @NonNull private final View[] mLayers = new View[2];
    @NonNull private final float[] mRestAlpha = new float[2];
    private boolean mHeld;

    /** Overview opens: remember each layer's alpha and hide it. A null layer is skipped. */
    void hide(@Nullable View pages, @Nullable View chrome) {
        hold(pages, chrome);
        for (View layer : mLayers) {
            if (layer != null) layer.setAlpha(0f);
        }
    }

    /**
     * Overview is part way in, swiped past the first page (#356): the pages show at their own
     * alpha, sliding beside it, and the chrome at {@code chromeShown} (0 to 1) of its own, going
     * as Overview covers it. Remembers each layer's alpha the first time. Per frame: alpha only.
     */
    void showUnder(@Nullable View pages, @Nullable View chrome, float chromeShown) {
        hold(pages, chrome);
        if (mLayers[0] != null) mLayers[0].setAlpha(mRestAlpha[0]);
        if (mLayers[1] != null) {
            float shown = Math.max(0f, Math.min(1f, chromeShown));
            mLayers[1].setAlpha(mRestAlpha[1] * shown);
        }
    }

    /** Remembers the layers and their alphas at rest, once until {@link #restore}. */
    private void hold(@Nullable View pages, @Nullable View chrome) {
        if (mHeld) return;
        mLayers[0] = pages;
        mLayers[1] = chrome;
        for (int i = 0; i < mLayers.length; i++) {
            View layer = mLayers[i];
            if (layer != null) mRestAlpha[i] = layer.getAlpha();
        }
        mHeld = true;
    }

    /** The layers go back to the alpha they had. Idempotent. */
    void restore() {
        if (!mHeld) return;
        mHeld = false;
        for (int i = 0; i < mLayers.length; i++) {
            View layer = mLayers[i];
            mLayers[i] = null;
            if (layer != null) layer.setAlpha(mRestAlpha[i]);
        }
    }
}
