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
    private boolean mHidden;

    /** Overview opens: remember each layer's alpha and hide it. A null layer is skipped. */
    void hide(@Nullable View pages, @Nullable View chrome) {
        if (mHidden) return;
        mLayers[0] = pages;
        mLayers[1] = chrome;
        for (int i = 0; i < mLayers.length; i++) {
            View layer = mLayers[i];
            if (layer == null) continue;
            mRestAlpha[i] = layer.getAlpha();
            layer.setAlpha(0f);
        }
        mHidden = true;
    }

    /** The layers go back to the alpha they had. Idempotent. */
    void restore() {
        if (!mHidden) return;
        mHidden = false;
        for (int i = 0; i < mLayers.length; i++) {
            View layer = mLayers[i];
            mLayers[i] = null;
            if (layer != null) layer.setAlpha(mRestAlpha[i]);
        }
    }
}
