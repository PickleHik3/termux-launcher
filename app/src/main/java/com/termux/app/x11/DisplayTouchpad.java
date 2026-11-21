package com.termux.app.x11;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;

/**
 * The Display place's mouse-mode frame: the touchpad that stands in the keyboard's frame, the
 * keyboard parked behind it, and the content policy's decisions applied to both. Main thread only.
 */
public final class DisplayTouchpad {

    /** What the frame asks of its window. */
    public interface Host {
        @Nullable X11PaneFrame displayFrame();
        @NonNull DisplayFrameContentPolicy frameContent();
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        /** The keyboard view the pad borrows its frame from; null while none is attached. */
        @Nullable View attachedKeyboardView();
        boolean isDisplayWindowInFront();
        @Nullable <T extends View> T findView(int id);
        /** A new pad over the pointer sink, dressed in the overlay's colours. */
        @NonNull DisplayTouchpadView newTouchpad(@NonNull DisplayTouchpadView.PointerSink sink);
        void setTouchMouseMode(boolean mouseMode);
        void setMouseMode(boolean enabled);
        void syncMouseModeMark();
        void claimDrawerRaisedKeyboard();
        void hideClipboardPanel();
        boolean isReducedMotionEnabled();
        float dpToPx(float dp);
    }

    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    @NonNull private final DisplayServer mDisplayServer;
    @NonNull private final DisplayKeys mDisplayKeys;
    @NonNull private final DisplayTouchpadSeat mSeat;
    /** The keyboard layout the pad follows, held so a second attach does not stack another. */
    @Nullable private View.OnLayoutChangeListener mDisplayTouchpadFollower;
    /** Set while the pad is waiting out a keyboard layout before it is attached. */
    @Nullable private com.termux.app.window.FirstLayout.Handle mDisplayTouchpadWait;
    /** The pad waited out one layout already: it is attached at the next ask, whatever it finds. */
    private boolean mDisplayTouchpadWaited;
    /** Set while the frame the touchpad stands in is being raised or lowered for the pad's sake. */
    private boolean mRaisingDisplayFrameKeyboard;

    public DisplayTouchpad(@NonNull android.content.Context context, @NonNull Host host,
                           @NonNull DisplayServer displayServer, @NonNull DisplayKeys displayKeys,
                           @NonNull DisplayTouchpadSeat seat) {
        mContext = context;
        mHost = host;
        mDisplayServer = displayServer;
        mDisplayKeys = displayKeys;
        mSeat = seat;
    }

    public boolean isRaisingDisplayFrameKeyboard() {
        return mRaisingDisplayFrameKeyboard;
    }

    public void setRaisingDisplayFrameKeyboard(boolean raising) {
        mRaisingDisplayFrameKeyboard = raising;
    }

    /**
     * True while mouse mode's touchpad owns the keyboard frame: the frame then holds the pad or a
     * keyboard parked in front of it, and every request for the keyboard swaps the two instead of
     * putting the frame away. Off the Display place, and with no display running, mouse mode is
     * the terminal's alone and the keyboard is nobody's but the user's.
     */
    public boolean isDisplayTouchpadFrame() {
        return mHost.frameContent().isMouseMode() && mHost.isDisplayWindowInFront() && mDisplayServer.isRunning()
            && mHost.inAppKeyboard() != null && mHost.findView(R.id.inapp_keyboard_view_host) != null;
    }

    /** The same, for a request that names which way it wants the keyboard. */
    public boolean applyDisplayFrameKeyboard(boolean show) {
        if (mDisplayKeys.applyDisplaySystemKeyboard(show)) return true;
        if (!isDisplayTouchpadFrame()) return false;
        applyDisplayFrameDecision(mHost.frameContent().onKeyboardIntent(show));
        return true;
    }

    /** The text-focus policy's own show and hide, which swap the frame's content in mouse mode. */
    public boolean applyDisplayFrameTextFocus(boolean focused) {
        if (mDisplayKeys.applyDisplaySystemKeyboard(focused)) return true;
        if (!isDisplayTouchpadFrame()) return false;
        applyDisplayFrameDecision(mHost.frameContent().onTextFocus(focused));
        return true;
    }

