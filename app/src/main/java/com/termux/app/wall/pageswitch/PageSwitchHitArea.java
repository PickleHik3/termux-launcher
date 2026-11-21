package com.termux.app.wall.pageswitch;

/** A place on screen where a touch opens the page switcher (the page bar, the key's cap). */
public interface PageSwitchHitArea {
    boolean containsRaw(float rawX, float rawY);
}
