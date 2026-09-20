package com.termux.view;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** A new pane view starts from a sibling's renderer caches, and a repeated size is a no-op. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalViewFontSeedTest {

    private static TerminalView view() {
        return new TerminalView(RuntimeEnvironment.getApplication(), null);
    }

    @Test
    public void settingTheSameSizeAgainKeepsTheRenderer() {
        TerminalView view = view();
        view.setTextSize(24);
        TerminalRenderer renderer = view.mRenderer;
        view.setTextSize(24);
        assertSame(renderer, view.mRenderer);
    }

    @Test
    public void aNewViewAdoptsASiblingsFontsAndCaches() {
        TerminalView sibling = view();
        sibling.setTextSize(24);
        sibling.setTypeface(Typeface.SERIF, null, null, null);
        TerminalView fresh = view();
        assertNull(fresh.mRenderer);

        fresh.adoptFontFrom(sibling);

        assertNotNull(fresh.mRenderer);
        assertSame(Typeface.SERIF, fresh.mRenderer.mTypeface);
        assertSame(sibling.mRenderer.variationTypefaceCache(), fresh.mRenderer.variationTypefaceCache());
        assertSame(sibling.mRenderer.asciiMeasures(), fresh.mRenderer.asciiMeasures());
        // The host's own default-size pass then finds nothing to rebuild.
        TerminalRenderer adopted = fresh.mRenderer;
        fresh.setTextSize(24);
        assertSame(adopted, fresh.mRenderer);
    }

    @Test
    public void adoptionNeverReplacesARendererTheViewAlreadyHas() {
        TerminalView sibling = view();
        sibling.setTextSize(24);
        TerminalView own = view();
        own.setTextSize(30);
        TerminalRenderer before = own.mRenderer;
        own.adoptFontFrom(sibling);
        assertSame(before, own.mRenderer);
    }
}
