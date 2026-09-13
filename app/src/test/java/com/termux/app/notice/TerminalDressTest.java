package com.termux.app.notice;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The dress is the one place a terminal-styled surface reads its shape and colour from, so the
 * arithmetic in it is worth asserting on its own: it answers for every screen in the app, with and
 * without a terminal on it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
@LooperMode(LooperMode.Mode.LEGACY)
public class TerminalDressTest {

    /** The pill takes the terminal's radius whole, until half its own height is less. */
    @Test
    public void theRadiusIsTheTerminalsCappedAtHalfTheHeight() {
        assertEquals(24f, TerminalDress.cornerRadiusPx(24f, 100, 1f), .001f);
        // A two-line pill is a rounded rectangle; a short one is a capsule, never a lozenge.
        assertEquals(20f, TerminalDress.cornerRadiusPx(40f, 40, 1f), .001f);
    }

    /**
     * A flush square terminal lends no radius at all, and a pill with literally square corners
     * reads as a torn rectangle. The hint cards keep 4dp for the same reason.
     */
    @Test
    public void aSquareTerminalStillSoftensItsNotices() {
        assertEquals(4f, TerminalDress.cornerRadiusPx(0f, 100, 1f), .001f);
        assertEquals(8f, TerminalDress.cornerRadiusPx(0f, 100, 2f), .001f);
        // Even the floor yields to a pill too short to wear it.
        assertEquals(3f, TerminalDress.cornerRadiusPx(0f, 6, 1f), .001f);
    }

    /** Before the pill has ever been measured there is no height to cap against. */
    @Test
    public void anUnmeasuredPillKeepsTheTerminalsOwnRadius() {
        assertEquals(24f, TerminalDress.cornerRadiusPx(24f, 0, 1f), .001f);
    }

    /**
     * The fill is what the terminal is painted with, over the window's own ground. A terminal that
     * paints nothing hands over transparent, and the pill is then simply the ground.
     */
    @Test
    public void aTerminalPaintingNothingLeavesThePillOnTheGround() {
        int fill = TerminalDress.fillColor(Color.TRANSPARENT, 0xFF102030);
        assertEquals(TerminalDress.FILL_ALPHA, Color.alpha(fill));
        assertEquals(0x10, Color.red(fill));
        assertEquals(0x20, Color.green(fill));
        assertEquals(0x30, Color.blue(fill));
    }

    /**
     * A glass pane's tint is an alpha meant to sit on a blur the pill has none of, so it is
     * composited on the ground rather than used raw — otherwise the pill is a translucent smear
     * where the slab beside it is a colour.
     */
    @Test
    public void aGlassTintIsCompositedOnTheGroundRatherThanUsedRaw() {
        int tint = Color.argb(128, 255, 255, 255);
        int fill = TerminalDress.fillColor(tint, 0xFF000000);
        assertEquals(TerminalDress.FILL_ALPHA, Color.alpha(fill));
        assertTrue("composited to " + Integer.toHexString(fill), Color.red(fill) > 100);
        assertTrue("composited to " + Integer.toHexString(fill), Color.red(fill) < 200);
    }

    /**
     * The opacity slider is the terminal's contract, not the pill's: a message on a terminal turned
     * down to a whisper still has to be legible.
     */
    @Test
    public void theFillIgnoresHowTransparentTheTerminalItselfIs() {
        int faint = TerminalDress.fillColor(Color.argb(40, 0x11, 0x22, 0x33), 0xFF112233);
        int solid = TerminalDress.fillColor(Color.argb(255, 0x11, 0x22, 0x33), 0xFF112233);
        assertEquals(TerminalDress.FILL_ALPHA, Color.alpha(faint));
        assertEquals(solid, faint);
    }

    /** With a terminal on screen the live numbers win outright. */
    @Test
    public void aLiveTerminalAnswersForItself() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TerminalDress dress = TerminalDress.resolve(activity, new TerminalDress.Source() {
            @Override public float terminalCornerRadiusPx() { return 37f; }
            @Override public int terminalFillColor() { return Color.argb(255, 9, 9, 9); }
        });
        assertEquals(37f, dress.terminalRadiusPx, .001f);
        assertEquals(9, Color.red(dress.fillColor));
        assertEquals(TerminalDress.FILL_ALPHA, Color.alpha(dress.fillColor));
    }

    /**
     * And with none — a Settings page, the keyboard colour scheme — the same numbers come out of
     * stored preferences, so the pill is recognisably the same thing there.
     */
    @Test
    public void aScreenWithoutATerminalReadsTheStoredRadius() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity);
        org.junit.Assume.assumeNotNull(preferences);
        float density = activity.getResources().getDisplayMetrics().density;

        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT);
        preferences.setTerminalCornerRadius(30);
        assertEquals(30 * density, TerminalDress.stored(activity).terminalRadiusPx, .001f);

        // Floating's slabs round by the dock capsule, capped where the panes cap it, so the knob is
        // not what answers there.
        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        assertEquals(TerminalDress.FLOATING_RADIUS_DP * density,
            TerminalDress.stored(activity).terminalRadiusPx, .001f);
        assertNotEquals(30 * density, TerminalDress.stored(activity).terminalRadiusPx, .001f);
    }

    /** The hairline is the one the hint cards are edged with, not a fresh number. */
    @Test
    public void theHairlineIsTheHintSurfacesOwn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TerminalDress dress = TerminalDress.stored(activity);
        assertEquals(TerminalDress.STROKE_ALPHA, Color.alpha(dress.strokeColor));
        assertTrue(dress.strokeWidthPx >= 1f);
        assertEquals(TerminalDress.SUB_TEXT_ALPHA, Color.alpha(dress.subTextColor));
        assertEquals(255, Color.alpha(dress.textColor));
    }
}
