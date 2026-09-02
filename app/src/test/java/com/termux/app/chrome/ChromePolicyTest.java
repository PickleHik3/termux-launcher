package com.termux.app.chrome;

import org.junit.Assert;
import org.junit.Test;

/** The chrome module's pure decisions, previously statics on {@code TermuxActivity}. */
public class ChromePolicyTest {

    @Test
    public void testWallpaperReadPromptOnlyAfterAFailedReadThatThePermissionExplains() {
        // The one case worth a dialog: bands want the wallpaper, the read failed, no permission yet.
        Assert.assertTrue(ChromePolicy.shouldPromptForWallpaperRead(true, true, false, false));
        // Never asked before a read has actually failed.
        Assert.assertFalse(ChromePolicy.shouldPromptForWallpaperRead(false, true, false, false));
        // Bands are not sourcing the wallpaper, so the permission buys nothing.
        Assert.assertFalse(ChromePolicy.shouldPromptForWallpaperRead(true, false, false, false));
        // Held already: whatever refused the read, it was not this.
        Assert.assertFalse(ChromePolicy.shouldPromptForWallpaperRead(true, true, true, false));
        // Asked once, answered; do not nag.
        Assert.assertFalse(ChromePolicy.shouldPromptForWallpaperRead(true, true, false, true));
    }

    @Test
    public void testDockGlassOpacityHasLiteralEndpoints() {
        Assert.assertEquals(0, ChromePolicy.dockGlassBaseAlpha(0f));
        Assert.assertEquals(128, ChromePolicy.dockGlassBaseAlpha(0.5f));
        Assert.assertEquals(255, ChromePolicy.dockGlassBaseAlpha(1f));
        Assert.assertEquals(0, ChromePolicy.dockGlassBaseAlpha(-1f));
        Assert.assertEquals(255, ChromePolicy.dockGlassBaseAlpha(2f));
        Assert.assertEquals(255, ChromePolicy.DOCK_GLASS_BASE_MAX_ALPHA);
    }

    @Test
    public void testKeyboardChinLiftsTheCapsuleAndPadsTheDockedSlab() {
        // Floating: the allowance rides on the capsule's own gap, so the glass still wraps the keys.
        Assert.assertEquals(6, ChromePolicy.keyboardChinBottomMarginPx(true, 6, 0));
        Assert.assertEquals(30, ChromePolicy.keyboardChinBottomMarginPx(true, 6, 24));
        Assert.assertEquals(6, ChromePolicy.keyboardChinBottomPaddingPx(true, 6, 24));
        // Docked: no gap under a slab that reaches the screen edge; the keys move up inside it.
        Assert.assertEquals(0, ChromePolicy.keyboardChinBottomMarginPx(false, 6, 24));
        Assert.assertEquals(24, ChromePolicy.keyboardChinBottomPaddingPx(false, 0, 24));
        // A negative allowance can only come from a bad read; it must not eat the capsule's gap.
        Assert.assertEquals(6, ChromePolicy.keyboardChinBottomMarginPx(true, 6, -10));
        Assert.assertEquals(0, ChromePolicy.keyboardChinBottomPaddingPx(false, 0, -10));
    }

    @Test
    public void testBlurAndGrainDoNotDependOnTintOpacity() {
        Assert.assertFalse(ChromePolicy.dockBlurEnabled(0));
        Assert.assertTrue(ChromePolicy.dockBlurEnabled(22));
        Assert.assertEquals(0, ChromePolicy.dockGlassGrainAlpha(0));
        Assert.assertEquals(30, ChromePolicy.dockGlassGrainAlpha(50));
        Assert.assertEquals(60, ChromePolicy.dockGlassGrainAlpha(100));
    }
}
