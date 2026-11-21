package com.termux.app.launcher.widget.builtin;

import android.os.Build;

/**
 * When a built-in widget's glass slab asks for Live blur's picture of what is drawn beneath it
 * rather than the stored frame. One answer, asked when the slab is dressed and when it shows or
 * hides; never as the page moves, and never per draw.
 *
 * <p>The glass service grants the kind (widgets last in its budget): a widget that asks may be
 * granted the stored frame, or moved to it by a policy event, and then draws the stored frame.</p>
 */
public final class WidgetLiveGlass {

    /** The first Android whose recorder can record what is beneath a surface. */
    public static final int MIN_SDK = Build.VERSION_CODES.S;

    private WidgetLiveGlass() {}

    /**
     * Whether the slab asks for the live picture. Pure. Whether its page is on screen, shown or
     * sliding is no input: motion and a landing never change a glass surface's kind (ADR 0029
     * section 4), and a hidden widget holds no layer.
     *
     * @param sdkInt    the phone's Android
     * @param slabShown the widget wears its glass slab now (glass on, not drawn solid or Bare)
     */
    public static boolean wants(int sdkInt, boolean slabShown) {
        return sdkInt >= MIN_SDK && slabShown;
    }
}
