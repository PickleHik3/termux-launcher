package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A view told to hide its cursor keeps it hidden when a new text size replaces its renderer: a
 * watch-only terminal fits its text to its width after it is laid out, and the cursor came back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewCursorSuppressedTest {

    @Test
    public void aNewTextSizeKeepsTheCursorHidden() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTextSize(24);
        view.setCursorSuppressed(true);
        view.setTextSize(30);
        assertTrue(view.mRenderer.isCursorSuppressed());
    }

    @Test
    public void hidingBeforeThereIsARendererStillHidesIt() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setCursorSuppressed(true);
        view.setTextSize(24);
        assertTrue(view.mRenderer.isCursorSuppressed());
    }

    @Test
    public void aShownCursorStaysShownAcrossTextSizes() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTextSize(24);
        view.setCursorSuppressed(true);
        view.setCursorSuppressed(false);
        view.setTextSize(30);
        assertFalse(view.mRenderer.isCursorSuppressed());
    }
}
