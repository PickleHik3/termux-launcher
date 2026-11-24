package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * How one page's mark looks: what a page kind's mark is at rest, when it is the current page and
 * in the lift of the bubble. {@link PageBarView} asks the look once per entry set and only calls
 * {@code draw} per frame; the look draws into any canvas, centred on a point at a given size and
 * ink, so another surface (the status strip) can draw the same marks without the bar's layout,
 * fold or slide.
 *
 * <p>Implementations allocate nothing in {@code draw} after their first use of a kind.
 */
public interface PageMarkLook {

    /**
     * {@code kind}'s mark centred on ({@code cx}, {@code cy}), {@code size} the side of its box, in
     * {@code ink}. {@code number} is the window's number (kinds that show one). {@code current}
     * marks the page the user is on; {@code lift} runs 0 (at rest in the slab) to 1 (fully in the
     * bubble above it). The ink already carries the current/other tier and the lift's ground.
     */
    void draw(@NonNull Canvas canvas, @NonNull PageKind kind, int number, boolean current,
              float lift, float cx, float cy, float size, int ink);

    /**
     * A run of {@code count} pages of one kind folded into one slot, {@code ink} the front's ink and
     * {@code plateInk} the cards behind it.
     */
    void drawStack(@NonNull Canvas canvas, @NonNull PageKind kind, int count, boolean current,
                   float lift, float cx, float cy, float size, int ink, int plateInk);
}
