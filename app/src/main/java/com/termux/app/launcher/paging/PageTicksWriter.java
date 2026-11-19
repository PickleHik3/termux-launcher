package com.termux.app.launcher.paging;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one writer of the page tick strip. The host the apps row stands in binds the strip that
 * belongs to it (the dock's own band, or the portable host's), and the row's ticks are written to
 * whichever strip is bound. The row never holds the strip.
 */
public final class PageTicksWriter {
    @Nullable private PageTickStripView strip;
    @Nullable private PageTicks last;

    /**
     * Points the writer at {@code next}. The strip being put down is reset first, so a band that
     * is on screen but no longer bound cannot be left holding the last row's pages; the new one is
     * written the last ticks at once.
     */
    public void bind(@Nullable PageTickStripView next) {
        if (strip == next) return;
        PageTickStripView previous = strip;
        strip = next;
        if (previous != null) {
            previous.setPages(1, 0f, -1);
            if (com.termux.app.chrome.MotionLog.enabled && previous.getVisibility() == View.VISIBLE)
                com.termux.app.chrome.MotionLog.log("ticks-shown", "shown=false unbound by="
                    + caller()); // DIAG(ticks-shown)
            previous.setVisibility(View.INVISIBLE);
        }
        if (last != null) writeTo(strip, last);
    }

    /** Remembers {@code ticks} and writes them to the bound strip. */
    public void write(@NonNull PageTicks ticks) {
        last = ticks;
        writeTo(strip, ticks);
    }

    /** The strip being written, or null while there is none. */
    @Nullable
    public PageTickStripView bound() {
        return strip;
    }

    // DIAG(ticks-shown): revert before merge.
    private static String caller() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder by = new StringBuilder();
        for (int k = 2; k < Math.min(st.length, 8); k++)
            by.append(st[k].getClassName().replaceAll(".*\\.", "")).append('.')
                .append(st[k].getMethodName()).append(':').append(st[k].getLineNumber()).append(' ');
        return by.toString();
    }

    private static void writeTo(@Nullable PageTickStripView target, @NonNull PageTicks ticks) {
        if (target == null) return;
        target.setVerticalForm(ticks.vertical);
        target.setGlassInk(ticks.glassBackdrop, ticks.glassInk);
        target.setAccentColor(ticks.accentColor);
        target.setPages(ticks.pages, ticks.position, ticks.warmIndex);
        // INVISIBLE rather than GONE: the band it holds is the row's own air, and a row that
        // gained a page would otherwise grow by it and shove the terminal.
        int visibility = ticks.shown ? View.VISIBLE : View.INVISIBLE;
        // DIAG(ticks-shown): a change of the strip's shown state; reverted before merge.
        if (com.termux.app.chrome.MotionLog.enabled && target.getVisibility() != visibility)
            com.termux.app.chrome.MotionLog.log("ticks-shown", "shown=" + ticks.shown
                + " pages=" + ticks.pages + " pos=" + ticks.position + " by=" + caller());
        target.setVisibility(visibility);
    }
}
