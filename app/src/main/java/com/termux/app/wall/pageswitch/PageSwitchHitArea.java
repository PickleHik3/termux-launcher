package com.termux.app.wall.pageswitch;

/** A place on screen where a touch opens the page switcher (the glyph row, the key's cap). */
public interface PageSwitchHitArea {
    boolean containsRaw(float rawX, float rawY);

    /**
     * The panel is open over this area: a resting row of glyphs steps aside (alpha, not layout) so
     * there are not two rows; the key's cap stays as it is.
     */
    default void setCovered(boolean covered) {}
}
