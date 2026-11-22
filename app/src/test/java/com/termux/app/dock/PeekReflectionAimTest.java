package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Apart, the reflection is part of Home's card: it is aimed by Home's rest with Home in front,
 * with the Terminal in front (whose rows stand the row elsewhere over the parked host) and at
 * every point of both crossings, so nothing re-aims it while parked and nothing jumps at the
 * landing. Not apart, it follows the card cut now and the rows it was cut from.
 */
public class PeekReflectionAimTest {

    /** Home's rest card on the phone of record (Floating, Peek, 1080 wide). */
    private static final PeekGlassGeometry.Card HOME_REST =
        new PeekGlassGeometry.Card(40f, 96f, 1040f, 400f, 66f, 330f, 176f, 300f);
    /** A card cut with the Terminal's rows standing over the host. */
    private static final PeekGlassGeometry.Card TERMINAL_CUT =
        new PeekGlassGeometry.Card(40f, 62f, 1040f, 400f, 66f, 330f, 142f, 266f);
    /** Where Home's rest laid the row out over the host. */
    private static final float HOME_REST_ROWS = 16f;
    /** Where the live layout reads it with the Terminal in front. */
    private static final float TERMINAL_ROWS = -18f;

    private static float[] aim(boolean apart, PeekGlassGeometry.Card shown, float cutRows) {
        PeekGlassGeometry.Card card = PeekGlassReflection.aimCard(apart, HOME_REST, shown);
        float rowDy = PeekGlassReflection.aimRowDy(apart, HOME_REST, HOME_REST_ROWS, cutRows);
        return new float[] {rowDy, card.mirrorAxisY, card.top, card.reflectionDepth()};
    }

    @Test public void apartTheAimIsHomesRestWhicheverPageIsInFront() {
        float[] homeInFront = aim(true, HOME_REST, HOME_REST_ROWS);
        float[] terminalInFront = aim(true, TERMINAL_CUT, TERMINAL_ROWS);
        assertAimEquals(homeInFront, terminalInFront);
        // Both crossings, at every point: the live rows run between the two rests either way.
        for (int step = 0; step <= 20; step++) {
            float t = step / 20f;
            float towardHome = TERMINAL_ROWS + (HOME_REST_ROWS - TERMINAL_ROWS) * t;
            float towardTerminal = HOME_REST_ROWS + (TERMINAL_ROWS - HOME_REST_ROWS) * t;
            assertAimEquals(homeInFront, aim(true, TERMINAL_CUT, towardHome));
            assertAimEquals(homeInFront, aim(true, HOME_REST, towardHome));
            assertAimEquals(homeInFront, aim(true, TERMINAL_CUT, towardTerminal));
            assertAimEquals(homeInFront, aim(true, HOME_REST, towardTerminal));
        }
    }

    @Test public void notApartTheAimFollowsTheCardCutNow() {
        assertSame(TERMINAL_CUT, PeekGlassReflection.aimCard(false, HOME_REST, TERMINAL_CUT));
        assertEquals(TERMINAL_ROWS,
            PeekGlassReflection.aimRowDy(false, HOME_REST, HOME_REST_ROWS, TERMINAL_ROWS), 0f);
    }

    @Test public void beforeHomesFirstRestTheCardCutNowIsAimedBy() {
        assertSame(TERMINAL_CUT, PeekGlassReflection.aimCard(true, null, TERMINAL_CUT));
        assertEquals(TERMINAL_ROWS,
            PeekGlassReflection.aimRowDy(true, null, 0f, TERMINAL_ROWS), 0f);
    }

    private static void assertAimEquals(float[] expected, float[] actual) {
        String[] names = {"rowDy", "axis", "cardTop", "depth"};
        for (int i = 0; i < expected.length; i++)
            assertEquals(names[i], expected[i], actual[i], 0f);
    }
}