    /**
     * Apply one of the content policy's decisions: the panes hear about mouse mode, the frame is
     * re-synced to what it now holds, and only then is the text-focus policy told what the user
     * meant — the sync itself raises and lowers keyboards, and none of that is the user's doing.
     */
    public void applyDisplayFrameDecision(
            @NonNull DisplayFrameContentPolicy.Decision decision) {
        if (!decision.taken) return;
        mHost.setTouchMouseMode(decision.mouseMode());
        syncDisplayTouchpad();
        // The frame the pad raised for itself goes down with the pad, so the mouse key closes
        // exactly what it opened and the user is not left with a keyboard to dismiss.
        if (decision.releaseFrame) lowerDisplayFrameKeyboard();
        mHost.syncMouseModeMark();
        if (mDisplayServer.controller() == null) return;
        if (decision.intent != DisplayFrameContentPolicy.Intent.NONE)
            mHost.claimDrawerRaisedKeyboard();
        switch (decision.intent) {
            case PIN:
                // A keyboard the user asked for is theirs — but only if one is actually up: the
                // pad's frame going away with it is nobody's keyboard.
                if (mHost.inAppKeyboard() != null && mHost.inAppKeyboard().isVisible())
                    mDisplayServer.controller().onUserKeyboardIntent(true);
                break;
            case UNPIN:
                mDisplayServer.controller().onUserKeyboardIntent(false);
                break;
            default:
                break;
        }
    }

    /**
     * The touchpad is there exactly while mouse mode is on, the wall rests on the Display place
     * and a display is running. It lies over the in-app keyboard inside the keyboard's host, so
     * it has the keyboard's size — whatever the user set — and the keyboard's place; the keys
     * fade under it and come back when it goes.
     *
     * <p>That host is also what a floating keyboard carries into its frame, so the pad takes the
     * floating card and is dragged around with it for free. It deliberately stops at the keyboard,
     * not at the card: the grab handle above it has to stay grabbable while mouse mode is on.
     */
    public void syncDisplayTouchpad() {
        FrameLayout host = mHost.findView(R.id.inapp_keyboard_view_host);
        boolean ours = isDisplayTouchpadFrame() && host != null;
        // The frame stops being mouse mode's when the wall leaves the place or the display goes:
        // mouse mode itself stays on, and comes back to the pad rather than to a parked keyboard.
        if (!ours) mHost.frameContent().onFrameLost();
        boolean wanted = ours
            && mHost.frameContent().content() == DisplayFrameContentPolicy.Content.PAD;
        float density = mContext.getResources().getDisplayMetrics().density;
        // The pad stands in a split keyboard's parting, so while it is wanted the keyboard parts
        // wide enough to point in and both halves shrink toward the edges; mouse mode off gives
        // the user's own parting back. A parting that moved is a relayout the pad has to wait
        // for before it can be sized to it.
        boolean parting = mHost.inAppKeyboard() != null && mHost.inAppKeyboard().setMinimumSplitGapPx(
            wanted ? DisplayTouchpadPlacement.minimumGapPx(density) : 0);
        DisplayTouchpadView pad = mSeat.touchpad();
        long duration = com.termux.app.terminal.Motion.FLOAT_DEPTH_MS;
        android.view.animation.Interpolator settle = com.termux.app.terminal.Motion.settle();
        boolean reduced = mHost.isReducedMotionEnabled();
        // The pad and the clipboard panel stand in the same frame; the pad's arrival puts the
        // panel away first, so the two never stack.
        if (wanted) mHost.hideClipboardPanel();
        // The keyboard is the frame both contents stand in: it has to be up for either to have a
        // size, and it stays up across a swap so the X screen is never resized to make room.
        if (ours && !mHost.inAppKeyboard().isVisible()) {
            raiseDisplayFrameKeyboard();
            mHost.frameContent().onFrameRaisedForPad();
        }
        if (!wanted) {
            releaseDisplayTouchpadWait();
            if (pad == null) return;
            final DisplayTouchpadView leaving = pad;
            mSeat.setTouchpad(null);
            View keys = mHost.attachedKeyboardView();
            if (keys != null) {
                keys.animate().cancel();
                if (reduced) keys.setAlpha(1f);
                else keys.animate().alpha(1f).setDuration(duration).setInterpolator(settle).start();
            }
            Runnable detach = () -> {
                if (leaving.getParent() instanceof ViewGroup) {
                    ((ViewGroup) leaving.getParent()).removeView(leaving);
                }
            };
            leaving.animate().cancel();
            if (reduced) detach.run();
            else leaving.animate().alpha(0f).translationY(mHost.dpToPx(12)).setDuration(duration)
                .setInterpolator(settle).withEndAction(detach).start();
            return;
        }
        if (pad != null && pad.getParent() == host) {
            // The keyboard's type may have moved under a pad that is already up.
            mSeat.applyDisplayTouchpadFrame(pad, mHost.attachedKeyboardView());
            return;
        }
        if (pad == null) {
            pad = mHost.newTouchpad(
                () -> {
                    X11PaneFrame frame = mHost.displayFrame();
                    return frame == null || !mDisplayServer.isRunning() ? null : frame.display();
                });
            pad.setListener(() -> mHost.setMouseMode(false));
            // A tap on the pad is the display's tap: it opens the window a cursor name answers
            // in, exactly as a tap on the picture does.
            pad.setTapListener(() -> {
                if (mDisplayServer.controller() != null) mDisplayServer.controller().onDisplayTap();
            });
            pad.setModeSwitchListener(mSeat::switchDisplayKeyboardMode);
            mSeat.setTouchpad(pad);
        }
        // The keyboard's frame is the pad's: the host wraps its content, so a match-parent pad
        // would take every pixel above the keyboard instead of the keyboard's own room. Over a
        // split keyboard it is a key's seat in the parting between the two key runs.
        View keyboardView = mHost.attachedKeyboardView();
        // A keyboard that has not been laid out has no parting to report, and one that has just
        // been asked to part wider has not reported the new one yet. Wait that layout out rather
        // than standing the pad over the whole frame, and putting the keys out, for a frame.
        if (keyboardView != null && (parting || !keyboardView.isLaidOut())
            && !mDisplayTouchpadWaited) {
            // One layout, once: the 160 ms backstop is the keyboard reveal gate's, for the same
            // reason — a layout that never comes must not swallow the pad.
            if (mDisplayTouchpadWait == null) {
                mDisplayTouchpadWait = com.termux.app.window.FirstLayout.afterNextLayout(
                    keyboardView, 160L, () -> {
                        mDisplayTouchpadWait = null;
                        mDisplayTouchpadWaited = true;
                        syncDisplayTouchpad();
                    });
            }
            return;
        }
        releaseDisplayTouchpadWait();
        if (pad.getParent() instanceof ViewGroup) ((ViewGroup) pad.getParent()).removeView(pad);
        Rect gap = mSeat.splitKeyboardTouchpadGap(keyboardView);
        host.addView(pad, mSeat.seatDisplayTouchpad(pad, keyboardView, gap));
        if (keyboardView != null) {
            final DisplayTouchpadView following = pad;
            if (mDisplayTouchpadFollower != null)
                keyboardView.removeOnLayoutChangeListener(mDisplayTouchpadFollower);
            mDisplayTouchpadFollower = (v, l, t, r, b, ol, ot, or, ob) -> {
                if (following.getParent() != host) return;
                mSeat.applyDisplayTouchpadFrame(following, v);
            };
            keyboardView.addOnLayoutChangeListener(mDisplayTouchpadFollower);
        }
        View keys = mHost.attachedKeyboardView();
        // A pad standing in the parting leaves both halves typing, so the keys stay lit. Only a
        // measured keyboard that has no parting to stand in is put out under a whole-frame pad.
        if (keys != null && gap == null && keys.getHeight() > 0) {
            keys.animate().cancel();
            if (reduced) keys.setAlpha(0f);
            else keys.animate().alpha(0f).setDuration(duration).setInterpolator(settle).start();
        }
        pad.animate().cancel();
        if (reduced) {
            pad.setAlpha(1f);
            pad.setTranslationY(0f);
            return;
        }
        pad.setAlpha(0f);
        pad.setTranslationY(mHost.dpToPx(12));
        pad.animate().alpha(1f).translationY(0f).setDuration(duration).setInterpolator(settle)
            .start();
    }

