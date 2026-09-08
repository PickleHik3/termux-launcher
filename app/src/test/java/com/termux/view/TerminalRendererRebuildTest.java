package com.termux.view;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * A text size change and a same-font reload both replace the renderer. The variable-font
 * instances and the fallback memo are properties of the faces, not of the size, so the replacement
 * inherits them — and starts cold the moment a face is different.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalRendererRebuildTest {

    private static final Typeface[] CHAIN = {Typeface.SERIF, Typeface.SANS_SERIF};

    private static TerminalRenderer renderer(int textSize, Typeface regular, Typeface[] chain,
                                             TerminalRenderer previous) {
        return new TerminalRenderer(textSize, regular, null, null, null, null, null, null, null,
            null, null, chain, null, previous);
    }

    @Test
    public void aTextSizeChangeKeepsBothCaches() {
        TerminalRenderer first = renderer(24, Typeface.MONOSPACE, CHAIN, null);
        TerminalRenderer resized = renderer(30, Typeface.MONOSPACE, CHAIN, first);

        assertSame(first.variationTypefaceCache(), resized.variationTypefaceCache());
        assertSame(first.fallbackResolver(), resized.fallbackResolver());
    }

    @Test
    public void aChangedPrimaryFaceStartsCold() {
        TerminalRenderer first = renderer(24, Typeface.MONOSPACE, CHAIN, null);
        TerminalRenderer reloaded = renderer(24, Typeface.DEFAULT_BOLD, CHAIN, first);

        assertNotSame(first.variationTypefaceCache(), reloaded.variationTypefaceCache());
        assertNotSame(first.fallbackResolver(), reloaded.fallbackResolver());
    }

    @Test
    public void aChangedFallbackChainStartsCold() {
        TerminalRenderer first = renderer(24, Typeface.MONOSPACE, CHAIN, null);
        TerminalRenderer rechained = renderer(24, Typeface.MONOSPACE,
            new Typeface[] {Typeface.SERIF}, first);

        assertNotSame("the memo is sized for the chain it was built on",
            first.fallbackResolver(), rechained.fallbackResolver());
        assertNotSame(first.variationTypefaceCache(), rechained.variationTypefaceCache());
    }

    @Test
    public void theSameChainInADifferentArrayStillCounts() {
        TerminalRenderer first = renderer(24, Typeface.MONOSPACE, CHAIN, null);
        TerminalRenderer again = renderer(24, Typeface.MONOSPACE, CHAIN.clone(), first);

        assertSame(first.fallbackResolver(), again.fallbackResolver());
    }
}
