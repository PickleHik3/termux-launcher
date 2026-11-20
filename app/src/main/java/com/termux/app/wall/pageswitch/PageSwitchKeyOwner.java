package com.termux.app.wall.pageswitch;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.extrakeys.ExtraKeysPageView;

import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * The extra-keys bar's page switcher key. The row hands it the whole touch of the key; it feeds
 * the {@link PageBarController} the finger in raw screen coordinates, with the page bar popped up
 * above the band of the page the key stands on. It is also the key's hit area, so the window gives the
 * stream to the views first while a page switcher cap is on screen.
 */
public final class PageSwitchKeyOwner implements ExtraKeysPageView.KeyTouchOwner, PageSwitchHitArea {

    private final PageBarController mController;
    private final IntSupplier mPageCount;
    private final IntFunction<ExtraKeysPageView> mPageAt;
    private final int[] mBandAt = new int[2];

    public PageSwitchKeyOwner(@NonNull PageBarController controller,
                              @NonNull IntSupplier pageCount,
                              @NonNull IntFunction<ExtraKeysPageView> pageAt) {
        mController = controller;
        mPageCount = pageCount;
        mPageAt = pageAt;
        controller.addHitArea(this);
    }

    @Override
    public boolean ownsKey(@NonNull String keyValue) {
        return ExtraKeysPageView.PAGE_SWITCHER_KEY.equals(keyValue);
    }

    @Override
    public boolean down(@NonNull MotionEvent ev, @NonNull View cap) {
        ViewParent parent = cap.getParent();
        View band = parent instanceof View ? (View) parent : cap;
        band.getLocationOnScreen(mBandAt);
        return mController.keyDown(ev.getRawX(), ev.getRawY(), mBandAt[1]);
    }

    @Override
    public void move(@NonNull MotionEvent ev) {
        mController.keyMove(ev.getRawX(), ev.getRawY());
    }

    @Override
    public void up(@NonNull MotionEvent ev) {
        mController.keyUp(ev.getRawX(), ev.getRawY());
    }

    @Override
    public void cancel() {
        mController.keyCancel();
    }

    /**
     * The plain click (an accessibility tap or a script): the bar opens above {@code band} and
     * stays open, as after a tap on the key.
     */
    public void openAbove(@Nullable View band) {
        if (band == null || !band.isShown()) return;
        band.getLocationOnScreen(mBandAt);
        float x = mBandAt[0] + band.getWidth() / 2f;
        float y = mBandAt[1] + band.getHeight() / 2f;
        if (mController.keyDown(x, y, mBandAt[1])) {
            mController.keyUp(x, y);
        }
    }

    @Override
    public boolean containsRaw(float rawX, float rawY) {
        for (int i = 0, n = mPageCount.getAsInt(); i < n; i++) {
            ExtraKeysPageView page = mPageAt.apply(i);
            if (page != null && page.pageSwitcherKeyContainsRaw(rawX, rawY)) return true;
        }
        return false;
    }
}