    /**
     * Put up the keyboard the touchpad borrows its frame from. Nobody asked for a keyboard here,
     * so the visibility listener is held off: this must not read as the user pinning one.
     */
    public void raiseDisplayFrameKeyboard() {
        if (mHost.inAppKeyboard() == null) return;
        mRaisingDisplayFrameKeyboard = true;
        try {
            mHost.inAppKeyboard().show(com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard
                .ShowReason.KEYBOARD_ACTION);
        } finally {
            mRaisingDisplayFrameKeyboard = false;
        }
    }

    /**
     * Take down the keyboard the touchpad had raised for its frame, now that the pad is gone.
     * Nobody asked for a keyboard to go here either, so the visibility listener is held off.
     */
    public void lowerDisplayFrameKeyboard() {
        if (mHost.inAppKeyboard() == null || !mHost.inAppKeyboard().isVisible()) return;
        mRaisingDisplayFrameKeyboard = true;
        try {
            mHost.inAppKeyboard().hide(com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard
                .HideReason.KEYBOARD_ACTION);
        } finally {
            mRaisingDisplayFrameKeyboard = false;
        }
    }

    /** Ends the pad's layout wait, if one is on, and lets the next attach wait once again. */
    public void releaseDisplayTouchpadWait() {
        if (mDisplayTouchpadWait != null) mDisplayTouchpadWait.cancel();
        mDisplayTouchpadWait = null;
        mDisplayTouchpadWaited = false;
    }
}
