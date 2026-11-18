package com.termux.app.x11;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;
import com.termux.view.TerminalView;
import com.termux.x11.LorieView;

/**
 * The one display view every Display page shares, and the work a page's front and leave do with
 * it: attached and focused once when a Display page becomes the front; let go, with the keys
 * handed to the terminal once, when the front goes to a page that is not a Display page. Back on
 * the page is {@link DisplayBackPolicy}'s answer applied. Main thread only.
 */
public final class DisplayPages {

    /** The part of the server connection the pages use. */
    public interface Link {
        void attachView(@NonNull View view);
        void detachView();
        void placeEntered(boolean keyboardRaisedOnEnter);
        void placeLeft();
        void sendBack();
        @Nullable DisplayTextFocusPolicy.State textFocus();
    }

    /** {@code controller} as the pages' link. */
    @NonNull
    public static Link linkOf(@NonNull X11DisplayHostController controller) {
        return new Link() {
            @Override public void attachView(@NonNull View view) {
                controller.attachView((LorieView) view);
            }
            @Override public void detachView() { controller.detachView(); }
            @Override public void placeEntered(boolean keyboardRaisedOnEnter) {
                controller.onDisplayPlaceEntered(keyboardRaisedOnEnter);
            }
            @Override public void placeLeft() { controller.onDisplayPlaceLeft(); }
            @Override public void sendBack() { controller.sendBackKey(); }
            @Nullable @Override public DisplayTextFocusPolicy.State textFocus() {
                return controller.textFocusPolicy().state();
            }
        };
    }

    @NonNull private final DisplayPageHost mHost;
    /** The view stays attached through the slide out; it lets go once the wall is at rest. */
    private boolean mDetachOwed;

    public DisplayPages(@NonNull DisplayPageHost host) {
        mHost = host;
    }

    @NonNull
    DisplayPageHost host() {
        return mHost;
    }

    /** A page for Display page {@code pageId}; the wall registers one per Display page. */
    @NonNull
    public Page page(int pageId) {
        return new DisplayPage(pageId, this);
    }

    /**
     * The display's view is the front's: admitted to the focus, attached to the server and
     * focused, once. Also what brings the surface back when the window returns to a Display page
     * at rest, or a connection is built there, with no landing to come.
     */
    public void attach() {
        View view = mHost.displayView();
        Link link = mHost.link();
        if (view == null || link == null) return;
        // The message may be stale if a package landed in the prefix while the wall was away.
        mHost.refreshEmptyState();
        mDetachOwed = false;
        DisplayViewFocus.admit(view);
        link.attachView(view);
        view.requestFocus();
        // A place that asked for the keyboard as it arrived asked for it itself, so the
        // text-focus policy leaves that keyboard alone until it is put down again.
        link.placeEntered(mHost.wantsKeyboardOnEnter());
        // The in-app keyboard and the extra-keys row type into X while the page is the front.
        mHost.restoreTypingClaim();
        mHost.syncKeyboardRoute();
    }

    /**
     * The front left the Display pages: the keys go to the terminal once and the view can no
     * longer take them back; the view lets go of the server once the wall is at rest (it stays
     * attached under the still while the page slides out).
     */
    public void leave() {
        releaseFocus();
        View view = mHost.displayView();
        Link link = mHost.link();
        if (view == null || link == null) return;
        if (mHost.wallAtRest()) link.detachView();
        else mDetachOwed = true;
        link.placeLeft();
        mHost.restoreTypingClaim();
        // The phone's keyboard belongs to the place it was raised on; it goes down with it.
        mHost.hideSystemKeyboard();
        mHost.syncKeyboardRoute();
    }

    /** The wall came to rest off the Display pages: a view kept through the slide lets go. */
    public void detachIfOwed() {
        if (!mDetachOwed) return;
        mDetachOwed = false;
        Link link = mHost.link();
        if (link != null && !mHost.isDisplayShowing()) link.detachView();
    }

    /** Whether the view is still attached for a slide that has not come to rest; for tests. */
    boolean isDetachOwed() {
        return mDetachOwed;
    }

    /**
     * The display's view stops being able to take the keys; unless a terminal holds them, they
     * are handed to the active pane, once, with no retry: a key that finds no holder is delivered
     * by the per-key safety net.
     */
    public void releaseFocus() {
        View view = mHost.displayView();
        if (view == null) return;
        DisplayViewFocus.release(view, focused -> focused instanceof TerminalView,
            mHost::handKeysToTerminal);
    }

    /** What Back on the front Display page does now; PASS leaves it to the chain. */
    @NonNull
    public DisplayBackPolicy.Action back() {
        Link link = mHost.link();
        return DisplayBackPolicy.decide(mHost.isDisplayShowing(), mHost.isLayerOpen(),
            mHost.isRunning(), link == null ? null : link.textFocus(), mHost.frameContent());
    }

    /**
     * Back on a Display page: a keyboard the page raised goes down first; with nothing of its own
     * to put down, the app on the display is told to go back. False when the display is stopped
     * with nothing raised, so the chain's default (Home page 1) answers.
     */
    public boolean onBack() {
        switch (back()) {
            case LOWER_KEYBOARD:
                mHost.lowerKeyboard();
                return true;
            case BACK_IN_APP:
                Link link = mHost.link();
                if (link != null) link.sendBack();
                return true;
            case PASS:
            default:
                return false;
        }
    }
}
