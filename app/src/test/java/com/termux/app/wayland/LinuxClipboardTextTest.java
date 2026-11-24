package com.termux.app.wayland;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxClipboardTextTest {

    @Test
    public void nullEmptyAndOversizeTextsDoNotCross() {
        assertFalse(LinuxClipboardText.acceptable(null));
        assertFalse(LinuxClipboardText.acceptable(""));
        assertFalse(LinuxClipboardText.acceptable("x".repeat(LinuxClipboardText.MAX_CHARS + 1)));
    }

    @Test
    public void textUpToTheCapCrosses() {
        assertTrue(LinuxClipboardText.acceptable("a"));
        assertTrue(LinuxClipboardText.acceptable("x".repeat(LinuxClipboardText.MAX_CHARS)));
    }

    @Test
    public void aTextDoesNotBounceBack() {
        LinuxClipboardText crossed = new LinuxClipboardText();
        assertTrue(crossed.crossing("from android"));
        assertFalse("the host's echo of it", crossed.crossing("from android"));
        assertTrue(crossed.crossing("from linux"));
        assertFalse("Android's clipboard change after we set it", crossed.crossing("from linux"));
        assertTrue("copying the older text again is new", crossed.crossing("from android"));
    }
}
