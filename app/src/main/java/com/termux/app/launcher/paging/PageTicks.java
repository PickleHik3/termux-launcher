package com.termux.app.launcher.paging;

import androidx.annotation.NonNull;

/**
 * Everything the page tick strip is told, as one value: how many pages there are and where
 * between them the row stands, which of them is the warm (most-used) one, whether the band shows
 * at all, and the colours it draws in. The apps row computes it; {@link PageTicksWriter} is the
 * only thing that writes it onto a strip.
 */
public final class PageTicks {
    public final int pages;
    public final float position;
    /** The page whose tick is warm, or -1 for none. */
    public final int warmIndex;
    /** Whether the strip shows; false keeps its band (INVISIBLE), it never collapses it. */
    public final boolean shown;
    public final boolean vertical;
    /** The opaque colour measured under the strip, as {@link PageTickStripView#setGlassInk}. */
    public final int glassBackdrop;
    public final int glassInk;
    public final int accentColor;

    public PageTicks(int pages, float position, int warmIndex, boolean shown, boolean vertical,
                     int glassBackdrop, int glassInk, int accentColor) {
        this.pages = pages;
        this.position = position;
        this.warmIndex = warmIndex;
        this.shown = shown;
        this.vertical = vertical;
        this.glassBackdrop = glassBackdrop;
        this.glassInk = glassInk;
        this.accentColor = accentColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageTicks)) return false;
        PageTicks t = (PageTicks) o;
        return pages == t.pages && Float.compare(position, t.position) == 0
            && warmIndex == t.warmIndex && shown == t.shown && vertical == t.vertical
            && glassBackdrop == t.glassBackdrop && glassInk == t.glassInk
            && accentColor == t.accentColor;
    }

    @Override
    public int hashCode() {
        int h = pages;
        h = 31 * h + Float.floatToIntBits(position);
        h = 31 * h + warmIndex;
        h = 31 * h + (shown ? 1 : 0);
        h = 31 * h + (vertical ? 1 : 0);
        h = 31 * h + glassBackdrop;
        h = 31 * h + glassInk;
        h = 31 * h + accentColor;
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        return "PageTicks{pages=" + pages + ", position=" + position + ", warm=" + warmIndex
            + ", shown=" + shown + ", vertical=" + vertical + ", backdrop=" + glassBackdrop
            + ", ink=" + glassInk + ", accent=" + accentColor + "}";
    }
}
