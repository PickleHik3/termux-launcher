package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.ReducedMotion;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.Theme;

/**
 * Puts the pressed-key popup on screen and keeps it honest.
 *
 * <p>The keyboard reports what each finger currently holds; this turns that into the overlay's own
 * coordinates and hands it over. The overlay is not a child of the keyboard: it is added to the
 * window's content view, so a top-row popup floats over the terminal output instead of being
 * clipped by the keyboard's container. Whether the keyboard is docked or floating makes no
 * difference — the coordinates are resolved from the screen each time a finger goes down.
 */
public final class KeyPopupController implements Keyboard2View.KeyPopupListener {

    @NonNull private final ViewGroup mHost;
    @NonNull private final Keyboard2View mKeyboardView;
    @NonNull private final KeyPopupOverlayView mOverlay;

    private final int[] mKeyboardLocation = new int[2];
    private final int[] mOverlayLocation = new int[2];
    private final RectF mBounds = new RectF();

    private boolean mEnabled;

    public KeyPopupController(@NonNull ViewGroup host, @NonNull Keyboard2View keyboardView) {
        this(host, keyboardView, new KeyPopupOverlayView(host.getContext()));
    }

    @VisibleForTesting
    KeyPopupController(@NonNull ViewGroup host, @NonNull Keyboard2View keyboardView,
                       @NonNull KeyPopupOverlayView overlay) {
        mHost = host;
        mKeyboardView = keyboardView;
        mOverlay = overlay;
        Context context = host.getContext();
        mOverlay.setTypefaces(keyboardView.labelFont(), Theme.getKeyFont(context));
        mOverlay.setPalette(KeyPopupPalette.resolve(context));
        mOverlay.setReducedMotion(ReducedMotion.isEnabled(context));
    }

    /** The view the popup is drawn in. Attached only while the setting is on. */
    @NonNull
    public KeyPopupOverlayView overlay() {
        return mOverlay;
    }

    /**
     * Turns the popup on or off. Off, the keyboard reports nothing and the overlay is not in the
     * view tree at all, so the feature costs exactly one boolean when the user does not want it.
     */
    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        if (enabled) {
            attach();
            mKeyboardView.setKeyPopupListener(this);
        } else {
            mKeyboardView.setKeyPopupListener(null);
            mOverlay.hideAll();
            detach();
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /** Re-reads the Material roles and the phone's animation setting. */
    public void refresh() {
        Context context = mHost.getContext();
        mOverlay.setPalette(KeyPopupPalette.resolve(context));
        mOverlay.setReducedMotion(ReducedMotion.isEnabled(context));
        mOverlay.setTypefaces(mKeyboardView.labelFont(), Theme.getKeyFont(context));
    }

    /** The keyboard is going away: drop every popup and leave the host as it was found. */
    public void destroy() {
        mKeyboardView.setKeyPopupListener(null);
        mOverlay.hideAll();
        detach();
        mEnabled = false;
    }

    private void attach() {
        if (mOverlay.getParent() == mHost) return;
        if (mOverlay.getParent() instanceof ViewGroup)
            ((ViewGroup) mOverlay.getParent()).removeView(mOverlay);
        mHost.addView(mOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void detach() {
        if (mOverlay.getParent() instanceof ViewGroup)
            ((ViewGroup) mOverlay.getParent()).removeView(mOverlay);
    }

    // ------------------------------------------------------------------ keyboard callbacks

    @Override
    public void onKeyPopupShow(int pointerId, @NonNull Keyboard2View.KeyPopupInfo info) {
        if (!mEnabled || mOverlay.getParent() == null) return;
        mKeyboardView.getLocationOnScreen(mKeyboardLocation);
        mOverlay.getLocationOnScreen(mOverlayLocation);
        float dx = mKeyboardLocation[0] - mOverlayLocation[0];
        float dy = mKeyboardLocation[1] - mOverlayLocation[1];
        mBounds.set(info.keyBounds);
        mBounds.offset(dx, dy);
        mOverlay.show(pointerId, mBounds, dx, dx + mKeyboardView.getWidth(),
            info.label, info.labelKeyFont);
    }

    @Override
    public void onKeyPopupTarget(int pointerId, @NonNull String label, boolean labelKeyFont,
                                 int slot) {
        mOverlay.target(pointerId, label, labelKeyFont, slot);
    }

    /** Nothing to show: the popup is one glyph, with no room for a held or latched mark. */
    @Override
    public void onKeyPopupLatch(int pointerId, boolean latched) {
    }

    @Override
    public void onKeyPopupHide(int pointerId) {
        mOverlay.hide(pointerId);
    }

    @Override
    public void onKeyPopupHideAll() {
        mOverlay.hideAll();
    }

    /** The window-wide group a popup floats in, or null when the view is not in a window yet. */
    @Nullable
    public static ViewGroup findPopupHost(@Nullable View anyViewInWindow) {
        if (anyViewInWindow == null) return null;
        View root = anyViewInWindow.getRootView();
        if (root == null) return null;
        View content = root.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) return (ViewGroup) content;
        return root instanceof ViewGroup ? (ViewGroup) root : null;
    }
}
