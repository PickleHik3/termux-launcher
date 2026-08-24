package com.termux.app.notice;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps the notice chip hanging off the bottom edge of whatever chrome sits above it.
 *
 * <p>The offset used to be measured once, when the host was first attached, off whatever the
 * toolbar happened to be at that moment. That is wrong more often than it is right: a notice raised
 * from a screen's {@code onCreate} measures a toolbar that has never been laid out and lands on the
 * title, and an offset that was correct at attach time survives a rotation, a multi-window resize,
 * a cutout change and a bar that shows or hides — all of which move the edge the chip is supposed
 * to be hanging from.
 *
 * <p>So the offset is derived, not remembered. It is recomputed from the real chrome and the real
 * insets whenever either moves, and once more immediately before a chip is shown, which is the only
 * moment it has to be right. Layout params are touched only when the number actually changes, so a
 * screen that never moves its chrome never lays out on this account.
 *
 * <p>The pill is centred horizontally, so all this settles is the row it lands in: clear of the
 * screen's own chrome, by a hair, so it reads as floating over the content rather than stuck to the
 * bar above it.
 *
 * <p>Every screen goes through the same rule, including the terminal. The terminal used to hand
 * over a structural anchor instead — its surface host, whose top edge is the window bar's bottom
 * edge — but a child of that host is a sibling of whatever the terminal opens inside it, and a
 * later sibling draws on top: the preset-applied notice was laid out at the right place and
 * completely hidden behind the surface editor that raised it. The chip belongs in the window's
 * topmost layer, positioned against the chrome rather than parented to it.
 */
final class AppNoticePlacement implements View.OnLayoutChangeListener {

    /**
     * The chrome the chip can hang from, in order of preference. The container comes before the
     * toolbar inside it: a screen that grows a second row under its title bar means the bottom edge
     * moved, and the chip belongs under the whole thing.
     */
    private static final int[] CHROME_IDS = {
        com.termux.shared.R.id.toolbar_container,
        com.termux.shared.R.id.toolbar,
        com.termux.R.id.terminal_window_bar_host,
    };

    /** Air between the chrome's bottom edge and the pill. */
    private static final float TOP_GAP_DP = 8f;

    @NonNull private final ViewGroup mAnchor;
    @NonNull private final AppNoticeHostView mHost;

    /** The chrome we are currently listening to, so a screen that swaps it is followed. */
    @Nullable private View mChrome;

    private int mAppliedTopPx = Integer.MIN_VALUE;

    /**
     * Starts keeping {@code host} under the chrome of the screen {@code anchor} belongs to. A
     * structural anchor is left alone: there is nothing to measure and nothing to go stale.
     */
    static void attach(@NonNull ViewGroup anchor, @NonNull AppNoticeHostView host) {
        new AppNoticePlacement(anchor, host).install();
    }

    private AppNoticePlacement(@NonNull ViewGroup anchor, @NonNull AppNoticeHostView host) {
        mAnchor = anchor;
        mHost = host;
    }

    private void install() {
        // Insets are read at apply() time rather than remembered from this callback: with a legacy
        // target a sibling that fits system windows consumes them before they reach the chip, so
        // what arrives here is not what the screen is actually inset by. The callback is only a
        // signal that something moved.
        ViewCompat.setOnApplyWindowInsetsListener(mHost, (view, insets) -> {
            schedule();
            return insets;
        });
        mAnchor.addOnLayoutChangeListener(this);
        mHost.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View view) { schedule(); }
            @Override public void onViewDetachedFromWindow(@NonNull View view) { uninstall(); }
        });
        // The one moment the offset has to be right: the frame a chip becomes visible.
        mHost.setPlacementRefresh(this::apply);
        apply();
    }

    private void uninstall() {
        mAnchor.removeOnLayoutChangeListener(this);
        if (mChrome != null) {
            mChrome.removeOnLayoutChangeListener(this);
            mChrome = null;
        }
        mHost.setPlacementRefresh(null);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        schedule();
    }

    /**
     * Applies, but never from inside the layout pass that told us to: writing layout params there
     * is the "requestLayout() improperly called during layout" warning, and one frame late is
     * invisible for a chip that is not on screen yet.
     */
    private void schedule() {
        if (mHost.isInLayout() || mAnchor.isInLayout()) mHost.post(this::apply);
        else apply();
    }

    private void apply() {
        View chrome = resolveChrome();
        int top = Math.max(insetFloor(), chromeBottom(chrome))
            + Math.round(TOP_GAP_DP * density());
        if (top == mAppliedTopPx) return;
        ViewGroup.LayoutParams params = mHost.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        mAppliedTopPx = top;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        margins.topMargin = top;
        mHost.setLayoutParams(margins);
    }

    /**
     * How far the status bar and any cutout reach into the anchor. Subtracting the anchor's own
     * position is what makes one rule work on both kinds of window: a screen whose content already
     * starts below the status bar needs no offset for it, and one drawn edge to edge needs the lot.
     */
    private int insetFloor() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mHost);
        if (insets == null) return 0;
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()
            | WindowInsetsCompat.Type.displayCutout()
            | WindowInsetsCompat.Type.captionBar());
        int[] anchorLocation = new int[2];
        mAnchor.getLocationInWindow(anchorLocation);
        return Math.max(0, bars.top - anchorLocation[1]);
    }

    /** The chrome's bottom edge, in the anchor's own coordinates. */
    private int chromeBottom(@Nullable View chrome) {
        if (chrome == null || chrome.getVisibility() == View.GONE) return 0;
        if (chrome.getHeight() <= 0) {
            // Raised before the first layout pass — a settings page can raise a notice from
            // onCreate. The declared minimum height is a good enough guess to keep the chip off
            // the title, and the anchor's first layout corrects it.
            return insetFloor() + chrome.getMinimumHeight();
        }
        int[] chromeLocation = new int[2];
        int[] anchorLocation = new int[2];
        chrome.getLocationInWindow(chromeLocation);
        mAnchor.getLocationInWindow(anchorLocation);
        return Math.max(0, chromeLocation[1] + chrome.getHeight() - anchorLocation[1]);
    }

    /** The chrome this screen has, looked up afresh: screens replace their bars. */
    @Nullable
    private View resolveChrome() {
        View root = mAnchor.getRootView();
        View found = null;
        for (int id : CHROME_IDS) {
            View candidate = root.findViewById(id);
            if (candidate != null && candidate.getVisibility() != View.GONE) {
                found = candidate;
                break;
            }
        }
        if (found != mChrome) {
            if (mChrome != null) mChrome.removeOnLayoutChangeListener(this);
            mChrome = found;
            // A collapsing bar changes height without the anchor moving, so the chrome is watched
            // in its own right rather than only through the container it sits in.
            if (mChrome != null) mChrome.addOnLayoutChangeListener(this);
        }
        return found;
    }

    private float density() {
        return mHost.getResources().getDisplayMetrics().density;
    }
}
