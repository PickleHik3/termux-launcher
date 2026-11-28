package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WindowLookTest {

    @Test public void aSmallTileClampsTheGlobalRadius() {
        // 14dp at density 2 is 28px; a 60px-high tile may claim a third of its short side: 20px.
        assertEquals(20f, WindowLook.cornerPx(14, 2f, 400, 60), 0f);
        assertEquals(28f, WindowLook.cornerPx(14, 2f, 400, 600), 0f);
    }

    @Test public void aWindowWithNoSizeYetHasNoRadius() {
        assertEquals(0f, WindowLook.cornerPx(14, 2f, 0, 0), 0f);
    }

    @Test public void aSquareCornerStaysSquare() {
        assertEquals(0f, WindowLook.cornerPx(0, 3f, 500, 500), 0f);
    }

    @Test public void opacityStaysInItsRange() {
        assertEquals(20, WindowLook.clampOpacity(0));
        assertEquals(100, WindowLook.clampOpacity(250));
        assertEquals(0.5f, WindowLook.linuxAlpha(50), 0f);
        assertEquals(1f, WindowLook.linuxAlpha(100), 0f);
    }

    @Test public void onlyAnUnfocusedWindowIsDimmed() {
        assertEquals(0, WindowLook.dimAlpha(true, 40));
        assertEquals(0, WindowLook.dimAlpha(false, 0));
        assertEquals(102, WindowLook.dimAlpha(false, 40));
        assertEquals(153, WindowLook.dimAlpha(false, 99));
    }

    @Test public void theRimIsOnlyOnTheFocusedWindowWhenOn() {
        assertEquals(4, WindowLook.rimWidthPx(true, true, 2f, 2f));
        assertEquals(0, WindowLook.rimWidthPx(false, true, 2f, 2f));
        assertEquals(0, WindowLook.rimWidthPx(true, false, 2f, 2f));
        assertEquals(1, WindowLook.rimWidthPx(true, true, 0.1f, 1f));
    }

    @Test public void theRimFollowsTheAccentUnlessAColourIsNamed() {
        assertEquals(0xFF123456, WindowLook.rimColour("accent", 0xFF123456));
        assertEquals(0xFF123456, WindowLook.rimColour("nonsense", 0xFF123456));
        assertEquals(0xFF123456, WindowLook.rimColour(null, 0xFF123456));
        assertEquals(0xFFFFFFFF, WindowLook.rimColour("white", 0xFF123456));
    }

    @Test public void glassAlphaFollowsOpacity() {
        assertEquals(0, WindowLook.glassAlpha(0));
        assertEquals(255, WindowLook.glassAlpha(100));
        assertEquals(255, WindowLook.glassAlpha(300));
    }

    @Test public void theDefaultsAreTheDesktopsOwn() {
        assertEquals(8, WindowLook.DEFAULT.gapDp());
        assertEquals(0, WindowLook.DEFAULT.unfocusedDimPercent());
        assertEquals(100, WindowLook.DEFAULT.linuxOpacity("any"));
    }

    @Test public void theRimColourNamesAreTheDeclarations() {
        assertEquals(com.termux.shared.termux.settings.registry.SettingDeclarations.WINDOWS_RIM_COLOUR.choices,
            WindowLook.RIM_COLOURS);
    }

    @Test public void theFingerprintChangesWithAnythingTheFramesWear() {
        String base = WindowLook.fingerprint(WindowLook.DEFAULT);
        assertEquals(base, WindowLook.fingerprint(WindowLook.DEFAULT));
        WindowLook.Source wider = new WindowLook.Source() {
            @Override public int gapDp() { return 16; }
            @Override public int cornerDp() { return 14; }
            @Override public int glassOpacity() { return 46; }
            @Override public boolean rimOn() { return true; }
            @androidx.annotation.NonNull @Override public String rimColour() { return "accent"; }
            @Override public int unfocusedDimPercent() { return 0; }
            @Override public int linuxOpacity(@androidx.annotation.NonNull String appId) { return 100; }
            @androidx.annotation.NonNull @Override public String appIdOf(long linuxId) { return ""; }
        };
        org.junit.Assert.assertNotEquals(base, WindowLook.fingerprint(wider));
    }

    /** Round 3: navigation mode dims the windows (the prototype's brightness .82), not the screen. */
    @org.junit.Test public void navigationModeDimsEveryWindowTheFocusedOneToo() {
        // Review of round 3: the prototype darkens every window, the focused one too.
        assertEquals(Math.round(0.18f * 255f), WindowLook.veilAlpha(true, 40, true));
        assertEquals(Math.round(0.18f * 255f), WindowLook.veilAlpha(false, 0, true));
        assertEquals("on top of the unfocused dim", Math.round((1f - (1f - 102 / 255f) * 0.82f) * 255f),
            WindowLook.veilAlpha(false, 40, true));
        assertEquals("off the mode, the dim alone", WindowLook.dimAlpha(false, 40), WindowLook.veilAlpha(false, 40, false));
    }

    /**
     * The cheap-tile parity (live window residency): a tile over the ground and a window over
     * moving content wear one material at the Windows Blur and Opacity, so the parity route and
     * the effect route draw at the same strength.
     */
    @Test public void bothRoutesWearOneMaterialAtTheWindowsBlurAndOpacity() {
        com.termux.app.chrome.GlassStack.Spec global =
            com.termux.app.chrome.GlassStack.Spec.of(12, 0.3f, 4, 28f, null).withRim(true);
        com.termux.app.chrome.GlassStack.Spec spec = WindowLook.groundSpec(global, 20, 46);
        assertEquals(20, spec.blurRadiusDp);
        assertEquals(0.46f, spec.tintAlpha, 1e-6f);
        assertEquals(false, spec.rim);
        assertEquals(4, spec.grainPercent);
        assertEquals(spec, WindowLook.groundSpec(global, 20, 46));
        // A shell's ground leaves the tint to its pane: none here.
        assertEquals(0f, WindowLook.groundSpec(global, 20, 0).tintAlpha, 0f);
        assertEquals(1f, WindowLook.groundSpec(global, 20, 140).tintAlpha, 0f);
    }
}
