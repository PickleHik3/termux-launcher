package com.termux.app.launcher.widget.builtin;

import android.graphics.Rect;
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
     * @param liveBlur  Live blur is in effect ({@code LiveBlurPolicy#live}: the switch and what
     *                  it needs)
     * @param sdkInt    the phone's Android
     * @param slabShown the widget wears its glass slab now (glass on, not drawn solid or Bare)
     * @param pageShown the widget and every view above it are shown: its Home page is the one in
     *                  front, or the page beside it while Home's pager slides between them, and
     *                  Home is on the wall's screen
     * @param onScreen  some of the widget lies inside the window as it is placed now
     */
    public static boolean wants(boolean liveBlur, int sdkInt, boolean slabShown,
                                boolean pageShown, boolean onScreen) {
        return liveBlur && sdkInt >= MIN_SDK && slabShown && pageShown && onScreen;
    }

    /**
     * Whether some of {@code view} is inside the window now, its parents' clips, scrolls and
     * transforms (a page slid off by the wall or the pager) included. Allocates nothing;
     * {@code scratch} is overwritten.
     */
    static boolean onScreen(@NonNull View view, @NonNull Rect scratch) {
        return view.isAttachedToWindow() && view.getWidth() > 0 && view.getHeight() > 0
            && view.getGlobalVisibleRect(scratch);
    }
}
