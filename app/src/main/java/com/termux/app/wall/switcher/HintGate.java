package com.termux.app.wall.switcher;

/**
 * When the page switcher's first-run hint may open the picker. Pure: the controller asks on a
 * drawn frame and marks the hint shown only after the picker with the hint has opened.
 */
public final class HintGate {

    private HintGate() {
    }

    /**
     * Whether the picker should open now with the hint. The switcher must be on and its dot
     * visible, the hint not shown before, and nothing (the setup wizard, a first-run sheet)
     * covering the launcher: while covered the hint waits and is not consumed.
     */
    public static boolean opensNow(boolean switcherOn, boolean dotVisible, boolean alreadyShown,
                                   boolean covered) {
        return switcherOn && dotVisible && !alreadyShown && !covered;
    }
}
