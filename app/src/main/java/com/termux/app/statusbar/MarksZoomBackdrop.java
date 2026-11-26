package com.termux.app.statusbar;

import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.termux.app.chrome.PanelBackdrop;

/**
 * The panel controller of the marks zoom: it owns the zoom's {@link PanelBackdrop}, the layered Live
 * picture of what is beneath the zoom's glass ground, registered with the glass service under
 * {@link MarksZoomView#GLASS_ELEMENT} (the chrome rank every panel's picture wears). The zoom opens it
 * once at touch-down and closes it at the lift and on every cancel path; nothing is kept after.
 */
final class MarksZoomBackdrop {

    @NonNull private final PanelBackdrop mBackdrop;

    /** @param picture the zoom's picture view, the lowest view of its glass. */
    MarksZoomBackdrop(@NonNull ImageView picture) {
        mBackdrop = new PanelBackdrop(picture, picture, MarksZoomView.GLASS_ELEMENT);
    }

    /** True when Live is granted and the picture is up; false when the ground carries the stored frame. */
    boolean open(float blurRadiusPx) {
        return mBackdrop.open(blurRadiusPx);
    }

    void close() {
        mBackdrop.close();
    }
}
