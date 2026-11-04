package com.termux.app.launcher.widget.builtin;

import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * When a built-in widget's glass slab shows Live blur's picture of what is drawn beneath it
 * rather than the stored frame. One answer, asked when the slab is dressed, when the widget or
 * its page shows or hides, and as the wall or the Home pager moves the page; never per draw.
 *
 * <p>The budget is the recorder's ({@code LiveBackdrop}, widgets at
 * {@code LiveBackdrop.RANK_WIDGET}): a widget this policy wants live may still be refused, or
 * later displaced by the dock, a panel or a float, and then draws the stored frame.</p>
 */
public final class WidgetLiveGlass {

    /** The first Android whose recorder can record what is beneath a surface. */
    public static final int MIN_SDK = Build.VERSION_CODES.S;

    private WidgetLiveGlass() {}

    /**
     * Whether the slab asks for the live picture. Pure.
     *
     * @param liveBlur   Live blur is in effect ({@code LiveBlurPolicy#live}: the switch and what
     *                   it needs)
     * @param sdkInt     the phone's Android
     * @param slabShown  the widget wears its glass slab now (glass on, not drawn solid or Bare)
     * @param pageShown  the widget and every view above it are shown: its Home page is the one in
     *                   front, or the page beside it while Home's pager slides between them, and
     *                   Home is on the wall's screen
     * @param nearScreen the widget is on screen or one screen from it ({@link #near}): the page
     *                   coming in on a swipe is armed before its first frame shows, so the first
     *                   frame the user sees of it is already live
     */
    public static boolean wants(boolean liveBlur, int sdkInt, boolean slabShown,
                                boolean pageShown, boolean nearScreen) {
        return liveBlur && sdkInt >= MIN_SDK && slabShown && pageShown && nearScreen;
    }

    /**
     * Whether a {@code width} x {@code height} rect at {@code x, y} in a {@code windowWidth} x
     * {@code windowHeight} window lies on screen or within one window of it on any side: where
     * Home's pager and the wall pose the page that comes in next. Not yet laid out (no size), it
     * is armed: the recorder records it on the first frame it has one. Pure.
     */
    public static boolean near(int x, int y, int width, int height, int windowWidth,
                               int windowHeight) {
        if (width <= 0 || height <= 0 || windowWidth <= 0 || windowHeight <= 0) return true;
        return x + width > -windowWidth && x < 2 * windowWidth
            && y + height > -windowHeight && y < 2 * windowHeight;
    }

    /**
     * {@link #near} for {@code view} as it is placed now, its parents' transforms (a page slid by
     * the wall or posed by the pager) included. Allocates nothing; {@code scratch} is overwritten.
     */
    static boolean nearScreen(@NonNull View view, @NonNull int[] scratch) {
        if (!view.isAttachedToWindow()) return false;
        View root = view.getRootView();
        view.getLocationInWindow(scratch);
        return near(scratch[0], scratch[1], view.getWidth(), view.getHeight(),
            root.getWidth(), root.getHeight());
    }
}
