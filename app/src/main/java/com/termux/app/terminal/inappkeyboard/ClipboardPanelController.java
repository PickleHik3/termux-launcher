package com.termux.app.terminal.inappkeyboard;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClipboardHistory;
import com.termux.app.terminal.Motion;

/**
 * Puts the clipboard panel up over the keys and takes it down again.
 *
 * <p>The panel stands in the keyboard's own view host, with the keyboard's height — whatever the
 * user set — and the keyboard's place, exactly as mouse mode's touchpad does: so it takes a
 * floating keyboard's card and rides along with it, and never changes what the accessory stack
 * reserves. The keys fade under it and come back when it goes; the panel itself arrives from a
 * few dp below on the shared settle curve and leaves the same way, or at once with reduced
 * motion. Nothing animates while it is up. A keyboard that is laid out again while the panel
 * is showing re-sizes the panel to its new height, once, from the layout listener.
 */
public final class ClipboardPanelController {

    /** What the panel needs of the activity: the frame, the keys, the colours and a paste. */
    public interface Host {
        /** The keyboard's view host, {@code inapp_keyboard_view_host}; null before inflation. */
        @Nullable FrameLayout keyboardViewHost();

        /** The keyboard view the panel takes the size of; null while there is no keyboard. */
        @Nullable View keyboardView();

        /** A card with the surfaces, or flush against a square dock. */
        boolean isCard();

        boolean isReducedMotionEnabled();

        @NonNull ClipboardPanelView.Palette palette();

        /** Paste {@code text} where the keyboard's own paste key pastes, clipboard included. */
        void pasteFromHistory(@NonNull String text);
    }

    private static final float ENTER_OFFSET_DP = 12f;

    @NonNull private final Host mHost;
    @NonNull private final ClipboardHistory mHistory;
    @Nullable private ClipboardPanelView mPanel;
    @Nullable private View mFollowed;
    @Nullable private View.OnLayoutChangeListener mFollower;

    public ClipboardPanelController(@NonNull Host host, @NonNull ClipboardHistory history) {
        mHost = host;
        mHistory = history;
    }

    /** True while a panel is up, or on its way up. */
    public boolean isShowing() {
        return mPanel != null;
    }

    public void toggle() {
        if (isShowing()) hide();
        else show();
    }

    /**
     * Puts the panel up. Nothing happens without a keyboard view to take the size of: the panel
     * has no size of its own, and the key that opens it is on that keyboard anyway.
     */
    public void show() {
        if (mPanel != null) return;
        FrameLayout host = mHost.keyboardViewHost();
        View keys = mHost.keyboardView();
        if (host == null || keys == null) return;
        ClipboardPanelView panel = new ClipboardPanelView(host.getContext(), mHost.palette(),
            mHost.isCard(), new ClipboardPanelView.Listener() {
                @Override public void onPasteRequested(@NonNull String text) {
                    // One tap: the item is pasted and the keys are back for what comes next.
                    hide();
                    mHost.pasteFromHistory(text);
                }

                @Override public void onCloseRequested() {
                    hide();
                }
            });
        panel.bind(mHistory);
        mPanel = panel;
        host.addView(panel, params(keys.getHeight()));
        follow(keys);

        long duration = Motion.FLOAT_DEPTH_MS;
        Interpolator settle = Motion.settle();
        boolean reduced = mHost.isReducedMotionEnabled();
        keys.animate().cancel();
        if (reduced) keys.setAlpha(0f);
        else keys.animate().alpha(0f).setDuration(duration).setInterpolator(settle).start();
        panel.animate().cancel();
        if (reduced) {
            panel.setAlpha(1f);
            panel.setTranslationY(0f);
            return;
        }
        float offset = ENTER_OFFSET_DP * host.getResources().getDisplayMetrics().density;
        panel.setAlpha(0f);
        panel.setTranslationY(offset);
        panel.animate().alpha(1f).translationY(0f).setDuration(duration).setInterpolator(settle)
            .start();
    }

    /** Takes the panel down and brings the keys back. */
    public void hide() {
        ClipboardPanelView panel = mPanel;
        if (panel == null) return;
        mPanel = null;
        unfollow();
        View keys = mHost.keyboardView();
        long duration = Motion.FLOAT_DEPTH_MS;
        Interpolator settle = Motion.settle();
        boolean reduced = mHost.isReducedMotionEnabled();
        if (keys != null) {
            keys.animate().cancel();
            if (reduced) keys.setAlpha(1f);
            else keys.animate().alpha(1f).setDuration(duration).setInterpolator(settle).start();
        }
        Runnable detach = () -> detach(panel);
        panel.animate().cancel();
        if (reduced) {
            detach.run();
            return;
        }
        float offset = ENTER_OFFSET_DP * panel.getResources().getDisplayMetrics().density;
        panel.animate().alpha(0f).translationY(offset).setDuration(duration)
            .setInterpolator(settle).withEndAction(detach).start();
    }

    /**
     * Drops the panel at once, with no motion and without touching the keys: for a keyboard
     * view that is being replaced or taken away, whose alpha the caller owns.
     */
    public void drop() {
        ClipboardPanelView panel = mPanel;
        if (panel == null) return;
        mPanel = null;
        unfollow();
        panel.animate().cancel();
        detach(panel);
    }

    private static void detach(@NonNull View panel) {
        if (panel.getParent() instanceof ViewGroup) {
            ((ViewGroup) panel.getParent()).removeView(panel);
        }
    }

    /** The whole of the keyboard's frame, at the keyboard's height. */
    private static FrameLayout.LayoutParams params(int keyboardHeightPx) {
        int height = keyboardHeightPx > 0 ? keyboardHeightPx : ViewGroup.LayoutParams.WRAP_CONTENT;
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height,
            Gravity.TOP);
    }

    private void follow(@NonNull View keys) {
        unfollow();
        mFollowed = keys;
        mFollower = (v, l, t, r, b, ol, ot, or, ob) -> {
            ClipboardPanelView panel = mPanel;
            if (panel == null) return;
            int height = b - t;
            ViewGroup.LayoutParams current = panel.getLayoutParams();
            if (current != null && current.height == height) return;
            panel.setLayoutParams(params(height));
        };
        keys.addOnLayoutChangeListener(mFollower);
    }

    private void unfollow() {
        if (mFollowed != null && mFollower != null)
            mFollowed.removeOnLayoutChangeListener(mFollower);
        mFollowed = null;
        mFollower = null;
    }
}
