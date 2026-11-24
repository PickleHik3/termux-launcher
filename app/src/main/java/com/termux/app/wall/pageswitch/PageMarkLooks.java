package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Which {@link PageMarkLook} the page bar draws its entries with. */
public final class PageMarkLooks {

    private PageMarkLooks() {
    }

    /**
     * The look to draw with: today's filled glyphs. {@code held} is the look the view already
     * draws with (null on its first ask); it is kept while it is still the chosen one, so asking
     * on every entry change allocates nothing.
     */
    @NonNull
    public static PageMarkLook chosen(@Nullable PageMarkLook held) {
        return chosen(PageBarLook.GLYPHS, held);
    }

    /** The look {@code setting} names; {@code held} is kept while it is that one. */
    @NonNull
    public static PageMarkLook chosen(@NonNull PageBarLook setting, @Nullable PageMarkLook held) {
        if (setting == PageBarLook.MARKS) {
            return held instanceof MarksLook ? held : new MarksLook();
        }
        return held instanceof PageBarGlyphPainter ? held : new PageBarGlyphPainter();
    }
}
