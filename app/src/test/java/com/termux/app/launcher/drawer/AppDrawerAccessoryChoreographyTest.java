package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerAccessoryChoreography.Band;
import com.termux.app.launcher.drawer.AppDrawerAccessoryChoreography.Result;

import org.junit.Test;

/**
 * The two accessory recipes.
 *
 * <p>Reference geometry is a 1080x2400 panel at 3x with the in-app keyboard up: the extra-keys row
 * sits inside the dock glass, the keyboard capsule floats 12px (4dp) below it.
 */
public class AppDrawerAccessoryChoreographyTest {

    private static final float EPS = 1e-4f;

    private static final float DOCK_BOTTOM = 1788f;
    private static final float SCREEN_BOTTOM = 2340f;
    private static final float CAPTURED_GAP = 12f;

    private static final Band EXTRA_KEYS = new Band(1620f, 120f);
    private static final Band KEYBOARD = new Band(1800f, 560f);

    /** The plane's bottom edge over the transition; at p = 0 it is the dock's own bottom. */
    private static float planeBottom(float p) {
        return AppDrawerTransitionGeometry.lerp(DOCK_BOTTOM, SCREEN_BOTTOM, p);
    }

    @Test
    public void roundedKeepsTheDockToKeyboardGapIdenticalAtEveryProgress() {
        // Byte-identical, not approximately: the whole point of the rounded recipe is that the one
        // measurement a user can see between the plane and the capsule never moves by a pixel.
        for (float p : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
            float bottom = planeBottom(p);
            Result r = AppDrawerAccessoryChoreography.resolve(
                true, p, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, bottom);
            assertEquals("p=" + p, CAPTURED_GAP, r.keyboardVisibleTopPx - bottom, 0f);
        }
    }

