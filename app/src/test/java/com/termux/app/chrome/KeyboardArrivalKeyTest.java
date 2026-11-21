package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Floating H&rarr;T on pong (float4): the keyboard's card was built as the crossing began, and
 * the Terminal's rest pass built it again 57 ms after the landing. The arriving end is now the
 * rest's own build (the dress ladder under the arriving look), so the rest pass must find it under
 * the same {@link HeldBackground} key. The input that moved at the landing was the even-out token:
 * the global one moves whenever any band is measured again (the dock is, at every landing), while
 * the keyboard bakes only its own band's matrix.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardArrivalKeyTest {

    private static final TerminalContrastLevel LEVEL = TerminalContrastLevel.DEFAULT;
    private static final GlassBackdropCache.Band DOCK = GlassBackdropCache.Band.DOCK;
    private static final GlassBackdropCache.Band KEYBOARD = GlassBackdropCache.Band.KEYBOARD;
    /** The keyboard's card as laid out for the Terminal's rest, and the dock over two grounds. */
    private static final Rect KEYS = new Rect(21, 1700, 1059, 2354);
    private static final Rect DOCK_HOME = new Rect(0, 2000, 1080, 2200);
    private static final Rect DOCK_PILL = new Rect(21, 1500, 1059, 1598);

    private ChromeRenderer chrome;
    private FakeChromeSurfaces surfaces;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = Color.TRANSPARENT;
        chrome = new ChromeRenderer(surfaces, null);
        int dark = 0xFF2A3140;
        int light = 0xFFB7C9E9;
        chrome.ink().backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                return screenRect.top >= DOCK_HOME.top ? dark : light;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                return screenRect.top >= DOCK_HOME.top
                    ? new GlassBackdropCache.Lightness(13.4d, 30.2d, 63.8d, dark)
                    : new GlassBackdropCache.Lightness(57.86d, 80.58d, 94.69d, light);
            }
        });
        EvenOut.Ceiling[] ceilings = MaterialTerminalColorScheme.evenOutCeilings(
            RuntimeEnvironment.getApplication(), LEVEL);
        ChromeInk.EvenOutPolicy policy = new ChromeInk.EvenOutPolicy(LEVEL, ceilings[0],
            ceilings[1], ChromeInk.Polarity.PALE_INK);
        chrome.ink().setBandEvenOutPolicy(DOCK, policy);
        chrome.ink().setBandEvenOutPolicy(KEYBOARD, policy);
    }

    private static void idle() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
    }

    /** The keyboard glass's key as the activity names it, for one look's values. */
    @NonNull
    private List<Object> keyOf(@NonNull Object spec, @NonNull Object backdrop) {
        chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(KEYS));
        return HeldBackground.key("glass", spec, backdrop, 2.625f, null,
            chrome.ink().bandEvenOutChanges(KEYBOARD), 0xFF101010, 0xFF5070F0, "look");
    }

    @Test
    public void theRestPassFindsTheArrivingCardTheCrossingBuilt() {
        Object spec = "terminal-spec";
        Object backdrop = new Object();
        HeldBackground<Drawable> held = new HeldBackground<>();
        chrome.ink().bandEvenOutMatrix(DOCK, new Rect(DOCK_HOME));
        idle();

        // The crossing's begin: the arriving card, built under the Terminal's look and held.
        List<Object> arriving = keyOf(spec, backdrop);
        Drawable card = new ColorDrawable(Color.GRAY);
        held.hold(arriving, card);
        ColorMatrix keys = chrome.ink().lastBandEvenOutMatrix(KEYBOARD);
        assertNotNull("the keyboard band is evened out", keys);
        int generation = chrome.ink().evenOutGeneration();

        // The landing: the dock is measured again where it rests, on another ground.
        chrome.ink().bandEvenOutMatrix(DOCK, new Rect(DOCK_PILL));
        idle();
        assertNotEquals("the dock's new measure moves the global token (the old key input)",
            generation, chrome.ink().evenOutGeneration());

        // The Terminal's rest pass: same spec, backdrop and band; it reuses the card.
        assertSame("the rest pass reuses the arriving card", card,
            held.reuse(keyOf(spec, backdrop)));
    }

    @Test
    public void aKeyboardWhoseOwnEvenOutMovedIsBuiltAgain() {
        Object spec = "terminal-spec";
        Object backdrop = new Object();
        HeldBackground<Drawable> held = new HeldBackground<>();
        held.hold(keyOf(spec, backdrop), new ColorDrawable(Color.GRAY));
        int before = chrome.ink().bandEvenOutChanges(KEYBOARD);
        // The keyboard over other ground: its own matrix moves, and so does its token.
        chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(DOCK_HOME));
        assertNotEquals(before, chrome.ink().bandEvenOutChanges(KEYBOARD));
    }

    private static boolean same(@Nullable ColorMatrix a, @Nullable ColorMatrix b) {
        return java.util.Arrays.equals(a == null ? null : a.getArray(),
            b == null ? null : b.getArray());
    }

    /**
     * The arriving card is built after the crossing froze measuring (6dfc8da11's diagnosis for the
     * dock): a plain read then answers nothing and the card wore no even-out until the rest put it
     * back. Under the dock's plan the band's arriving even-out is worked out past the freeze over
     * the arriving page's ground, the card's own reads keep it through the travel, and the rest
     * measures the same matrix, so its token does not move and the card is not built again.
     */
    @Test
    public void theArrivingKeyboardsEvenOutIsWorkedOutPastTheFreezeAndTheRestKeepsIt() {
        int terminalGround = 0x73000000;
        boolean[] crossing = {false};
        chrome.ink().setMeasureFreeze(() -> crossing[0]);
        crossing[0] = true;
        ColorMatrix frozen = chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(KEYS));
        assertEquals("frozen, a plain read answers nothing", null, frozen);

        chrome.ink().standBandEvenOutOver(KEYBOARD, new Rect(KEYS), terminalGround);
        ColorMatrix stood = chrome.ink().lastBandEvenOutMatrix(KEYBOARD);
        assertNotNull("past the freeze the arriving end is worked out", stood);
        assertFalse("standing books nothing", chrome.isCommitPending());
        int token = chrome.ink().bandEvenOutChanges(KEYBOARD);
        assertTrue("the card's own read before the hold keeps it",
            same(stood, chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(KEYS))));
        chrome.holdEvenOut();
        assertTrue("held through the travel",
            same(stood, chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(KEYS))));

        // Landed on the Terminal: the freeze and the hold go, the band measures where it rests.
        crossing[0] = false;
        surfaces.frontGround = terminalGround;
        chrome.settleEvenOut();
        idle();
        assertTrue("the rest measures what the crossing began with",
            same(stood, chrome.ink().bandEvenOutMatrix(KEYBOARD, new Rect(KEYS))));
        assertEquals("so the card's key is unchanged", token,
            chrome.ink().bandEvenOutChanges(KEYBOARD));
    }
}