    @Test
    public void defaultStyleMovesExtraKeysAndKeyboardAsOneEntity() {
        float combined = EXTRA_KEYS.heightPx + KEYBOARD.heightPx;
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            Result r = AppDrawerAccessoryChoreography.resolve(
                false, p, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(p));
            assertEquals("p=" + p, r.extraKeysTranslationY, r.keyboardTranslationY, 0f);
            assertEquals("p=" + p, r.extraKeysAlpha, r.keyboardAlpha, 0f);
            // Neither band is ever clipped in this style; they slide as one slab.
            assertEquals("p=" + p, 0f, r.extraKeysClipTopPx, 0f);
            assertEquals("p=" + p, 0f, r.keyboardClipTopPx, 0f);
            assertTrue("p=" + p, r.keyboardTranslationY >= 0f && r.keyboardTranslationY <= combined);
        }
        Result end = AppDrawerAccessoryChoreography.resolve(
            false, 1f, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(1f));
        assertEquals(combined, end.keyboardTranslationY, EPS);
        assertEquals(0f, end.keyboardAlpha, EPS);
    }

    @Test
    public void bandHeightsAreNeverNegative() {
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            for (boolean rounded : new boolean[] {true, false}) {
                Result r = AppDrawerAccessoryChoreography.resolve(
                    rounded, p, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(p));
                assertTrue("p=" + p, r.keyboardVisibleHeightPx >= 0f);
                assertTrue("p=" + p, r.extraKeysVisibleHeightPx >= 0f);
                assertTrue("p=" + p, r.keyboardAlpha >= 0f && r.keyboardAlpha <= 1f);
            }
        }
        // A plane bottom driven past the capsule entirely — a rotation or an inset change landing
        // mid-drag — must collapse the band, not invert it.
        Result swallowed = AppDrawerAccessoryChoreography.resolve(
            true, 1f, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, 4000f);
        assertEquals(0f, swallowed.keyboardVisibleHeightPx, EPS);
        assertEquals(0f, swallowed.extraKeysVisibleHeightPx, EPS);
    }

    @Test
    public void blendAtZeroRevealIsTheInputBitForBit() {
        // The controller pipes every frame through blendTowardIdentity whether or not a search
        // keyboard exists, so a zero reveal has to be free — byte-identical, not within an epsilon,
        // or the three cases above stop describing what the drawer actually does.
        for (float p : new float[] {0f, 0.25f, 0.5f, 0.75f, 1f}) {
            for (boolean rounded : new boolean[] {true, false}) {
                Result r = AppDrawerAccessoryChoreography.resolve(
                    rounded, p, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(p));
                assertResultsEqual("p=" + p + " rounded=" + rounded,
                    r, AppDrawerAccessoryChoreography.blendTowardIdentity(r, 0f));
            }
        }
    }

    @Test
    public void blendAtFullRevealRestoresTheUntouchedBands() {
        for (float p : new float[] {0f, 0.5f, 1f}) {
            for (boolean rounded : new boolean[] {true, false}) {
                Result r = AppDrawerAccessoryChoreography.blendTowardIdentity(
                    AppDrawerAccessoryChoreography.resolve(
                        rounded, p, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(p)),
                    1f);
                String at = "p=" + p + " rounded=" + rounded;
                assertEquals(at, 0f, r.extraKeysTranslationY, 0f);
                assertEquals(at, 0f, r.keyboardTranslationY, 0f);
                assertEquals(at, 0f, r.extraKeysClipTopPx, 0f);
                assertEquals(at, 0f, r.keyboardClipTopPx, 0f);
                assertEquals(at, 1f, r.extraKeysAlpha, 0f);
                assertEquals(at, 1f, r.keyboardAlpha, 0f);
                assertEquals(at, EXTRA_KEYS.topPx, r.extraKeysVisibleTopPx, EPS);
                assertEquals(at, KEYBOARD.topPx, r.keyboardVisibleTopPx, EPS);
                // At least the whole band: the rounded style swallows the extra-keys row outright,
                // and a swallowed band cannot report the height it no longer has.
                assertTrue(at, r.extraKeysVisibleHeightPx >= EXTRA_KEYS.heightPx - EPS);
                assertTrue(at, r.keyboardVisibleHeightPx >= KEYBOARD.heightPx - EPS);
            }
        }
    }

    @Test
    public void blendIsMonotonicInTheRevealFraction() {
        // The keyboard rises on a spring, so k is sampled at arbitrary points: every step of the
        // reveal must give back displacement and opacity, never take some back.
        for (boolean rounded : new boolean[] {true, false}) {
            Result open = AppDrawerAccessoryChoreography.resolve(
                rounded, 1f, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(1f));
            float previousTranslation = Float.MAX_VALUE;
            float previousClip = Float.MAX_VALUE;
            float previousAlpha = -1f;
            for (int i = 0; i <= 100; i++) {
                float k = i / 100f;
                Result r = AppDrawerAccessoryChoreography.blendTowardIdentity(open, k);
                String at = "k=" + k + " rounded=" + rounded;
                assertTrue(at, r.keyboardTranslationY <= previousTranslation);
                assertTrue(at, r.keyboardClipTopPx <= previousClip);
                assertTrue(at, r.keyboardAlpha >= previousAlpha);
                assertTrue(at, r.keyboardAlpha <= 1f);
                assertTrue(at, r.keyboardVisibleHeightPx >= 0f);
                previousTranslation = r.keyboardTranslationY;
                previousClip = r.keyboardClipTopPx;
                previousAlpha = r.keyboardAlpha;
            }
        }
        // A reveal outside the unit range arrives clamped, like every other fraction here.
        Result open = AppDrawerAccessoryChoreography.resolve(
            false, 1f, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, planeBottom(1f));
        assertEquals(0f,
            AppDrawerAccessoryChoreography.blendTowardIdentity(open, 4f).keyboardTranslationY, 0f);
        assertEquals(open.keyboardTranslationY,
            AppDrawerAccessoryChoreography.blendTowardIdentity(open, -2f).keyboardTranslationY, 0f);
    }

    @Test
    public void bothStylesAreIdentityAtZeroProgress() {
        for (boolean rounded : new boolean[] {true, false}) {
            Result r = AppDrawerAccessoryChoreography.resolve(
                rounded, 0f, EXTRA_KEYS, KEYBOARD, CAPTURED_GAP, DOCK_BOTTOM);
            assertEquals(0f, r.extraKeysTranslationY, 0f);
            assertEquals(0f, r.keyboardTranslationY, 0f);
            assertEquals(0f, r.extraKeysClipTopPx, 0f);
            assertEquals(0f, r.keyboardClipTopPx, 0f);
            assertEquals(1f, r.extraKeysAlpha, 0f);
            assertEquals(1f, r.keyboardAlpha, 0f);
            assertEquals(EXTRA_KEYS.topPx, r.extraKeysVisibleTopPx, 0f);
            assertEquals(KEYBOARD.topPx, r.keyboardVisibleTopPx, 0f);
            assertEquals(EXTRA_KEYS.heightPx, r.extraKeysVisibleHeightPx, 0f);
            assertEquals(KEYBOARD.heightPx, r.keyboardVisibleHeightPx, 0f);
        }
    }

    private static void assertResultsEqual(String message, Result expected, Result actual) {
        assertEquals(message, expected.extraKeysTranslationY, actual.extraKeysTranslationY, 0f);
        assertEquals(message, expected.extraKeysClipTopPx, actual.extraKeysClipTopPx, 0f);
        assertEquals(message, expected.extraKeysVisibleTopPx, actual.extraKeysVisibleTopPx, 0f);
        assertEquals(message, expected.extraKeysVisibleHeightPx, actual.extraKeysVisibleHeightPx, 0f);
        assertEquals(message, expected.extraKeysAlpha, actual.extraKeysAlpha, 0f);
        assertEquals(message, expected.keyboardTranslationY, actual.keyboardTranslationY, 0f);
        assertEquals(message, expected.keyboardClipTopPx, actual.keyboardClipTopPx, 0f);
        assertEquals(message, expected.keyboardVisibleTopPx, actual.keyboardVisibleTopPx, 0f);
        assertEquals(message, expected.keyboardVisibleHeightPx, actual.keyboardVisibleHeightPx, 0f);
        assertEquals(message, expected.keyboardAlpha, actual.keyboardAlpha, 0f);
    }
}
